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
    private final BackgroundService backgrounds;
    /** 用于把 {@code {{papi.xxx}}} 解析成 PlaceholderAPI 的值；为空时原样保留。 */
    private online.yudream.plugin.qqrobot.bridge.BridgeHub bridge;
    /** 服务器在线列表的数据来源；为空时退回内置示例变量。 */
    private ServerListData servers;
    /**
     * 文件存储不可用（未部署对象存储）时的兜底：把封面留在内存里。
     * 只在当前进程有效，重启后重新预热；总比模板列表全是空卡片好。
     */
    private final Map<String, byte[]> memoryCovers = java.util.Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                    // 单张封面通常 50–200KB，限 32 张足够覆盖内置模板和常用自定义模板。
                    return size() > 32;
                }
            });

    public TemplateService(PluginDocumentStore documents, PluginFileStore files, RenderSupport render,
                           String publicPrefix, AvatarService avatars) {
        this(documents, files, render, publicPrefix, avatars, new BackgroundService());
    }

    public TemplateService(PluginDocumentStore documents, PluginFileStore files, RenderSupport render,
                           String publicPrefix, AvatarService avatars, BackgroundService backgrounds) {
        this.documents = documents;
        this.files = files;
        this.render = render;
        this.publicPrefix = publicPrefix;
        this.avatars = avatars == null ? new AvatarService(false) : avatars;
        this.backgrounds = backgrounds == null ? new BackgroundService() : backgrounds;
    }

    /** 首次打开时灌入两个可直接使用的模板，避免空列表。 */
    public void seedDefaults() {
        // 按 id 逐个补齐：旧环境可能只留下部分内置模板（例如运营删掉过「玩家信息卡」），
        // 只要集合非空就整体跳过会让缺失的模板永远回不来。自定义模板仍不受影响。
        for (ImageTemplate template : BuiltinTemplates.all()) {
            if (find(template.id()).isEmpty()) {
                documents.save(COLLECTION, template.id(), template.toDocument());
            }
        }
        upgradeBuiltinServerList();
    }

    /**
     * 升级路径：旧版本存下来的「服务器在线列表」是整段 HTML，没有结构化配置。
     * 这里只重建内置模板（作者为「系统模板」），自定义模板一律不动；重建后
     * 页面上的分组配置就能立刻生效，不用等运营手动保存一次。
     */
    private void upgradeBuiltinServerList() {
        ImageTemplate existing = find("builtin-server-list").orElse(null);
        if (existing == null || !"系统模板".equals(existing.author())) {
            return;
        }
        if (!existing.settings().isEmpty() && existing.html().contains("scrim")) {
            return;
        }
        Map<String, Object> settings = ServerListSchema.merge(existing.settings(), ServerListSchema.defaults());
        documents.save(COLLECTION, existing.id(), new ImageTemplate(
                existing.id(), existing.name(), existing.version(), existing.description(), existing.author(),
                existing.category(), existing.tags(), existing.cover(), existing.background(),
                ServerListSchema.html(settings), ServerListSchema.widthOf(settings),
                settings, existing.createdAt(), Instant.now()).toDocument());
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
        Map<String, Object> settings = ServerListSchema.merge(
                existing == null ? Map.of() : existing.settings(), template.settings());
        boolean serverTemplate = isServerList(template.id(), existing);
        // 服务器在线列表的 HTML 由配置编译而来：运营改的是表单，不是整段样式。
        String html = serverTemplate ? ServerListSchema.html(settings) : template.html();
        int width = serverTemplate ? ServerListSchema.widthOf(settings) : template.width();
        ImageTemplate value = new ImageTemplate(
                existing == null ? UUID.randomUUID().toString() : existing.id(),
                template.name(), template.version(), template.description(), template.author(),
                template.category(), template.tags(),
                template.cover().isBlank() && existing != null ? existing.cover() : template.cover(),
                template.background(),
                html, width, settings,
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

    private static boolean isServerList(String id, ImageTemplate existing) {
        String key = id != null && !id.isBlank() ? id : existing == null ? "" : existing.id();
        return "builtin-server-list".equals(key);
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
                cover, existing.background(), existing.html(), existing.width(),
                existing.settings(), existing.createdAt(), Instant.now()).toDocument());
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
        return render(id, variables, null);
    }

    /**
     * 带草稿配置的预览：后台编辑页在保存前也要看到效果，所以把表单里的
     * settings 传进来，按它重新编译 HTML，不落库。
     */
    public PluginRenderedImage render(String id, Map<String, Object> variables, Map<String, Object> draftSettings) {
        ImageTemplate template = find(id).orElseThrow(() -> QqRobotException.notFound("模板不存在"));
        if (draftSettings != null && !draftSettings.isEmpty() && "builtin-server-list".equals(template.id())) {
            Map<String, Object> settings = ServerListSchema.merge(template.settings(), draftSettings);
            template = new ImageTemplate(template.id(), template.name(), template.version(),
                    template.description(), template.author(), template.category(), template.tags(),
                    template.cover(), template.background(), ServerListSchema.html(settings),
                    ServerListSchema.widthOf(settings), settings, template.createdAt(), template.updatedAt());
        }
        Map<String, Object> scope;
        if (variables == null || variables.isEmpty()) {
            // 没传变量时按模板配置生成示例：服务器列表优先用真实服务器台账。
            scope = servers != null ? servers.variables(template.settings())
                    : BuiltinTemplates.sampleVariables(template.id(), avatars);
            boolean emptyServers = !(scope.get("servers") instanceof List<?> rows) || rows.isEmpty();
            if (emptyServers) {
                scope = BuiltinTemplates.sampleVariables(template.id(), avatars);
            }
        } else {
            scope = variables;
        }
        scope = withBackground(template, scope);
        scope = applyApiSelection(template.settings(), scope);
        scope = fillAvatars(scope);
        scope = fillPlaceholders(scope);
        String html = apply(template.html(), scope);
        PluginRenderedImage image = render.html(html, "#template-root", "图片模板");
        cachePreview(template.id(), image);
        return image;
    }

    /**
     * 模板配置了背景来源（固定 URL 或随机图 API）且调用方没传背景时，
     * 在后台抓图并转成 data URI——渲染容器不允许外链子资源。
     */
    private Map<String, Object> withBackground(ImageTemplate template, Map<String, Object> variables) {
        Object current = variables.get("background");
        if (current != null && !String.valueOf(current).isBlank()) {
            return variables;
        }
        String source = template.background();
        if (source == null || source.isBlank()) {
            return variables;
        }
        String dataUri = backgrounds.dataUri(source);
        if (dataUri.isBlank()) {
            return variables;
        }
        Map<String, Object> result = new LinkedHashMap<>(variables);
        result.put("background", dataUri);
        return result;
    }

    /**
     * 编辑器中的 API 开关只控制后端注入的数据范围；模板 HTML 仍然不能自行
     * 访问网络。空数组保持兼容旧模板，表示使用全部可用数据源。
     */
    private static Map<String, Object> applyApiSelection(Map<String, Object> settings,
                                                          Map<String, Object> variables) {
        Object configured = settings == null ? null : settings.get("apiSources");
        if (!(configured instanceof List<?> list) || list.isEmpty()) {
            return variables;
        }
        java.util.Set<String> enabled = list.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet());
        Map<String, Object> result = new LinkedHashMap<>(variables);
        if (!enabled.contains("servers")) {
            result.remove("servers");
            result.remove("serverCount");
            result.remove("total");
        }
        if (!enabled.contains("player")) {
            result.remove("player");
            result.remove("avatar");
            result.remove("skin");
        }
        if (!enabled.contains("inventory")) {
            result.remove("inventory");
            result.remove("items");
            result.remove("container");
        }
        if (!enabled.contains("textures")) {
            result.remove("textures");
            result.remove("textureSource");
        }
        if (!enabled.contains("skin")) {
            result.remove("skin");
        }
        if (!enabled.contains("binding")) {
            result.remove("binding");
            result.remove("openid");
            result.remove("account");
        }
        return result;
    }

    public void attachBridge(online.yudream.plugin.qqrobot.bridge.BridgeHub bridge) {
        this.bridge = bridge;
    }

    /** 注入服务器台账（minecraft-server 插件）；为空时内置模板用示例数据。 */
    public void attachServers(ServerListData servers) {
        this.servers = servers;
    }

    /**
     * 模板里写 {@code {{papi.player_health}}} 时，后台向服务端查询真实值。
     *
     * <p>渲染容器不能访问服务器，所以这一步必须在后台完成；查询失败时
     * 保留空值，模板里对应的行会隐藏。变量里的 {@code player} 作为目标玩家，
     * 没传玩家就不查，避免无意义地广播。</p>
     */
    private Map<String, Object> fillPlaceholders(Map<String, Object> variables) {
        if (bridge == null) {
            return variables;
        }
        Object player = variables.get("player");
        String name = player == null ? "" : String.valueOf(player).trim();
        if (name.isEmpty()) {
            return variables;
        }
        List<String> keys = variables.keySet().stream()
                .filter(key -> key.startsWith("papi."))
                .toList();
        if (keys.isEmpty()) {
            return variables;
        }
        Map<String, String> values = bridge.queryPlaceholders(name,
                keys.stream().map(key -> key.substring("papi.".length())).toList(), 5000);
        if (values.isEmpty()) {
            return variables;
        }
        Map<String, Object> result = new LinkedHashMap<>(variables);
        values.forEach((placeholder, value) -> result.put("papi." + placeholder, value));
        return result;
    }

    /**
     * 启动预热内置模板封面。
     *
     * <p>封面来自第一次渲染的缓存，模板列表在没人点过「渲染」之前会全是空白卡片。
     * 这里在后台线程里把缺封面的内置模板各渲染一次；渲染服务不可用时静默跳过，
     * 等用户真正点渲染再补。</p>
     */
    public void warmCovers() {
        Thread thread = new Thread(() -> {
            for (ImageTemplate template : BuiltinTemplates.all()) {
                if (cachedPreview(template.id()).isPresent()) {
                    continue;
                }
                try {
                    render(template.id(), Map.of());
                } catch (RuntimeException ignored) {
                    // 渲染能力未启用时保持空白封面，不阻塞插件启动。
                }
            }
        }, "qqrobot-cover-warmup");
        thread.setDaemon(true);
        thread.start();
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
        byte[] memory = memoryCovers.get(id);
        if (memory != null && memory.length > 0) {
            return Optional.of(memory);
        }
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
        memoryCovers.put(id, image.content());
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
        row.put("background", template.background());
        row.put("builtin", template.author().equals("系统模板"));
        row.put("variables", variables(template.html()));
        row.put("settings", template.settings());
        Map<String, Object> schema = new LinkedHashMap<>();
        if ("builtin-server-list".equals(template.id())) {
            schema.putAll(ServerListSchema.view());
        }
        schema.put("apiSources", TemplateApiCatalog.view());
        row.put("schema", schema);
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
