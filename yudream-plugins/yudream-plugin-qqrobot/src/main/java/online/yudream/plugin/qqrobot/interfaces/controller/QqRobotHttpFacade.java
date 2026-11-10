package online.yudream.plugin.qqrobot.interfaces.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.plugin.qqrobot.application.service.BindCodeService;
import online.yudream.plugin.qqrobot.application.service.BindingService;
import online.yudream.plugin.qqrobot.application.service.CommandMatcher;
import online.yudream.plugin.qqrobot.application.service.QqRobotException;
import online.yudream.plugin.qqrobot.application.service.GroupService;
import online.yudream.plugin.qqrobot.application.service.InventoryPreview;
import online.yudream.plugin.qqrobot.application.service.NoticeService;
import online.yudream.plugin.qqrobot.application.service.RenderSupport;
import online.yudream.plugin.qqrobot.application.service.ResourceService;
import online.yudream.plugin.qqrobot.application.service.ServerListData;
import online.yudream.plugin.qqrobot.application.service.SettingsService;
import online.yudream.plugin.qqrobot.application.service.TemplateService;
import online.yudream.plugin.qqrobot.bridge.BridgeHub;
import online.yudream.plugin.qqrobot.domain.BindSettings;
import online.yudream.plugin.qqrobot.domain.CommandSettings;
import online.yudream.plugin.qqrobot.domain.GroupSettings;
import online.yudream.plugin.qqrobot.domain.ResourceEntry;
import online.yudream.plugin.qqrobot.domain.ImageTemplate;
import online.yudream.plugin.qqrobot.domain.QqBotSettings;
import online.yudream.plugin.qqrobot.domain.QqIntegrationSettings;
import online.yudream.plugin.qqrobot.application.service.QqApiClient;
import online.yudream.plugin.qqrobot.application.service.RegistrationReviewIntegration;
import online.yudream.base.plugin.spi.system.secret.PluginSecretStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class QqRobotHttpFacade {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SettingsService settings;
    private final BindingService bindings;
    private final BindCodeService codes;
    private final GroupService groups;
    private final ResourceService resources;
    private final BridgeHub bridge;
    private final InventoryPreview preview;
    private final RenderSupport renderSupport;
    private final NoticeService notices;
    private final TemplateService templates;
    private final PluginSecretStore secrets;
    private final ServerListData serverData;
    private final RegistrationReviewIntegration registration;

    public QqRobotHttpFacade(SettingsService settings, BindingService bindings, BindCodeService codes,
                             GroupService groups, ResourceService resources, BridgeHub bridge,
                             InventoryPreview preview, RenderSupport renderSupport, NoticeService notices,
                             TemplateService templates, PluginSecretStore secrets) {
        this(settings, bindings, codes, groups, resources, bridge, preview, renderSupport, notices, templates,
                secrets, null, null);
    }

    public QqRobotHttpFacade(SettingsService settings, BindingService bindings, BindCodeService codes,
                             GroupService groups, ResourceService resources, BridgeHub bridge,
                             InventoryPreview preview, RenderSupport renderSupport, NoticeService notices,
                             TemplateService templates, PluginSecretStore secrets, ServerListData serverData) {
        this(settings, bindings, codes, groups, resources, bridge, preview, renderSupport, notices, templates,
                secrets, serverData, null);
    }

    public QqRobotHttpFacade(SettingsService settings, BindingService bindings, BindCodeService codes,
                             GroupService groups, ResourceService resources, BridgeHub bridge,
                             InventoryPreview preview, RenderSupport renderSupport, NoticeService notices,
                             TemplateService templates, PluginSecretStore secrets, ServerListData serverData,
                             RegistrationReviewIntegration registration) {
        this.settings = settings;
        this.bindings = bindings;
        this.codes = codes;
        this.groups = groups;
        this.resources = resources;
        this.bridge = bridge;
        this.preview = preview;
        this.renderSupport = renderSupport;
        this.notices = notices;
        this.templates = templates;
        this.secrets = secrets;
        this.serverData = serverData;
        this.registration = registration;
    }

    /** 服务器台账（来自 minecraft-server 插件），供模板配置里的筛选器使用。 */
    public PluginHttpResponse servers() {
        if (serverData == null) {
            return PluginHttpResponse.ok(Map.of("servers", List.of(), "available", false));
        }
        Map<String, Object> variables = serverData.variables(Map.of("showOnlyOnline", false, "showPlayerList", false));
        return PluginHttpResponse.ok(Map.of("servers", variables.get("servers"), "available", true));
    }

    public PluginHttpResponse bindSettings() {
        return PluginHttpResponse.ok(settings.bindSettings());
    }

    public PluginHttpResponse botSettings() {
        Map<String, Object> view = new LinkedHashMap<>(settings.botSettings().toView());
        view.put("secretConfigured", secretConfigured(settings.botSettings().appSecretRef()));
        view.put("publicKeyConfigured", secretConfigured("qq-webhook-public-key"));
        return PluginHttpResponse.ok(view);
    }

    /**
     * 机器人接入状态：连接列表直接来自宿主「QQ 消息平台」能力，插件不再保存
     * AppID / AppSecret。用户在这里看到的连接与「平台 → 消息平台」完全一致，
     * 避免同一套凭证在两处维护、互相覆盖。
     */
    public PluginHttpResponse botConnections() {
        List<Map<String, Object>> connections = new ArrayList<>();
        String capabilityState = "ready";
        String message = "";
        try {
            for (var connection : groups.connectionsOrThrow()) {
                Map<String, Object> row = new LinkedHashMap<>(connection);
                row.put("official", "official".equalsIgnoreCase(String.valueOf(connection.get("protocol"))));
                connections.add(row);
            }
        } catch (RuntimeException exception) {
            capabilityState = "unavailable";
            message = exception.getMessage() == null ? "QQ 消息平台未启用" : exception.getMessage();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", connections.isEmpty() && "ready".equals(capabilityState) ? "empty" : capabilityState);
        body.put("message", message);
        body.put("connections", connections);
        body.put("capability", "milky");
        body.put("capabilityName", "QQ 消息平台");
        body.put("managePath", "/platform/milky");
        body.put("replyEnabled", settings.botSettings().enabled());
        body.put("replyToFriendAdd", settings.botSettings().replyToFriendAdd());
        body.put("replyToGroupAdd", settings.botSettings().replyToGroupAdd());
        body.put("friendAddMessage", settings.botSettings().friendAddMessage());
        body.put("groupAddMessage", settings.botSettings().groupAddMessage());
        return PluginHttpResponse.ok(body);
    }

    public PluginHttpResponse saveBotSettings(PluginHttpRequest request) {
        return handle(() -> {
            JsonNode body = read(request);
            // 局部更新：旧版前端只提交回复策略，缺失字段按当前值补齐，
            // 避免 ConvertValue 把 appId / apiBase / sandbox 置空。
            QqBotSettings current = settings.botSettings();
            Map<String, Object> merged = new LinkedHashMap<>(current.toDocument());
            body.fields().forEachRemaining(entry -> {
                if (!entry.getValue().isNull()) {
                    merged.put(entry.getKey(), mapper.convertValue(entry.getValue(), Object.class));
                }
            });
            QqBotSettings saved = settings.saveBotSettings(
                    mapper.convertValue(merged, QqBotSettings.class));
            String secret = body.path("appSecret").asText("");
            if (!secret.isBlank()) {
                secrets.put(saved.appSecretRef(), secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            String publicKey = body.path("webhookPublicKey").asText("");
            if (!publicKey.isBlank()) {
                secrets.put("qq-webhook-public-key",
                        publicKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            Map<String, Object> view = new LinkedHashMap<>(saved.toView());
            view.put("secretConfigured", secretConfigured(saved.appSecretRef()));
            view.put("publicKeyConfigured", secretConfigured("qq-webhook-public-key"));
            return PluginHttpResponse.ok(view);
        });
    }

    /**
     * 连通性自检：用当前凭证换一次 app access token。只读，不发消息，
     * 避免误打扰真实用户。
     */
    public PluginHttpResponse testBot(PluginHttpRequest request) {
        return handle(() -> {
            QqBotSettings bot = settings.botSettings();
            String secret = secretValue(bot.appSecretRef());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("appId", bot.appId());
            body.put("apiBase", bot.apiBase());
            body.put("webhookPath", bot.webhookPath());
            if (!bot.enabled()) {
                body.put("ok", false);
                body.put("message", "机器人尚未启用");
                return PluginHttpResponse.ok(body);
            }
            if (bot.appId().isBlank() || secret.isBlank()) {
                body.put("ok", false);
                body.put("message", "缺少 AppID 或 AppSecret");
                return PluginHttpResponse.ok(body);
            }
            QqApiClient client = new QqApiClient();
            String token = client.accessToken(bot.appId(), secret);
            body.put("ok", token != null);
            body.put("message", token == null
                    ? "换取 access_token 失败，请检查 AppID / AppSecret 是否与开放平台一致"
                    : "凭证有效，可正常调用开放平台接口");
            return PluginHttpResponse.ok(body);
        });
    }

    private boolean secretConfigured(String name) {
        return !secretValue(name).isBlank();
    }

    private String secretValue(String name) {
        try {
            return secrets.get(name).map(String::new).orElse("");
        } catch (RuntimeException exception) {
            return "";
        }
    }

    public PluginHttpResponse saveBindSettings(PluginHttpRequest request) {
        return handle(() -> PluginHttpResponse.ok(settings.saveBindSettings(
                mapper.convertValue(read(request), BindSettings.class))));
    }

    /**
     * 指令设置。保存后立即广播给已连接的服务端，聊天栏触发词不需要重启。
     */
    public PluginHttpResponse commandSettings() {
        return PluginHttpResponse.ok(settings.commandSettings());
    }

    public PluginHttpResponse saveCommandSettings(PluginHttpRequest request) {
        return handle(() -> {
            CommandSettings saved = settings.saveCommandSettings(
                    mapper.convertValue(read(request), CommandSettings.class));
            broadcastCommands(saved);
            return PluginHttpResponse.ok(saved);
        });
    }

    /**
     * 指令模拟：用当前（或请求里临时传入的未保存）指令配置跑一次匹配。
     * 只返回命中的动作/参数，不执行绑定、出图等副作用。
     */
    public PluginHttpResponse testCommand(PluginHttpRequest request) {
        return handle(() -> {
            JsonNode body = read(request);
            String text = body.path("text").asText("");
            String scope = body.path("scope").asText("group");
            if (text.isBlank()) {
                throw QqRobotException.badRequest("text 不能为空");
            }
            CommandSettings draft = body.path("commands").isObject()
                    ? mapper.convertValue(body.path("commands"), CommandSettings.class)
                    : settings.commandSettings();
            CommandMatcher.Match match = new CommandMatcher(draft).match(text, scope);
            if (match == null) {
                return PluginHttpResponse.ok(Map.of("matched", false, "text", text, "scope", scope));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("matched", true);
            result.put("text", text);
            result.put("scope", scope);
            result.put("action", match.action());
            result.put("label", match.label());
            result.put("args", match.args());
            result.put("replyHint", match.command().replyHint());
            result.put("willReply", !match.command().replyHint().isBlank());
            return PluginHttpResponse.ok(result);
        });
    }

    public PluginHttpResponse integrationSettings() {
        QqIntegrationSettings value = settings.integrationSettings();
        return PluginHttpResponse.ok(value.toView(registration != null && registration.providerAvailable()));
    }

    public PluginHttpResponse saveIntegrationSettings(PluginHttpRequest request) {
        return handle(() -> {
            QqIntegrationSettings saved = settings.saveIntegrationSettings(
                    mapper.convertValue(read(request), QqIntegrationSettings.class));
            return PluginHttpResponse.ok(saved.toView(registration != null && registration.providerAvailable()));
        });
    }

    /** 只把启用的动作同步给服务端，避免服务端匹配到后台已关闭的指令。 */
    private void broadcastCommands(CommandSettings settings) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commands", settings.commands().stream()
                .filter(CommandSettings.CommandBinding::enabled)
                .filter(command -> command.supports("game"))
                .map(command -> Map.of(
                        "action", command.action(),
                        "triggers", command.triggers()))
                .toList());
        payload.put("chatTriggerEnabled", true);
        try {
            bridge.broadcast("commands.sync", payload);
        } catch (RuntimeException ignored) {
            // 没有在线服务端时下次接入会通过 hello 后的同步补齐。
        }
    }

    public PluginHttpResponse groups(PluginHttpRequest request) {
        return handle(() -> {
            int page = intQuery(request, "page", 1);
            int size = intQuery(request, "size", 10);
            List<GroupSettings> rows = groups.groups(page, size);
            return PluginHttpResponse.ok(Map.of("records", rows, "total", groups.count(), "page", page, "size", size));
        });
    }

    public PluginHttpResponse saveGroup(PluginHttpRequest request) {
        return handle(() -> PluginHttpResponse.ok(groups.save(
                mapper.convertValue(read(request), GroupSettings.class))));
    }

    public PluginHttpResponse deleteGroup(PluginHttpRequest request) {
        return handle(() -> {
            groups.delete(pathVar(request, "groupId"));
            return PluginHttpResponse.ok(Map.of("deleted", true));
        });
    }

    public PluginHttpResponse connections() {
        return PluginHttpResponse.ok(groups.connections());
    }

    public PluginHttpResponse optionsGroups(PluginHttpRequest request) {
        return PluginHttpResponse.ok(groups.availableGroups(query(request, "connectionId")));
    }

    public PluginHttpResponse cards(PluginHttpRequest request) {
        return handle(() -> {
            int page = intQuery(request, "page", 1);
            int size = intQuery(request, "size", 12);
            String keyword = query(request, "keyword");
            String status = query(request, "status");
            List<Map<String, Object>> records = resources.page(keyword, status, page, size).stream()
                    .map(entry -> cardView(entry)).toList();
            return PluginHttpResponse.ok(Map.of("records", records, "total", resources.count(keyword, status),
                    "page", page, "size", size));
        });
    }

    public PluginHttpResponse saveCard(PluginHttpRequest request) {
        return handle(() -> {
            ResourceEntry saved = resources.save(mapper.convertValue(read(request), ResourceEntry.class));
            resources.invalidateCard(saved.id());
            return PluginHttpResponse.ok(cardView(saved));
        });
    }

    /** 主动丢弃卡片与封面缓存，下次访问时重新下载封面并出图。 */
    public PluginHttpResponse refreshCard(PluginHttpRequest request) {
        return handle(() -> {
            String id = pathVar(request, "id");
            resources.find(id).orElseThrow(() -> QqRobotException.notFound("资源不存在"));
            resources.invalidateCard(id);
            resources.invalidateCover(id);
            return PluginHttpResponse.ok(Map.of("refreshed", true));
        });
    }

    public PluginHttpResponse deleteCard(PluginHttpRequest request) {
        return handle(() -> {
            resources.delete(pathVar(request, "id"));
            return PluginHttpResponse.ok(Map.of("deleted", true));
        });
    }

    public PluginHttpResponse cardImage(PluginHttpRequest request) {
        try {
            String id = pathVar(request, "id");
            byte[] png = resources.card(id).get(30, TimeUnit.SECONDS);
            return new PluginHttpResponse(200, Map.of("Cache-Control", "public, max-age=300"),
                    "image/png", png, false);
        } catch (QqRobotException exception) {
            return PluginHttpResponse.json(exception.status(), Map.of("message", exception.getMessage()));
        } catch (java.util.concurrent.ExecutionException exception) {
            return renderFailure(exception.getCause(), "资源卡片");
        } catch (Exception exception) {
            return renderFailure(exception, "资源卡片");
        }
    }

    public PluginHttpResponse bindingCodes(PluginHttpRequest request) {
        return handle(() -> {
            String player = query(request, "player");
            String serverId = query(request, "serverId");
            String socialId = query(request, "socialId");
            if (player == null || player.isBlank()) {
                throw QqRobotException.badRequest("player 不能为空");
            }
            BindCodeService.PendingCode code = codes.issue(player, serverId == null ? "" : serverId,
                    socialId == null ? "" : socialId, "", settings.bindSettings());
            return PluginHttpResponse.ok(Map.of("code", code.code(), "expiresAt", code.expiresAt().toString()));
        });
    }

    public PluginHttpResponse bindings(PluginHttpRequest request) {
        return handle(() -> {
            int page = intQuery(request, "page", 1);
            int size = intQuery(request, "size", 10);
            String socialId = query(request, "socialId");
            List<?> rows = socialId == null || socialId.isBlank()
                    ? bindings.page(page, size)
                    : bindings.bySocial(socialId, page, size);
            return PluginHttpResponse.ok(Map.of("records", rows, "total", bindings.count(), "page", page, "size", size));
        });
    }

    public PluginHttpResponse deleteBinding(PluginHttpRequest request) {
        return handle(() -> {
            // 两个路径变量不能都用「最后一段」推导：优先读显式参数，路径方式按点位取。
            String socialId = firstNonBlank(query(request, "socialId"), pathAt(request, -2));
            String player = firstNonBlank(query(request, "player"), lastSegment(request));
            boolean deleted = bindings.unbind(socialId, player);
            return PluginHttpResponse.ok(Map.of("deleted", deleted));
        });
    }

    public PluginHttpResponse bridgeState() {
        try {
            return PluginHttpResponse.ok(Map.of("servers", bridge.snapshot(),
                    "token", bridge.bridgeToken(), "pendingBindings", codes.pendingCount(),
                    "tokenPersistent", bridge.tokenPersistent(), "tokenMeta", bridge.tokenMetadata()));
        } catch (RuntimeException exception) {
            return PluginHttpResponse.json(503, Map.of("message",
                    "桥接状态读取失败：" + (exception.getMessage() == null ? exception.getClass().getSimpleName()
                            : exception.getMessage())));
        }
    }

    public PluginHttpResponse rotateToken() {
        String operator = "admin";
        return PluginHttpResponse.ok(Map.of("token", bridge.rotateToken(operator),
                "metadata", bridge.tokenMetadata()));
    }

    /** 只读通知流：供页面横幅与其他程序轮询令牌变更等事件。 */
    public PluginHttpResponse notices() {
        return PluginHttpResponse.ok(Map.of("notices", notices.active()));
    }

    public PluginHttpResponse kick(PluginHttpRequest request) {
        return handle(() -> {
            JsonNode body = read(request);
            boolean sent = bridge.kick(body.path("serverId").asText(), body.path("player").asText(),
                    body.path("message").asText(""));
            return PluginHttpResponse.ok(Map.of("sent", sent));
        });
    }

    /**
     * 渲染模型预览：不依赖服务端连接，直接用后台渲染服务生成 PNG。
     */
    public PluginHttpResponse previewInventory() {
        return renderPreview(() -> preview.sample().get(30, TimeUnit.SECONDS));
    }

    /** 指定物品的单图预览：{@code ?item=minecraft:leather_helmet}，走内置纹理与着色。 */
    public PluginHttpResponse previewItem(PluginHttpRequest request) {
        return renderPreview(() -> {
            String id = query(request, "item");
            if (id == null || id.isBlank()) {
                id = "minecraft:diamond_sword";
            }
            int count = intQuery(request, "count", 1);
            return preview.item(id, count).get(30, TimeUnit.SECONDS);
        });
    }

    /** 末影箱 + 多个潜影盒的容器版式预览。 */
    public PluginHttpResponse previewContainers() {
        return renderPreview(() -> preview.containerSample().get(30, TimeUnit.SECONDS));
    }

    public PluginHttpResponse previewSingle() {
        return renderPreview(() -> preview.single().get(30, TimeUnit.SECONDS));
    }

    /**
     * 前置自检：真的跑一次最小渲染，前端据此显示「去启用能力」引导。
     */
    public PluginHttpResponse renderStatus() {
        RenderSupport.Probe probe = renderSupport.probe();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ready", probe.ready());
        body.put("message", probe.message());
        body.put("capability", RenderSupport.CAPABILITY_CODE);
        body.put("capabilityName", RenderSupport.CAPABILITY_NAME);
        body.put("latencyMs", probe.latencyMs());
        if (!probe.size().isBlank()) {
            body.put("size", probe.size());
        }
        body.put("hint", probe.ready()
                ? "由后台「消息渲染」能力（独立 Headless Chromium）统一出图；无需在插件内配置浏览器。"
                : "请在「平台 → 能力目录 → 消息渲染」确认能力已启用，并检查服务地址可被后台访问。");
        return PluginHttpResponse.ok(body);
    }

    public PluginHttpResponse templates(PluginHttpRequest request) {
        return handle(() -> PluginHttpResponse.ok(Map.of(
                "records", templates.list(query(request, "keyword"), query(request, "category")))));
    }

    public PluginHttpResponse template(PluginHttpRequest request) {
        return handle(() -> {
            ImageTemplate template = templates.find(pathVar(request, "id"))
                    .orElseThrow(() -> QqRobotException.notFound("模板不存在"));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", template.id());
            body.put("name", template.name());
            body.put("version", template.version());
            body.put("description", template.description());
            body.put("author", template.author());
            body.put("category", template.category());
            body.put("tags", template.tags());
            body.put("cover", template.cover());
            body.put("background", template.background());
            body.put("html", template.html());
            body.put("settings", template.settings());
            Map<String, Object> schema = new LinkedHashMap<>();
            if ("builtin-server-list".equals(template.id())) {
                schema.putAll(online.yudream.plugin.qqrobot.application.service.ServerListSchema.view());
            }
            schema.put("apiSources", online.yudream.plugin.qqrobot.application.service.TemplateApiCatalog.view());
            body.put("schema", schema);
            body.put("width", template.width());
            body.put("variables", TemplateService.variables(template.html()));
            body.put("updatedAt", template.updatedAt().toString());
            return PluginHttpResponse.ok(body);
        });
    }

    public PluginHttpResponse saveTemplate(PluginHttpRequest request) {
        return handle(() -> PluginHttpResponse.ok(
                templates.save(mapper.convertValue(read(request), ImageTemplate.class)).toDocument()));
    }

    public PluginHttpResponse deleteTemplate(PluginHttpRequest request) {
        return handle(() -> {
            templates.delete(pathVar(request, "id"));
            return PluginHttpResponse.ok(Map.of("deleted", true));
        });
    }

    public PluginHttpResponse uploadTemplateCover(PluginHttpRequest request) {
        return handle(() -> {
            var part = request.parts().get("file");
            if (part == null && request.parts().size() == 1) {
                part = request.parts().values().iterator().next();
            }
            if (part == null || part.data() == null || part.data().length == 0) {
                throw QqRobotException.badRequest("请用 multipart/form-data 上传 file 字段");
            }
            String cover = templates.saveCover(pathVar(request, "id"), part.data(), part.contentType());
            return PluginHttpResponse.ok(Map.of("cover", cover));
        });
    }

    public PluginHttpResponse templateCover(PluginHttpRequest request) {
        String id = pathVar(request, "id");
        return templates.cover(id)
                .map(cover -> new PluginHttpResponse(200,
                        Map.of("Cache-Control", "public, max-age=600"), cover.contentType(), cover.content(), false))
                .orElseGet(() -> PluginHttpResponse.json(404, Map.of("message", "模板没有封面")));
    }

    public PluginHttpResponse renderTemplate(PluginHttpRequest request) {
        return handle(() -> {
            String id = pathVar(request, "id");
            JsonNode body = read(request);
            JsonNode variables = body.has("variables") ? body.get("variables") : body;
            Map<String, Object> map = mapper.convertValue(variables, Map.class);
            Map<String, Object> draft = body.has("settings")
                    ? mapper.convertValue(body.get("settings"), Map.class) : null;
            var image = templates.render(id, map, draft);
            return new PluginHttpResponse(200, Map.of("Cache-Control", "no-store"),
                    "image/png", image.content(), false);
        });
    }

    /** 不带草稿的预览（GET），给浏览器/外部工具直接取图。 */
    public PluginHttpResponse renderTemplatePreview(PluginHttpRequest request) {
        return handle(() -> {
            var image = templates.render(pathVar(request, "id"), Map.of());
            return new PluginHttpResponse(200, Map.of("Cache-Control", "no-store"),
                    "image/png", image.content(), false);
        });
    }

    /**
     * 服务端插件上报的模板渲染：body 形如 {"template":"builtin-server-list","variables":{...}}。
     * 返回 data URI，服务端可直接塞进 QQ 图片消息或写盘。
     */
    public PluginHttpResponse bridgeTemplateRender(PluginHttpRequest request) {
        return handle(() -> {
            JsonNode body = read(request);
            String templateId = body.path("template").asText("");
            JsonNode variables = body.path("variables");
            Map<String, Object> map = mapper.convertValue(variables, Map.class);
            var image = templates.render(templateId, map);
            String dataUri = "data:image/png;base64,"
                    + java.util.Base64.getEncoder().encodeToString(image.content());
            return PluginHttpResponse.ok(Map.of("width", image.width(), "height", image.height(),
                    "bytes", image.content().length, "dataUri", dataUri));
        });
    }

    private PluginHttpResponse renderPreview(ThrowingSupplier<online.yudream.base.plugin.spi.system.render.PluginRenderedImage> supplier) {
        try {
            var image = supplier.get();
            return new PluginHttpResponse(200, Map.of("Cache-Control", "no-store"),
                    "image/png", image.content(), false);
        } catch (java.util.concurrent.ExecutionException exception) {
            return renderFailure(exception.getCause(), "模型预览");
        } catch (QqRobotException exception) {
            return PluginHttpResponse.json(exception.status(), Map.of("message", exception.getMessage()));
        } catch (Exception exception) {
            return renderFailure(exception, "模型预览");
        }
    }

    private PluginHttpResponse renderFailure(Throwable error, String scene) {
        if (error instanceof QqRobotException exception) {
            return PluginHttpResponse.json(exception.status(), Map.of("message", exception.getMessage()));
        }
        String message = error == null || error.getMessage() == null
                ? "未知错误" : error.getMessage();
        return PluginHttpResponse.json(503, Map.of("message",
                RenderSupport.CAPABILITY_NAME + "服务不可用（" + scene + "）：" + message,
                "capability", RenderSupport.CAPABILITY_CODE));
    }

    private Map<String, Object> cardView(ResourceEntry entry) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entry.id());
        row.put("title", entry.title());
        row.put("subtitle", entry.subtitle());
        row.put("description", entry.description());
        row.put("author", entry.author());
        row.put("free", entry.free());
        row.put("platforms", entry.platforms());
        row.put("tags", entry.tags());
        row.put("views", entry.views());
        row.put("likes", entry.likes());
        row.put("downloads", entry.downloads());
        row.put("status", entry.status());
        row.put("coverUrl", entry.coverUrl());
        row.put("cardUrl", resources.cardUrl(entry));
        row.put("createdAt", entry.createdAt().toString());
        row.put("updatedAt", entry.updatedAt().toString());
        return row;
    }

    private PluginHttpResponse handle(ThrowingSupplier<PluginHttpResponse> action) {
        try {
            return action.get();
        } catch (QqRobotException exception) {
            return PluginHttpResponse.json(exception.status(), Map.of("message", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return PluginHttpResponse.json(400, Map.of("message", exception.getMessage()));
        } catch (Exception exception) {
            return PluginHttpResponse.json(500, Map.of("message", "操作失败：" + exception.getMessage()));
        }
    }

    private JsonNode read(PluginHttpRequest request) throws Exception {
        String body = request.body();
        return body == null || body.isBlank() ? mapper.createObjectNode() : mapper.readTree(body);
    }

    private static String query(PluginHttpRequest request, String key) {
        List<String> values = request.query().get(key);
        return values == null || values.isEmpty() ? "" : values.getFirst();
    }

    private static int intQuery(PluginHttpRequest request, String key, int fallback) {
        try {
            String value = query(request, key);
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String pathVar(PluginHttpRequest request, String name) {
        List<String> values = request.query().get(name);
        if (values != null && !values.isEmpty()) {
            return values.getFirst();
        }
        String[] parts = request.path().split("/");
        if (parts.length == 0) {
            return "";
        }
        String last = parts[parts.length - 1];
        // 形如 /resources/{id}/card.png 或 /templates/{id}/render：{id} 在最后一个动作段之前。
        if ((last.contains(".") || last.equals("render") || last.equals("cover")) && parts.length >= 2) {
            return parts[parts.length - 2];
        }
        return last;
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    /** 取路径倒数第 N 段（-1 为最后一段），越界返回空串。 */
    private static String pathAt(PluginHttpRequest request, int offset) {
        String[] parts = request.path().split("/");
        int index = parts.length + offset;
        return index >= 0 && index < parts.length ? parts[index] : "";
    }

    private static String lastSegment(PluginHttpRequest request) {
        return pathAt(request, -1);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
