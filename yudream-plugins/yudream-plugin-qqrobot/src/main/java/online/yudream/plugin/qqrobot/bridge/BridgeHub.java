package online.yudream.plugin.qqrobot.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import online.yudream.base.plugin.spi.ws.PluginWsSession;
import online.yudream.base.plugin.spi.system.secret.PluginSecretStore;
import online.yudream.plugin.qqrobot.application.service.BindCodeService;
import online.yudream.plugin.qqrobot.application.service.BindingService;
import online.yudream.plugin.qqrobot.application.service.QqRobotException;
import online.yudream.plugin.qqrobot.application.service.GroupService;
import online.yudream.plugin.qqrobot.application.service.ItemCardRenderer;
import online.yudream.plugin.qqrobot.application.service.SettingsService;
import online.yudream.plugin.qqrobot.domain.BindSettings;
import online.yudream.plugin.qqrobot.domain.ItemView;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 桥接协议中枢。协议为 JSON 文本帧，服务端主动连入后台。
 *
 * <pre>
 * 服务端 → 后台：hello / heartbeat / bind.request / bind.verify / bind.unbind
 *                / bind.query / player.quit / player.card / inventory.render / state.report
 * 后台 → 服务端：bind.code / bind.result / bind.reboot / inventory.rendered / error
 * </pre>
 *
 * 服务端插件通过 {@code token} 查询参数鉴权；token 保存在插件密钥存储，
 * 不在日志或前端回显。
 */
public class BridgeHub {
    public static final String PATH = "/bridge";

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, BridgeSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final SettingsService settings;
    private final BindCodeService codes;
    private final BindingService bindings;
    private final GroupService groups;
    private final ItemCardRenderer items;
    private final PluginSecretStore secrets;
    private final java.util.function.Consumer<String> notice;
    private volatile String token;

    public BridgeHub(SettingsService settings, BindCodeService codes, BindingService bindings,
                     GroupService groups, ItemCardRenderer items, PluginSecretStore secrets,
                     java.util.function.Consumer<String> notice) {
        this.settings = settings;
        this.codes = codes;
        this.bindings = bindings;
        this.groups = groups;
        this.items = items;
        this.secrets = secrets;
        this.notice = notice == null ? message -> { } : notice;
    }

    public boolean authorized(String token) {
        return token != null && !token.isBlank() && token.equals(bridgeToken());
    }

    public String bridgeToken() {
        String cached = token;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (token != null) {
                return token;
            }
            String stored = null;
            try {
                stored = secrets.get("bridge-token")
                        .map(String::new)
                        .filter(value -> !value.isBlank())
                        .orElse(null);
            } catch (RuntimeException exception) {
                // 宿主未配置密钥存储（如缺少 YUDREAM_CREDENTIAL_KEY）时降级为进程内令牌，
                // 页面不应因为拿不到令牌而 500。
                secretPersistent = false;
            }
            if (stored != null) {
                token = stored;
                return token;
            }
            String value = randomToken();
            token = value;
            try {
                secrets.put("bridge-token", value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                secretPersistent = true;
            } catch (RuntimeException exception) {
                secretPersistent = false;
            }
            generatedAt = Instant.now();
            notice.accept("令牌|桥接令牌已自动生成（" + fingerprint(value) + "），请同步到服务端插件 config.yml；"
                    + (secretPersistent ? "" : "密钥存储不可用，重启后台后令牌会变化。"));
            return token;
        }
    }

    private volatile boolean secretPersistent = true;
    private volatile Instant generatedAt = Instant.now();
    private volatile String rotatedBy = "";

    /** 令牌指纹：只暴露前后各 4 位，其他程序用它确认“换的是哪一个”而不用拿明文。 */
    public static String fingerprint(String value) {
        if (value == null || value.length() < 12) {
            return "****";
        }
        return value.substring(0, 4) + "…" + value.substring(value.length() - 4);
    }

    public Map<String, Object> tokenMetadata() {
        String value = bridgeToken();
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("fingerprint", fingerprint(value));
        meta.put("persistent", secretPersistent);
        meta.put("generatedAt", generatedAt.toString());
        if (!rotatedBy.isBlank()) {
            meta.put("rotatedBy", rotatedBy);
        }
        return meta;
    }

    /** 令牌是否已持久化；false 表示重启后服务端插件需要重新配置令牌。 */
    public boolean tokenPersistent() {
        return secretPersistent;
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String rotateToken() {
        return rotateToken("");
    }

    public String rotateToken(String operator) {
        synchronized (this) {
            String value = randomToken();
            try {
                secrets.put("bridge-token", value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                secretPersistent = true;
            } catch (RuntimeException exception) {
                secretPersistent = false;
            }
            token = value;
            generatedAt = Instant.now();
            rotatedBy = operator == null ? "" : operator;
            sessions.values().forEach(session -> session.close(1008, "桥接令牌已轮换"));
            notice.accept("令牌|桥接令牌已轮换（" + fingerprint(value) + "）"
                    + (rotatedBy.isBlank() ? "" : "，操作人 " + rotatedBy)
                    + "；已连接的服务端会被断开，需更新 config.yml 后重连。");
            return value;
        }
    }

    public void open(PluginWsSession socket, String serverId, String serverName) {
        BridgeSession session = new BridgeSession(socket, serverId == null || serverId.isBlank() ? socket.id() : serverId,
                serverName == null || serverName.isBlank() ? serverId : serverName);
        sessions.put(session.id(), session);
        write(session, frame("hello", Map.of(
                "sessionId", session.id(),
                "heartbeatSeconds", 25,
                "protocol", 1
        )));
        // 服务端接入/重连是不可见的运维事件，写入短期通知，便于其他程序确认桥接已生效。
        notice.accept("桥接|服务端已接入：" + session.serverName() + "（" + session.serverId() + "），令牌 "
                + fingerprint(bridgeToken()) + "。");
    }

    public void close(String sessionId) {
        sessions.remove(sessionId);
        pending.values().forEach(future -> future.completeExceptionally(new IllegalStateException("桥接连接已断开")));
        pending.clear();
    }

    public void onText(String sessionId, String payload) {
        BridgeSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        session.touch();
        try {
            JsonNode root = mapper.readTree(payload);
            String type = text(root, "type");
            String requestId = text(root, "requestId");
            JsonNode data = root.path("data");
            switch (type) {
                case "heartbeat" -> write(session, frame("heartbeat.ack", Map.of("time", Instant.now().toString())));
                case "state.report" -> handleState(session, data);
                case "bind.request" -> handleBindRequest(session, requestId, data);
                case "bind.verify" -> handleBindVerify(session, requestId, data);
                case "bind.unbind" -> handleUnbind(session, requestId, data);
                case "bind.query" -> handleQuery(session, requestId, data);
                case "player.quit" -> handleQuit(session, data);
                case "player.card" -> handleCard(session, data);
                case "inventory.render" -> handleInventory(session, requestId, data);
                default -> write(session, error(requestId, "未知的桥接消息类型：" + type));
            }
        } catch (QqRobotException exception) {
            write(session, error(null, exception.getMessage()));
        } catch (Exception exception) {
            write(session, error(null, "桥接消息处理失败：" + exception.getMessage()));
        }
    }

    public List<Map<String, Object>> snapshot() {
        return sessions.values().stream().map(session -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("sessionId", session.id());
            row.put("serverId", session.serverId());
            row.put("serverName", session.serverName());
            row.put("connectedAt", session.connectedAt().toString());
            row.put("lastSeen", session.lastSeen().toString());
            row.putAll(session.stateSnapshot());
            return row;
        }).toList();
    }

    public Optional<BridgeSession> session(String serverId) {
        return sessions.values().stream().filter(session -> session.serverId().equals(serverId)).findFirst();
    }

    public void broadcast(String type, Map<String, Object> data) {
        String payload = frame(type, data);
        sessions.values().forEach(session -> write(session, payload));
    }

    public boolean kick(String serverId, String playerName, String message) {
        return session(serverId).map(session -> {
            write(session, frame("bind.reboot", Map.of("player", playerName, "message", message)));
            return true;
        }).orElse(false);
    }

    public boolean renameCard(String serverId, String groupId, String playerName, String card) {
        return session(serverId).map(session -> {
            write(session, frame("group.card", Map.of("groupId", groupId, "player", playerName, "card", card)));
            return true;
        }).orElse(false);
    }

    private void handleState(BridgeSession session, JsonNode data) {
        data.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            Object plain = value.isNumber() ? value.numberValue()
                    : value.isBoolean() ? value.booleanValue()
                    : value.isTextual() ? value.textValue() : value.toString();
            session.state(entry.getKey(), plain);
        });
    }

    private void handleBindRequest(BridgeSession session, String requestId, JsonNode data) {
        String player = text(data, "player");
        String socialId = text(data, "socialId");
        String socialName = text(data, "socialName");
        BindSettings current = settings.bindSettings();
        BindCodeService.PendingCode code = codes.issue(player, session.serverId(), socialId, socialName, current);
        write(session, frame("bind.code", Map.of(
                "requestId", nullToEmpty(requestId),
                "player", player,
                "code", code.code(),
                "expiresAt", code.expiresAt().toString(),
                "commonPrefix", current.commonPrefix(),
                "keyword", current.bindKeyword()
        )));
    }

    private void handleBindVerify(BridgeSession session, String requestId, JsonNode data) {
        String code = text(data, "code");
        String player = text(data, "player");
        String socialId = text(data, "socialId");
        String socialName = text(data, "socialName");
        Optional<BindCodeService.PendingCode> pending = codes.take(code);
        if (pending.isEmpty()) {
            write(session, frame("bind.result", Map.of("requestId", nullToEmpty(requestId), "success", false,
                    "message", "绑定码无效或已过期")));
            return;
        }
        BindCodeService.PendingCode value = pending.get();
        if (!value.playerName().equalsIgnoreCase(player) || !value.serverId().equals(session.serverId())) {
            write(session, frame("bind.result", Map.of("requestId", nullToEmpty(requestId), "success", false,
                    "message", "绑定码与玩家或服务器不匹配")));
            return;
        }
        try {
            var record = bindings.bind(socialId, socialName, player, session.serverId());
            write(session, frame("bind.result", Map.of("requestId", nullToEmpty(requestId), "success", true,
                    "player", record.playerName(), "socialId", record.socialId(),
                    "message", renderMessage(settings.bindSettings().bindSuccessMessage(), record.socialId(), player))));
        } catch (QqRobotException exception) {
            write(session, frame("bind.result", Map.of("requestId", nullToEmpty(requestId), "success", false,
                    "message", renderMessage(settings.bindSettings().bindFailMessage(), socialId, player)
                            .replace("#why", exception.getMessage()))));
        }
    }

    private void handleUnbind(BridgeSession session, String requestId, JsonNode data) {
        BindSettings current = settings.bindSettings();
        if (!current.allowUnbind()) {
            write(session, frame("bind.result", Map.of("requestId", nullToEmpty(requestId), "success", false,
                    "message", "管理员已关闭解绑")));
            return;
        }
        String socialId = text(data, "socialId");
        String player = text(data, "player");
        boolean removed = bindings.unbind(socialId, player);
        write(session, frame("bind.result", Map.of("requestId", nullToEmpty(requestId), "success", removed,
                "message", renderMessage(removed ? current.unbindSuccessMessage() : current.unbindFailMessage(),
                socialId, player).replace("#why", removed ? "" : "未找到绑定记录"))));
    }

    private void handleQuery(BridgeSession session, String requestId, JsonNode data) {
        String player = text(data, "player");
        var record = bindings.findByPlayer(player);
        ObjectNode result = mapper.createObjectNode();
        result.put("requestId", nullToEmpty(requestId));
        result.put("player", player);
        result.put("bound", record.isPresent());
        record.ifPresent(value -> {
            result.put("socialId", value.socialId());
            result.put("socialName", value.socialName());
            result.put("boundAt", value.boundAt().toString());
        });
        write(session, frame("bind.status", mapper.convertValue(result, Map.class)));
    }

    private void handleQuit(BridgeSession session, JsonNode data) {
        String groupId = text(data, "groupId");
        String player = text(data, "player");
        String connectionId = text(data, "connectionId");
        groups.notifyQuit(connectionId, groupId, player);
    }

    private void handleCard(BridgeSession session, JsonNode data) {
        String groupId = text(data, "groupId");
        String player = text(data, "player");
        String connectionId = text(data, "connectionId");
        groups.notifyCard(connectionId, groupId, player);
    }

    private void handleInventory(BridgeSession session, String requestId, JsonNode data) {
        String title = text(data, "title");
        List<ItemView> items = new ArrayList<>();
        JsonNode array = data.path("items");
        if (array.isArray()) {
            for (JsonNode node : array) {
                items.add(readItem(node));
            }
        }
        this.items.inventory(title, items).whenComplete((image, error) -> {
            if (error != null || image == null || image.content() == null) {
                write(session, error(requestId, "物品渲染失败"));
                return;
            }
            write(session, frame("inventory.rendered", Map.of(
                    "requestId", nullToEmpty(requestId),
                    "image", "base64://" + Base64.getEncoder().encodeToString(image.content())
            )));
        });
    }

    private ItemView readItem(JsonNode node) {
        List<String> lore = new ArrayList<>();
        node.path("lore").forEach(line -> lore.add(line.asText()));
        List<String> enchants = new ArrayList<>();
        node.path("enchantments").forEach(line -> enchants.add(line.asText()));
        List<ItemView> container = new ArrayList<>();
        node.path("container").forEach(line -> container.add(readItem(line)));
        return new ItemView(text(node, "id"), node.path("count").asInt(1), node.path("damage").asInt(0),
                node.path("maxDamage").asInt(0), text(node, "name"), lore, enchants, text(node, "icon"),
                text(node, "rarity"), node.path("customModelData").asInt(0), container).normalized();
    }

    private String renderMessage(String template, String socialId, String player) {
        String value = template == null ? "" : template;
        value = value.replace("#account", socialId == null ? "" : socialId).replace("#name", player == null ? "" : player);
        return value.replace('&', '§');
    }

    private void write(BridgeSession session, String payload) {
        try {
            session.send(payload);
        } catch (RuntimeException ignored) {
            // 断线写失败由宿主关闭会话，下一次 snapshot/close 会清理。
        }
    }

    private String error(String requestId, String message) {
        return frame("error", Map.of("requestId", nullToEmpty(requestId), "message", message == null ? "" : message));
    }

    private String frame(String type, Map<String, Object> data) {
        try {
            return mapper.writeValueAsString(Map.of("type", type, "data", data, "time", Instant.now().toString()));
        } catch (Exception exception) {
            return "{\"type\":\"error\",\"data\":{\"message\":\"序列化失败\"}}";
        }
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
