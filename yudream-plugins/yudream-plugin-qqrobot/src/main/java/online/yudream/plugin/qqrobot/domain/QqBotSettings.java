package online.yudream.plugin.qqrobot.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * QQ 官方机器人接入配置。
 *
 * <p>{@code appId} 与 {@code appSecret} 是 QQ 开放平台「机器人」页签里的凭证；
 * {@code token} 是回调配置里的 Token，用于签名校验；{@code secret} 用于主动
 * 调用接口时换取 app access token。secret 不落文档库，单独存插件密钥库。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QqBotSettings(
        boolean enabled,
        String appId,
        String appSecretRef,
        String webhookPath,
        String apiBase,
        boolean sandbox,
        boolean replyToFriendAdd,
        boolean replyToGroupAdd,
        String friendAddMessage,
        String groupAddMessage,
        boolean verifySignature
) {
    public static final String SANDBOX_API = "https://sandbox.api.sgroup.qq.com";
    public static final String PRODUCTION_API = "https://api.sgroup.qq.com";

    public static QqBotSettings defaults() {
        return new QqBotSettings(
                false,
                "",
                "qq-bot-secret",
                "/api/plugins/qqrobot/qq/webhook",
                PRODUCTION_API,
                true,
                true,
                true,
                "你好，我是服务器助手。发送「绑定 游戏ID」即可获取进服验证码。",
                "机器人已加入本群，绑定相关问题可以在群里直接问我。",
                true
        );
    }

    public QqBotSettings normalized() {
        String base = apiBase == null || apiBase.isBlank()
                ? (sandbox ? SANDBOX_API : PRODUCTION_API) : apiBase.trim().replaceAll("/+$", "");
        return new QqBotSettings(
                enabled,
                appId == null ? "" : appId.trim(),
                appSecretRef == null || appSecretRef.isBlank() ? "qq-bot-secret" : appSecretRef.trim(),
                webhookPath == null || webhookPath.isBlank()
                        ? "/api/plugins/qqrobot/qq/webhook" : webhookPath.trim(),
                base,
                sandbox,
                replyToFriendAdd,
                replyToGroupAdd,
                friendAddMessage == null ? "" : friendAddMessage,
                groupAddMessage == null ? "" : groupAddMessage,
                verifySignature
        );
    }

    /** 不含 secret 的对外视图，前端和日志都只看这个。 */
    public Map<String, Object> toView() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("enabled", enabled);
        view.put("appId", appId);
        view.put("secretRef", appSecretRef);
        view.put("webhookPath", webhookPath);
        view.put("apiBase", apiBase);
        view.put("sandbox", sandbox);
        view.put("replyToFriendAdd", replyToFriendAdd);
        view.put("replyToGroupAdd", replyToGroupAdd);
        view.put("friendAddMessage", friendAddMessage);
        view.put("groupAddMessage", groupAddMessage);
        view.put("verifySignature", verifySignature);
        view.put("secretConfigured", false);
        return view;
    }

    public Map<String, Object> toDocument() {
        Map<String, Object> document = toView();
        document.remove("secretConfigured");
        return document;
    }

    public static QqBotSettings fromDocument(Map<String, Object> document) {
        QqBotSettings defaults = defaults();
        return new QqBotSettings(
                bool(document.get("enabled"), false),
                text(document.get("appId"), ""),
                text(document.get("secretRef"), defaults.appSecretRef()),
                text(document.get("webhookPath"), defaults.webhookPath()),
                text(document.get("apiBase"), defaults.apiBase()),
                bool(document.get("sandbox"), true),
                bool(document.get("replyToFriendAdd"), true),
                bool(document.get("replyToGroupAdd"), true),
                text(document.get("friendAddMessage"), defaults.friendAddMessage()),
                text(document.get("groupAddMessage"), defaults.groupAddMessage()),
                bool(document.get("verifySignature"), true)
        ).normalized();
    }

    private static String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean flag ? flag : fallback;
    }
}
