package online.yudream.plugin.qqrobot.application.service;

import online.yudream.base.plugin.spi.system.render.PluginRenderedImage;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.spi.system.storage.PluginFileStore;
import online.yudream.base.plugin.spi.system.storage.PluginStoredFile;
import online.yudream.plugin.qqrobot.domain.ImageTemplate;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地图片模板。模板是可编辑的 HTML，渲染统一交给宿主「消息渲染」能力，
 * 插件本身不维护第二套浏览器。
 *
 * <p>变量语法：{@code {{key}}} 自动 HTML 转义，{@code {{{key}}}} 原样输出；
 * {@code {{#each key}}...{{/each}}} 按列表重复。这样服务端上报的玩家名/背包
 * 数据可以直接进模板，不需要模板执行脚本。</p>
 */
public class TemplateService {
    private static final String COLLECTION = "qqrobot-template";
    private static final String COVER_PREFIX = "templates/";
    private static final Pattern EACH_OPEN = Pattern.compile("\\{\\{#each\\s+([\\w.]+)}}");
    private static final Pattern EACH_CLOSE = Pattern.compile("\\{\\{/each}}");
    private static final Pattern TRIPLE = Pattern.compile("\\{\\{\\{([\\w.]+)}}}");
    private static final Pattern DOUBLE = Pattern.compile("\\{\\{([\\w.]+)}}");
    private static final Pattern VAR_NAME = Pattern.compile("\\{\\{[#/]?([\\w.]+)}}");

    private final PluginDocumentStore documents;
    private final PluginFileStore files;
    private final RenderSupport render;
    private final String publicPrefix;
    private final AvatarService avatars;

    public TemplateService(PluginDocumentStore documents, PluginFileStore files, RenderSupport render,
                           String publicPrefix, AvatarService avatars) {
        this.documents = documents;
        this.files = files;
        this.render = render;
        this.publicPrefix = publicPrefix;
        this.avatars = avatars == null ? new AvatarService(false) : avatars;
    }

    /** 首次打开时灌入两个可直接使用的模板，避免空列表。 */
    public void seedDefaults() {
        if (!documents.findAll(COLLECTION, 1, 1).isEmpty()) {
            return;
        }
        for (ImageTemplate template : BuiltinTemplates.all()) {
            documents.save(COLLECTION, template.id(), template.toDocument());
        }
    }

    public List<Map<String, Object>> list(String keyword, String category) {
        seedDefaults();
        return documents.findAll(COLLECTION, 1, 500).stream()
                .map(ImageTemplate::fromDocument)
                .filter(template -> keyword == null || keyword.isBlank()
                        || template.name().contains(keyword) || template.description().contains(keyword)
                        || template.tags().stream().anyMatch(tag -> tag.contains(keyword)))
                .filter(template -> category == null || category.isBlank() || category.equals("全部")
                        || category.equals(template.category()))
                .sorted(Comparator.comparing(ImageTemplate::updatedAt).reversed())
                .map(this::view)
                .toList();
    }

    public Optional<ImageTemplate> find(String id) {
        return documents.findById(COLLECTION, id).map(ImageTemplate::fromDocument);
    }

    public ImageTemplate save(ImageTemplate template) {
        if (template == null) {
            throw QqRobotException.badRequest("模板内容不能为空");
        }
        ImageTemplate existing = template.id() == null || template.id().isBlank()
                ? null : find(template.id()).orElse(null);
        Instant now = Instant.now();
        ImageTemplate value = new ImageTemplate(
                existing == null ? UUID.randomUUID().toString() : existing.id(),
                template.name(), template.version(), template.description(), template.author(),
                template.category(), template.tags(),
                template.cover().isBlank() && existing != null ? existing.cover() : template.cover(),
                template.html(), template.width(),
                existing == null ? now : existing.createdAt(),
                now
        ).normalized();
        if (value.name().isBlank()) {
            throw QqRobotException.badRequest("模板名称不能为空");
        }
        if (value.html().isBlank()) {
            throw QqRobotException.badRequest("模板 HTML 不能为空");
        }
        if (value.html().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > ImageTemplate.MAX_HTML_BYTES) {
            throw QqRobotException.badRequest("模板 HTML 超过 512KB，请把图片放进资源包或封面而不是内联");
        }
        if (!value.html().stripLeading().toLowerCase().startsWith("<!doctype html>")) {
            throw QqRobotException.badRequest("模板 HTML 必须以 <!doctype html> 开头，渲染容器按整页出图");
        }
        documents.save(COLLECTION, value.id(), value.toDocument());
        return value;
    }

    public void delete(String id) {
        if (find(id).isEmpty()) {
            throw QqRobotException.notFound("模板不存在");
        }
        documents.delete(COLLECTION, id);
        for (String name : List.of(".png", ".jpg", ".svg")) {
            try {
                files.delete(COVER_PREFIX + id + "-cover" + name);
            } catch (RuntimeException ignored) {
                // 封面可能没上传过。
            }
        }
    }

    public String saveCover(String id, byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            throw QqRobotException.badRequest("封面文件为空");
        }
        if (content.length > 4 * 1024 * 1024) {
            throw QqRobotException.badRequest("封面超过 4MB，请先压缩");
        }
        String type = contentType == null ? "" : contentType.toLowerCase();
        String extension;
        if (type.contains("jpeg") || type.contains("jpg")) {
            extension = ".jpg";
        } else if (type.contains("svg")) {
            extension = ".svg";
        } else if (type.contains("png")) {
            extension = ".png";
        } else {
            throw QqRobotException.badRequest("封面只支持 PNG / JPEG / SVG");
        }
        String key = COVER_PREFIX + id + "-cover" + extension;
        files.put(key, new ByteArrayInputStream(content), content.length,
                type.contains("svg") ? "image/svg+xml" : "image/" + extension.substring(1));
        ImageTemplate existing = find(id).orElseThrow(() -> QqRobotException.notFound("模板不存在"));
        String cover = publicPrefix + "/" + id + "/cover";
        documents.save(COLLECTION, id, new ImageTemplate(existing.id(), existing.name(), existing.version(),
                existing.description(), existing.author(), existing.category(), existing.tags(),
                cover, existing.html(), existing.width(), existing.createdAt(), Instant.now()).toDocument());
        return cover;
    }

    public Optional<CoverFile> cover(String id) {
        for (String name : List.of(".png", ".jpg", ".svg")) {
            try {
                PluginStoredFile file = files.get(COVER_PREFIX + id + "-cover" + name);
                if (file != null && file.inputStream() != null) {
                    byte[] bytes = file.inputStream().readAllBytes();
                    if (bytes.length == 0) {
                        continue;
                    }
                    String type = name.equals(".svg") ? "image/svg+xml"
                            : name.equals(".jpg") ? "image/jpeg" : "image/png";
                    return Optional.of(new CoverFile(type, bytes));
                }
            } catch (RuntimeException | java.io.IOException ignored) {
                // 换扩展名重试。
            }
        }
        return Optional.empty();
    }

    /** 按模板出图；变量里带 data URI 的图片不会被渲染容器拦截。 */
    public PluginRenderedImage render(String id, Map<String, Object> variables) {
        ImageTemplate template = find(id).orElseThrow(() -> QqRobotException.notFound("模板不存在"));
        Map<String, Object> scope = variables == null || variables.isEmpty()
                ? BuiltinTemplates.sampleVariables(template.id(), avatars) : variables;
        scope = fillAvatars(scope);
        String html = apply(template.html(), scope);
        PluginRenderedImage image = render.html(html, "#template-root", "图片模板");
        cachePreview(template.id(), image);
        return image;
    }

    /**
     * 服务端上报的玩家名很多，逐个让服务端插件下载头像不现实。这里统一补齐：
     * 任何带 {@code name} 且没有 {@code avatar} 的行自动取正版头像；变量里有
     * {@code player} 时补 {@code skin}。取不到就留空，模板里会隐藏。
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> fillAvatars(Map<String, Object> variables) {
        Map<String, Object> result = new LinkedHashMap<>(variables);
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            if (entry.getValue() instanceof List<?> list) {
                List<Object> rows = new ArrayList<>(list.size());
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Map<String, Object> row = toStringMap(map);
                        Object name = row.get("name");
                        if (name != null && !String.valueOf(name).isBlank()
                                && String.valueOf(row.getOrDefault("avatar", "")).isBlank()) {
                            row.put("avatar", avatars.head(String.valueOf(name)));
                        }
                        rows.add(row);
                    } else {
                        rows.add(item);
                    }
                }
                entry.setValue(rows);
            }
        }
        Object player = result.get("player");
        if (player != null && !String.valueOf(player).isBlank()) {
            result.putIfAbsent("skin", avatars.body(String.valueOf(player)));
            result.putIfAbsent("avatar", avatars.head(String.valueOf(player)));
        }
        return result;
    }

    /** 用内置示例变量出图，模板列表的缩略图就用它。 */
    public PluginRenderedImage preview(String id) {
        return render(id, BuiltinTemplates.sampleVariables(id));
    }

    public Optional<byte[]> cachedPreview(String id) {
        try {
            PluginStoredFile file = files.get(COVER_PREFIX + id + "-preview.png");
            if (file == null || file.inputStream() == null) {
                return Optional.empty();
            }
            byte[] bytes = file.inputStream().readAllBytes();
            return bytes.length == 0 ? Optional.empty() : Optional.of(bytes);
        } catch (RuntimeException | java.io.IOException exception) {
            return Optional.empty();
        }
    }

    private void cachePreview(String id, PluginRenderedImage image) {
        try {
            files.put(COVER_PREFIX + id + "-preview.png", new ByteArrayInputStream(image.content()),
                    image.content().length, "image/png");
        } catch (RuntimeException ignored) {
            // 缓存失败不影响本次返回。
        }
    }

    /** 模板里出现的变量名，前端据此生成填写表单。 */
    public static List<String> variables(String html) {
        List<String> names = new ArrayList<>();
        Matcher matcher = VAR_NAME.matcher(html == null ? "" : html);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!name.equals("each") && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    static String apply(String html, Map<String, Object> variables) {
        String value = html == null ? "" : html;
        Map<String, Object> scope = new LinkedHashMap<>(variables);
        // {{serverCount}} 由 servers 列表自动得出，模板作者不用自己数。
        if (!scope.containsKey("serverCount") && scope.get("servers") instanceof List<?> servers) {
            scope.put("serverCount", String.valueOf(servers.size()));
        }
        StringBuilder expanded = new StringBuilder();
        int cursor = 0;
        Matcher open = EACH_OPEN.matcher(value);
        while (open.find(cursor)) {
            expanded.append(value, cursor, open.start());
            // 逐层匹配 {{/each}}，内层块由递归展开，因此这里只处理同名层数。
            int depth = 1;
            int scan = open.end();
            Matcher token = Pattern.compile("\\{\\{#each\\s+[\\w.]+}}|\\{\\{/each}}").matcher(value);
            token.region(scan, value.length());
            int blockEnd = value.length();
            while (token.find()) {
                String text = token.group();
                depth += text.startsWith("{{#each") ? 1 : -1;
                if (depth == 0) {
                    blockEnd = token.start();
                    scan = token.end();
                    break;
                }
            }
            String body = value.substring(open.end(), blockEnd);
            Object list = scope.get(open.group(1));
            if (list instanceof List<?> rows) {
                for (Object row : rows) {
                    // 内层作用域继承外层字段：players 里继续用外层的服务器名也能取到。
                    Map<String, Object> child = new LinkedHashMap<>(scope);
                    if (row instanceof Map<?, ?> map) {
                        child.putAll(toStringMap(map));
                    }
                    expanded.append(apply(body, child));
                }
            }
            cursor = scan;
        }
        expanded.append(value.substring(cursor));
        String result = TRIPLE.matcher(expanded).replaceAll(match ->
                Matcher.quoteReplacement(String.valueOf(scope.getOrDefault(match.group(1), ""))));
        return DOUBLE.matcher(result).replaceAll(match -> Matcher.quoteReplacement(
                escape(String.valueOf(scope.getOrDefault(match.group(1), "")))));
    }

    private static Map<String, Object> toStringMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private Map<String, Object> view(ImageTemplate template) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", template.id());
        row.put("name", template.name());
        row.put("version", template.version());
        row.put("description", template.description());
        row.put("author", template.author());
        row.put("category", template.category());
        row.put("tags", template.tags());
        String cover = template.cover();
        if (cover.isBlank()) {
            cover = cachedPreview(template.id())
                    .map(bytes -> "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes))
                    .orElse("");
        }
        row.put("cover", cover);
        row.put("builtin", template.author().equals("系统模板"));
        row.put("variables", variables(template.html()));
        row.put("width", template.width());
        row.put("updatedAt", template.updatedAt().toString());
        return row;
    }

    static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    public record CoverFile(String contentType, byte[] content) {
    }
}
