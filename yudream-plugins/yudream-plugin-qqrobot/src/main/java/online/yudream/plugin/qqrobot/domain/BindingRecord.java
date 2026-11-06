package online.yudream.plugin.qqrobot.domain;

import java.time.Instant;
import java.util.Map;

public record BindingRecord(
        String id,
        String socialId,
        String socialName,
        String playerName,
        String serverId,
        Instant boundAt
) {
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
                Instant.parse(String.valueOf(document.getOrDefault("boundAt", Instant.now().toString())))
        );
    }
}
