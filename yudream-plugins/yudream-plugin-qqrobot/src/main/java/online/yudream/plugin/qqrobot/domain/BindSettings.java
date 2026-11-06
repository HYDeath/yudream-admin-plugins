package online.yudream.plugin.qqrobot.domain;

import java.util.List;
import java.util.Map;

/**
 * 绑定设置。字段与 QqRobot 后台「绑定设置」页一一对应；
 * 服务端只消费生效值，不直接读写文档。
 */
public record BindSettings(
        String bindMode,
        List<String> forcedServers,
        int accountsPerSocial,
        int codeTtlMinutes,
        int codeLength,
        boolean allowUnbind,
        boolean noKeywordMode,
        boolean forbidChinese,
        String commonPrefix,
        String codeCharset,
        String bindKeyword,
        String unbindKeyword,
        String unboundKickMessage,
        String bindSuccessMessage,
        String bindFailMessage,
        String unbindSuccessMessage,
        String unbindFailMessage
) {
    public static final String MODE_DUAL = "dual";
    public static final String MODE_TRUST = "trust";

    public static BindSettings defaults() {
        return new BindSettings(
                MODE_DUAL,
                List.of(),
                2,
                5,
                4,
                false,
                true,
                true,
                "",
                "numeric",
                "绑定",
                "解绑",
                "&8[&c!&8] &c你还没有绑定社交账号，无法进入本服务器。",
                "&8[&a!&8] &f绑定 &a#account &f成功，欢迎进入服务器！",
                "&8[&c!&8] &c绑定失败：#why",
                "&8[&a!&8] &f已解除绑定。",
                "&8[&c!&8] &c解绑失败：#why"
        );
    }

    public BindSettings normalized() {
        String mode = MODE_TRUST.equals(bindMode) ? MODE_TRUST : MODE_DUAL;
        int perSocial = Math.max(1, Math.min(20, accountsPerSocial));
        int ttl = Math.max(1, Math.min(120, codeTtlMinutes));
        int length = Math.max(4, Math.min(12, codeLength));
        String charset = "alnum".equals(codeCharset) ? "alnum" : "numeric";
        return new BindSettings(
                mode,
                forcedServers == null ? List.of() : forcedServers.stream().filter(s -> s != null && !s.isBlank()).distinct().toList(),
                perSocial, ttl, length, allowUnbind, noKeywordMode, forbidChinese,
                text(commonPrefix), charset,
                textOr(bindKeyword, "绑定"), textOr(unbindKeyword, "解绑"),
                textOr(unboundKickMessage, defaults().unboundKickMessage()),
                textOr(bindSuccessMessage, defaults().bindSuccessMessage()),
                textOr(bindFailMessage, defaults().bindFailMessage()),
                textOr(unbindSuccessMessage, defaults().unbindSuccessMessage()),
                textOr(unbindFailMessage, defaults().unbindFailMessage())
        );
    }

    public boolean isForced(String serverId) {
        return forcedServers != null && forcedServers.contains(serverId);
    }

    public Map<String, Object> toDocument() {
        BindSettings value = normalized();
        return Map.ofEntries(
                Map.entry("bindMode", value.bindMode()),
                Map.entry("forcedServers", value.forcedServers()),
                Map.entry("accountsPerSocial", value.accountsPerSocial()),
                Map.entry("codeTtlMinutes", value.codeTtlMinutes()),
                Map.entry("codeLength", value.codeLength()),
                Map.entry("allowUnbind", value.allowUnbind()),
                Map.entry("noKeywordMode", value.noKeywordMode()),
                Map.entry("forbidChinese", value.forbidChinese()),
                Map.entry("commonPrefix", value.commonPrefix()),
                Map.entry("codeCharset", value.codeCharset()),
                Map.entry("bindKeyword", value.bindKeyword()),
                Map.entry("unbindKeyword", value.unbindKeyword()),
                Map.entry("unboundKickMessage", value.unboundKickMessage()),
                Map.entry("bindSuccessMessage", value.bindSuccessMessage()),
                Map.entry("bindFailMessage", value.bindFailMessage()),
                Map.entry("unbindSuccessMessage", value.unbindSuccessMessage()),
                Map.entry("unbindFailMessage", value.unbindFailMessage())
        );
    }

    public static BindSettings fromDocument(Map<String, Object> document) {
        BindSettings base = defaults();
        if (document == null) {
            return base;
        }
        return new BindSettings(
                string(document, "bindMode", base.bindMode()),
                list(document, "forcedServers"),
                integer(document, "accountsPerSocial", base.accountsPerSocial()),
                integer(document, "codeTtlMinutes", base.codeTtlMinutes()),
                integer(document, "codeLength", base.codeLength()),
                bool(document, "allowUnbind", base.allowUnbind()),
                bool(document, "noKeywordMode", base.noKeywordMode()),
                bool(document, "forbidChinese", base.forbidChinese()),
                string(document, "commonPrefix", base.commonPrefix()),
                string(document, "codeCharset", base.codeCharset()),
                string(document, "bindKeyword", base.bindKeyword()),
                string(document, "unbindKeyword", base.unbindKeyword()),
                string(document, "unboundKickMessage", base.unboundKickMessage()),
                string(document, "bindSuccessMessage", base.bindSuccessMessage()),
                string(document, "bindFailMessage", base.bindFailMessage()),
                string(document, "unbindSuccessMessage", base.unbindSuccessMessage()),
                string(document, "unbindFailMessage", base.unbindFailMessage())
        ).normalized();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String string(Map<String, Object> document, String key, String fallback) {
        Object value = document.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static int integer(Map<String, Object> document, String key, int fallback) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean bool(Map<String, Object> document, String key, boolean fallback) {
        Object value = document.get(key);
        return value instanceof Boolean flag ? flag : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Map<String, Object> document, String key) {
        Object value = document.get(key);
        if (value instanceof List<?> items) {
            return items.stream().filter(item -> item != null).map(String::valueOf).toList();
        }
        return List.of();
    }
}
