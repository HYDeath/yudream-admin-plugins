package online.yudream.plugin.easybot.application.service;

import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.plugin.easybot.domain.BindSettings;
import online.yudream.plugin.easybot.domain.GroupSettings;

import java.util.List;

public class SettingsService {
    private static final String COLLECTION = "easybot-settings";
    private static final String BIND_ID = "bind";
    private static final String GROUP_PREFIX = "group:";

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
            throw EasyBotException.badRequest("绑定设置不能为空");
        }
        BindSettings normalized = settings.normalized();
        validate(normalized);
        documents.save(COLLECTION, BIND_ID, normalized.toDocument());
        return normalized;
    }

    public GroupSettings group(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            throw EasyBotException.badRequest("群号不能为空");
        }
        return documents.findById(COLLECTION, GROUP_PREFIX + groupId)
                .map(GroupSettings::fromDocument)
                .orElseGet(() -> GroupSettings.defaults(groupId));
    }

    public GroupSettings saveGroup(GroupSettings settings) {
        if (settings == null || settings.groupId() == null || settings.groupId().isBlank()) {
            throw EasyBotException.badRequest("群号不能为空");
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
            throw EasyBotException.badRequest("强制绑定服务器数量过多");
        }
        if (settings.bindKeyword().contains(" ")) {
            throw EasyBotException.badRequest("绑定关键词不能包含空格");
        }
    }

}
