package online.yudream.plugin.schematic.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.plugin.schematic.domain.Schematic;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

/** Sends validated block data to the isolated model renderer, never uploaded files or HTML. */
public final class DetailedPreview implements AutoCloseable {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json = new ObjectMapper();
    private final URI endpoint;
    public DetailedPreview() { this(URI.create(System.getenv().getOrDefault("SCHEMATIC_RENDER_URL", "http://schematic-render-server:3000/render"))); }
    public DetailedPreview(URI endpoint) { this.endpoint = endpoint; }
    public byte[] render(Schematic model) throws Exception {
        byte[] body = json.writeValueAsBytes(Map.of("size", new int[]{model.width(), model.height(), model.length()}, "blocks", model.blocks()));
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(100)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        byte[] png;
        try (var input = response.body()) { png = input.readNBytes(12 * 1024 * 1024 + 1); }
        if (response.statusCode() != 200) {
            String reason = new String(png, 0, Math.min(png.length, 1000), java.nio.charset.StandardCharsets.UTF_8);
            throw new IllegalArgumentException("详细渲染失败：" + reason);
        }
        if (png.length > 12 * 1024 * 1024) throw new IOException("渲染图片超过大小限制");
        var image = ImageIO.read(new ByteArrayInputStream(png));
        if (image == null || image.getWidth() != 1600 || image.getHeight() != 640 || !image.getColorModel().hasAlpha()) throw new IOException("详细渲染器返回了无效的透明预览图");
        return png;
    }
    @Override public void close() { http.shutdownNow(); }
}
