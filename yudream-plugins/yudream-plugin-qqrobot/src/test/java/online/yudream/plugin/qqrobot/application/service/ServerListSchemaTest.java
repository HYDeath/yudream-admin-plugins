package online.yudream.plugin.qqrobot.application.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerListSchemaTest {
    @Test
    void defaultsUseLiquidGlassAndStandardWidth() {
        Map<String, Object> settings = ServerListSchema.defaults();
        String html = ServerListSchema.html(settings);

        assertTrue(html.startsWith("<!doctype html>"));
        assertTrue(html.contains("backdrop-filter:blur(14px)"));
        assertEquals(900, ServerListSchema.widthOf(settings));
    }

    @Test
    void hiddenSectionsProduceCssOverrides() {
        Map<String, Object> settings = ServerListSchema.merge(Map.of(),
                Map.of("showServerCount", false, "showTotalOnline", false, "showEdition", false));
        String html = ServerListSchema.html(settings);

        assertTrue(html.contains(".stat.count{display:none}"));
        assertTrue(html.contains(".online{display:none}"));
        assertTrue(html.contains(".edition{display:none}"));
    }

    @Test
    void resolutionChangesOutputWidth() {
        assertEquals(1080, ServerListSchema.widthOf(Map.of("resolution", "high")));
        assertEquals(1440, ServerListSchema.widthOf(Map.of("resolution", "ultra")));
        assertEquals(1800, ServerListSchema.widthOf(Map.of("resolution", "xlarge")));
        assertEquals(1920, ServerListSchema.widthOf(Map.of("resolution", "custom", "outputWidth", 2200)));
        assertEquals(900, ServerListSchema.widthOf(Map.of("resolution", "custom", "outputWidth", 640)));
    }

    @Test
    void customCssIsAppendedAndEscapingStaysOff() {
        String html = ServerListSchema.html(Map.of("customCss", ".title{color:#ff0}"));
        assertTrue(html.contains(".title{color:#ff0}"));
        // 模板变量里的玩家名仍走 HTML 转义，只有 {{{ }}} 原样输出。
        assertTrue(html.contains("{{name}}"));
    }

    @Test
    void mergeKeepsExistingValuesAndOverridesIncoming() {
        Map<String, Object> merged = ServerListSchema.merge(
                Map.of("title", "旧标题", "darken", 20),
                Map.of("title", "新标题"));

        assertEquals("新标题", merged.get("title"));
        assertEquals(20, merged.get("darken"));
    }

    @Test
    void schemaExposesSelectOptions() {
        Map<String, Object> view = ServerListSchema.view();
        assertTrue(view.containsKey("themes"));
        assertFalse(((List<?>) view.get("themes")).isEmpty());
    }
}
