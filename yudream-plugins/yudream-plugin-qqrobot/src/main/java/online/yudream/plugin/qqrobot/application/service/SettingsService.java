package online.yudream.plugin.qqrobot.application.service;

import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.plugin.qqrobot.domain.BindSettings;
import online.yudream.plugin.qqrobot.domain.CommandSettings;
import online.yudream.plugin.qqrobot.domain.GroupSettings;
import online.yudream.plugin.qqrobot.domain.QqBotSettings;
import online.yudream.plugin.qqrobot.domain.QqIntegrationSettings;

import java.util.List;

public class SettingsService {
    private static final String COLLECTION = "qqrobot-settings";
    private static final String BIND_ID = "bind";
    private static final String GROUP_PREFIX = "group:";
    private static final String BOT_ID = "qqbot";
    private static final String COMMAND_ID = "commands";
    private static final String INTEGRATION_ID = "integrations";

    private final PluginDocumentStore documents;

    public SettingsService(PluginDocumentStore documents) {
        this.documents = documents;
    }

    public BindSettings bindSettings() {
        return documents.findById(COLLECTION, BIND_ID)
                .map(BindSettings::fromDocument)
                .orElseGet(BindSettings::defaults);
    }

    public BindSettings saveBindSettings(BindSettings settings) {
        if (settings == null) {
            throw QqRobotException.badRequest("绑定设置不能为空");
        }
        BindSettings normalized = settings.normalized();
        validate(normalized);
        documents.save(COLLECTION, BIND_ID, normalized.toDocument());
        return normalized;
    }

    public GroupSettings group(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            throw QqRobotException.badRequest("群号不能为空");
        }
        return documents.findById(COLLECTION, GROUP_PREFIX + groupId)
                .map(GroupSettings::fromDocument)
                .orElseGet(() -> GroupSettings.defaults(groupId));
    }

    public QqBotSettings botSettings() {
        return documents.findById(COLLECTION, BOT_ID)
                .map(QqBotSettings::fromDocument)
                .orElseGet(QqBotSettings::defaults);
    }

    public QqBotSettings saveBotSettings(QqBotSettings settings) {
        if (settings == null) {
            throw QqRobotException.badRequest("机器人设置不能为空");
        }
        QqBotSettings normalized = settings.normalized();
        // 接入凭证由宿主「QQ 消息平台」能力统一管理，这里只保存回复策略，
        // 不再强制填写 AppID（否则和宿主连接配置互相矛盾）。
        documents.save(COLLECTION, BOT_ID, normalized.toDocument());
        return normalized;
    }

    public CommandSettings commandSettings() {
        return documents.findById(COLLECTION, COMMAND_ID)
                .map(CommandSettings::fromDocument)
                .orElseGet(CommandSettings::defaults)
                .normalized();
    }

    public CommandSettings saveCommandSettings(CommandSettings settings) {
        if (settings == null) {
            throw QqRobotException.badRequest("指令设置不能为空");
        }
        CommandSettings normalized = settings.normalized();
        for (CommandSettings.CommandBinding command : normalized.commands()) {
            if (command.triggers().isEmpty() && command.enabled()) {
                throw QqRobotException.badRequest("指令「" + command.label() + "」已启用但没有触发词");
            }
        }
        documents.save(COLLECTION, COMMAND_ID, normalized.toDocument());
        return normalized;
    }

    public QqIntegrationSettings integrationSettings() {
        return documents.findById(COLLECTION, INTEGRATION_ID)
                .map(QqIntegrationSettings::fromDocument)
                .orElseGet(QqIntegrationSettings::defaults);
    }

    public QqIntegrationSettings saveIntegrationSettings(QqIntegrationSettings settings) {
        if (settings == null) {
            throw QqRobotException.badRequest("联动设置不能为空");
        }
        QqIntegrationSettings normalized = settings.normalized();
        documents.save(COLLECTION, INTEGRATION_ID, normalized.toDocument());
        return normalized;
    }

    public GroupSettings saveGroup(GroupSettings settings) {
        if (settings == null || settings.groupId() == null || settings.groupId().isBlank()) {
            throw QqRobotException.badRequest("群号不能为空");
        }
        GroupSettings normalized = settings.normalized();
        documents.save(COLLECTION, GROUP_PREFIX + normalized.groupId(), normalized.toDocument());
        return normalized;
    }

    public void deleteGroup(String groupId) {
        documents.delete(COLLECTION, GROUP_PREFIX + groupId);
    }

    public List<GroupSettings> groups(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, size));
        return documents.findAll(COLLECTION, safePage, safeSize * 4).stream()
                .filter(document -> String.valueOf(document.getOrDefault("id", "")).startsWith(GROUP_PREFIX))
                .skip((long) (safePage - 1) * safeSize)
                .limit(safeSize)
                .map(GroupSettings::fromDocument)
                .toList();
    }

    public long countGroups() {
        return documents.findAll(COLLECTION, 1, 10_000).stream()
                .filter(document -> String.valueOf(document.getOrDefault("id", "")).startsWith(GROUP_PREFIX))
                .count();
    }

    private void validate(BindSettings settings) {
        if (settings.forcedServers().size() > 200) {
            throw QqRobotException.badRequest("强制绑定服务器数量过多");
        }
        if (settings.bindKeyword().contains(" ")) {
            throw QqRobotException.badRequest("绑定关键词不能包含空格");
        }
    }

}
