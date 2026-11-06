package online.yudream.plugin.easybot.domain;

import java.util.List;

/**
 * 服务端上报的物品快照。纹理以 PNG data URI 形式携带，
 * 后台不解析资源包格式，只负责排版渲染。
 */
public record ItemView(
        String itemId,
        int count,
        int damage,
        int maxDamage,
        String displayName,
        List<String> lore,
        List<String> enchantments,
        String iconDataUri,
        String rarity,
        int customModelData,
        List<ItemView> container
) {
    public ItemView normalized() {
        return new ItemView(
                itemId == null || itemId.isBlank() ? "minecraft:air" : itemId,
                Math.max(0, Math.min(9999, count)),
                Math.max(0, damage),
                Math.max(0, maxDamage),
                displayName == null ? "" : displayName,
                lore == null ? List.of() : lore.stream().filter(line -> line != null && !line.isBlank()).toList(),
                enchantments == null ? List.of() : enchantments.stream().filter(line -> line != null && !line.isBlank()).toList(),
                iconDataUri == null || !iconDataUri.startsWith("data:image/") ? "" : iconDataUri,
                rarity == null || rarity.isBlank() ? "common" : rarity,
                Math.max(0, customModelData),
                container == null ? List.of() : container.stream().map(ItemView::normalized).toList()
        );
    }

    public String shortId() {
        String id = itemId == null ? "air" : itemId;
        int index = id.indexOf(':');
        return index >= 0 ? id.substring(index + 1) : id;
    }

    public boolean empty() {
        return "minecraft:air".equals(itemId) || count <= 0;
    }

    /** 有容器内容时按 3×9 渲染；空潜影盒按普通物品处理。 */
    public boolean openedContainer() {
        return container.size() == 27 && container.stream().anyMatch(item -> !item.empty());
    }
}
