package online.yudream.plugin.qqrobot.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import online.yudream.base.plugin.spi.system.messaging.PluginEvent;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageContent;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageRequest;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingService;
import online.yudream.base.plugin.spi.system.command.PluginCommandService;
import online.yudream.base.plugin.spi.system.user.PluginMessagingIdentity;
import online.yudream.base.plugin.spi.system.user.PluginUserService;
import online.yudream.plugin.qqrobot.bridge.BridgeHub;
import online.yudream.plugin.qqrobot.domain.BindSettings;
import online.yudream.plugin.qqrobot.domain.CommandSettings;
import online.yudream.plugin.qqrobot.domain.QqBotSettings;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * QQ 官方机器人事件处理。
 *
 * <p>官方 Webhook 推送的事件与其它网关不同：好友添加是 {@code FRIEND_ADD}
 * （intent {@code GROUP_AND_C2C_EVENT}，1&lt;&lt;25），字段为
 * {@code openid / timestamp / scene / scene_param / author.union_openid}；
 * 群添加是 {@code GROUP_ADD_ROBOT}；私聊消息是 {@code C2C_MESSAGE_CREATE}，
 * 群里 @机器人 是 {@code GROUP_AT_MESSAGE_CREATE}。</p>
 *
 * <p>指令本身不写死：触发词来自「指令设置」，前缀可选，{@code /} 始终接受。
 * 容器类指令（背包/末影箱/潜影盒）向服务端要图后直接回图片消息。</p>
 */
public class QqBotService {
    private static final String FRIEND_ADD = "FRIEND_ADD";
    private static final String C2C_MESSAGE_CREATE = "C2C_MESSAGE_CREATE";
    private static final String GROUP_AT_MESSAGE_CREATE = "GROUP_AT_MESSAGE_CREATE";
    private static final String GROUP_ADD_ROBOT = "GROUP_ADD_ROBOT";
    /** 服务端渲染背包的等待上限；超时回一句提示而不是让用户一直等。 */
    private static final long RENDER_TIMEOUT_SECONDS = 25;

    private final SettingsService settings;
    private final BindCodeService codes;
    private final NoticeService notices;
    private final TemplateService templates;
    private final SecretReader secrets;
    private final QqApiClient api;
    private final BindingService bindings;
    private final RegistrationReviewIntegration registration;
    private BridgeHub bridge;
    /** 出站统一走宿主消息端口；未接入（单测）时为空。 */
    private PluginMessagingService messaging;
    /** 群组策略与群名片同步；单测/旧构造路径可为空。 */
    private GroupService groups;
    /** 宿主统一命令总线；自己的快捷词未命中时转发到这里。 */
    private PluginCommandService commandService;
    private PluginUserService users;

    public QqBotService(SettingsService settings, BindCodeService codes, NoticeService notices,
                        TemplateService templates) {
        this(settings, codes, notices, templates, null, name -> Optional.empty(), new QqApiClient());
    }

    public QqBotService(SettingsService settings, BindCodeService codes, NoticeService notices,
                        TemplateService templates, SecretReader secrets, QqApiClient api) {
        this(settings, codes, notices, templates, null, secrets, api);
    }

    public QqBotService(SettingsService settings, BindCodeService codes, NoticeService notices,
                        TemplateService templates, BindingService bindings,
                        SecretReader secrets, QqApiClient api) {
        this(settings, codes, notices, templates, bindings, secrets, api, null);
    }

    public QqBotService(SettingsService settings, BindCodeService codes, NoticeService notices,
                        TemplateService templates, BindingService bindings,
                        SecretReader secrets, QqApiClient api,
                        RegistrationReviewIntegration registration) {
        this.settings = settings;
        this.codes = codes;
        this.notices = notices;
        this.templates = templates;
        this.bindings = bindings;
        this.secrets = secrets == null ? name -> Optional.empty() : secrets;
        this.api = api == null ? new QqApiClient() : api;
        this.registration = registration;
    }

    /** 桥接中枢在插件启动时注入；为 null 时容器指令只回文字提示（便于单测）。 */
    public void attachBridge(BridgeHub bridge) {
        this.bridge = bridge;
    }

    /** 宿主消息端口：入站事件与出站回复都用它，插件不再自建 Webhook / HTTP 客户端。 */
    public void attachMessaging(PluginMessagingService messaging) {
        this.messaging = messaging;
    }

    public void attachGroups(GroupService groups) {
        this.groups = groups;
    }

    public void attachCommandService(PluginCommandService commandService, PluginUserService users) {
        this.commandService = commandService;
        this.users = users;
    }

    /**
     * 宿主分发的消息事件入口。
     *
     * <p>与官方 Webhook 的 JSON 不同，这里拿到的是归一化后的 {@link PluginEvent}：
     * {@code type=message_receive} 表示私聊或群聊消息，{@code userId} 是 QQ 号/openid，
     * {@code channelId} 是群 ID（私聊为空）。回复走 {@code messaging().send(...)}，
     * 由宿主按 Milky / 官方协议降级。</p>
     */
    public void onEvent(PluginEvent event) {
        if (event == null) {
            return;
        }
        QqBotSettings bot = settings.botSettings();
        if (!bot.enabled()) {
            return;
        }
        String type = event.type() == null ? "" : event.type();
        if (!"message_receive".equals(type) && !"friend_add".equals(type)
                && !"group_request".equals(type) && !"group_member_decrease".equals(type)) {
            return;
        }
        if ("group_member_decrease".equals(type)) {
            handleMemberLeave(event);
            return;
        }
        if (messaging == null) {
            return;
        }
        // 「QQ 群 QQ 绑定」插件管的是系统账号绑定（绑定 + 六位码），
        // 本插件管的是 QQ ↔ 游戏角色。宿主已把消息路由给前者时不要再回一遍。
        if (event.command() != null && event.command().startsWith("qq-binding.")) {
            return;
        }
        if ("friend_add".equals(type)) {
            if (bot.replyToFriendAdd() && !bot.friendAddMessage().isBlank()) {
                send(event, new PluginMessageContent(PluginMessageContent.Type.TEXT,
                        bot.friendAddMessage(), null, referrer(event)));
            }
            return;
        }
        if ("group_request".equals(type)) {
            // GROUP_ADD_ROBOT 表示机器人刚被加入群；GROUP_JOIN_REQUEST 表示普通成员申请入群。
            // 后者由 qqbot-automation 负责审核，不能把“机器人已加入本群”误发到群里。
            if (robotAddedToGroup(event) && bot.replyToGroupAdd() && !bot.groupAddMessage().isBlank()
                    && event.channelId() != null) {
                send(event, new PluginMessageContent(PluginMessageContent.Type.TEXT,
                        bot.groupAddMessage(), null, referrer(event)));
            }
            return;
        }
        String content = event.content() == null ? "" : event.content().trim();
        if (content.isBlank()) {
            return;
        }
        String scope = event.channelId() == null || event.channelId().isBlank() ? "private" : "group";
        // 宿主内部事件仍以 milky 作为兼容性平台标识；连接真实协议由
        // dispatcher 放入 referrer，官方连接必须按 OpenID 走审核查询。
        String platform = referrerProtocol(event);
        String channel = event.channelId() == null ? "" : event.channelId();
        PluginMessagingIdentity identity = identityOf(event);
        if ("group".equals(scope) && groups != null) {
            groups.syncCard(event.connectionId(), channel, event.userId(), currentCard(event), memberData(event),
                    bindings, false);
        }
        CommandMatcher.Match ownMatch = new CommandMatcher(settings.commandSettings()).match(content, scope);
        if (ownMatch == null && publishGenericCommand(event, content, identity)) {
            return;
        }
        Result reply = ownMatch == null ? Result.empty()
                : message(content, event.userId(), scope, platform, channel, identity);
        if (reply == null || (reply.text().isBlank() && !reply.hasImage())) {
            return;
        }
        if (reply.hasImage()) {
            send(event, new PluginMessageContent(PluginMessageContent.Type.IMAGE,
                    "base64://" + Base64.getEncoder().encodeToString(reply.image()), null, referrer(event)));
        } else {
            send(event, new PluginMessageContent(PluginMessageContent.Type.TEXT, reply.text(), null,
                    referrer(event)));
        }
    }

    /** 出站请求原样回传事件会话：官方被动回复依赖 message_scene / msg_id。 */
    private void send(PluginEvent event, PluginMessageContent content) {
        String channel = event.channelId() == null ? "" : event.channelId();
        String platform = event.platform() == null ? "qq" : event.platform();
        messaging.send(new PluginMessageRequest(event.connectionId(), platform, event.userId(), channel, content))
                .exceptionally(error -> {
                    notices.publish("warning", "QQ机器人 回复失败",
                            error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
                    return null;
                });
    }

    /**
     * 官方被动回复必须带 message_scene 与消息 ID；Milky 忽略多余字段。
     * 事件里的 {@code nativeData} 是宿主保留的原始载荷。
     */
    private static Map<String, Object> referrer(PluginEvent event) {
        Map<String, Object> referrer = new LinkedHashMap<>();
        if (event.nativeData() instanceof Map<?, ?> data) {
            copy(referrer, data, "message_scene");
            copy(referrer, data, "msg_id");
            copy(referrer, data, "message_id");
            copy(referrer, data, "event_id");
            copy(referrer, data, "interaction_id");
        }
        if (event.messageId() != null && !event.messageId().isBlank()) {
            referrer.putIfAbsent("message_id", event.messageId());
        }
        return referrer;
    }

    private static String referrerProtocol(PluginEvent event) {
        if (event != null && event.referrer() != null) {
            Object protocol = event.referrer().get("protocol");
            if (protocol != null && !String.valueOf(protocol).isBlank()) {
                return String.valueOf(protocol);
            }
        }
        return event == null || event.platform() == null || event.platform().isBlank()
                ? "official" : event.platform();
    }

    private static boolean robotAddedToGroup(PluginEvent event) {
        if (event == null) {
            return false;
        }
        String nativeType = event.nativeType();
        if (nativeType == null && event.nativeData() instanceof Map<?, ?> data) {
            Object value = data.get("native_type");
            nativeType = value == null ? null : String.valueOf(value);
        }
        return "GROUP_ADD_ROBOT".equalsIgnoreCase(nativeType)
                || "group_add_robot".equalsIgnoreCase(nativeType);
    }

    private static void copy(Map<String, Object> target, Map<?, ?> source, String key) {
        Object value = source.get(key);
        if (value != null && !String.valueOf(value).isBlank()) {
            target.putIfAbsent(key, String.valueOf(value));
        }
    }

    /**
     * 把其它插件声明的 @PluginCommand 接到官方 QQ 消息入口。
     * 只发布已注册命令，命令处理器自己的回复仍统一走宿主消息端口。
     */
    private boolean publishGenericCommand(PluginEvent event, String content, PluginMessagingIdentity identity) {
        if (commandService == null || content == null || content.isBlank()) {
            return false;
        }
        String value = content.trim();
        CommandSettings commandSettings = settings.commandSettings();
        String prefix = commandSettings.prefixes().stream()
                .filter(value::startsWith).findFirst().orElse("");
        if (!prefix.isBlank()) {
            value = value.substring(prefix.length()).trim();
        } else if (commandSettings.prefixRequired()) {
            return false;
        }
        if (value.isBlank()) {
            return false;
        }
        String[] parts = value.split("\\s+");
        String command = parts[0];
        java.util.List<String> arguments = parts.length <= 1 ? java.util.List.of()
                : java.util.Arrays.stream(parts).skip(1).toList();
        Long userId = null;
        if (users != null && identity != null) {
            userId = users.findByMessagingIdentity(identity).map(profile -> profile.id()).orElse(null);
        }
        commandService.publish(event, command, arguments, userId, null);
        return true;
    }

    /**
     * 处理一条 Webhook 事件，返回要回给 QQ 的纯文本（无回复时返回空串）。
     *
     * @param event 官方事件体，{@code t} 字段是事件类型
     */
    public Map<String, Object> handle(JsonNode event) {
        String type = event.path("t").asText(event.path("event").asText(""));
        String openid = openid(event);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("event", type);
        result.put("openid", openid);
        QqBotSettings bot = settings.botSettings();
        if (!bot.enabled()) {
            result.put("ignored", "机器人未启用");
            result.put("reply", "");
            return result;
        }
        BindSettings bind = settings.bindSettings();
        switch (type) {
            case FRIEND_ADD -> {
                // 好友添加：告知绑定方式，并把当前可用前缀带出去，避免用户记错关键字。
                String prefix = bind.commonPrefix().isBlank() ? "" : "（前缀 " + bind.commonPrefix() + "）";
                String keyword = settings.commandSettings().command(CommandSettings.ACTION_BIND).label();
                String reply = bot.replyToFriendAdd() ? bot.friendAddMessage() : "";
                if (reply.isBlank()) {
                    reply = "欢迎添加机器人" + prefix + "。发送「" + keyword
                            + " 游戏ID」获取验证码，进服后在聊天栏输入即可完成绑定。";
                }
                result.put("reply", reply);
                result.put("scene", event.path("scene").asInt(0));
                if (!reply.isBlank() && !openid.isBlank()) {
                    result.put("delivered", deliverFriendEvent(bot, openid, reply, event.path("id").asText(""))
                            .toView());
                }
                notices.publish("info", "QQ 好友接入", "openid " + mask(openid) + " 添加了机器人。");
            }
            case GROUP_ADD_ROBOT -> {
                String reply = bot.replyToGroupAdd() ? bot.groupAddMessage() : "";
                String groupOpenid = event.path("group_openid").asText("");
                result.put("reply", reply);
                if (!reply.isBlank() && !groupOpenid.isBlank()) {
                    result.put("delivered", deliverGroupEvent(bot, groupOpenid, reply, event.path("id").asText(""))
                            .toView());
                }
                notices.publish("info", "QQ 群接入", "群 " + groupOpenid + " 添加了机器人。");
            }
            case C2C_MESSAGE_CREATE -> {
                Result reply = message(event, bind, openid, "private", "official", "");
                publish(result, reply, bot, openid, "", event.path("id").asText(""));
            }
            case GROUP_AT_MESSAGE_CREATE -> {
                String groupOpenid = event.path("group_openid").asText("");
                Result reply = message(event, bind, openid, "group", "official", groupOpenid);
                publish(result, reply, bot, openid, groupOpenid, event.path("id").asText(""));
            }
            default -> result.put("reply", "");
        }
        return result;
    }

    private void publish(Map<String, Object> result, Result reply, QqBotSettings bot,
                         String openid, String groupOpenid, String msgId) {
        result.put("reply", reply.text());
        boolean group = groupOpenid != null && !groupOpenid.isBlank();
        if (reply.hasImage()) {
            QqApiClient.Result delivered = group
                    ? deliverGroupImage(bot, groupOpenid, reply.image(), msgId)
                    : deliverImage(bot, openid, reply.image(), msgId);
            result.put("delivered", delivered.toView());
        } else if (!reply.text().isBlank()) {
            QqApiClient.Result delivered = group
                    ? deliverGroup(bot, groupOpenid, reply.text(), msgId)
                    : deliver(bot, openid, reply.text(), msgId);
            result.put("delivered", delivered.toView());
        }
    }

    /** 被动回复：带 msg_id 走官方接口；失败只体现在返回值里，不影响事件本身。 */
    private QqApiClient.Result deliver(QqBotSettings bot, String openid, String reply, String msgId) {
        String secret = secrets.read(bot.appSecretRef()).orElse("");
        if (secret.isBlank()) {
            return new QqApiClient.Result(false, 0, "", "未配置 AppSecret", "");
        }
        return api.sendC2c(bot.appId(), secret, bot.apiBase(), openid, reply, msgId);
    }

    private QqApiClient.Result deliverGroup(QqBotSettings bot, String groupOpenid, String reply, String msgId) {
        String secret = secrets.read(bot.appSecretRef()).orElse("");
        if (secret.isBlank()) {
            return new QqApiClient.Result(false, 0, "", "未配置 AppSecret", "");
        }
        return api.sendGroup(bot.appId(), secret, bot.apiBase(), groupOpenid, reply, msgId);
    }

    private QqApiClient.Result deliverFriendEvent(QqBotSettings bot, String openid, String reply, String eventId) {
        String secret = secrets.read(bot.appSecretRef()).orElse("");
        if (secret.isBlank()) {
            return new QqApiClient.Result(false, 0, "", "未配置 AppSecret", "");
        }
        return api.sendC2cEvent(bot.appId(), secret, bot.apiBase(), openid, reply, eventId);
    }

    private QqApiClient.Result deliverGroupEvent(QqBotSettings bot, String groupOpenid,
                                                 String reply, String eventId) {
        String secret = secrets.read(bot.appSecretRef()).orElse("");
        if (secret.isBlank()) {
            return new QqApiClient.Result(false, 0, "", "未配置 AppSecret", "");
        }
        return api.sendGroupEvent(bot.appId(), secret, bot.apiBase(), groupOpenid, reply, eventId);
    }

    private QqApiClient.Result deliverImage(QqBotSettings bot, String openid, byte[] png, String msgId) {
        String secret = secrets.read(bot.appSecretRef()).orElse("");
        if (secret.isBlank()) {
            return new QqApiClient.Result(false, 0, "", "未配置 AppSecret", "");
        }
        return api.sendC2cImage(bot.appId(), secret, bot.apiBase(), openid, png, msgId);
    }

    private QqApiClient.Result deliverGroupImage(QqBotSettings bot, String groupOpenid,
                                                 byte[] png, String msgId) {
        String secret = secrets.read(bot.appSecretRef()).orElse("");
        if (secret.isBlank()) {
            return new QqApiClient.Result(false, 0, "", "未配置 AppSecret", "");
        }
        return api.sendGroupImage(bot.appId(), secret, bot.apiBase(), groupOpenid, png, msgId);
    }

    private String message(JsonNode event, BindSettings bind, String openid) {
        return message(event, bind, openid, "private").text();
    }

    /**
     * 统一指令入口：绑定走绑定服务，容器类指令向服务端要图，其余按触发词回提示。
     * 没有命中任何指令时返回空，绝不刷屏。
     */
    private Result message(JsonNode event, BindSettings bind, String openid, String scope) {
        return message(event, bind, openid, scope, "official", "");
    }

    private Result message(JsonNode event, BindSettings bind, String openid, String scope,
                           String platform, String groupId) {
        String content = event.path("content").asText("").trim();
        if (content.isBlank()) {
            return Result.empty();
        }
        return message(content, openid, scope, platform, groupId);
    }

    /** 归一化消息入口：宿主事件和官方 Webhook 都走这里，指令触发词完全一致。 */
    private Result message(String content, String openid, String scope) {
        return message(content, openid, scope, "official", "");
    }

    private Result message(String content, String openid, String scope,
                           String platform, String groupId) {
        return message(content, openid, scope, platform, groupId, null);
    }

    private Result message(String content, String openid, String scope,
                           String platform, String groupId, PluginMessagingIdentity identity) {
        CommandSettings commands = settings.commandSettings();
        CommandMatcher.Match match = new CommandMatcher(commands).match(content, scope);
        if (match == null) {
            return Result.empty();
        }
        if (!CommandSettings.ACTION_REGISTRATION.equals(match.action())
                && !CommandSettings.ACTION_BIND.equals(match.action())
                && "group".equals(scope) && registration != null) {
            Optional<String> gate = identity == null
                    ? registration.groupGate(platform, openid, groupId)
                    : registration.groupGate(identity);
            if (gate.isPresent()) {
                return Result.text(gate.get());
            }
        }
        return switch (match.action()) {
            case CommandSettings.ACTION_BIND -> bind(openid, groupId, identity == null ? "" : identity.connectionId(),
                    match.args(), commands);
            case CommandSettings.ACTION_UNBIND -> unbind(settings.bindSettings());
            case CommandSettings.ACTION_INVENTORY, CommandSettings.ACTION_ENDER_CHEST,
                    CommandSettings.ACTION_SHULKER -> container(match, openid);
            case CommandSettings.ACTION_REGISTRATION -> registration(identity == null
                    ? null : identity);
            default -> Result.text(match.command().replyHint());
        };
    }

    private Result registration(PluginMessagingIdentity identity) {
        if (identity == null) {
            return registration("official", "", "");
        }
        if (registration == null) {
            return Result.text("注册审核联动尚未接入，请前往官网查看注册审核页面。");
        }
        RegistrationReviewIntegration.Status state = registration.status(identity);
        return registrationResult(state);
    }

    private Result registration(String platform, String identity, String groupId) {
        if (registration == null) {
            return Result.text("注册审核联动尚未接入，请前往官网查看注册审核页面。");
        }
        RegistrationReviewIntegration.Status state = registration.status(platform, identity, groupId);
        return registrationResult(state);
    }

    private Result registrationResult(RegistrationReviewIntegration.Status state) {
        return Result.text(switch (state.state()) {
            case "disabled" -> "注册审核联动已关闭，请联系管理员。";
            case "unavailable" -> "注册审核插件当前不可用，请联系管理员。";
            case "unbound" -> "当前消息身份尚未绑定站点账号，请先完成消息身份绑定。";
            case "not_started" -> "尚未发起注册审核，请打开：" + state.registrationUrl();
            case "passed" -> "问卷已通过，等待管理员确认。";
            case "approved" -> "注册审核已通过，可以继续使用群内功能。";
            default -> "当前注册审核状态：" + state.state() + "。审核入口：" + state.registrationUrl();
        });
    }

    private static PluginMessagingIdentity identityOf(PluginEvent event) {
        if (event == null || event.userId() == null || event.userId().isBlank()) {
            return null;
        }
        Map<String, Object> referrer = event.referrer();
        String protocol = text(referrer.get("protocol"), event.platform());
        if (protocol == null || protocol.isBlank()) {
            protocol = "official";
        }
        String groupId = text(referrer.get("groupOpenid"), event.channelId());
        String type = text(referrer.get("identityType"), null);
        if (type == null || type.isBlank()) {
            type = "official".equalsIgnoreCase(protocol)
                    ? (groupId == null || groupId.isBlank() ? "user_openid" : "member_openid") : "qq";
        }
        return new PluginMessagingIdentity(protocol, type, event.userId(), text(referrer.get("appId"), null),
                "member_openid".equalsIgnoreCase(type) ? groupId : null, event.connectionId());
    }

    private void handleMemberLeave(PluginEvent event) {
        BindSettings current = settings.bindSettings();
        if (!current.autoUnbindOnGroupLeave() || bindings == null || event == null
                || event.userId() == null || event.userId().isBlank()) {
            return;
        }
        var removed = bindings.unbindAll(event.userId());
        if (!removed.isEmpty()) {
            notices.publish("info", "QQ 退群解绑",
                    "群 " + text(event.channelId(), "未知") + " 的成员 " + mask(event.userId())
                            + " 退群，已自动解绑 " + removed.size() + " 个游戏账号。");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> memberData(PluginEvent event) {
        if (event == null || !(event.nativeData() instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        Object nested = result.get("native");
        if (nested instanceof Map<?, ?> values) {
            values.forEach((key, value) -> result.putIfAbsent(String.valueOf(key), value));
        }
        Object sender = result.get("sender");
        if (sender instanceof Map<?, ?> values) {
            values.forEach((key, value) -> result.putIfAbsent(String.valueOf(key), value));
        }
        Object member = result.get("member");
        if (member instanceof Map<?, ?> values) {
            values.forEach((key, value) -> result.putIfAbsent(String.valueOf(key), value));
        }
        return result;
    }

    private static String currentCard(PluginEvent event) {
        Map<String, Object> data = memberData(event);
        for (String key : List.of("card", "member_card", "nickname", "member_nickname", "display_name")) {
            Object value = data.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return "";
    }

    private static String text(Object value, String fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value).trim();
    }

    private Result bind(String openid, String groupId, String connectionId, String args, CommandSettings commands) {
        String keyword = commands.command(CommandSettings.ACTION_BIND).label();
        String player = args;
        // 六位纯数字是系统账号绑定码，交给 qq-binding 插件处理，避免两条回复。
        if (player.matches("\\d{6}")) {
            return Result.empty();
        }
        if (player.isBlank()) {
            return Result.text("请发送「" + keyword + " 游戏ID」，例如「" + keyword + " Steve」。");
        }
        BindSettings bind = settings.bindSettings();
        if (bind.forbidChinese() && player.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp)
                == Character.UnicodeScript.HAN)) {
            return Result.text("游戏 ID 不能包含中文。");
        }
        BindCodeService.PendingCode code = codes.issue(player, "", openid, "", groupId, connectionId, bind);
        return Result.text("验证码 " + code.code() + "，请在 " + bind.codeTtlMinutes()
                + " 分钟内进入服务器，在聊天栏输入「" + code.code() + "」完成绑定。"
                + (bind.commonPrefix().isBlank() ? "" : "（群内前缀「" + bind.commonPrefix() + "」）"));
    }

    /**
     * 解绑只在机器人侧解除「QQ ↔ 玩家」记录；服务端上线时再同步。
     * 这里不直接踢人，避免误伤正在游戏的玩家。
     */
    private Result unbind(BindSettings bind) {
        if (!bind.allowUnbind()) {
            return Result.text("管理员已关闭解绑功能。");
        }
        return Result.text("解绑请求已收到。若需要立即生效，请进入服务器后在聊天栏执行解绑，"
                + "或联系管理员在后台「绑定查询」中删除记录。");
    }

    /**
     * 容器类指令：把玩家名（指令参数）翻译成在线玩家，向服务端要图。
     *
     * <p>QQ 官方接口不能主动拉取用户资料，所以这里要求带上游戏 ID；管理员
     * 代查也可以用同样的写法。服务端返回 Base64 PNG，直接走富媒体接口发图。</p>
     */
    private Result container(CommandMatcher.Match match, String openid) {
        String player = resolvePlayer(match.args(), openid);
        if (player.isBlank()) {
            return Result.text("还没有找到你绑定的游戏账号。先发送「绑定 游戏ID」完成绑定，"
                    + "或直接发送「" + match.label() + " 玩家ID」查询指定玩家。");
        }
        if (bridge == null) {
            return Result.text("服务端桥接未就绪，暂时无法获取背包数据。");
        }
        Optional<BridgeHub.PendingRender> pending = bridge.requestContainer(player, match.action(), match.index());
        if (pending.isEmpty()) {
            return Result.text("当前没有已连接的服务端，无法获取 " + player + " 的背包。");
        }
        BridgeHub.PendingRender render = pending.get();
        try {
            JsonNode result = render.future().get(RENDER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!result.path("ok").asBoolean(false)) {
                return Result.text(result.path("message").asText("渲染失败，请稍后再试。"));
            }
            String image = result.path("image").asText("");
            if (image.startsWith("base64://")) {
                return Result.image(Base64.getDecoder().decode(image.substring(9)),
                        result.path("title").asText(""));
            }
            return Result.text("渲染完成，但图片数据为空。");
        } catch (java.util.concurrent.TimeoutException exception) {
            return Result.text(player + " 的背包渲染超时（服务端可能繁忙），请稍后再试。");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Result.text("渲染被中断。");
        } catch (Exception exception) {
            return Result.text("渲染失败：" + exception.getMessage());
        }
    }

    /**
     * 指令参数优先；不写玩家名时按 QQ openid 反查已绑定的游戏账号。
     *
     * <p>没有绑定服务（单测）或未绑定时返回空串，由调用方提示先绑定。
     * 绑定了多个账号时取第一个，管理员代查仍可用参数指定。</p>
     */
    private String resolvePlayer(String args, String openid) {
        String explicit = args == null ? "" : args.trim().split("\\s+")[0];
        if (!explicit.isBlank()) {
            return explicit;
        }
        if (bindings == null || openid == null || openid.isBlank()) {
            return "";
        }
        return bindings.bySocial(openid, 1, 20).stream()
                .map(record -> record.playerName())
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse("");
    }

    private static String openid(JsonNode event) {
        String direct = event.path("openid").asText("");
        if (!direct.isBlank()) {
            return direct;
        }
        String author = event.path("author").path("union_openid").asText("");
        if (!author.isBlank()) {
            return author;
        }
        String member = event.path("author").path("member_openid").asText("");
        if (!member.isBlank()) {
            return member;
        }
        return event.path("author").path("user_openid").asText("");
    }

    /** openid 是用户隐私标识，日志/通知里只留前后各 4 位。 */
    static String mask(String value) {
        if (value == null || value.length() <= 8) {
            return value == null ? "" : value;
        }
        return value.substring(0, 4) + "…" + value.substring(value.length() - 4);
    }

    /** 供上层读取机器人密钥（AppSecret）；缺省实现返回空，便于单测。 */
    @FunctionalInterface
    public interface SecretReader {
        Optional<String> read(String name);
    }

    /**
     * 一次回复：要么是文本，要么是图片。图片优先，文本作为图片消息的说明
     * （官方图片消息没有 caption 时留空）。
     */
    public record Result(String text, byte[] image) {
        public static Result empty() {
            return new Result("", null);
        }

        public static Result text(String value) {
            return new Result(value == null ? "" : value, null);
        }

        public static Result image(byte[] content, String caption) {
            return new Result(caption == null ? "" : caption, content);
        }

        public boolean hasImage() {
            return image != null && image.length > 0;
        }
    }
}
