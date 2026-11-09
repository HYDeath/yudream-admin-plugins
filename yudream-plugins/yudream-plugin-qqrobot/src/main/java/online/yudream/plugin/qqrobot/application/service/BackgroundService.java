package online.yudream.plugin.qqrobot.application.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模板背景图。
 *
 * <p>原插件的模板设置允许填「背景图 URL」或「随机图 API」，渲染时由浏览器
 * 直接加载。我们的渲染容器禁止外链子资源，所以必须先在后台把图抓回来，
 * 转成 data URI 再内联进 HTML。</p>
 *
 * <p>抓取有这些约束：</p>
 * <ul>
 *   <li>支持 JSON 重定向接口（很多随机图 API 返回 {@code {"url":"..."}}）；</li>
 *   <li>只接受图片 content-type，单图上限 6MB，超时 8 秒；</li>
 *   <li>按 URL 缓存，随机图 API 带时间戳参数时自然不复用；</li>
 *   <li>失败返回空串，模板用纯色兜底，不让整张图渲染失败。</li>
 * </ul>
 */
public class BackgroundService {
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_BYTES = 6 * 1024 * 1024;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * @param source   固定 URL 或随机图 API
     * @param refresh  true 时跳过缓存（后台点「换一张」）
     */
    public String dataUri(String source, boolean refresh) {
        if (source == null || source.isBlank()) {
            return "";
        }
        String url = source.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "";
        }
        if (!refresh) {
            String cached = cache.get(url);
            if (cached != null) {
                return cached;
            }
        }
        String value = fetch(url);
        cache.put(url, value);
        return value;
    }

    public String dataUri(String source) {
        return dataUri(source, false);
    }

    private String fetch(String url) {
        try {
            ResponseEntity first = get(url);
            if (first == null) {
                return "";
            }
            if (first.image()) {
                return first.dataUri();
            }
            // 随机图 API 常见两种返回：纯文本 URL、{"url": "..."}。
            String text = new String(first.body(), java.nio.charset.StandardCharsets.UTF_8).trim();
            String next = extractUrl(text);
            if (next.isBlank() || next.equals(url)) {
                return "";
            }
            ResponseEntity second = get(next);
            return second != null && second.image() ? second.dataUri() : "";
        } catch (Exception exception) {
            return "";
        }
    }

    private ResponseEntity get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "YuDream-QqRobot/0.3")
                    .header("Accept", "image/*, application/json, text/plain")
                    .GET().build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            byte[] body = response.body();
            if (body == null || body.length == 0 || body.length > MAX_BYTES) {
                return null;
            }
            String type = response.headers().firstValue("content-type").orElse("").toLowerCase();
            return new ResponseEntity(body, type.startsWith("image/"), type);
        } catch (Exception exception) {
            return null;
        }
    }

    /** 从 API 响应里抠 URL：先按 JSON 的 {@code url} 字段，再退回整段文本。 */
    private static String extractUrl(String text) {
        if (text.isBlank()) {
            return "";
        }
        int index = text.indexOf("\"url\"");
        if (index >= 0) {
            int start = text.indexOf('"', index + 5);
            if (start >= 0) {
                StringBuilder builder = new StringBuilder();
                for (int cursor = start + 1; cursor < text.length(); cursor++) {
                    char ch = text.charAt(cursor);
                    if (ch == '\\' && cursor + 1 < text.length()) {
                        builder.append(text.charAt(++cursor));
                        continue;
                    }
                    if (ch == '"') {
                        break;
                    }
                    builder.append(ch);
                }
                String value = builder.toString().trim();
                if (value.startsWith("http")) {
                    return value;
                }
            }
        }
        return text.startsWith("http") && !text.contains("\n") ? text : "";
    }

    private record ResponseEntity(byte[] body, boolean image, String type) {
        String dataUri() {
            String mime = type == null || type.isBlank() ? "image/jpeg" : type.split(";")[0];
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(body);
        }
    }
}
