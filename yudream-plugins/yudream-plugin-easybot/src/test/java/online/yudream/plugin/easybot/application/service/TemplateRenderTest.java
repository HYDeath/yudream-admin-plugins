package online.yudream.plugin.easybot.application.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateRenderTest {
    @Test
    void doubleBracesEscapeUserContent() {
        String html = TemplateService.apply("<b>{{name}}</b>", Map.of("name", "<script>"));
        assertEquals("<b>&lt;script&gt;</b>", html);
    }

    @Test
    void tripleBracesKeepRawDataUri() {
        String uri = "data:image/png;base64,AAA";
        String html = TemplateService.apply("<img src=\"{{{skin}}}\">", Map.of("skin", uri));
        assertTrue(html.contains(uri));
    }

    @Test
    void eachBlockRepeatsRows() {
        String template = "{{#each servers}}<div>{{name}}:{{online}}</div>{{/each}}";
        String html = TemplateService.apply(template, Map.of("servers", List.of(
                Map.of("name", "A", "online", "3"),
                Map.of("name", "B", "online", "0"))));
        assertEquals("<div>A:3</div><div>B:0</div>", html);
    }

    @Test
    void missingVariableBecomesEmpty() {
        assertEquals("x--y", TemplateService.apply("x-{{missing}}-y", Map.of()));
    }

    @Test
    void variablesAreDiscovered() {
        List<String> names = TemplateService.variables(
                "<div>{{title}}</div>{{#each players}}{{name}}{{/each}}");
        assertTrue(names.contains("title"));
        assertTrue(names.contains("name"));
        assertFalse(names.contains("players"));
    }

    @Test
    void builtinTemplateFillsSampleVariables() {
        String rendered = TemplateService.apply(BuiltinTemplates.all().getFirst().html(),
                BuiltinTemplates.sampleVariables("builtin-server-list"));
        assertTrue(rendered.contains("服务器在线列表"));
        assertTrue(rendered.contains("MAPS____"));
        assertTrue(rendered.contains("27 人在线"));
        assertFalse(rendered.contains("{{"));
    }
}
