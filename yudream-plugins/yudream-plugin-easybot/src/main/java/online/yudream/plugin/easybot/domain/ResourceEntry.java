package online.yudream.plugin.easybot.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 资源市场条目。卡片在服务端渲染为 PNG，避免浏览器端依赖外部图片。
 */
public record ResourceEntry(
        String id,
        String title,
        String subtitle,
        String description,
        String coverUrl,
        String author,
        boolean free,
        List<String> platforms,
        List<String> tags,
        long views,
        long likes,
        long downloads,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public ResourceEntry normalized() {
        return new ResourceEntry(
                id,
                title == null ? "" : title.trim(),
                subtitle == null ? "" : subtitle.trim(),
                description == null ? "" : description.trim(),
                coverUrl == null ? "" : coverUrl.trim(),
                author == null || author.isBlank() ? "官方" : author.trim(),
                free,
                platforms == null ? List.of("Java") : platforms.stream().distinct().toList(),
                tags == null ? List.of() : tags.stream().filter(t -> t != null && !t.isBlank()).distinct().toList(),
                Math.max(0, views),
                Math.max(0, likes),
                Math.max(0, downloads),
                status == null || status.isBlank() ? "published" : status,
                createdAt == null ? Instant.now() : createdAt,
                updatedAt == null ? Instant.now() : updatedAt
        );
    }

    public Map<String, Object> toDocument() {
        ResourceEntry value = normalized();
        return Map.ofEntries(
                Map.entry("id", value.id()),
                Map.entry("title", value.title()),
                Map.entry("subtitle", value.subtitle()),
                Map.entry("description", value.description()),
                Map.entry("coverUrl", value.coverUrl()),
                Map.entry("author", value.author()),
                Map.entry("free", value.free()),
                Map.entry("platforms", value.platforms()),
                Map.entry("tags", value.tags()),
                Map.entry("views", value.views()),
                Map.entry("likes", value.likes()),
                Map.entry("downloads", value.downloads()),
                Map.entry("status", value.status()),
                Map.entry("createdAt", value.createdAt().toString()),
                Map.entry("updatedAt", value.updatedAt().toString())
        );
    }

    public static ResourceEntry fromDocument(Map<String, Object> document) {
        return new ResourceEntry(
                str(document, "id"),
                str(document, "title"),
                str(document, "subtitle"),
                str(document, "description"),
                str(document, "coverUrl"),
                str(document, "author"),
                bool(document, "free", false),
                strings(document, "platforms"),
                strings(document, "tags"),
                integer(document, "views"),
                integer(document, "likes"),
                integer(document, "downloads"),
                str(document, "status"),
                instant(document, "createdAt"),
                instant(document, "updatedAt")
        ).normalized();
    }

    private static String str(Map<String, Object> document, String key) {
        Object value = document.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static long integer(Map<String, Object> document, String key) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static boolean bool(Map<String, Object> document, String key, boolean fallback) {
        Object value = document.get(key);
        return value instanceof Boolean flag ? flag : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static List<String> strings(Map<String, Object> document, String key) {
        Object value = document.get(key);
        if (value instanceof List<?> items) {
            return items.stream().filter(item -> item != null).map(String::valueOf).toList();
        }
        return List.of();
    }

    private static Instant instant(Map<String, Object> document, String key) {
        Object value = document.get(key);
        if (value == null) {
            return Instant.now();
        }
        try {
            return Instant.parse(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return Instant.now();
        }
    }
}
