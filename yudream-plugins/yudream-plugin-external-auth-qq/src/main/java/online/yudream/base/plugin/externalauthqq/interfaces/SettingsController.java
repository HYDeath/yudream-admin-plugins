package online.yudream.base.plugin.externalauthqq.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import online.yudream.base.plugin.externalauthqq.application.SettingsService;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

/** QQ OAuth 提供方设置页接口。 */
public final class SettingsController {
    private static final String MANAGE = "plugin:external-auth-qq:manage";
    private final SettingsService settings;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    public SettingsController(SettingsService settings, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        this.settings = settings;
        this.mapper = mapper;
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/settings/schema", permission = MANAGE)
    public PluginHttpResponse schema(PluginHttpRequest request) {
        return PluginHttpResponse.ok(settings.schema());
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/settings", permission = MANAGE)
    public PluginHttpResponse get(PluginHttpRequest request) {
        return PluginHttpResponse.ok(Map.of("values", settings.effective()));
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/settings", permission = MANAGE)
    public PluginHttpResponse put(PluginHttpRequest request) {
        try {
            JsonNode body = mapper.readTree(request.body());
            Map<String, Object> patch = new LinkedHashMap<>();
            body.fields().forEachRemaining(entry -> {
                if (entry.getKey().equals("appKeyConfigured") || entry.getKey().equals("configured") || entry.getKey().equals("active")) return;
                patch.put(entry.getKey(), javaValue(entry.getValue()));
            });
            return PluginHttpResponse.ok(Map.of("values", settings.update(patch)));
        }
        catch (IllegalArgumentException e) {
            return PluginHttpResponse.rawJson(400, Map.of("code", 400, "message", e.getMessage()));
        }
        catch (Exception e) {
            return PluginHttpResponse.rawJson(400, Map.of("code", 400, "message", "请求体不是合法 JSON"));
        }
    }

    private static Object javaValue(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (value.isBoolean()) return value.asBoolean();
        if (value.isIntegralNumber()) return value.asInt();
        if (value.isArray()) {
            List<String> values = new ArrayList<>();
            value.forEach(item -> values.add(item.asText("")));
            return values;
        }
        return value.asText("");
    }
}
