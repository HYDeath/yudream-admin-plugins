package online.yudream.base.plugin.registrationreview.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import online.yudream.base.plugin.questionbank.api.QuestionBankApi;
import online.yudream.base.plugin.registrationreview.application.ReviewService;
import online.yudream.base.plugin.registrationreview.application.SettingsService;
import online.yudream.base.plugin.registrationreview.bootstrap.RegistrationReviewPlugin;
import online.yudream.base.plugin.registrationreview.domain.ReviewApplication;
import online.yudream.base.plugin.registrationreview.infrastructure.JsonSupport;
import online.yudream.base.plugin.registrationreview.support.QuestionBankLocator;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.spi.core.PluginContext;
/** 管理端审核与设置接口。 */
public final class ReviewAdminController {
    private final ReviewService reviews;
    private final SettingsService settings;
    private final JsonSupport json;
    private final PluginContext context;

    public ReviewAdminController(ReviewService reviews, SettingsService settings, JsonSupport json, PluginContext context) {
        this.reviews = reviews;
        this.settings = settings;
        this.json = json;
        this.context = context;
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/applications", permission = RegistrationReviewPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse list(PluginHttpRequest request) {
        return ReviewMeController.guard(() -> {
            ReviewService.PageResult result = reviews.queryAdmin(first(request, "status"), first(request, "keyword"),
                    page(request), size(request));
            return PluginHttpResponse.ok(Map.of(
                    "records", result.records().stream().map(ReviewViews::application).toList(),
                    "total", result.total()));
        });
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/applications/{id}/approve", permission = RegistrationReviewPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse approve(PluginHttpRequest request) {
        return ReviewMeController.guard(() -> {
            String id = segmentAfter(request.path(), "applications");
            String reviewer = reviewer(request);
            ReviewApplication application = reviews.approve(id, reviewer);
            return PluginHttpResponse.ok(Map.of("success", true, "application", ReviewViews.application(application)));
        });
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/applications/{id}/reject", permission = RegistrationReviewPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse reject(PluginHttpRequest request) {
        return ReviewMeController.guard(() -> {
            String id = segmentAfter(request.path(), "applications");
            JsonNode body = json.readTree(request.body());
            String reason = body.path("reason").asText("");
            ReviewApplication application = reviews.reject(id, reviewer(request), reason);
            return PluginHttpResponse.ok(Map.of("success", true, "application", ReviewViews.application(application)));
        });
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/settings/schema", permission = RegistrationReviewPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse schema(PluginHttpRequest request) {
        return ReviewMeController.guard(() -> PluginHttpResponse.ok(settings.schema()));
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/settings", permission = RegistrationReviewPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse settings(PluginHttpRequest request) {
        return ReviewMeController.guard(() -> PluginHttpResponse.ok(Map.of("values", settings.effective())));
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/settings", permission = RegistrationReviewPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse updateSettings(PluginHttpRequest request) {
        return ReviewMeController.guard(() -> {
            JsonNode body = json.readTree(request.body());
            Map<String, Object> patch = new LinkedHashMap<>();
            body.fields().forEachRemaining(entry -> patch.put(entry.getKey(), javaValue(entry.getValue())));
            return PluginHttpResponse.ok(Map.of("values", settings.update(patch)));
        });
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/questionbank/categories", permission = RegistrationReviewPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse categories(PluginHttpRequest request) {
        return ReviewMeController.guard(() -> {
            var questionBank = QuestionBankLocator.find(context);
            List<Map<String, Object>> categories = questionBank
                    .map(QuestionBankApi::categories)
                    .orElse(List.of())
                    .stream().map(option -> Map.<String, Object>of("id", option.id(), "name", option.name()))
                    .toList();
            return PluginHttpResponse.ok(Map.of("records", categories,
                    "questionbankAvailable", questionBank.isPresent()));
        });
    }

    private static Object javaValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isIntegralNumber()) {
            return value.asInt();
        }
        if (value.isArray()) {
            List<String> values = new ArrayList<>();
            value.forEach(item -> values.add(item.asText("")));
            return values;
        }
        return value.asText("");
    }

    private static String reviewer(PluginHttpRequest request) {
        return request.principal() == null || request.principal().userId() == null
                ? "admin" : String.valueOf(request.principal().userId());
    }

    private static String first(PluginHttpRequest request, String key) {
        List<String> values = request.query().get(key);
        return values == null || values.isEmpty() ? "" : values.getFirst();
    }

    private static int page(PluginHttpRequest request) {
        try {
            return Math.max(1, Integer.parseInt(first(request, "page")));
        }
        catch (NumberFormatException e) {
            return 1;
        }
    }

    private static int size(PluginHttpRequest request) {
        try {
            return Math.max(1, Math.min(100, Integer.parseInt(first(request, "size"))));
        }
        catch (NumberFormatException e) {
            return 20;
        }
    }

    private static String segmentAfter(String path, String anchor) {
        String[] parts = path == null ? new String[0] : path.split("/");
        for (int i = 0; i + 1 < parts.length; i++) {
            if (anchor.equals(parts[i])) {
                return java.net.URLDecoder.decode(parts[i + 1], java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return "";
    }
}
