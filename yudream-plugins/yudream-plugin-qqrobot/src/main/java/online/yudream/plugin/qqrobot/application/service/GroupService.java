package online.yudream.plugin.qqrobot.application.service;

import online.yudream.base.plugin.spi.system.messaging.PluginMessageContent;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageRequest;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingService;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingRawService;
import online.yudream.plugin.qqrobot.domain.BindSettings;
import online.yudream.plugin.qqrobot.domain.BindingRecord;
import online.yudream.plugin.qqrobot.domain.GroupSettings;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 群组策略与群名片/退群通知。群列表来自宿主消息服务，
 * 未接入连接时返回空列表而不是报错。
 */
public class GroupService {
    private final SettingsService settings;
    private final PluginMessagingService messaging;
    private final PluginMessagingRawService raw;

    public GroupService(SettingsService settings, PluginMessagingService messaging) {
        this(settings, messaging, null);
    }

    public GroupService(SettingsService settings, PluginMessagingService messaging,
                        PluginMessagingRawService raw) {
        this.settings = settings;
        this.messaging = messaging;
        this.raw = raw;
    }

    public List<GroupSettings> groups(int page, int size) {
        return settings.groups(page, size);
    }

    public long count() {
        return settings.countGroups();
    }

    public GroupSettings save(GroupSettings group) {
        return settings.saveGroup(group);
    }

    public void delete(String groupId) {
        settings.deleteGroup(groupId);
    }

    public List<Map<String, String>> connections() {
        try {
            return connectionsOrThrow();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    /**
     * 与 {@link #connections()} 相同，但不吞掉「能力未启用」的错误：
     * 机器人设置页需要区分「没有连接」和「消息平台没启用」。
     */
    public List<Map<String, String>> connectionsOrThrow() {
        return messaging.connections().stream()
                .map(connection -> {
                    Map<String, String> row = new java.util.LinkedHashMap<>();
                    row.put("id", connection.id());
                    row.put("name", connection.name() == null ? connection.id() : connection.name());
                    row.put("platform", connection.platform() == null ? "" : connection.platform());
                    row.put("protocol", connection.protocol() == null ? "" : connection.protocol());
                    return row;
                })
                .toList();
    }

    public List<Map<String, String>> availableGroups(String connectionId) {
        try {
            return messaging.groups(connectionId).stream()
                    .map(group -> Map.of("id", group.id(), "name", group.name() == null ? group.id() : group.name()))
                    .toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    /**
     * 发送群名片修改提示。真正的群名片操作由机器人协议侧完成，
     * 这里只下发模板，避免后台依赖具体协议实现。
     */
    public CompletableFuture<Boolean> notifyCard(String connectionId, String groupId, String playerName) {
        GroupSettings group = settings.group(groupId);
        if (!group.enabled() || !group.cardEnabled()) {
            return CompletableFuture.completedFuture(false);
        }
        String text = group.cardFormat()
                .replace("#group", group.effectiveDisplayName())
                .replace("#name", playerName);
        text = text.length() > group.cardMaxLength() ? text.substring(0, group.cardMaxLength()) : text;
        return send(connectionId, groupId, text);
    }

    public CompletableFuture<Boolean> notifyQuit(String connectionId, String groupId, String playerName) {
        GroupSettings group = settings.group(groupId);
        if (!group.enabled() || !group.quitNoticeEnabled()) {
            return CompletableFuture.completedFuture(false);
        }
        String text = group.quitMessage()
                .replace("#group", group.effectiveDisplayName())
                .replace("#name", playerName);
        return send(connectionId, groupId, text);
    }

    /**
     * Synchronize a member's group card from all of the social identity's bound players.
     * {@code force} is used after a successful bind/unbind; ordinary messages only run
     * this path when the administrator enabled the expensive correction check.
     */
    public CompletableFuture<Boolean> syncCard(String connectionId, String groupId, String socialId,
                                               String currentCard, Map<String, Object> memberData,
                                               online.yudream.plugin.qqrobot.application.service.BindingService bindings,
                                               boolean force) {
        if (raw == null || connectionId == null || connectionId.isBlank()
                || groupId == null || groupId.isBlank() || socialId == null || socialId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        BindSettings global = settings.bindSettings();
        if (!force && !global.preventUserRename()) {
            return CompletableFuture.completedFuture(false);
        }
        // Without a card/nickname in the normalized event we cannot know whether
        // a correction is needed; skip instead of issuing the same API call on
        // every group message.
        if (!force && (currentCard == null || currentCard.isBlank())) {
            return CompletableFuture.completedFuture(false);
        }
        GroupSettings group = settings.group(groupId);
        if (!group.enabled() || !group.cardEnabled()) {
            return CompletableFuture.completedFuture(false);
        }
        if (ignoredMember(global, memberData)) {
            return CompletableFuture.completedFuture(false);
        }
        List<BindingRecord> records = bindings.bySocial(socialId, 1, 200);
        if (records.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        String names = records.stream().map(BindingRecord::playerName)
                .filter(value -> value != null && !value.isBlank()).distinct()
                .reduce((left, right) -> left + " " + right).orElse("");
        if (names.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        if (global.ignoreAlreadyBoundName() && !force && containsBoundName(currentCard, records)) {
            return CompletableFuture.completedFuture(false);
        }
        String card = group.cardFormat()
                .replace("#group", group.effectiveDisplayName())
                .replace("#name", names)
                .replaceAll("&[0-9a-fk-orA-FK-OR]", "")
                .replace("§", "");
        if (card.length() > group.cardMaxLength()) {
            card = card.substring(0, group.cardMaxLength());
        }
        if (card.equals(currentCard)) {
            return CompletableFuture.completedFuture(false);
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("group_id", groupId);
        payload.put("user_id", socialId);
        payload.put("card", card);
        String method = protocol(connectionId).equalsIgnoreCase("official")
                ? "set_group_member_card" : "set_group_member_card";
        if (!protocol(connectionId).equalsIgnoreCase("official")) {
            try {
                payload.put("group_id", Long.parseLong(groupId));
                payload.put("user_id", Long.parseLong(socialId));
            } catch (NumberFormatException ignored) {
                return CompletableFuture.completedFuture(false);
            }
        }
        return raw.invoke(connectionId, method, payload).toCompletableFuture()
                .thenApply(result -> true).exceptionally(error -> false);
    }

    private String protocol(String connectionId) {
        try {
            return messaging.connections().stream().filter(value -> connectionId.equals(value.id()))
                    .map(value -> value.protocol() == null ? "" : value.protocol()).findFirst().orElse("");
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static boolean containsBoundName(String card, List<BindingRecord> records) {
        if (card == null || card.isBlank()) return false;
        String normalized = card.toLowerCase(java.util.Locale.ROOT);
        return records.stream().anyMatch(record -> record.playerName() != null
                && !record.playerName().isBlank()
                && normalized.contains(record.playerName().toLowerCase(java.util.Locale.ROOT)));
    }

    private static boolean ignoredMember(BindSettings settings, Map<String, Object> member) {
        if (member == null || member.isEmpty()) return false;
        String role = text(member, "role", "permission", "member_role", "authority").toLowerCase(java.util.Locale.ROOT);
        if (settings.ignoreGroupOwner() && (role.contains("owner") || role.contains("群主") || "2".equals(role))) return true;
        if (settings.ignoreGroupAdmins() && (role.contains("admin") || role.contains("管理员") || "administrator".equals(role) || "1".equals(role))) return true;
        Object roles = member.get("roles");
        if (roles instanceof Iterable<?> values) {
            for (Object value : values) {
                String item = String.valueOf(value).toLowerCase(java.util.Locale.ROOT);
                if (settings.ignoredPermissionGroups().stream().anyMatch(group -> group.equalsIgnoreCase(item))) return true;
            }
        }
        return settings.ignoredPermissionGroups().stream().anyMatch(group -> role.equalsIgnoreCase(group));
    }

    private static String text(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return "";
    }

    private CompletableFuture<Boolean> send(String connectionId, String groupId, String text) {
        if (connectionId == null || connectionId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        return messaging.sendToChannel(connectionId, groupId,
                        new PluginMessageContent(PluginMessageContent.Type.TEXT, text, null, Map.of()))
                .toCompletableFuture()
                .thenApply(result -> true)
                .exceptionally(error -> false);
    }

    @SuppressWarnings("unused")
    private static PluginMessageRequest unused() {
        return null;
    }
}
