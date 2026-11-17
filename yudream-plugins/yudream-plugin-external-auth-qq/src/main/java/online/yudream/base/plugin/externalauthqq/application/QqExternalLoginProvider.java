package online.yudream.base.plugin.externalauthqq.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginAuthorizeRequest;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginDescriptor;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginExchangeRequest;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginIdentity;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginPresentation;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginProvider;

/**
 * QQ Graph OAuth2 provider。
 *
 * <p>协议步骤与旧官网一致：authorize → token → me(openid) → user/get_user_info；
 * state、登录态和外部账号绑定由宿主统一处理。</p>
 */
public final class QqExternalLoginProvider implements PluginExternalLoginProvider, AutoCloseable {
    public static final String PROVIDER_CODE = "qq-oauth";
    public static final String TYPE = "oauth2";
    private static final String AUTHORIZE = "https://graph.qq.com/oauth2.0/authorize";
    private static final String TOKEN = "https://graph.qq.com/oauth2.0/token";
    private static final String ME = "https://graph.qq.com/oauth2.0/me";
    private static final String USER_INFO = "https://graph.qq.com/user/get_user_info";

    private final SettingsService settings;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public QqExternalLoginProvider(SettingsService settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public PluginExternalLoginDescriptor descriptor() {
        return new PluginExternalLoginDescriptor(PROVIDER_CODE, settings.displayName(), "i-ri:qq-line",
                java.util.List.of(TYPE), settings.sort());
    }

    @Override
    public PluginExternalLoginPresentation presentation() {
        return PluginExternalLoginPresentation.TAB;
    }

    @Override
    public boolean enabled() {
        return settings.enabled();
    }

    @Override
    public String authorizationUrl(PluginExternalLoginAuthorizeRequest request) {
        requireEnabled();
        String redirect = settings.redirectUri();
        if (redirect.isBlank()) throw new IllegalStateException("QQ 回调地址未配置");
        return AUTHORIZE + "?" + params(
                "response_type", "code",
                "client_id", settings.appId(),
                "redirect_uri", redirect,
                "state", request.state());
    }

    @Override
    public PluginExternalLoginIdentity exchange(PluginExternalLoginExchangeRequest request) {
        requireEnabled();
        if (request.ticket() == null || request.ticket().isBlank()) {
            throw new IllegalArgumentException("QQ 回调缺少 code");
        }
        String appKey = settings.appKey().orElseThrow(() -> new IllegalStateException("QQ AppKey 未配置"));
        String redirect = settings.redirectUri();
        JsonNode token = getJson(TOKEN, params(
                "grant_type", "authorization_code",
                "client_id", settings.appId(),
                "client_secret", appKey,
                "code", request.ticket(),
                "redirect_uri", redirect,
                "fmt", "json"));
        String accessToken = required(token, "access_token", "QQ token 响应缺少 access_token");
        JsonNode me = getJson(ME, params("access_token", accessToken, "fmt", "json"));
        String openid = required(me, "openid", "QQ 身份响应缺少 openid");
        JsonNode profile = getJson(USER_INFO, params(
                "access_token", accessToken,
                "oauth_consumer_key", settings.appId(),
                "openid", openid,
                "fmt", "json"));
        String nickname = text(profile, "nickname", "QQ 用户");
        String avatar = firstText(profile, "figureurl_qq_2", "figureurl_qq_1", "figureurl");
        return new PluginExternalLoginIdentity(openid, nickname, avatar, "", "", null);
    }

    private void requireEnabled() {
        if (!settings.enabled()) {
            throw new IllegalStateException("QQ 登录提供方未启用或配置不完整");
        }
    }

    private JsonNode getJson(String endpoint, String query) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "?" + query))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json,text/plain,*/*")
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("QQ 服务返回 HTTP " + response.statusCode());
            }
            String body = response.body() == null ? "" : response.body().trim();
            if (body.startsWith("callback")) {
                int start = body.indexOf('{');
                int end = body.lastIndexOf('}');
                if (start >= 0 && end > start) body = body.substring(start, end + 1);
            }
            JsonNode parsed = mapper.readTree(body);
            if (parsed == null || !parsed.isObject()) throw new IllegalStateException("QQ 服务返回格式无效");
            if (parsed.has("error") && !parsed.get("error").isNull()) {
                throw new IllegalStateException("QQ 服务拒绝本次认证，请重新登录");
            }
            if (parsed.has("ret") && parsed.path("ret").asInt(-1) != 0) {
                throw new IllegalStateException("QQ 用户资料读取失败，请重新登录");
            }
            return parsed;
        }
        catch (IllegalStateException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IllegalStateException("QQ 服务请求失败，请稍后重试");
        }
    }

    private static String required(JsonNode node, String field, String message) {
        String value = node.path(field).asText("").trim();
        if (value.isEmpty()) throw new IllegalStateException(message);
        return value;
    }

    private static String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("").trim();
        return value.isEmpty() ? fallback : value;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("").trim();
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String params(String... values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i += 2) {
            if (i > 0) out.append('&');
            out.append(encode(values[i])).append('=').append(encode(values[i + 1]));
        }
        return out.toString();
    }

    @Override
    public void close() {
        client.shutdownNow();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
