package online.yudream.plugin.qqrobot.application.service;

import online.yudream.base.plugin.spi.system.render.PluginRenderedImage;
import online.yudream.base.plugin.spi.system.render.PluginRenderService;
import online.yudream.plugin.qqrobot.domain.ItemView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryPreviewTest {
    @Test
    void sampleUsesRealInventorySlots() {
        Capture capture = new Capture();
        InventoryPreview preview = new InventoryPreview(new ItemCardRenderer(capture));
        List<ItemView> items = preview.sampleItems();

        assertEquals(41, items.size());
        assertEquals("minecraft:diamond_sword", items.get(0).itemId());
        assertEquals("minecraft:netherite_helmet", items.get(36).itemId());
        assertEquals("minecraft:shield", items.get(40).itemId());
        assertTrue(items.get(9).openedContainer());
        assertEquals(27, items.get(9).container().size());
    }

    @Test
    void inventoryHtmlSeparatesSectionsAndRendersContainer() {
        Capture capture = new Capture();
        InventoryPreview preview = new InventoryPreview(new ItemCardRenderer(capture));
        preview.sample().join();

        String html = capture.html.get();
        assertTrue(html.contains("快捷栏"));
        assertTrue(html.contains("class=\"player\""));
        assertTrue(html.contains("equip-slot"));
        assertTrue(html.contains("潜影盒"));
        assertTrue(html.contains("durability"));
        assertTrue(html.contains("class=\"glint\""));
        assertTrue(html.contains("战利品箱"));
    }

    @Test
    void emptyInventoryDoesNotCrash() {
        Capture capture = new Capture();
        ItemCardRenderer renderer = new ItemCardRenderer(capture);
        renderer.inventory("空背包", List.of()).join();

        assertTrue(capture.html.get().contains("空背包"));
        assertFalse(capture.html.get().contains("潜影盒"));
    }

    @Test
    void previewEndpointsRenderPng() {
        Capture capture = new Capture();
        InventoryPreview preview = new InventoryPreview(new ItemCardRenderer(capture));

        PluginRenderedImage image = preview.single().join();
        assertEquals("image/png", image.contentType());
        assertTrue(image.content().length > 0);
    }

    private static final class Capture implements PluginRenderService {
        private final AtomicReference<String> html = new AtomicReference<>("");

        @Override
        public CompletionStage<PluginRenderedImage> html(String html) {
            this.html.set(html);
            return CompletableFuture.completedFuture(new PluginRenderedImage("image/png", new byte[]{1, 2, 3}, 1, 1));
        }

        @Override
        public CompletionStage<PluginRenderedImage> html(String html, String selector) {
            this.html.set(html);
            return CompletableFuture.completedFuture(new PluginRenderedImage("image/png", new byte[]{1, 2, 3}, 1, 1));
        }

        @Override
        public CompletionStage<PluginRenderedImage> markdown(String markdown) {
            return html(markdown);
        }

        @Override
        public CompletionStage<PluginRenderedImage> url(String url) {
            return html(url);
        }
    }
}
