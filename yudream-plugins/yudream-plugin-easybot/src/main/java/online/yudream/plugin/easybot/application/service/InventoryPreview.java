package online.yudream.plugin.easybot.application.service;

import online.yudream.base.plugin.spi.system.render.PluginRenderedImage;
import online.yudream.plugin.easybot.domain.ItemView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 渲染模型：不依赖 Minecraft 服务端，合成一份有代表性的背包，
 * 用于后台自检、前端预览和样式回归。
 */
public class InventoryPreview {
    private final ItemCardRenderer renderer;
    private final AvatarService avatars;

    public InventoryPreview(ItemCardRenderer renderer) {
        this(renderer, new AvatarService(false));
    }

    public InventoryPreview(ItemCardRenderer renderer, AvatarService avatars) {
        this.renderer = renderer;
        this.avatars = avatars == null ? new AvatarService(false) : avatars;
    }

    public CompletableFuture<PluginRenderedImage> sample() {
        return renderer.inventory("背包预览", sampleItems(), avatars.body("Steve"), "Steve · 瑶光天工");
    }

    public CompletableFuture<PluginRenderedImage> single() {
        return renderer.single(diamondSword());
    }

    /**
     * 槽位顺序与 Minecraft 玩家背包一致：0-8 快捷栏，9-35 主存储，36-39 护甲，40 副手。
     */
    public List<ItemView> sampleItems() {
        List<ItemView> items = new ArrayList<>(41);
        for (int index = 0; index < 41; index++) {
            items.add(air());
        }
        items.set(0, item("minecraft:diamond_sword", 1, "&b锋利之刃", List.of("&7对亡灵造成额外伤害"),
                List.of("sharpness V", "unbreaking III"), "epic"));
        items.set(1, item("minecraft:golden_apple", 16, "", List.of(), List.of(), "common"));
        items.set(2, item("minecraft:torch", 64, "", List.of(), List.of(), "common"));
        items.set(3, item("minecraft:water_bucket", 1, "", List.of(), List.of(), "common"));
        items.set(4, item("minecraft:netherite_pickaxe", 1, "", List.of(), List.of("efficiency V"), "rare"));
        items.set(9, shulker());
        items.set(10, item("minecraft:enchanted_book", 1, "&d经验修补", List.of("&7可应用于任意可附魔物品"),
                List.of("mending I"), "rare"));
        items.set(12, item("minecraft:cooked_beef", 32, "", List.of(), List.of(), "common"));
        items.set(20, damaged("minecraft:elytra", 1, 216, 432, "&5鞘翅", List.of(), List.of(), "epic"));
        items.set(36, item("minecraft:netherite_helmet", 1, "", List.of(), List.of("protection IV"), "rare"));
        items.set(37, item("minecraft:netherite_chestplate", 1, "", List.of(), List.of("protection IV"), "rare"));
        items.set(38, item("minecraft:netherite_leggings", 1, "", List.of(), List.of("protection IV"), "rare"));
        items.set(39, item("minecraft:netherite_boots", 1, "", List.of(), List.of("protection IV"), "rare"));
        items.set(40, item("minecraft:shield", 1, "", List.of(), List.of(), "common"));
        return items;
    }

    private ItemView shulker() {
        List<ItemView> content = new ArrayList<>(27);
        for (int index = 0; index < 27; index++) {
            content.add(air());
        }
        content.set(0, item("minecraft:diamond", 64, "", List.of(), List.of(), "common"));
        content.set(1, item("minecraft:emerald", 48, "", List.of(), List.of(), "common"));
        content.set(2, item("minecraft:gold_ingot", 32, "", List.of(), List.of(), "common"));
        content.set(9, item("minecraft:redstone", 64, "", List.of(), List.of(), "common"));
        content.set(10, item("minecraft:lapis_lazuli", 64, "", List.of(), List.of(), "common"));
        content.set(18, item("minecraft:ancient_debris", 7, "", List.of(), List.of(), "rare"));
        return new ItemView("minecraft:shulker_box", 1, 0, 0, "&d战利品箱",
                List.of("&7内含 7 块远古残骸"), List.of(), "", "epic", 0, content).normalized();
    }

    private ItemView diamondSword() {
        return new ItemView("minecraft:diamond_sword", 1, 120, 1561, "&b锋利之刃",
                List.of("&7攻击伤害 &c7", "&7耐久 1441 / 1561"), List.of("sharpness V", "unbreaking III"),
                "", "epic", 0, List.of()).normalized();
    }

    private static ItemView item(String id, int count, String name, List<String> lore,
                                 List<String> enchantments, String rarity) {
        return new ItemView(id, count, 0, 0, name, lore, enchantments, "", rarity, 0, List.of()).normalized();
    }

    private static ItemView damaged(String id, int count, int damage, int maxDamage, String name,
                                    List<String> lore, List<String> enchantments, String rarity) {
        return new ItemView(id, count, damage, maxDamage, name, lore, enchantments, "", rarity, 0, List.of()).normalized();
    }

    private static ItemView air() {
        return new ItemView("minecraft:air", 0, 0, 0, "", List.of(), List.of(), "", "common", 0, List.of());
    }
}
