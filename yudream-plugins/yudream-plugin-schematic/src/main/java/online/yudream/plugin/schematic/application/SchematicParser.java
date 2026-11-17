package online.yudream.plugin.schematic.application;

import online.yudream.plugin.schematic.domain.Schematic;
import online.yudream.plugin.schematic.domain.Schematic.Block;
import online.yudream.plugin.schematic.infrastructure.NbtReader;
import java.util.*;
import static online.yudream.plugin.schematic.infrastructure.NbtReader.compound;

public final class SchematicParser {
    public static final long MAX_VOLUME = 16_000_000;
    public static final int MAX_BLOCKS = 350_000;
    private record Cell(int x, int y, int z) { }

    public Schematic parse(String filename, byte[] bytes) {
        if (!supported(filename)) throw new IllegalArgumentException("仅支持 .litematic 和 Java 结构 .nbt");
        Map<String, Object> root = NbtReader.read(bytes);
        Map<Cell, String> cells = new HashMap<>();
        int[] min = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] max = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        long volume = 0;
        Map<String, Object> meta = root.get("Metadata") instanceof Map ? compound(root.get("Metadata")) : Map.of();
        if (root.get("Regions") instanceof Map) {
            Map<String, Object> regions = compound(root.get("Regions"));
            if (regions.isEmpty() || regions.size() > 256) throw new IllegalArgumentException("投影区域须为 1–256 个");
            for (Object value : regions.values()) {
                var region = compound(value);
                int[] size = xyz(compound(region.get("Size"))), pos = xyz(compound(region.get("Position")));
                int sx = dimension(size[0]), sy = dimension(size[1]), sz = dimension(size[2]);
                volume = Math.addExact(volume, (long) sx * sy * sz);
                if (volume > MAX_VOLUME) throw new IllegalArgumentException("区域总体积超过 1600 万");
                int ox = origin(pos[0], size[0]), oy = origin(pos[1], size[1]), oz = origin(pos[2], size[2]);
                bounds(min, max, ox, oy, oz, sx, sy, sz);
                var palette = palette(region.get("BlockStatePalette"));
                if (!(region.get("BlockStates") instanceof long[] packed)) throw new IllegalArgumentException("缺少方块位数组");
                int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
                long count = (long) sx * sy * sz;
                if (packed.length < (count * bits + 63) / 64) throw new IllegalArgumentException("方块位数组被截断");
                long mask = (1L << bits) - 1;
                for (int i = 0; i < count; i++) {
                    long bit = (long) i * bits;
                    int word = (int) (bit >>> 6), offset = (int) (bit & 63);
                    long state = packed[word] >>> offset;
                    if (offset + bits > 64) state |= packed[word + 1] << (64 - offset);
                    int index = (int) (state & mask);
                    if (index >= palette.size()) throw new IllegalArgumentException("方块调色板索引越界");
                    put(cells, new Cell(ox + i % sx, oy + i / (sx * sz), oz + i / sx % sz), palette.get(index));
                }
            }
        } else if (root.get("size") instanceof List<?> && root.get("blocks") instanceof List<?>) {
            int[] size = triple(root.get("size"));
            int sx = positive(size[0]), sy = positive(size[1]), sz = positive(size[2]);
            volume = (long) sx * sy * sz;
            if (volume > MAX_VOLUME) throw new IllegalArgumentException("结构体积超过 1600 万");
            bounds(min, max, 0, 0, 0, sx, sy, sz);
            Object rawPalette = root.get("palette");
            if (rawPalette == null && root.get("palettes") instanceof List<?> palettes && !palettes.isEmpty()) rawPalette = palettes.getFirst();
            var palette = palette(rawPalette);
            for (Object item : (List<?>) root.get("blocks")) {
                var block = compound(item); int[] pos = triple(block.get("pos")); int index = number(block, "state").intValue();
                if (pos[0] < 0 || pos[0] >= sx || pos[1] < 0 || pos[1] >= sy || pos[2] < 0 || pos[2] >= sz || index < 0 || index >= palette.size()) throw new IllegalArgumentException("结构方块坐标或状态越界");
                put(cells, new Cell(pos[0], pos[1], pos[2]), palette.get(index));
            }
        } else throw new IllegalArgumentException("无法识别结构：需要 Litematica Regions 或 Java 结构 size/palette/blocks；不支持基岩版小端 NBT");
        if (cells.isEmpty()) throw new IllegalArgumentException("投影中没有可渲染方块");
        List<Block> blocks = new ArrayList<>(cells.size()); Map<String, Long> materials = new TreeMap<>();
        cells.forEach((p, state) -> { blocks.add(new Block(p.x - min[0], p.y - min[1], p.z - min[2], state)); materials.merge(state, 1L, Long::sum); });
        return new Schematic(text(meta, "Name", filename), text(meta, "Author", "未知"), number(meta, "TimeCreated").longValue(),
                number(root, "Version").intValue(), number(root, "MinecraftDataVersion").intValue() != 0 ? number(root, "MinecraftDataVersion").intValue() : number(root, "DataVersion").intValue(),
                max[0] - min[0], max[1] - min[1], max[2] - min[2], volume, List.copyOf(blocks), materials);
    }
    public static boolean supported(String name) { return name != null && name.toLowerCase(Locale.ROOT).matches(".*\\.(litematic|nbt)$"); }
    private static List<String> palette(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty() || list.size() > 65536) throw new IllegalArgumentException("无效的方块调色板");
        return list.stream().map(v -> { var state = compound(v); String name = text(state, "Name", ""); if (!name.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("无效的方块名称"); return name; }).toList();
    }
    private static void put(Map<Cell, String> cells, Cell p, String state) {
        if (Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:structure_void").contains(state)) cells.remove(p);
        else cells.put(p, state);
        if (cells.size() > MAX_BLOCKS) throw new IllegalArgumentException("非空气方块超过 35 万，建议按区域拆分文件");
    }
    private static int dimension(int v) { if (v == 0 || Math.abs((long) v) > 4096) throw new IllegalArgumentException("单轴尺寸须为 1–4096"); return Math.abs(v); }
    private static int positive(int v) { if (v < 1) throw new IllegalArgumentException("结构尺寸必须为正数"); return dimension(v); }
    private static int origin(int p, int size) { if (Math.abs((long) p) > 1_000_000) throw new IllegalArgumentException("区域坐标过大"); return p + Math.min(0, size + 1); }
    private static void bounds(int[] min, int[] max, int x, int y, int z, int sx, int sy, int sz) {
        int[] p = {x, y, z}, s = {sx, sy, sz};
        for (int a = 0; a < 3; a++) { min[a] = Math.min(min[a], p[a]); max[a] = Math.max(max[a], p[a] + s[a]); if (max[a] - min[a] > 8192) throw new IllegalArgumentException("投影包围盒跨度超过 8192"); }
    }
    private static int[] xyz(Map<String, Object> m) { return new int[]{number(m, "x").intValue(), number(m, "y").intValue(), number(m, "z").intValue()}; }
    private static int[] triple(Object value) { if (!(value instanceof List<?> l) || l.size() != 3 || l.stream().anyMatch(v -> !(v instanceof Number))) throw new IllegalArgumentException("无效的三维坐标"); return l.stream().mapToInt(v -> ((Number) v).intValue()).toArray(); }
    private static Number number(Map<String, Object> m, String key) { return m.get(key) instanceof Number n ? n : 0; }
    private static String text(Map<String, Object> m, String key, String fallback) { String v = String.valueOf(m.getOrDefault(key, fallback)); return v.substring(0, Math.min(v.length(), 160)); }
}
