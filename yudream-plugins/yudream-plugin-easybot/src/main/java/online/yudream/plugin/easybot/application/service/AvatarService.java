package online.yudream.plugin.easybot.application.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家头像/皮肤。渲染容器不能访问外网，所以图片必须在服务端取回并转成
 * data URI 内联进 HTML。
 *
 * <p>头像来自 Minotar（按玩家名或 UUID 取正版皮肤），失败时返回空串，
 * 模板会退回原版占位块——不会让整张图渲染失败。结果按玩家名缓存，
 * 避免每次出图都打一次第三方接口。</p>
 */
public class AvatarService {
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_BYTES = 512 * 1024;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final boolean enabled;

    public AvatarService(boolean enabled) {
        this.enabled = enabled;
    }

    /** 64px 头像，用于玩家列表。 */
    public String head(String player) {
        return fetch("head:" + player, "https://minotar.net/helm/" + encode(player) + "/64.png");
    }

    /** 180px 站立皮肤，用于玩家资料卡。 */
    public String body(String player) {
        return fetch("body:" + player, "https://minotar.net/armor/body/" + encode(player) + "/180.png");
    }

    private String fetch(String cacheKey, String url) {
        if (!enabled) {
            return "";
        }
        String normalized = cacheKey.toLowerCase();
        if (cache.containsKey(normalized)) {
            return cache.get(normalized);
        }
        String value = "";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "YuDream-EasyBot/0.1")
                    .GET().build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String type = response.headers().firstValue("content-type").orElse("");
            byte[] body = response.body();
            if (response.statusCode() == 200 && body != null && body.length > 0
                    && body.length <= MAX_BYTES && type.startsWith("image/")) {
                value = "data:" + type.split(";")[0] + ";base64,"
                        + Base64.getEncoder().encodeToString(body);
            }
        } catch (Exception ignored) {
            // 第三方接口不可达时退回占位，不影响模板其余部分。
        }
        cache.put(normalized, value);
        return value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
