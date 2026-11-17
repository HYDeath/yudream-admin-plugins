package online.yudream.plugin.qqrobot.domain;

import java.util.Map;

/**
 * 单个群聊的策略。为空字段表示继承全局设置。
 */
public record GroupSettings(
        String groupId,
        String displayName,
        boolean enabled,
        boolean bindEnabled,
        boolean cardEnabled,
        boolean quitNoticeEnabled,
        String quitMessage,
        String cardFormat,
        int cardMaxLength,
        boolean queryEnabled,
        boolean passwordEnabled,
        boolean welcomeEnabled,
        String welcomeMessage
) {
    public GroupSettings(String groupId, String displayName, boolean enabled, boolean bindEnabled,
                         boolean cardEnabled, boolean quitNoticeEnabled, String quitMessage,
                         String cardFormat, int cardMaxLength) {
        this(groupId, displayName, enabled, bindEnabled, cardEnabled, quitNoticeEnabled,
                quitMessage, cardFormat, cardMaxLength, true, true, false, "欢迎 #mention 加入 #group！");
    }
    public static GroupSettings defaults(String groupId) {
        return new GroupSettings(groupId, "", true, true, true, true,
                "&e#name &7离开了我们，后会有期。",
                "&7[&a#group&7] &f#name",
                16);
    }

    public GroupSettings normalized() {
        GroupSettings base = defaults(groupId);
        return new GroupSettings(
                groupId == null ? "" : groupId.trim(),
                displayName == null ? "" : displayName.trim(),
                enabled,
                bindEnabled,
                cardEnabled,
                quitNoticeEnabled,
                quitMessage == null || quitMessage.isBlank() ? base.quitMessage() : quitMessage,
                cardFormat == null || cardFormat.isBlank() ? base.cardFormat() : cardFormat,
                Math.max(1, Math.min(64, cardMaxLength)),
                queryEnabled, passwordEnabled, welcomeEnabled,
                welcomeMessage == null ? base.welcomeMessage() : welcomeMessage.strip()
        );
    }

    public String effectiveDisplayName() {
        return displayName == null || displayName.isBlank() ? groupId : displayName;
    }

    public Map<String, Object> toDocument() {
        GroupSettings value = normalized();
        return Map.ofEntries(
                Map.entry("groupId", value.groupId()),
                Map.entry("displayName", value.displayName()),
                Map.entry("enabled", value.enabled()),
                Map.entry("bindEnabled", value.bindEnabled()),
                Map.entry("cardEnabled", value.cardEnabled()),
                Map.entry("quitNoticeEnabled", value.quitNoticeEnabled()),
                Map.entry("quitMessage", value.quitMessage()),
                Map.entry("cardFormat", value.cardFormat()),
                Map.entry("cardMaxLength", value.cardMaxLength()),
                Map.entry("queryEnabled", value.queryEnabled()),
                Map.entry("passwordEnabled", value.passwordEnabled()),
                Map.entry("welcomeEnabled", value.welcomeEnabled()),
                Map.entry("welcomeMessage", value.welcomeMessage())
        );
    }

    public static GroupSettings fromDocument(Map<String, Object> document) {
        String groupId = document.get("groupId") == null ? "" : String.valueOf(document.get("groupId"));
        GroupSettings base = defaults(groupId);
        return new GroupSettings(
                groupId,
                document.get("displayName") == null ? "" : String.valueOf(document.get("displayName")),
                bool(document, "enabled", true),
                bool(document, "bindEnabled", true),
                bool(document, "cardEnabled", true),
                bool(document, "quitNoticeEnabled", true),
                document.get("quitMessage") == null ? base.quitMessage() : String.valueOf(document.get("quitMessage")),
                document.get("cardFormat") == null ? base.cardFormat() : String.valueOf(document.get("cardFormat")),
                document.get("cardMaxLength") instanceof Number number ? number.intValue() : base.cardMaxLength(),
                bool(document, "queryEnabled", true), bool(document, "passwordEnabled", true),
                bool(document, "welcomeEnabled", false),
                document.get("welcomeMessage") == null ? base.welcomeMessage() : String.valueOf(document.get("welcomeMessage"))
        ).normalized();
    }

    private static boolean bool(Map<String, Object> document, String key, boolean fallback) {
        Object value = document.get(key);
        return value instanceof Boolean flag ? flag : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
}
