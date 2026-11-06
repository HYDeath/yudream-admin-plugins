package online.yudream.plugin.qqrobot.application.service;

import online.yudream.base.plugin.spi.system.messaging.PluginMessageContent;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageRequest;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingService;
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

    public GroupService(SettingsService settings, PluginMessagingService messaging) {
        this.settings = settings;
        this.messaging = messaging;
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
            return messaging.connections().stream()
                    .map(connection -> Map.of(
                            "id", connection.id(),
                            "name", connection.name() == null ? connection.id() : connection.name(),
                            "platform", connection.platform() == null ? "" : connection.platform()))
                    .toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
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
