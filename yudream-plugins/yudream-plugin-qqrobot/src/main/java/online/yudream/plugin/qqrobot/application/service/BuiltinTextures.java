package online.yudream.plugin.qqrobot.application.service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内置原版物品/方块纹理。
 *
 * <p>后台自己的预览（背包试渲染、单个物品图）拿不到服务端资源包，之前只能退化成
 * 首字母占位。这里把游戏客户端里的 {@code textures/item} 与 {@code textures/block}
 * 打进插件资源，路径与服务端 {@code PackTextureIndex} 的索引键保持一致，
 * 于是预览和服务端上报走同一套查表逻辑。</p>
 *
 * <p>纹理打包成单个 {@code builtin-textures.zip}（2131 张，约 500KB 未压缩），
 * 首次用到物品渲染时整包读进内存；data URI 按需生成并缓存，避免每次渲染
 * 重复做 base64。</p>
 */
public class BuiltinTextures {
    private static final String ARCHIVE = "/builtin-textures.zip";
    private static final int DEFAULT_SIZE = 64;
    private static final int CACHE_LIMIT = 4096;
    /**
     * 方块拆图后的面后缀，按“最像图标”排序：正面 > 侧面 > 顶面。
     * 顶面放后面是因为原版顶面 UV 是倒置的，直接用会上下颠倒。
     */
    private static final List<String> FACE_SUFFIXES = List.of("_front", "_side", "_top", "_still", "_all");
    /**
     * 这些物品在原版里只有方块贴图，item 目录下同名文件是另一件东西
     * （例如 {@code item/quartz} 是下界石英，而石英块要 {@code block/quartz_block_side}）。
     */
    private static final Set<String> BLOCK_FIRST = Set.of(
            "shulker_box", "ancient_debris", "coal_block", "iron_block", "gold_block",
            "diamond_block", "emerald_block", "lapis_block", "redstone_block", "netherite_block",
            "copper_block", "quartz_block", "hay_block", "slime_block", "honey_block",
            "amethyst_block", "respawn_anchor", "lodestone", "bookshelf", "crafting_table",
            "furnace", "chest", "barrel", "smithing_table", "cartography_table", "fletching_table",
            "loom", "stonecutter", "grindstone", "composter", "beehive", "note_block", "jukebox");
    /**
     * 这些方块贴图本身是镂空的（只有外壳线条），直接渲染会透出卡片背景，
     * 看起来像缺图，所以额外垫一层中性底。整块不透明的方块不要加，否则会多出灰边。
     */
    private static final Set<String> HOLLOW = Set.of(
            "shulker_box", "black_shulker_box", "blue_shulker_box", "brown_shulker_box",
            "cyan_shulker_box", "gray_shulker_box", "green_shulker_box", "light_blue_shulker_box",
            "light_gray_shulker_box", "lime_shulker_box", "magenta_shulker_box", "orange_shulker_box",
            "pink_shulker_box", "purple_shulker_box", "red_shulker_box", "white_shulker_box",
            "yellow_shulker_box", "chest", "ender_chest", "trapped_chest", "barrel");
    /**
     * 游戏里有独立外观、但客户端没给同名的 item 贴图（附魔装备直接复用基础物品）。
     * 键是物品 ID，值是纹理档案里的相对路径（不含 .png）。
     */
    private static final Map<String, String> ALIASES = Map.of(
            "enchanted_golden_apple", "item/golden_apple",
            "enchanted_book", "item/enchanted_book",
            "tipped_arrow", "item/tipped_arrow_base",
            "lingering_potion", "item/lingering_potion",
            "splash_potion", "item/splash_potion",
            "potion", "item/potion"
    );

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final int size;
    private final Set<String> blockFirst;
    private volatile Map<String, byte[]> pack;

    public BuiltinTextures() {
        this(DEFAULT_SIZE);
    }

    public BuiltinTextures(int size) {
        this.size = Math.max(16, Math.min(256, size));
        this.blockFirst = BLOCK_FIRST;
    }

    /**
     * 按物品 ID 找图标：先 item，再 block（红石粉、甘蔗这类只有方块贴图），
     * 最后尝试去掉命名空间。返回 PNG data URI，找不到返回空。
     */
    public Optional<String> icon(String itemId) {
        return icon(itemId, 0);
    }

    /**
     * @param tint 颜色覆盖，0 表示原色。皮革装备在游戏里是灰白底图 + 队标着色，
     *             这里按同样方式乘算，避免渲染成一身白。
     */
    public Optional<String> icon(String itemId, int tint) {
        if (itemId == null || itemId.isBlank() || "minecraft:air".equals(itemId)) {
            return Optional.empty();
        }
        String key = normalize(itemId) + (tint == 0 ? "" : "#" + Integer.toHexString(tint));
        String cached = cache.get(key);
        if (cached != null) {
            return cached.isBlank() ? Optional.empty() : Optional.of(cached);
        }
        String uri = load(key).map(png -> {
            byte[] tinted = tint == 0 ? png : tinted(png, tint);
            // 方块贴图常见整块透明（空潜影盒只有边框），渲染器里衬一层中性底，
            // 否则透明区域会直接透出卡片背景，看起来像缺图。
            return dataUri(HOLLOW.contains(body(key)) ? backed(tinted) : tinted);
        }).orElse("");
        if (cache.size() < CACHE_LIMIT) {
            cache.put(key, uri);
        }
        return uri.isBlank() ? Optional.empty() : Optional.of(uri);
    }

    /** 皮革装备默认的棕色（原版未染色时用的 0xA06540）。 */
    public static int defaultLeatherColor(String itemId) {
        return itemId != null && itemId.contains("leather_") ? 0xA06540 : 0;
    }

    /** 只查内置包，不联网、不读磁盘。 */
    private Optional<byte[]> load(String key) {
        String plain = key.contains("#") ? key.substring(0, key.indexOf('#')) : key;
        String body = plain.substring(plain.indexOf(':') + 1);
        String alias = ALIASES.get(body);
        if (alias != null) {
            byte[] png = archive().get(alias + ".png");
            if (png != null) {
                return Optional.of(png);
            }
        }
        boolean block = blockFirst.contains(body);
        List<String> candidates = new ArrayList<>();
        for (String prefix : block ? new String[]{ "block/", "item/" } : new String[]{ "item/", "block/" }) {
            candidates.add(prefix + body);
            // 很多方块在 1.21 之后按面拆图（ancient_debris_side/top、furnace_front…），
            // 没有同名整图时按原版模型的默认面回退。
            for (String suffix : FACE_SUFFIXES) {
                candidates.add(prefix + body + suffix);
            }
        }
        for (String candidate : candidates) {
            byte[] png = archive().get(candidate + ".png");
            if (png != null) {
                return Optional.of(png);
            }
        }
        return Optional.empty();
    }

    /** 首次调用时把整包读进内存；zip 缺失或损坏时返回空表，退化成首字母占位。 */
    private Map<String, byte[]> archive() {
        Map<String, byte[]> loaded = pack;
        if (loaded != null) {
            return loaded;
        }
        synchronized (this) {
            if (pack != null) {
                return pack;
            }
            Map<String, byte[]> map = new ConcurrentHashMap<>();
            try (InputStream stream = BuiltinTextures.class.getResourceAsStream(ARCHIVE)) {
                if (stream != null) {
                    byte[] raw = stream.readAllBytes();
                    try (java.util.zip.ZipInputStream zip =
                                 new java.util.zip.ZipInputStream(new ByteArrayInputStream(raw))) {
                        for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                            if (entry.isDirectory() || !entry.getName().endsWith(".png")) {
                                continue;
                            }
                            map.put(entry.getName(), zip.readAllBytes());
                        }
                    }
                }
            } catch (IOException ignored) {
                // 包损坏时保持空表。
            }
            pack = map;
            return map;
        }
    }

    /**
     * 统一缩放到 64px：原版贴图有 16×16 也有 32×32 的动画首帧，
     * 渲染时再靠 CSS 放大，先缩小能显著压低 data URI 体积。
     */
    private String dataUri(byte[] png) {
        byte[] scaled = scale(png);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(scaled);
    }

    private byte[] scale(byte[] png) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));
            if (source == null || (source.getWidth() <= size && source.getHeight() <= size)) {
                return png;
            }
            int longest = Math.max(source.getWidth(), source.getHeight());
            int width = Math.max(1, source.getWidth() * size / longest);
            int height = Math.max(1, source.getHeight() * size / longest);
            BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = target.createGraphics();
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(source, 0, 0, width, height, null);
            graphics.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(target, "png", out);
            return out.toByteArray();
        } catch (IOException exception) {
            return png;
        }
    }

    /**
     * 按原版 {@code layer1 = base * color} 的规则给灰白底图着色：
     * 只在像素已不透明处乘 RGB，保留 alpha，白边不会变糊。
     */
    private byte[] tinted(byte[] png, int color) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));
            if (source == null) {
                return png;
            }
            int red = (color >> 16) & 0xFF;
            int green = (color >> 8) & 0xFF;
            int blue = color & 0xFF;
            BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    int argb = source.getRGB(x, y);
                    int alpha = (argb >>> 24) & 0xFF;
                    if (alpha == 0) {
                        continue;
                    }
                    int r = ((argb >> 16) & 0xFF) * red / 255;
                    int g = ((argb >> 8) & 0xFF) * green / 255;
                    int b = (argb & 0xFF) * blue / 255;
                    target.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(target, "png", out);
            return out.toByteArray();
        } catch (IOException exception) {
            return png;
        }
    }

    /** 给方块类图标垫一层圆角中性底，透明区域不会透出卡片背景。 */
    private byte[] backed(byte[] png) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));
            if (source == null) {
                return png;
            }
            int side = Math.max(source.getWidth(), source.getHeight());
            BufferedImage target = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = target.createGraphics();
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new java.awt.Color(0x2A, 0x2A, 0x33, 0xE0));
            int radius = Math.max(2, side / 6);
            graphics.fillRoundRect(0, 0, side, side, radius, radius);
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(source, (side - source.getWidth()) / 2, (side - source.getHeight()) / 2, null);
            graphics.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(target, "png", out);
            return out.toByteArray();
        } catch (IOException exception) {
            return png;
        }
    }

    private static String normalize(String id) {
        String value = id.trim().toLowerCase(Locale.ROOT);
        if (value.endsWith(".png")) {
            value = value.substring(0, value.length() - 4);
        }
        return value.contains(":") ? value : "minecraft:" + value;
    }

    private static String body(String key) {
        String plain = key.contains("#") ? key.substring(0, key.indexOf('#')) : key;
        return plain.substring(plain.indexOf(':') + 1);
    }

}
