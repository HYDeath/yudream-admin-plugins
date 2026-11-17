package online.yudream.plugin.qqrobot.application.service;

import online.yudream.base.plugin.spi.system.render.PluginRenderedImage;
import online.yudream.base.plugin.spi.system.render.PluginRenderService;
import online.yudream.plugin.qqrobot.domain.ContainerView;
import online.yudream.plugin.qqrobot.domain.ItemView;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 把物品/背包快照排版成 PNG。图片优先用服务端上报的 data URI（带资源包纹理），
 * 缺失时回落到插件内置的原版纹理，最后才是首字母占位。HTML 不访问外网，
 * 渲染结果可缓存。
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
    private final BuiltinTextures builtin;

    public ItemCardRenderer(RenderSupport render) {
        this(render, new BuiltinTextures());
    }

    public ItemCardRenderer(RenderSupport render, BuiltinTextures builtin) {
        this.render = render;
        this.builtin = builtin == null ? new BuiltinTextures() : builtin;
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
        return inventory(title, items, skinDataUri, subtitle, List.of());
    }

    /**
     * 带外部容器（末影箱、多个潜影盒）的版本。容器既可能来自顶层上报，
     * 也可能藏在物品的 {@code container} 字段里；这里合并去重后统一排在背包下方。
     */
    public CompletableFuture<PluginRenderedImage> inventory(String title, List<ItemView> items,
                                                           String skinDataUri, String subtitle,
                                                           List<ContainerView> containers) {
        return safely(() -> render.html(inventoryHtml(title, items, skinDataUri, subtitle, containers),
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

    private static final String[] MAIN_BACKGROUNDS = {"sq-01-courtyard-gate__710x714.jpg", "sq-02-bamboo-frame__710x714.jpg",
            "sq-03-lantern-hall__710x714.jpg", "sq-04-brocade-panel__710x714.jpg"};
    private static final String[] CONTAINER_BACKGROUNDS = {"wide-01-scroll-landscape__708x349.jpg",
            "wide-02-brocade-band__708x349.jpg", "wide-03-river-lantern__708x349.jpg"};
    private static final java.util.Map<String, String> BACKGROUNDS = new java.util.concurrent.ConcurrentHashMap<>();

    private static String background(String[] names, String seed) {
        String name = names[Math.floorMod(seed == null ? 0 : seed.hashCode(), names.length)];
        return BACKGROUNDS.computeIfAbsent(name, key -> {
            try (var input = ItemCardRenderer.class.getResourceAsStream("/inventory-backgrounds/" + key)) {
                if (input == null) throw new IllegalStateException("缺少背包背景资源：" + key);
                return "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(input.readAllBytes());
            } catch (java.io.IOException error) { throw new IllegalStateException("无法读取背包背景", error); }
        });
    }

    private String inventoryHtml(String title, List<ItemView> items, String skinDataUri, String subtitle,
                                 List<ContainerView> containers) {
        List<ItemView> normalized = items == null ? List.of() : items.stream().map(ItemView::normalized).toList();
        if (normalized.isEmpty() && (containers == null || containers.isEmpty())) {
            return page("<div id=\"inventory-card\"><div class=\"title\">" + escape(title) + "</div></div>");
        }
        if (normalized.isEmpty()) {
            return page("<div id=\"inventory-card\" class=\"inventory-containers\">"
                    + String.join("", containers(normalized, containers)) + "</div>");
        }
        List<ContainerView> boxes = new java.util.ArrayList<>();
        if (containers != null) containers.stream().filter(box -> "shulker".equals(box.kind())).forEach(boxes::add);
        for (int index = 0; index < normalized.size(); index++) {
            ItemView item = normalized.get(index);
            String source = slotLabel(index);
            if (item.openedContainer() && boxes.stream().noneMatch(box -> source.equals(box.sourceSlot())))
                boxes.add(new ContainerView("shulker", displayName(item), source, item.container()));
        }
        StringBuilder storage = new StringBuilder(), hotbar = new StringBuilder(), equipment = new StringBuilder();
        for (int index = 9; index < 36; index++) storage.append(markedSlot(itemAt(normalized, index), index, boxes));
        for (int index = 0; index < 9; index++) hotbar.append("<div class=\"hotbar-cell\"><span class=\"hotkey\">" + (index + 1)
                + "</span>" + markedSlot(itemAt(normalized, index), index, boxes) + "</div>");
        for (int index = 39; index >= 36; index--) equipment.append(equipSlot(itemAt(normalized, index), slotLabel(index)));
        String skin = skinDataUri != null && skinDataUri.startsWith("data:image/")
                ? "<img class=\"skin\" src=\"" + escape(skinDataUri) + "\" alt=\"玩家皮肤正背面\">" : "<div class=\"skin-empty\">皮肤暂不可用</div>";
        String summary = boxes.isEmpty() ? "该玩家背包中未发现潜影盒" : "潜影盒共 " + boxes.size() + " 个 · 发送「潜影盒」按编号打开";
        return page("<div id=\"inventory-card\" class=\"inventory-main\" style=\"background-image:linear-gradient(180deg,rgba(8,11,16,.18),rgba(8,11,16,.84)),url('"
                + background(MAIN_BACKGROUNDS, title) + "')\"><div class=\"head\"><span class=\"title\">" + escape(title)
                + "</span><span class=\"subtitle\">" + escape(subtitle) + "</span></div>"
                + "<div class=\"player\"><div class=\"equip left\">" + equipment + "</div><div class=\"figure\">" + skin
                + "</div><div class=\"offhand\">" + equipSlot(itemAt(normalized, 40), "副手") + "</div></div>"
                + "<div class=\"grid storage\">" + storage + "</div><div class=\"grid hotbar\">" + hotbar + "</div>"
                + "<div class=\"box-summary\">" + escape(summary) + "</div><div class=\"inventory-footer\">瑶光天工 · 背包预览 · 快捷栏 1–9</div></div>");
    }

    private String markedSlot(ItemView item, int index, List<ContainerView> boxes) {
        String marker = "";
        for (int box = 0; box < boxes.size(); box++) {
            if (slotLabel(index).equals(boxes.get(box).sourceSlot())) {
                marker = "<span class=\"box-index\">" + (box + 1) + "</span>";
                break;
            }
        }
        return "<div class=\"numbered-slot\">" + slot(item, 1) + marker + "</div>";
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

    private List<String> containers(List<ItemView> items, List<ContainerView> extra) {
        List<String> blocks = new java.util.ArrayList<>();
        // 已经由顶层列表上报过的槽位不再重复渲染（兼容还没去重的旧服务端）。
        java.util.Set<String> reported = new java.util.HashSet<>();
        // 末影箱排最前：它是玩家最常查的，放底部不容易被多个潜影盒挤走。
        if (extra != null) {
            for (ContainerView container : extra) {
                ContainerView value = container.normalized();
                if (!value.empty()) {
                    reported.add(value.sourceSlot());
                    blocks.add(containerBlock(value.kind(), value.label(), value.sourceSlot(),
                            value.items(), 1));
                }
            }
        }
        for (int index = 0; index < items.size(); index++) {
            ItemView item = items.get(index);
            String slot = slotLabel(index);
            if (item.openedContainer() && !reported.contains(slot)) {
                blocks.add(containerBlock("shulker", displayName(item), slot, item.container(), 1));
            }
        }
        return blocks;
    }

    /** 把背包下标翻译成玩家能对上的位置，例如“快捷栏 3”“背包 12”“护甲 2”。 */
    static String slotLabel(int index) {
        if (index >= 0 && index < HOTBAR_SLOTS) {
            return "快捷栏 " + (index + 1);
        }
        if (index >= HOTBAR_SLOTS && index < STORAGE_SLOTS + HOTBAR_SLOTS) {
            return "背包 " + (index - HOTBAR_SLOTS + 1);
        }
        // 与服务端 ItemSerializer 的槽位命名保持一致，避免同一格两边叫法不同。
        return switch (index) {
            case 36 -> "靴子";
            case 37 -> "护腿";
            case 38 -> "胸甲";
            case 39 -> "头盔";
            case 40 -> "副手";
            default -> "槽位 " + (index + 1);
        };
    }

    private String containerBlock(String kind, String label, String sourceSlot,
                                  List<ItemView> contents, int depth) {
        if (depth > MAX_CONTAINER_DEPTH) {
            return "";
        }
        StringBuilder grid = new StringBuilder();
        for (int slot = 0; slot < 27; slot++) {
            ItemView content = slot < contents.size() ? contents.get(slot) : null;
            grid.append(slot(content, depth));
        }
        StringBuilder nested = new StringBuilder();
        if (depth < MAX_CONTAINER_DEPTH) {
            for (ItemView content : contents) {
                if (content.openedContainer()) {
                    nested.append(containerBlock("shulker", displayName(content), "", content.container(),
                            depth + 1));
                }
            }
        }
        String title = switch (kind) {
            case "ender_chest" -> "末影箱";
            case "shulker" -> "潜影盒";
            default -> "容器";
        };
        // 末影箱的 label 就是“末影箱”，再拼一次会变成“末影箱 · 末影箱”。
        String name = label == null || label.isBlank() ? "" : stripColors(label);
        if (name.equals(title)) {
            name = "";
        }
        // 末影箱的来源槽位也是“末影箱”，和标题重复时不显示。
        String meta = sourceSlot == null || sourceSlot.isBlank()
                || sourceSlot.equals(title) || sourceSlot.equals(label)
                ? "" : "<span class=\"section-meta\">" + escape(sourceSlot) + "</span>";
        return "<div class=\"section container\" style=\"background-image:linear-gradient(180deg,rgba(8,11,16,.18),rgba(8,11,16,.66)),url('"
                + background(CONTAINER_BACKGROUNDS, label) + "')\"><div class=\"section-label\">" + title
                + (name.isEmpty() ? "" : " · " + escape(name)) + meta
                + "</div><div class=\"grid\">" + grid + "</div>" + nested + "</div>";
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
            extras.append(containerBlock("shulker", displayName(value), "", value.container(), 1));
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
        if ("shield".equals(item.shortId())) {
            return "<svg class=\"icon\" width=\"" + size + "\" height=\"" + size + "\" viewBox=\"0 0 32 40\" aria-label=\"盾牌\"><path d=\"M3 2h26v25L16 38 3 27z\" fill=\"#adada4\" stroke=\"#42413c\" stroke-width=\"2\"/><path d=\"M7 6h18v19l-9 8-9-8z\" fill=\"#806244\"/><path d=\"M11 6v22m10-22v22\" stroke=\"#5d452e\" stroke-width=\"2\"/></svg>";
        }
        // 服务端没上报（后台预览），或该物品在资源包里没命中：用内置原版纹理。
        var fallbackIcon = builtin.icon(item.itemId(), BuiltinTextures.defaultLeatherColor(item.itemId()));
        if (fallbackIcon.isPresent()) {
            return "<img class=\"icon\" style=\"width:" + size + "px;height:" + size + "px\" src=\""
                    + fallbackIcon.get() + "\" alt=\"\">";
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
                .section-meta{margin-left:8px;padding:1px 7px;border-radius:999px;background:#23232d;color:#9a9ab0}
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
                /* 附魔光效：一条 1px 斜向高光 + 极淡内辉光。整块填色会变成紫色方块。 */
                .glint{position:absolute;left:50%;top:50%;width:44px;height:44px;transform:translate(-50%,-50%);
                  border-radius:8px;pointer-events:none;
                  background:linear-gradient(115deg,
                    transparent 38%,rgba(216,190,255,.55) 47%,rgba(216,190,255,.55) 53%,transparent 62%);
                  box-shadow:inset 0 0 6px rgba(170,120,255,.35)}
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

                #inventory-card.inventory-main{position:relative;width:710px;height:714px;padding:0;border-radius:8px;overflow:hidden;background-size:cover;color:#eae2d0}
                .inventory-main .head{position:absolute;top:8px;left:26px;max-width:658px;margin:0;padding:6px 10px;border-radius:6px;background:rgba(10,12,16,.72);white-space:nowrap;overflow:hidden}
                .inventory-main .title{font-size:16px;margin:0;color:#d6b266}.inventory-main .subtitle{font-size:11px;color:#b9bdc5}
                .inventory-main .player{position:absolute;left:26px;top:62px;width:658px;height:298px;margin:0;padding:0;border:0;background:none;display:block}
                .inventory-main .equip.left{position:absolute;inset:0;display:block}
                .inventory-main .equip .equip-slot{position:absolute;left:0;top:0}.inventory-main .equip .equip-slot:nth-child(2){top:144px}
                .inventory-main .equip .equip-slot:nth-child(3){left:606px}.inventory-main .equip .equip-slot:nth-child(4){left:606px;top:144px}
                .inventory-main .figure{position:absolute;left:80px;top:-20px;width:492px;height:270px;border:1px solid rgba(126,134,146,.35);border-radius:8px;background:rgba(14,17,22,.28);align-items:center}
                .inventory-main .skin{width:100%;height:100%;object-fit:contain}.inventory-main .skin-empty{width:auto;height:auto;padding:20px;background:none;border:0;color:#969ea8;font-size:13px}
                .inventory-main .offhand{position:absolute;left:304px;top:182px}
                .inventory-main .equip-slot{width:52px;height:52px;border-radius:6px;background:rgba(14,17,22,.66);border:1px solid rgba(126,134,146,.47);box-shadow:none}
                .inventory-main .equip-slot .icon{max-width:44px;max-height:44px}
                .inventory-main .grid{position:absolute;left:38px;grid-template-columns:repeat(9,65px);gap:6px}
                .inventory-main .storage{top:338px}.inventory-main .hotbar{top:582px;margin:0}
                .inventory-main .cell,.inventory-main .slot{width:65px;height:65px}.inventory-main .cell{gap:0}
                .inventory-main .slot,.inventory-containers .slot{border-radius:6px;background:rgba(14,17,22,.66);border:1px solid rgba(126,134,146,.47);box-shadow:none}
                .inventory-main .label,.inventory-containers .label{display:none}.numbered-slot,.hotbar-cell{position:relative}.hotkey{position:absolute;z-index:2;top:3px;left:5px;color:#969ea8;font-size:11px}
                .box-index{position:absolute;top:2px;right:3px;padding:1px 5px;border-radius:5px;background:#d6b266;color:#101318;font-size:13px;font-weight:700}
                .inventory-main .box-summary{position:absolute;top:660px;left:38px;right:38px;font-size:12px;color:#eae2d0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
                .inventory-footer{position:absolute;top:689px;left:38px;font-size:11px;color:#969ea8}
                #inventory-card.inventory-containers{width:708px;padding:0;background:transparent;border-radius:8px}
                .inventory-containers .section.container{position:relative;width:708px;height:349px;margin:0;padding:0;border:0;border-radius:8px;background-size:cover;overflow:hidden}
                .inventory-containers .section-label{position:absolute;left:22px;top:10px;max-width:660px;padding:7px 12px;font-size:14px;color:#eae2d0;background:rgba(10,12,16,.6);border-radius:6px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
                .inventory-containers .grid{position:absolute;left:32px;top:56px;grid-template-columns:repeat(9,67px);gap:5px}
                .inventory-containers .cell,.inventory-containers .slot{width:67px;height:67px}
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
