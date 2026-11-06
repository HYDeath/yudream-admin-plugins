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
        int cardMaxLength
) {
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
                Math.max(1, Math.min(64, cardMaxLength))
        );
    }

    public String effectiveDisplayName() {
        return displayName == null || displayName.isBlank() ? groupId : displayName;
    }

    public Map<String, Object> toDocument() {
        GroupSettings value = normalized();
        return Map.of(
                "groupId", value.groupId(),
                "displayName", value.displayName(),
                "enabled", value.enabled(),
                "bindEnabled", value.bindEnabled(),
                "cardEnabled", value.cardEnabled(),
                "quitNoticeEnabled", value.quitNoticeEnabled(),
                "quitMessage", value.quitMessage(),
                "cardFormat", value.cardFormat(),
                "cardMaxLength", value.cardMaxLength()
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
                document.get("cardMaxLength") instanceof Number number ? number.intValue() : base.cardMaxLength()
        ).normalized();
    }

    private static boolean bool(Map<String, Object> document, String key, boolean fallback) {
        Object value = document.get(key);
        return value instanceof Boolean flag ? flag : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
}
