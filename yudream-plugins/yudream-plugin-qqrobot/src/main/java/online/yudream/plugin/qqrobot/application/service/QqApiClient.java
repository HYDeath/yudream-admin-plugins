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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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

    public Result sendC2cEvent(String appId, String secret, String base, String openid,
                               String content, String eventId) {
        return send(appId, secret, base, "/v2/users/" + openid + "/messages", content, null, eventId);
    }

    public Result sendGroup(String appId, String secret, String base, String groupOpenid,
                            String content, String msgId) {
        return send(appId, secret, base, "/v2/groups/" + groupOpenid + "/messages", content, msgId, null);
    }

    public Result sendGroupEvent(String appId, String secret, String base, String groupOpenid,
                                 String content, String eventId) {
        return send(appId, secret, base, "/v2/groups/" + groupOpenid + "/messages", content, null, eventId);
    }

    /**
     * 发图片消息：先走官方的富媒体上传接口换 {@code file_info}，再以
     * {@code msg_type=7} 发送。图片本身由后台抓取/渲染成 PNG 字节，
     * 不依赖公网可访问的 URL。
     */
    public Result sendC2cImage(String appId, String secret, String base, String openid,
                               byte[] png, String msgId) {
        return sendImage(appId, secret, base, "/v2/users/" + openid + "/messages",
                "/v2/users/" + openid + "/files", png, msgId, 1);
    }

    public Result sendGroupImage(String appId, String secret, String base, String groupOpenid,
                                 byte[] png, String msgId) {
        return sendImage(appId, secret, base, "/v2/groups/" + groupOpenid + "/messages",
                "/v2/groups/" + groupOpenid + "/files", png, msgId, 1);
    }

    private Result sendImage(String appId, String secret, String base, String messagePath,
                             String uploadPath,
                             byte[] png, String msgId, int fileType) {
        if (blank(appId) || blank(secret)) {
            return Result.failure(0, "未配置 AppID 或 AppSecret");
        }
        if (png == null || png.length == 0) {
            return Result.failure(0, "图片内容为空");
        }
        String accessToken = accessToken(appId, secret);
        if (accessToken == null) {
            return Result.failure(0, "获取 app access token 失败");
        }
        Optional<String> fileInfo = uploadMedia(appId, accessToken, base, uploadPath, png, fileType);
        if (fileInfo.isEmpty()) {
            return Result.failure(0, "上传富媒体失败");
        }
        try {
            Map<String, Object> payloadBody = new LinkedHashMap<>();
            // 官方当前接口要求富媒体消息保留 content 字段；空格是文档给出的
            // 兼容值，避免旧版本服务端以“缺少 content”拒绝图片。
            payloadBody.put("content", " ");
            payloadBody.put("msg_type", 7);
            payloadBody.put("media", Map.of("file_info", fileInfo.get()));
            payloadBody.put("msg_seq", 1);
            if (!blank(msgId)) {
                payloadBody.put("msg_id", msgId);
            }
            String payload = mapper.writeValueAsString(payloadBody);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + messagePath))
                    .timeout(TIMEOUT)
                    .header("Authorization", "QQBot " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
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

    /**
     * 富媒体上传。官方要求 {@code file_data} 为 base64，单图上限 4MB；
     * 超限直接失败，让调用方回落到文字提示，而不是把图片截断发出去。
     */
    private Optional<String> uploadMedia(String appId, String accessToken, String base,
                                         String uploadPath,
                                         byte[] png, int fileType) {
        if (env(appId, base, uploadPath, png, fileType)) {
            return Optional.empty();
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("file_type", fileType);
            body.put("file_data", Base64.getEncoder().encodeToString(png));
            body.put("srv_send_msg", false);
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + uploadPath))
                    .timeout(TIMEOUT)
                    .header("Authorization", "QQBot " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            String fileInfo = mapper.readTree(response.body()).path("file_info").asText("");
            return fileInfo.isBlank() ? Optional.empty() : Optional.of(fileInfo);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private static boolean env(String appId, String base, String uploadPath, byte[] png, int fileType) {
        return blank(appId) || blank(base) || blank(uploadPath) || png == null || png.length == 0
                || fileType < 1 || fileType > 2 || png.length > 4 * 1024 * 1024;
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
            Map<String, Object> payloadBody = new LinkedHashMap<>();
            payloadBody.put("content", content == null ? "" : content);
            payloadBody.put("msg_type", 0);
            payloadBody.put("msg_seq", 1);
            if (!blank(msgId)) {
                payloadBody.put("msg_id", msgId);
            }
            if (!blank(eventId)) {
                payloadBody.put("event_id", eventId);
            }
            String payload = mapper.writeValueAsString(payloadBody);
            HttpRequest.Builder builder = HttpRequest.newBuilder(
                            URI.create(base + path))
                    .timeout(TIMEOUT)
                    .header("Authorization", "QQBot " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
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
