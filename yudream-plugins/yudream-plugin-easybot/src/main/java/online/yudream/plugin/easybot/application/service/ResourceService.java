package online.yudream.plugin.easybot.application.service;

import online.yudream.base.plugin.spi.system.render.PluginRenderService;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.spi.system.storage.PluginFileStore;
import online.yudream.plugin.easybot.domain.ResourceEntry;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ResourceService {
    private static final String COLLECTION = "easybot-resource";
    private static final String CARD_PREFIX = "cards/";
    private static final String COVER_PREFIX = "covers/";
    private static final int MAX_COVER_BYTES = 4 * 1024 * 1024;
    private static final Duration COVER_TIMEOUT = Duration.ofSeconds(8);

    private final PluginDocumentStore documents;
    private final PluginFileStore files;
    private final ResourceCardRenderer renderer;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public ResourceService(PluginDocumentStore documents, PluginFileStore files, PluginRenderService render) {
        this(documents, files, new RenderSupport(render));
    }

    public ResourceService(PluginDocumentStore documents, PluginFileStore files, RenderSupport render) {
        this.documents = documents;
        this.files = files;
        this.renderer = new ResourceCardRenderer(render);
    }

    public List<ResourceEntry> page(String keyword, String status, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(50, size));
        return documents.findAll(COLLECTION, 1, 2000).stream()
                .map(ResourceEntry::fromDocument)
                .filter(entry -> status == null || status.isBlank() || status.equals(entry.status()))
                .filter(entry -> keyword == null || keyword.isBlank() || entry.title().contains(keyword)
                        || entry.description().contains(keyword) || entry.tags().stream().anyMatch(tag -> tag.contains(keyword)))
                .skip((long) (safePage - 1) * safeSize)
                .limit(safeSize)
                .toList();
    }

    public long count(String keyword, String status) {
        return documents.findAll(COLLECTION, 1, 2000).stream()
                .map(ResourceEntry::fromDocument)
                .filter(entry -> status == null || status.isBlank() || status.equals(entry.status()))
                .filter(entry -> keyword == null || keyword.isBlank() || entry.title().contains(keyword)
                        || entry.description().contains(keyword) || entry.tags().stream().anyMatch(tag -> tag.contains(keyword)))
                .count();
    }

    public Optional<ResourceEntry> find(String id) {
        return documents.findById(COLLECTION, id).map(ResourceEntry::fromDocument);
    }

    public ResourceEntry save(ResourceEntry entry) {
        if (entry == null) {
            throw EasyBotException.badRequest("资源内容不能为空");
        }
        ResourceEntry existing = entry.id() == null || entry.id().isBlank()
                ? null : find(entry.id()).orElse(null);
        Instant now = Instant.now();
        ResourceEntry value = new ResourceEntry(
                existing == null ? UUID.randomUUID().toString() : existing.id(),
                entry.title(), entry.subtitle(), entry.description(), entry.coverUrl(), entry.author(),
                entry.free(), entry.platforms(), entry.tags(),
                existing == null ? 0 : existing.views(),
                existing == null ? 0 : existing.likes(),
                existing == null ? 0 : existing.downloads(),
                entry.status(),
                existing == null ? now : existing.createdAt(),
                now
        ).normalized();
        if (value.title().isBlank()) {
            throw EasyBotException.badRequest("资源标题不能为空");
        }
        if (existing != null && !existing.coverUrl().equals(value.coverUrl())) {
            invalidateCover(value.id());
        }
        documents.save(COLLECTION, value.id(), value.toDocument());
        return value;
    }

    public void delete(String id) {
        Optional<ResourceEntry> existing = find(id);
        if (existing.isEmpty()) {
            throw EasyBotException.notFound("资源不存在");
        }
        documents.delete(COLLECTION, id);
        deleteQuietly(COVER_PREFIX + id + ".img");
        deleteQuietly(CARD_PREFIX + id + ".png");
    }

    private void deleteQuietly(String key) {
        try {
            files.delete(key);
        } catch (RuntimeException ignored) {
            // 卡片可能尚未生成，删除失败不影响元数据删除。
        }
    }

    public ResourceEntry visit(String id) {
        ResourceEntry entry = find(id).orElseThrow(() -> EasyBotException.notFound("资源不存在"));
        ResourceEntry value = new ResourceEntry(entry.id(), entry.title(), entry.subtitle(), entry.description(),
                entry.coverUrl(), entry.author(), entry.free(), entry.platforms(), entry.tags(),
                entry.views() + 1, entry.likes(), entry.downloads(), entry.status(), entry.createdAt(), Instant.now());
        documents.save(COLLECTION, value.id(), value.toDocument());
        return value;
    }

    /**
     * 生成并缓存资源卡片。已缓存时直接返回，避免重复调用渲染服务。
     */
    public CompletableFuture<byte[]> card(String id) {
        ResourceEntry entry = find(id).orElseThrow(() -> EasyBotException.notFound("资源不存在"));
        try {
            var stored = files.get(cardKey(entry));
            if (stored != null && stored.inputStream() != null) {
                try (var stream = stored.inputStream()) {
                    byte[] cached = stream.readAllBytes();
                    if (cached.length > 0) {
                        return CompletableFuture.completedFuture(cached);
                    }
                }
            }
        } catch (Exception ignored) {
            // 缓存不可用时回退到重新渲染。
        }
        String cover = coverDataUri(entry);
        return renderer.card(entry, cover).thenApply(image -> {
            byte[] content = image.content();
            if (content != null && content.length > 0) {
                try {
                    files.put(cardKey(entry), new ByteArrayInputStream(content), content.length, "image/png");
                } catch (RuntimeException ignored) {
                    // 插件文件存储（S3）不可用时只跳过缓存，卡片照常返回。
                }
            }
            return content;
        });
    }

    public void invalidateCard(String id) {
        deleteQuietly(CARD_PREFIX + id + ".png");
    }

    public void invalidateCover(String id) {
        deleteQuietly(COVER_PREFIX + id + ".img");
    }

    public String cardUrl(ResourceEntry entry) {
        return "/api/plugins/easybot/resources/" + entry.id() + "/card.png";
    }

    /**
     * 渲染容器对 HTML 的外链子资源一律 abort（安全隔离），所以封面必须在后台
     * 先下载成 data URI。下载失败不影响卡片本身，退回渐变占位。
     */
    String coverDataUri(ResourceEntry entry) {
        String url = entry.coverUrl();
        if (url == null || url.isBlank()) {
            return "";
        }
        String key = COVER_PREFIX + entry.id() + ".img";
        try {
            var stored = files.get(key);
            if (stored != null && stored.inputStream() != null) {
                try (var stream = stored.inputStream()) {
                    String cached = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    if (cached.startsWith("data:image/")) {
                        return cached;
                    }
                }
            }
        } catch (Exception ignored) {
            // 缓存不可用时重新下载。
        }
        try {
            String inline = download(url);
            if (inline.isEmpty()) {
                return "";
            }
            byte[] payload = inline.getBytes(StandardCharsets.UTF_8);
            try {
                files.put(key, new ByteArrayInputStream(payload), payload.length, "text/plain");
            } catch (RuntimeException ignored) {
                // 存储不可用时每次出图重新下载封面，不影响结果。
            }
            return inline;
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private String download(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException exception) {
            return "";
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            // 允许直接填 data URI，省去管理员自己转码的步骤。
            return url.trim().startsWith("data:image/") ? url.trim() : "";
        }
        if (isPrivateHost(uri.getHost())) {
            return "";
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "YuDream-EasyBot/0.1 (+resource-card)")
                .header("Accept", "image/png,image/jpeg,image/webp,image/gif,image/*;q=0.8")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "";
            }
            byte[] body = response.body();
            if (body == null || body.length == 0 || body.length > MAX_COVER_BYTES) {
                return "";
            }
            String type = response.headers().firstValue("content-type").orElse("").split(";")[0].trim().toLowerCase();
            if (!type.startsWith("image/")) {
                type = sniff(body);
            }
            if (!type.startsWith("image/")) {
                return "";
            }
            return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(body);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String sniff(byte[] body) {
        if (body.length > 8 && (body[0] & 0xff) == 0x89 && body[1] == 'P' && body[2] == 'N' && body[3] == 'G') {
            return "image/png";
        }
        if (body.length > 3 && (body[0] & 0xff) == 0xff && (body[1] & 0xff) == 0xd8) {
            return "image/jpeg";
        }
        if (body.length > 12 && body[0] == 'R' && body[1] == 'I' && body[2] == 'F' && body[3] == 'F'
                && body[8] == 'W' && body[9] == 'E' && body[10] == 'B' && body[11] == 'P') {
            return "image/webp";
        }
        if (body.length > 3 && body[0] == 'G' && body[1] == 'I' && body[2] == 'F') {
            return "image/gif";
        }
        return "";
    }

    /** 封面抓取走后台进程，必须挡住内网地址，避免被当成 SSRF 跳板。 */
    private static boolean isPrivateHost(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        String value = host.trim().toLowerCase();
        if (value.equals("localhost") || value.endsWith(".localhost") || value.endsWith(".internal")) {
            return true;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            return address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                    || address.isMulticastAddress();
        } catch (Exception exception) {
            return true;
        }
    }

    private static String cardKey(ResourceEntry entry) {
        return CARD_PREFIX + entry.id() + ".png";
    }

    @SuppressWarnings("unused")
    private static Map<String, Object> empty() {
        return Map.of();
    }
}
