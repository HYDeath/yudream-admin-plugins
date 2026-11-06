package online.yudream.plugin.qqrobot.application.service;

import online.yudream.base.plugin.spi.system.render.PluginRenderedImage;
import online.yudream.base.plugin.spi.system.render.PluginRenderService;
import online.yudream.plugin.qqrobot.domain.ItemView;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 把物品/背包快照排版成 PNG。所有图片都来自服务端上报的
 * data URI，HTML 不访问外网，渲染结果可缓存。
 */
public class ItemCardRenderer {
    private static final int STORAGE_SLOTS = 27;
    private static final int HOTBAR_SLOTS = 9;
    private static final int ARMOR_SLOTS = 4;
    private static final int MAX_CONTAINER_DEPTH = 2;
    /** 槽位与单物品卡的图标尺寸；原版纹理 16×16，靠 image-rendering:pixelated 放大。 */
    private static final int SLOT_ICON_SIZE = 48;
    private static final int CARD_ICON_SIZE = 160;

    private final RenderSupport render;

    public ItemCardRenderer(RenderSupport render) {
        this.render = render;
    }

    public ItemCardRenderer(PluginRenderService render) {
        this(new RenderSupport(render));
    }

    /**
     * 主背包按 Minecraft 真实顺序渲染：27 格存储 + 9 格快捷栏，随后护甲与副手。
     * 输入可以少于 41 格，缺失槽位按空格处理。
     */
    public CompletableFuture<PluginRenderedImage> inventory(String title, List<ItemView> items) {
        return inventory(title, items, "", "");
    }

    /** 带玩家皮肤与副标题的版本；皮肤是 data URI，由调用方保证不访问外网。 */
    public CompletableFuture<PluginRenderedImage> inventory(String title, List<ItemView> items,
                                                           String skinDataUri, String subtitle) {
        return safely(() -> render.html(inventoryHtml(title, items, skinDataUri, subtitle),
                "#inventory-card", "背包渲染"), "背包渲染");
    }

    public CompletableFuture<PluginRenderedImage> single(ItemView item) {
        return safely(() -> render.html(singleHtml(item), "#item-card", "物品渲染"), "物品渲染");
    }

    private static CompletableFuture<PluginRenderedImage> safely(java.util.function.Supplier<PluginRenderedImage> action,
                                                                 String scene) {
        try {
            return CompletableFuture.completedFuture(action.get());
        } catch (RuntimeException exception) {
            if (exception instanceof QqRobotException qqRobotException) {
                return CompletableFuture.failedFuture(qqRobotException);
            }
            return CompletableFuture.failedFuture(QqRobotException.unavailable(
                    RenderSupport.CAPABILITY_NAME + "渲染失败（" + scene + "）：" + exception.getMessage()));
        }
    }

    private String inventoryHtml(String title, List<ItemView> items, String skinDataUri, String subtitle) {
        List<ItemView> normalized = items == null ? List.of() : items.stream().map(ItemView::normalized).toList();
        // 参考 QqRobot 的「玩家背包」版式：左侧护甲两格、中间立体皮肤、右侧副手，
        // 下方 27 格存储 + 9 格快捷栏。装备不再单列，避免和护甲重复。
        StringBuilder equipment = new StringBuilder();
        equipment.append(equipSlot(itemAt(normalized, ARMOR_OFFSET + 3), "头盔"));
        equipment.append(equipSlot(itemAt(normalized, ARMOR_OFFSET + 2), "胸甲"));
        equipment.append(equipSlot(itemAt(normalized, ARMOR_OFFSET + 1), "护腿"));
        equipment.append(equipSlot(itemAt(normalized, ARMOR_OFFSET), "靴子"));
        String skin = skinDataUri == null || !skinDataUri.startsWith("data:image/")
                ? "<div class=\"skin-empty\"></div>"
                : "<img class=\"skin\" src=\"" + skinDataUri + "\" alt=\"\">";
        String offhand = equipSlot(itemAt(normalized, ARMOR_OFFSET + 4), "副手");
        StringBuilder storage = new StringBuilder();
        for (int index = HOTBAR_SLOTS; index < STORAGE_SLOTS + HOTBAR_SLOTS; index++) {
            storage.append(slot(itemAt(normalized, index), 0));
        }
        StringBuilder hotbar = new StringBuilder();
        for (int index = 0; index < HOTBAR_SLOTS; index++) {
            hotbar.append(slot(itemAt(normalized, index), 0));
        }
        List<String> containers = containers(normalized);
        return page("<div id=\"inventory-card\">"
                + "<div class=\"head\"><div class=\"title\">" + escape(title) + "</div>"
                + (subtitle == null || subtitle.isBlank() ? ""
                : "<div class=\"subtitle\">" + escape(subtitle) + "</div>") + "</div>"
                + "<div class=\"player\">"
                + "<div class=\"equip left\">" + equipment + "</div>"
                + "<div class=\"figure\">" + skin + "<div class=\"figure-glow\"></div></div>"
                + "<div class=\"equip right\">" + offhand + "</div>"
                + "</div>"
                + "<div class=\"grid\">" + storage + "</div>"
                + "<div class=\"section\"><div class=\"section-label\">快捷栏</div><div class=\"grid hotbar\">"
                + hotbar + "</div></div>"
                + String.join("", containers)
                + "</div>");
    }

    private static final int ARMOR_OFFSET = STORAGE_SLOTS + HOTBAR_SLOTS;

    private String equipSlot(ItemView item, String hint) {
        if (item == null || item.empty()) {
            return "<div class=\"equip-slot empty\" title=\"" + hint + "\"></div>";
        }
        String count = item.count() > 1 ? "<span class=\"count\">" + item.count() + "</span>" : "";
        String durability = durabilityBar(item);
        return "<div class=\"equip-slot\">" + icon(item, SLOT_ICON_SIZE) + count + durability + "</div>";
    }

    private static String durabilityBar(ItemView item) {
        if (item.maxDamage() <= 0 || item.damage() <= 0) {
            return "";
        }
        int percent = Math.max(0, Math.min(100, (item.maxDamage() - item.damage()) * 100 / item.maxDamage()));
        return "<span class=\"durability\"><span style=\"width:" + percent + "%\"></span></span>";
    }

    private List<String> containers(List<ItemView> items) {
        List<String> blocks = new java.util.ArrayList<>();
        for (int index = 0; index < items.size() && blocks.size() < 6; index++) {
            ItemView item = items.get(index);
            if (item.openedContainer()) {
                blocks.add(containerBlock(item, index, 1));
            }
        }
        return blocks;
    }

    private String containerBlock(ItemView item, int index, int depth) {
        if (depth > MAX_CONTAINER_DEPTH) {
            return "";
        }
        StringBuilder grid = new StringBuilder();
        for (int slot = 0; slot < 27; slot++) {
            ItemView content = slot < item.container().size() ? item.container().get(slot) : null;
            grid.append(slot(content, depth));
        }
        StringBuilder nested = new StringBuilder();
        if (depth < MAX_CONTAINER_DEPTH) {
            for (ItemView content : item.container()) {
                if (content.openedContainer()) {
                    nested.append(containerBlock(content, index, depth + 1));
                }
            }
        }
        return "<div class=\"section container\"><div class=\"section-label\">潜影盒 · "
                + escape(stripColors(displayName(item))) + "</div><div class=\"grid\">" + grid + "</div>" + nested + "</div>";
    }

    private static ItemView itemAt(List<ItemView> items, int index) {
        return index < items.size() ? items.get(index) : null;
    }

    private String singleHtml(ItemView item) {
        ItemView value = item.normalized();
        StringBuilder tooltip = new StringBuilder();
        tooltip.append("<div class=\"name ").append(rarityClass(value.rarity())).append("\">")
                .append(styled(value.displayName().isBlank() ? prettify(value.shortId()) : value.displayName()))
                .append("</div>");
        for (String line : value.lore()) {
            tooltip.append("<div class=\"lore\">").append(styled(line)).append("</div>");
        }
        for (String line : value.enchantments()) {
            tooltip.append("<div class=\"ench\">").append(escape(line)).append("</div>");
        }
        StringBuilder extras = new StringBuilder();
        if (value.maxDamage() > 0 && value.damage() > 0) {
            int remaining = Math.max(0, value.maxDamage() - value.damage());
            int percent = Math.max(0, Math.min(100, remaining * 100 / value.maxDamage()));
            extras.append("<div class=\"durability\"><span style=\"width:").append(percent)
                    .append("%\"></span></div><div class=\"lore\">耐久 ").append(remaining)
                    .append(" / ").append(value.maxDamage()).append("</div>");
        }
        if (value.openedContainer()) {
            extras.append(containerBlock(value, 0, 1));
        }
        return page("<div id=\"item-card\" class=\"single\">" + icon(value, CARD_ICON_SIZE)
                + "<div class=\"single-tip\">" + tooltip + extras + "</div></div>");
    }

    private String slot(ItemView item, int depth) {
        if (item == null || item.empty()) {
            return "<div class=\"slot empty\"></div>";
        }
        StringBuilder extra = new StringBuilder();
        if (!item.enchantments().isEmpty()) {
            extra.append("<div class=\"glint\"></div>");
        }
        if (item.openedContainer()) {
            extra.append("<span class=\"opened\"></span>");
        }
        String count = item.count() > 1 ? "<span class=\"count\">" + item.count() + "</span>" : "";
        String durability = "";
        durability = durabilityBar(item);
        String tooltip = "<div class=\"tooltip\"><div class=\"tt-name " + rarityClass(item.rarity()) + "\">"
                + styled(displayName(item)) + "</div>"
                + item.lore().stream().map(line -> "<div class=\"tt-lore\">" + styled(line) + "</div>")
                .reduce("", String::concat)
                + item.enchantments().stream().map(line -> "<div class=\"tt-ench\">" + escape(line) + "</div>")
                .reduce("", String::concat)
                + "</div>";
        String label = depth == 0
                ? "<span class=\"label\">" + escape(stripColors(displayName(item))) + "</span>" : "";
        return "<div class=\"cell\"><div class=\"slot\">" + extra + icon(item, SLOT_ICON_SIZE) + count
                + durability + tooltip + "</div>" + label + "</div>";
    }

    private String icon(ItemView item, int size) {
        if (!item.iconDataUri().isBlank()) {
            return "<img class=\"icon\" style=\"width:" + size + "px;height:" + size + "px\" src=\""
                    + item.iconDataUri() + "\" alt=\"\">";
        }
        String label = prettify(item.shortId());
        String initial = label.isEmpty() ? "?" : label.substring(0, 1).toUpperCase();
        return "<div class=\"fallback\" style=\"width:" + size + "px;height:" + size + "px\" title=\""
                + escape(item.shortId()) + "\">" + escape(initial) + "</div>";
    }

    private String page(String body) {
        return """
                <!doctype html><html><head><meta charset="utf-8"><style>
                *{box-sizing:border-box;margin:0;padding:0}
                body{background:transparent;color:#e8e8ef;font-family:"Microsoft YaHei","Noto Sans SC",sans-serif;padding:4px}
                #inventory-card,#item-card{background:#101014;border-radius:16px;padding:24px;width:max-content}
                .title{font-size:22px;font-weight:700;margin-bottom:18px;color:#f5f5fa}
                .grid{display:grid;grid-template-columns:repeat(9,72px);column-gap:10px;row-gap:24px}
                .section{margin-top:20px}
                .section.container{margin-top:14px;padding:12px;border-radius:12px;background:#141419;border:1px solid #2a2a35}
                .section-label{font-size:12px;color:#8b8ba0;margin-bottom:8px;letter-spacing:1px}
                .head{display:flex;align-items:baseline;gap:12px;margin-bottom:16px}
                .subtitle{font-size:12px;color:#8b8ba0}
                .player{display:flex;align-items:center;justify-content:center;gap:28px;margin:0 0 24px;
                  padding:18px 24px;border-radius:14px;background:radial-gradient(circle at 50% 40%,#24242f,#141419 70%);
                  border:1px solid #2b2b36}
                .equip{display:flex;flex-direction:column;gap:10px}
                .equip-slot{position:relative;display:flex;align-items:center;justify-content:center;width:64px;height:64px;
                  border-radius:30px 6px 30px 6px;background:linear-gradient(160deg,#2a2a35,#17171e);
                  border:1px solid #3a3a48;box-shadow:inset 0 0 0 2px #101015}
                .equip-slot.empty{background:#15151c;border-style:dashed;border-color:#2e2e3a;box-shadow:none}
                .figure{position:relative;width:180px;height:260px;display:flex;align-items:flex-end;justify-content:center}
                .skin{height:100%;image-rendering:pixelated;filter:drop-shadow(0 10px 18px rgba(0,0,0,.55))}
                .skin-empty{width:96px;height:180px;border-radius:12px;
                  background:linear-gradient(180deg,#2b2b38,#1b1b24);border:1px dashed #3a3a48}
                .figure-glow{position:absolute;bottom:0;left:50%;transform:translateX(-50%);width:150px;height:14px;
                  border-radius:50%;background:radial-gradient(ellipse,rgba(120,130,255,.35),transparent 70%)}
                .cell{display:flex;flex-direction:column;align-items:center;gap:3px;width:72px}
                .slot{position:relative;display:flex;align-items:center;justify-content:center;
                  width:72px;height:72px;border-radius:10px;background:linear-gradient(145deg,#23232d,#18181f);
                  border:1px solid #34343f;box-shadow:inset 0 0 0 2px #0d0d11;overflow:visible}
                .slot.empty{background:#171720;border-color:#26262f}
                .slot:hover{outline:2px solid #8c8cff;z-index:5}
                .grid.hotbar{margin-top:2px}
                .icon{image-rendering:pixelated;filter:drop-shadow(0 4px 6px rgba(0,0,0,.45))}
                .fallback{display:flex;align-items:center;justify-content:center;border-radius:8px;
                  background:#2c2c38;color:#9a9ab0;font-size:20px;font-weight:700}
                .count{position:absolute;right:6px;bottom:4px;font-size:15px;font-weight:700;
                  text-shadow:0 1px 2px #000}
                .durability{position:absolute;left:6px;right:6px;bottom:5px;height:3px;border-radius:2px;background:#2c2c38}
                .durability > span{display:block;height:100%;border-radius:2px;
                  background:linear-gradient(90deg,#3ddc84,#a3e635)}
                .label{display:block;width:72px;text-align:center;font-size:10px;line-height:1.2;
                  color:#8b8ba0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
                .glint{position:absolute;inset:0;border-radius:10px;
                  background:linear-gradient(120deg,rgba(170,120,255,.22),transparent 60%)}
                .opened{position:absolute;left:5px;top:5px;width:7px;height:7px;border-radius:50%;
                  background:#79c0ff;box-shadow:0 0 6px #79c0ff}
                .tooltip{display:none;position:absolute;left:50%;bottom:calc(100% + 10px);transform:translateX(-50%);
                  min-width:160px;max-width:260px;padding:10px 12px;border-radius:8px;background:rgba(12,12,18,.97);
                  border:1px solid #4a4a68;text-align:left;z-index:20;pointer-events:none}
                .slot:hover .tooltip{display:block}
                .tt-name{font-size:13px;font-weight:700;margin-bottom:4px;white-space:nowrap}
                .tt-lore{font-size:11px;color:#a6a6b8;line-height:1.5;white-space:pre-wrap}
                .tt-ench{font-size:11px;color:#b392f0}
                .single{display:flex;gap:28px;align-items:center;padding:24px;border-radius:16px;
                  background:linear-gradient(145deg,#22222c,#16161d);border:1px solid #34343f}
                .single-tip{display:flex;flex-direction:column;gap:6px}
                .name{font-size:20px;font-weight:700;color:#f2f2f8}
                .name.uncommon{color:#7ee787}.name.rare{color:#79c0ff}
                .name.epic{color:#d2a8ff}.name.legendary{color:#ffa657}
                .lore{font-size:13px;color:#a6a6b8;line-height:1.5}
                .ench{font-size:13px;color:#b392f0}
                </style></head><body>
                """ + body + """
                </body></html>
                """;
    }

    private static String rarityClass(String rarity) {
        return switch (rarity == null ? "" : rarity.toLowerCase()) {
            case "uncommon" -> "uncommon";
            case "rare" -> "rare";
            case "epic" -> "epic";
            case "legendary" -> "legendary";
            default -> "common";
        };
    }

    private static String prettify(String id) {
        if (id == null || id.isBlank()) {
            return "Air";
        }
        String[] parts = id.split("[_:]");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).replace('_', ' '));
        }
        return builder.isEmpty() ? id : builder.toString();
    }

    private static String displayName(ItemView item) {
        return item.displayName().isBlank() ? prettify(item.shortId()) : item.displayName();
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** 槽位下方的名称只保留文字，颜色码交给 tooltip 展示。 */
    static String stripColors(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('§', '&').replaceAll("&#[0-9a-fA-F]{6}", "").replaceAll("&[0-9a-fk-or]", "");
    }

    /**
     * 把 Minecraft 传统颜色码（{@code &a}/{@code §a}/{@code &#RRGGBB}）转成 span。
     * 服务端已清洗过组件 JSON，但颜色码会被保留下来，直接 escape 会让玩家看到
     * 字面的 “&b锋利之刃”。
     */
    static String styled(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String text = value.replace('§', '&');
        StringBuilder html = new StringBuilder(text.length() + 32);
        int color = -1;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strike = false;
        int index = 0;
        while (index < text.length()) {
            if (text.charAt(index) == '&' && index + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(index + 1));
                if (code == '#' && index + 7 < text.length()) {
                    String hex = text.substring(index + 2, index + 8);
                    if (hex.matches("[0-9a-fA-F]{6}")) {
                        color = (int) Long.parseLong(hex, 16);
                        index += 8;
                        continue;
                    }
                }
                int rgb = legacyColor(code);
                if (rgb != Integer.MIN_VALUE) {
                    if (rgb == -1) {
                        color = -1;
                        bold = italic = underline = strike = false;
                    } else {
                        color = rgb;
                    }
                    index += 2;
                    continue;
                }
                switch (code) {
                    case 'l' -> bold = true;
                    case 'o' -> italic = true;
                    case 'n' -> underline = true;
                    case 'm' -> strike = true;
                    case 'r' -> {
                        color = -1;
                        bold = italic = underline = strike = false;
                    }
                    default -> {
                        // k（乱码）与未知码直接丢弃
                    }
                }
                index += 2;
                continue;
            }
            int next = text.indexOf('&', index);
            int end = next < 0 ? text.length() : next;
            if (end > index) {
                html.append(span(escape(text.substring(index, end)), color, bold, italic, underline, strike));
            }
            index = end;
        }
        return html.toString();
    }

    private static String span(String content, int color, boolean bold, boolean italic,
                               boolean underline, boolean strike) {
        if (color < 0 && !bold && !italic && !underline && !strike) {
            return content;
        }
        StringBuilder style = new StringBuilder();
        if (color >= 0) {
            style.append("color:#").append(String.format("%06x", color)).append(';');
        }
        if (bold) {
            style.append("font-weight:700;");
        }
        if (italic) {
            style.append("font-style:italic;");
        }
        if (underline) {
            style.append("text-decoration:underline;");
        }
        if (strike) {
            style.append("text-decoration:line-through;");
        }
        return "<span style=\"" + style + "\">" + content + "</span>";
    }

    /** 返回 RGB；-1 表示 &r 重置，Integer.MIN_VALUE 表示不是颜色码。 */
    private static int legacyColor(char code) {
        return switch (code) {
            case '0' -> 0x000000;
            case '1' -> 0x0000aa;
            case '2' -> 0x00aa00;
            case '3' -> 0x00aaaa;
            case '4' -> 0xaa0000;
            case '5' -> 0xaa00aa;
            case '6' -> 0xffaa00;
            case '7' -> 0xaaaaaa;
            case '8' -> 0x555555;
            case '9' -> 0x5555ff;
            case 'a' -> 0x55ff55;
            case 'b' -> 0x55ffff;
            case 'c' -> 0xff5555;
            case 'd' -> 0xff55ff;
            case 'e' -> 0xffff55;
            case 'f' -> 0xffffff;
            case 'r' -> -1;
            default -> Integer.MIN_VALUE;
        };
    }
}
