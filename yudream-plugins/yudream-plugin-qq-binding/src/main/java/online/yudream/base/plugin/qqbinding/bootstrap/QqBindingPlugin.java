package online.yudream.base.plugin.qqbinding.bootstrap;

import online.yudream.base.plugin.spi.annotation.PluginCommand;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginFrontend;
import online.yudream.base.plugin.spi.annotation.PluginRoute;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.spi.system.command.PluginCommandContext;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageContent;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageRequest;
import online.yudream.base.plugin.spi.system.user.PluginMessagingIdentity;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@PluginSpec(code = QqBindingPlugin.CODE, name = "QQ 消息身份绑定", version = QqBindingPlugin.VERSION, description = "通过消息平台一次性绑定码绑定当前 QQ 号或官方 OpenID")
@PluginPermission(code = QqBindingPlugin.MANAGE_PERMISSION, name = "管理消息身份绑定", module = "平台插件", description = "生成消息身份绑定码")
@PluginFrontend(moduleName = "qq-binding", menuTitle = "QQ 绑定", menuIcon = "i-ri:links-line", menuSort = 76,
        styles = {"style.css"}, routes = {
        @PluginRoute(path = "/platform/plugins/qq-binding", name = "platform-plugin-qq-binding", title = "QQ 绑定",
                icon = "i-ri:links-line", component = "qq-binding/Settings", permission = QqBindingPlugin.MANAGE_PERMISSION, sort = 10)
})
public class QqBindingPlugin implements YuDreamPlugin {
    public static final String CODE = "qq-binding";
    public static final String VERSION = "1.1.0";
    public static final String MANAGE_PERMISSION = "plugin:qq-binding:manage";

    @Override
    public void onEnable(PluginContext context) {
        // @PluginHttpEndpoint methods on this entry class are registered by the host annotation scanner.
    }

    @PluginHttpEndpoint(method = "POST", path = "/binding-codes", permission = MANAGE_PERMISSION)
    public PluginHttpResponse issueCode(PluginHttpRequest request, PluginContext context) {
        Long userId = request.principal().userId();
        var code = context.framework().qqBindings().issue(userId);
        return PluginHttpResponse.ok(Map.of("code", code.code(), "expiresAt", code.expiresAt().toString()));
    }

    @PluginCommand(code = "qq-binding.bind", command = "绑定", name = "绑定消息身份", description = "使用后台生成的六位绑定码绑定当前消息身份", allowAnonymous = true)
    public void bind(PluginCommandContext command, PluginContext context) {
        if (!allowsGroup(command, context, "bind")) return;
        if (command.arguments().size() != 1 || command.event().userId() == null || command.event().channelId() == null) {
            reply(command, context, "用法：/绑定 六位绑定码");
            return;
        }
        try {
            Long userId = context.framework().qqBindings().consume(command.arguments().getFirst());
            context.framework().users().bindMessagingIdentityOnce(userId, identityOf(command.event()));
            reply(command, context, "消息身份绑定成功。", PROFILE_BUTTONS);
        } catch (RuntimeException exception) {
            reply(command, context, exception.getMessage() == null ? "绑定失败" : exception.getMessage());
        }
    }

    @PluginCommand(code = "qq-binding.profile", command = "我的账号", name = "查询绑定账号", description = "查询当前消息身份绑定的系统账号")
    public void profile(PluginCommandContext command, PluginContext context) {
        if (!allowsGroup(command, context, "query")) return;
        if (command.userId() == null) {
            reply(command, context, "当前消息身份尚未绑定系统账号。");
            return;
        }
        var profile = context.framework().users().findById(command.userId()).orElse(null);
        if (profile == null) {
            reply(command, context, "系统账号不存在或已不可用。");
            return;
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("username", profile.username());
        variables.put("nickname", profile.nickname() == null || profile.nickname().isBlank() ? profile.username() : profile.nickname());
        boolean official = "official".equalsIgnoreCase(text(command.event().referrer().get("protocol"), command.event().platform()));
        variables.put("identityLabel", official ? "官方 OpenID" : "QQ号");
        variables.put("identity", command.event().userId());
        variables.put("qq", profile.qq() == null ? "—" : profile.qq());
        variables.put("email", profile.email() == null || profile.email().isBlank() ? "未设置" : profile.email());
        variables.put("status", profile.status() == null ? "正常" : profile.status());
        context.templateRenderer().render("account-profile", variables, "#account-card").whenComplete((image, error) -> {
            if (error != null || image == null || image.content() == null || image.content().length == 0) {
                reply(command, context, "已绑定账号：" + profile.username());
                return;
            }
            send(command, context, new PluginMessageContent(PluginMessageContent.Type.IMAGE,
                    "base64://" + Base64.getEncoder().encodeToString(image.content()), null, replyReferrer(command)));
        });
    }

    private void reply(PluginCommandContext command, PluginContext context, String text) {
        reply(command, context, text, List.of());
    }

    private boolean allowsGroup(PluginCommandContext command, PluginContext context, String action) {
        var event = command.event();
        if ("friend".equals(event.referrer().get("message_scene")) || "c2c".equals(event.referrer().get("message_scene"))
                || event.channelId() == null || event.channelId().isBlank() || !context.dependencyAvailable("qqrobot")) return true;
        try {
            Class<?> api = Class.forName("online.yudream.plugin.qqrobot.api.QqGroupPolicyApi", false, getClass().getClassLoader());
            Object provider = context.service("qqrobot", api).orElse(null);
            if (provider != null && Boolean.TRUE.equals(api.getMethod("allows", String.class, String.class).invoke(provider, event.channelId(), action))) return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            reply(command, context, "群策略暂不可用，请稍后再试。");
            return false;
        }
        reply(command, context, "该群未开启此账号服务，请前往账号服务群操作。");
        return false;
    }

    private void reply(PluginCommandContext command, PluginContext context, String text,
                       List<PluginMessageContent.Button> buttons) {
        send(command, context, new PluginMessageContent(PluginMessageContent.Type.TEXT, text, null, replyReferrer(command), buttons));
    }

    /** 绑定成功后的「我的账号」快捷指令按钮（官方 QQ 原生交互，其余协议自动降级）。 */
    private static final List<PluginMessageContent.Button> PROFILE_BUTTONS = List.of(
            PluginMessageContent.Button.command("qq-binding-profile", "我的账号", "/我的账号"));

    private void send(PluginCommandContext command, PluginContext context, PluginMessageContent content) {
        context.framework().messaging().send(new PluginMessageRequest(command.event().connectionId(), command.event().platform(),
                command.event().selfId(), command.event().channelId(), content));
    }

    /**
     * 官方被动回复必须原样回传事件会话上下文：message_scene 决定回复落到的端点
     * （C2C 事件的 channelId 是用户 openid，缺 scene 会被宿主默认按群发送而 400），
     * msg_id/event_id/interaction_id 是官方被动回复凭证。
     */
    private Map<String, Object> replyReferrer(PluginCommandContext command) {
        Map<String, Object> referrer = new LinkedHashMap<>();
        if (command.event().nativeData() instanceof Map<?, ?> data) {
            copyReplyField(referrer, data, "message_scene");
            copyReplyField(referrer, data, "msg_id");
            copyReplyField(referrer, data, "message_id");
            copyReplyField(referrer, data, "event_id");
            copyReplyField(referrer, data, "interaction_id");
        }
        String messageId = command.event().messageId();
        if (!referrer.containsKey("message_id") && messageId != null && !messageId.isBlank()) {
            referrer.put("message_id", messageId);
        }
        return referrer;
    }

    private static void copyReplyField(Map<String, Object> target, Map<?, ?> source, String key) {
        Object value = source.get(key);
        if (value != null && !String.valueOf(value).isBlank()) {
            target.putIfAbsent(key, String.valueOf(value));
        }
    }

    private static PluginMessagingIdentity identityOf(online.yudream.base.plugin.spi.system.messaging.PluginEvent event) {
        Map<String, Object> referrer = event.referrer();
        String protocol = text(referrer.get("protocol"), event.platform());
        if (protocol == null || protocol.isBlank()) {
            protocol = "milky";
        }
        String type = text(referrer.get("identityType"), null);
        String groupOpenid = text(referrer.get("groupOpenid"), event.channelId());
        if (type == null || type.isBlank()) {
            type = "official".equalsIgnoreCase(protocol)
                    ? (groupOpenid == null || groupOpenid.isBlank() ? "user_openid" : "member_openid") : "qq";
        }
        return new PluginMessagingIdentity(protocol, type, event.userId(), text(referrer.get("appId"), null),
                "member_openid".equalsIgnoreCase(type) ? groupOpenid : null, event.connectionId());
    }

    private static String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim();
    }
}
