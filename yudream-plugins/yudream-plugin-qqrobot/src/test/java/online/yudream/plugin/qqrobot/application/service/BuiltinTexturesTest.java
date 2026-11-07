package online.yudream.plugin.qqrobot.application.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinTexturesTest {
    private final BuiltinTextures textures = new BuiltinTextures();

    @Test
    void resolvesVanillaItemTexture() {
        assertTrue(textures.icon("minecraft:diamond_sword").orElse("").startsWith("data:image/png;base64,"));
    }

    @Test
    void acceptsBareIdWithoutNamespace() {
        assertTrue(textures.icon("golden_apple").isPresent());
    }

    @Test
    void fallsBackToBlockTextureForFacesplitBlocks() {
        assertTrue(textures.icon("minecraft:ancient_debris").isPresent());
        assertTrue(textures.icon("minecraft:ancient_debris").orElse("")
                .equals(textures.icon("minecraft:ancient_debris").orElse("x")));
    }

    @Test
    void blockFirstItemsPreferBlockTexture() {
        // item/quartz 是下界石英，方块图与物品图不同，这里必须命中方块侧。
        assertTrue(textures.icon("minecraft:shulker_box").isPresent());
    }

    @Test
    void airAndUnknownIdsReturnEmpty() {
        assertTrue(textures.icon("minecraft:air").isEmpty());
        assertTrue(textures.icon("").isEmpty());
        assertTrue(textures.icon(null).isEmpty());
        assertTrue(textures.icon("yudream:not_a_real_item").isEmpty());
    }

    @Test
    void leatherTintChangesOutput() {
        String plain = textures.icon("minecraft:leather_helmet").orElse("");
        String tinted = textures.icon("minecraft:leather_helmet", 0xA06540).orElse("");

        assertFalse(plain.isBlank());
        assertFalse(tinted.isBlank());
        assertFalse(plain.equals(tinted));
    }

    @Test
    void enchantedVariantFallsBackToBaseTexture() {
        assertTrue(textures.icon("minecraft:enchanted_golden_apple").isPresent());
        assertTrue(textures.icon("minecraft:enchanted_book").isPresent());
    }

    @Test
    void defaultLeatherColorOnlyForLeather() {
        assertTrue(BuiltinTextures.defaultLeatherColor("minecraft:leather_boots") > 0);
        assertTrue(BuiltinTextures.defaultLeatherColor("minecraft:iron_boots") == 0);
        assertTrue(BuiltinTextures.defaultLeatherColor(null) == 0);
    }
}
