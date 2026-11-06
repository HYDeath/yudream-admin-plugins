package online.yudream.plugin.qqrobot.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 图片模板：一段带变量占位符的 HTML，由后台「消息渲染」能力出图。
 *
 * <p>结构与 QqRobot 的「本地图片模板」对齐：名称、版本、作者、分类标签、
 * 封面缩略图 + 可编辑的 HTML 源码。模板变量用 {@code {{name}}} 表示，
 * 渲染时由调用方传入，避免模板自己发 HTTP 请求（渲染容器禁止外链子资源）。</p>
 */
public record ImageTemplate(
        String id,
        String name,
        String version,
        String description,
        String author,
        String category,
        List<String> tags,
        String cover,
        String html,
        int width,
        Instant createdAt,
        Instant updatedAt
) {
    public static final int MIN_WIDTH = 320;
    public static final int MAX_WIDTH = 1920;
    public static final int MAX_HTML_BYTES = 512 * 1024;

    public ImageTemplate normalized() {
        return new ImageTemplate(
                id == null ? "" : id,
                name == null ? "" : name.trim(),
                version == null || version.isBlank() ? "1.0.0" : version.trim(),
                description == null ? "" : description.trim(),
                author == null || author.isBlank() ? "系统模板" : author.trim(),
                category == null || category.isBlank() ? "通用" : category.trim(),
                tags == null ? List.of() : tags.stream().filter(tag -> tag != null && !tag.isBlank())
                        .map(String::trim).distinct().limit(8).toList(),
                cover == null ? "" : cover,
                html == null ? "" : html,
                Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width)),
                createdAt,
                updatedAt
        );
    }

    public Map<String, Object> toDocument() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", id);
        document.put("name", name);
        document.put("version", version);
        document.put("description", description);
        document.put("author", author);
        document.put("category", category);
        document.put("tags", tags);
        document.put("cover", cover);
        document.put("html", html);
        document.put("width", width);
        document.put("createdAt", createdAt == null ? Instant.now().toString() : createdAt.toString());
        document.put("updatedAt", updatedAt == null ? Instant.now().toString() : updatedAt.toString());
        return document;
    }

    @SuppressWarnings("unchecked")
    public static ImageTemplate fromDocument(Map<String, Object> document) {
        List<String> tags = document.get("tags") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of();
        return new ImageTemplate(
                text(document.get("id")),
                text(document.get("name")),
                text(document.get("version")),
                text(document.get("description")),
                text(document.get("author")),
                text(document.get("category")),
                tags,
                text(document.get("cover")),
                text(document.get("html")),
                number(document.get("width"), 640),
                instant(document.get("createdAt")),
                instant(document.get("updatedAt"))
        ).normalized();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static Instant instant(Object value) {
        try {
            return value == null ? Instant.now() : Instant.parse(String.valueOf(value));
        } catch (RuntimeException exception) {
            return Instant.now();
        }
    }
}
