package online.yudream.plugin.qqrobot.domain;

import java.time.Instant;
import java.util.Map;

public record BindingRecord(
        String id,
        String socialId,
        String socialName,
        String playerName,
        String serverId,
        String groupId,
        String connectionId,
        Instant boundAt
) {
    /** Compatibility constructor for records written before group-aware card rules. */
    public BindingRecord(String id, String socialId, String socialName, String playerName,
                         String serverId, Instant boundAt) {
        this(id, socialId, socialName, playerName, serverId, "", "", boundAt);
    }
    public static String key(String socialId, String playerName) {
        return socialId + ":" + playerName.toLowerCase();
    }

    public Map<String, Object> toDocument() {
        return Map.of(
                "id", id,
                "socialId", socialId,
                "socialName", socialName == null ? "" : socialName,
                "playerName", playerName,
                "serverId", serverId == null ? "" : serverId,
                "groupId", groupId == null ? "" : groupId,
                "connectionId", connectionId == null ? "" : connectionId,
                "boundAt", boundAt.toString()
        );
    }

    public static BindingRecord fromDocument(Map<String, Object> document) {
        return new BindingRecord(
                String.valueOf(document.get("id")),
                String.valueOf(document.get("socialId")),
                String.valueOf(document.getOrDefault("socialName", "")),
                String.valueOf(document.get("playerName")),
                String.valueOf(document.getOrDefault("serverId", "")),
                String.valueOf(document.getOrDefault("groupId", "")),
                String.valueOf(document.getOrDefault("connectionId", "")),
                Instant.parse(String.valueOf(document.getOrDefault("boundAt", Instant.now().toString())))
        );
    }
}
