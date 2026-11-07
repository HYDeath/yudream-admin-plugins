package online.yudream.plugin.qqrobot.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * QQ 机器人开放平台 HTTP 客户端。
 *
 * <p>只做官方文档里的三件事：用 AppID + AppSecret 换 app access token、
 * 发单聊消息、发群消息。token 带过期时间缓存，提前 60 秒刷新；失败返回
 * {@link Result} 而不是抛异常，调用方可以只记日志不影响事件处理。</p>
 *
 * <p>这里不缓存任何用户内容，也不落盘 token。</p>
 */
public class QqApiClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** 官方 token 接口固定走生产域名，沙箱与生产共用。 */
    private static final String TOKEN_URL = "https://bots.qq.com/app/getAppAccessToken";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final AtomicReference<CachedToken> token = new AtomicReference<>();

    public Result sendC2c(String appId, String secret, String base, String openid,
                          String content, String msgId) {
        return send(appId, secret, base, "/v2/users/" + openid + "/messages", content, msgId, null);
    }

    public Result sendGroup(String appId, String secret, String base, String groupOpenid,
                            String content, String msgId) {
        return send(appId, secret, base, "/v2/groups/" + groupOpenid + "/messages", content, msgId, null);
    }

    /** 被动回复可以带 msg_id，超过 5 分钟或主动推送时留空。 */
    private Result send(String appId, String secret, String base, String path, String content,
                        String msgId, String eventId) {
        if (blank(appId) || blank(secret)) {
            return Result.failure(0, "未配置 AppID 或 AppSecret");
        }
        String accessToken = accessToken(appId, secret);
        if (accessToken == null) {
            return Result.failure(0, "获取 app access token 失败");
        }
        try {
            String payload = mapper.writeValueAsString(Map.of(
                    "content", content == null ? "" : content,
                    "msg_type", 0,
                    "msg_seq", 1));
            HttpRequest.Builder builder = HttpRequest.newBuilder(
                            URI.create(base + path))
                    .timeout(TIMEOUT)
                    .header("Authorization", "QQBot " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            if (!blank(msgId)) {
                builder.header("X-Union-Appid", appId);
                builder.uri(URI.create(base + path + "?msg_id=" + msgId));
            }
            HttpResponse<String> response = http.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode body = response.body() == null || response.body().isBlank()
                    ? mapper.createObjectNode() : mapper.readTree(response.body());
            return new Result(response.statusCode() >= 200 && response.statusCode() < 300,
                    response.statusCode(), body.path("id").asText(""),
                    body.path("message").asText(""), body.toString());
        } catch (Exception exception) {
            return Result.failure(0, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    /** 换取 app access token；成功返回 token，失败返回 null（不抛异常）。 */
    public synchronized String accessToken(String appId, String secret) {
        CachedToken cached = token.get();
        if (cached != null && cached.appId().equals(appId) && cached.expiresAt().isAfter(Instant.now())) {
            return cached.value();
        }
        try {
            String payload = mapper.writeValueAsString(Map.of("appId", appId, "clientSecret", secret));
            HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return null;
            }
            JsonNode body = mapper.readTree(response.body());
            String value = body.path("access_token").asText("");
            long expiresIn = body.path("expires_in").asLong(7200);
            if (value.isBlank()) {
                return null;
            }
            token.set(new CachedToken(appId, value,
                    Instant.now().plusSeconds(Math.max(60, expiresIn - 60))));
            return value;
        } catch (Exception exception) {
            return null;
        }
    }

    void clearToken() {
        token.set(null);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record CachedToken(String appId, String value, Instant expiresAt) {
    }

    public record Result(boolean ok, int status, String messageId, String message, String raw) {
        static Result failure(int status, String message) {
            return new Result(false, status, "", message, "");
        }

        public Map<String, Object> toView() {
            Map<String, Object> view = new java.util.LinkedHashMap<>();
            view.put("ok", ok);
            view.put("status", status);
            if (!messageId.isBlank()) {
                view.put("messageId", messageId);
            }
            if (!message.isBlank()) {
                view.put("message", message);
            }
            return view;
        }
    }
}
