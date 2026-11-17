package online.yudream.base.plugin.registrationreview.interfaces;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import online.yudream.base.plugin.registrationreview.application.NotFoundException;
import online.yudream.base.plugin.registrationreview.application.ReviewService;
import online.yudream.base.plugin.registrationreview.application.SettingsService;
import online.yudream.base.plugin.registrationreview.application.UserLabels;
import online.yudream.base.plugin.registrationreview.bootstrap.RegistrationReviewPlugin;
import online.yudream.base.plugin.registrationreview.domain.ReviewApplication;
import online.yudream.base.plugin.registrationreview.infrastructure.JsonSupport;
import online.yudream.base.plugin.registrationreview.support.QuestionBankLocator;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.spi.system.security.PluginPrincipal;

/** 玩家端注册审核接口：owner 一律取 principal.userId()。 */
public final class ReviewMeController {
    private final ReviewService reviews;
    private final SettingsService settings;
    private final UserLabels labels;
    private final JsonSupport json;

    public ReviewMeController(ReviewService reviews, SettingsService settings, UserLabels labels, JsonSupport json) {
        this.reviews = reviews;
        this.settings = settings;
        this.labels = labels;
        this.json = json;
    }

    @PluginHttpEndpoint(method = "GET", path = "/me/meta", permission = RegistrationReviewPlugin.USE_PERMISSION)
    public PluginHttpResponse meta(PluginHttpRequest request) {
        return guard(() -> PluginHttpResponse.ok(reviews.meta()));
    }

    @PluginHttpEndpoint(method = "GET", path = "/me/applications", permission = RegistrationReviewPlugin.USE_PERMISSION)
    public PluginHttpResponse list(PluginHttpRequest request) {
        return guard(() -> {
            String userId = userId(request.principal());
            List<Map<String, Object>> records = reviews.listMine(userId).stream().map(ReviewViews::application).toList();
            return PluginHttpResponse.ok(Map.of("records", records));
        });
    }

    @PluginHttpEndpoint(method = "POST", path = "/me/applications/start", permission = RegistrationReviewPlugin.USE_PERMISSION)
    public PluginHttpResponse start(PluginHttpRequest request) {
        return guard(() -> {
            String userId = userId(request.principal());
            String method = request.body() == null || request.body().isBlank() ? "quiz" : json.readTree(request.body()).path("method").asText("quiz");
            ReviewApplication application = reviews.start(userId, labels.label(userId), method);
            Map<String, Object> data = new LinkedHashMap<>(ReviewViews.application(application));
            data.put("questionBankSessionPath", "/platform/plugins/questionbank/session?id="
                    + application.questionBankSessionId());
            return PluginHttpResponse.ok(data);
        });
    }

    @PluginHttpEndpoint(method = "GET", path = "/me/applications/{id}", permission = RegistrationReviewPlugin.USE_PERMISSION)
    public PluginHttpResponse detail(PluginHttpRequest request) {
        return guard(() -> {
            String id = segmentAfter(request.path(), "applications");
            ReviewApplication application = reviews.refreshMine(userId(request.principal()), id);
            return PluginHttpResponse.ok(ReviewViews.application(application));
        });
    }

    @PluginHttpEndpoint(method = "POST", path = "/me/applications/{id}/refresh", permission = RegistrationReviewPlugin.USE_PERMISSION)
    public PluginHttpResponse refresh(PluginHttpRequest request) {
        return guard(() -> {
            String id = segmentAfter(request.path(), "applications");
            ReviewApplication application = reviews.refreshMine(userId(request.principal()), id);
            return PluginHttpResponse.ok(ReviewViews.application(application));
        });
    }

    private static String userId(PluginPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new IllegalStateException("请先登录后再操作");
        }
        return String.valueOf(principal.userId());
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

    static PluginHttpResponse guard(java.util.function.Supplier<PluginHttpResponse> handler) {
        try {
            return handler.get();
        }
        catch (NotFoundException e) {
            return PluginHttpResponse.rawJson(404, Map.of("code", 404, "message", e.getMessage()));
        }
        catch (IllegalArgumentException | IllegalStateException e) {
            return PluginHttpResponse.rawJson(400, Map.of("code", 400, "message", e.getMessage()));
        }
    }
}
