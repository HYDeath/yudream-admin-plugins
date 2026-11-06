package online.yudream.plugin.qqrobot.application.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemCardRendererStyleTest {
    @Test
    void legacyColorsBecomeSpans() {
        String html = ItemCardRenderer.styled("&b锋利之刃&r");
        assertTrue(html.contains("color:#55ffff"));
        assertTrue(html.contains("锋利之刃"));
        assertFalse(html.contains("&b"));
    }

    @Test
    void hexColorsAndDecorationsWork() {
        String html = ItemCardRenderer.styled("&#ff8800&l极品");
        assertTrue(html.contains("color:#ff8800"));
        assertTrue(html.contains("font-weight:700"));
    }

    @Test
    void markupIsStillEscaped() {
        String html = ItemCardRenderer.styled("<script>&a");
        assertTrue(html.contains("&lt;script&gt;"));
        assertFalse(html.contains("<script>"));
    }

    @Test
    void missingRenderServiceBecomesActionable503() {
        RenderSupport support = new RenderSupport(new PluginRenderServiceStub(
                new IllegalStateException("消息渲染服务不可用")));
        QqRobotException error = assertThrows(QqRobotException.class, () -> support.html("<html></html>", "#x"));
        assertEquals(503, error.status());
        assertTrue(error.getMessage().contains("不可达"));
    }

    @Test
    void disabledCapabilityMentionsWhereToEnable() {
        RenderSupport support = new RenderSupport(new PluginRenderServiceStub(
                new IllegalStateException("能力未启用：消息渲染")));
        QqRobotException error = assertThrows(QqRobotException.class, () -> support.html("<html></html>", "#x"));
        assertTrue(error.getMessage().contains("能力目录"));
    }
}
