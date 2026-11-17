package online.yudream.plugin.qqrobot.application.service;

import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.plugin.qqrobot.domain.BindSettings;
import online.yudream.plugin.qqrobot.domain.BindingRecord;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BindingService {
    private static final String COLLECTION = "qqrobot-binding";
    public static final String UNBOUND_ID = "未绑定ID";

    private final PluginDocumentStore documents;
    private final SettingsService settings;

    public BindingService(PluginDocumentStore documents, SettingsService settings) {
        this.documents = documents;
        this.settings = settings;
    }

    public BindingRecord bind(String socialId, String socialName, String playerName, String serverId) {
        return bind(socialId, socialName, playerName, serverId, "", "");
    }

    public BindingRecord bind(String socialId, String socialName, String playerName, String serverId,
                              String groupId, String connectionId) {
        if (socialId == null || socialId.isBlank()) {
            throw QqRobotException.badRequest("社交账号不能为空");
        }
        if (playerName == null || playerName.isBlank()) {
            throw QqRobotException.badRequest("玩家名不能为空");
        }
        BindSettings current = settings.bindSettings();
        if (current.forbidChinese() && playerName.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)) {
            throw QqRobotException.badRequest("不允许绑定中文玩家名");
        }
        long existingForSocial = countBySocial(socialId);
        boolean already = find(socialId, playerName).isPresent();
        if (!already && existingForSocial >= current.accountsPerSocial()) {
            throw QqRobotException.conflict("该社交账号最多绑定 " + current.accountsPerSocial() + " 个游戏账号");
        }
        BindingRecord record = new BindingRecord(BindingRecord.key(socialId, playerName), socialId,
                socialName == null ? "" : socialName, playerName, serverId == null ? "" : serverId,
                groupId == null ? "" : groupId, connectionId == null ? "" : connectionId, Instant.now());
        documents.save(COLLECTION, record.id(), record.toDocument());
        return record;
    }

    public boolean unbind(String socialId, String playerName) {
        Optional<BindingRecord> record = find(socialId, playerName);
        record.ifPresent(value -> documents.delete(COLLECTION, value.id()));
        return record.isPresent();
    }

    /** Remove every game account owned by one social identity, used by member-leave cleanup. */
    public List<BindingRecord> unbindAll(String socialId) {
        List<BindingRecord> records = allBySocial(socialId);
        records.forEach(record -> documents.delete(COLLECTION, record.id()));
        return records;
    }

    /** 官方成员 OpenID 属于连接和群，不能清理同值但属于另一身份作用域的记录。 */
    public List<BindingRecord> unbindInGroup(String socialId, String connectionId, String groupId) {
        if (connectionId == null || connectionId.isBlank() || groupId == null || groupId.isBlank()) {
            return List.of();
        }
        List<BindingRecord> records = allBySocial(socialId).stream()
                .filter(record -> connectionId.equals(record.connectionId()) && groupId.equals(record.groupId()))
                .toList();
        records.forEach(record -> documents.delete(COLLECTION, record.id()));
        return records;
    }

    /** 先收集再删除，避免删除前页记录后，后页偏移导致漏掉历史绑定。 */
    private List<BindingRecord> allBySocial(String socialId) {
        List<BindingRecord> records = new java.util.ArrayList<>();
        for (int pageNumber = 1; ; pageNumber++) {
            List<BindingRecord> batch = bySocial(socialId, pageNumber, 100);
            records.addAll(batch);
            if (batch.size() < 100) return records;
        }
    }

    public Optional<BindingRecord> find(String socialId, String playerName) {
        return documents.findById(COLLECTION, BindingRecord.key(socialId, playerName))
                .map(BindingRecord::fromDocument);
    }

    public Optional<BindingRecord> findByPlayer(String playerName) {
        for (int pageNumber = 1; ; pageNumber++) {
            List<BindingRecord> records = page(pageNumber, 100);
            Optional<BindingRecord> match = records.stream()
                    .filter(record -> record.playerName().equalsIgnoreCase(playerName))
                    .findFirst();
            if (match.isPresent() || records.size() < 100) {
                return match;
            }
        }
    }

    public List<BindingRecord> bySocial(String socialId, int page, int size) {
        return documents.findByField(COLLECTION, "socialId", socialId, Math.max(1, page), Math.max(1, Math.min(100, size)))
                .stream().map(BindingRecord::fromDocument).toList();
    }

    public List<BindingRecord> page(int page, int size) {
        return documents.findAll(COLLECTION, Math.max(1, page), Math.max(1, Math.min(100, size)))
                .stream().map(BindingRecord::fromDocument).toList();
    }

    public long count() {
        return documents.count(COLLECTION);
    }

    public long countBySocial(String socialId) {
        return documents.findByField(COLLECTION, "socialId", socialId, 1, 200).size();
    }

    public String describe(String socialId) {
        List<BindingRecord> records = bySocial(socialId, 1, 50);
        if (records.isEmpty()) {
            return UNBOUND_ID;
        }
        return records.stream().map(BindingRecord::playerName).reduce((left, right) -> left + "、" + right).orElse("");
    }

}
