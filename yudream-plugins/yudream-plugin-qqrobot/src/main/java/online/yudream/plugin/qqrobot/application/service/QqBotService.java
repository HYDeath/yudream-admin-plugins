package online.yudream.plugin.qqrobot.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import online.yudream.plugin.qqrobot.domain.BindSettings;
import online.yudream.plugin.qqrobot.domain.QqBotSettings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * QQ 官方机器人事件处理。
 *
 * <p>官方 Webhook 推送的事件与 QqRobot 的协议不同：好友添加是
 * {@code FRIEND_ADD}（intent {@code GROUP_AND_C2C_EVENT}，1<<25），字段为
 * {@code openid / timestamp / scene / scene_param / author.union_openid}；
 * 群添加是 {@code GROUP_ADD_ROBOT}。这里只做与绑定相关的响应：私聊里发来的
 * 绑定码直接回写绑定结果，添加好友时返回欢迎语与绑定指引。</p>
 *
 * <p>真正的消息收发由外部 QQ 机器人网关完成（与宿主已有 QQ 插件共用网关），
 * 本服务只负责“事件 → 后台动作 → 回包文本/图片”的纯函数部分，便于单测。</p>
 */
public class QqBotService {
    private static final String FRIEND_ADD = "FRIEND_ADD";
    private static final String C2C_MESSAGE_CREATE = "C2C_MESSAGE_CREATE";
    private static final String GROUP_ADD_ROBOT = "GROUP_ADD_ROBOT";

    private final SettingsService settings;
    private final BindCodeService codes;
    private final NoticeService notices;
    private final TemplateService templates;
    private final SecretReader secrets;
    private final QqApiClient api;

    public QqBotService(SettingsService settings, BindCodeService codes, NoticeService notices,
                        TemplateService templates) {
        this(settings, codes, notices, templates, name -> Optional.empty(), new QqApiClient());
    }

    public QqBotService(SettingsService settings, BindCodeService codes, NoticeService notices,
                        TemplateService templates, SecretReader secrets, QqApiClient api) {
        this.settings = settings;
        this.codes = codes;
        this.notices = notices;
        this.templates = templates;
        this.secrets = secrets == null ? name -> Optional.empty() : secrets;
        this.api = api == null ? new QqApiClient() : api;
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
                String reply = bot.replyToFriendAdd() ? bot.friendAddMessage() : "";
                if (reply.isBlank()) {
                    reply = "欢迎添加机器人" + prefix + "。发送「" + bind.bindKeyword()
                            + " 游戏ID」获取验证码，进服后在聊天栏输入即可完成绑定。";
                }
                result.put("reply", reply);
                result.put("scene", event.path("scene").asInt(0));
                notices.publish("info", "QQ 好友接入", "openid " + mask(openid) + " 添加了机器人。");
            }
            case GROUP_ADD_ROBOT -> {
                result.put("reply", bot.replyToGroupAdd() ? bot.groupAddMessage() : "");
                notices.publish("info", "QQ 群接入", "群 " + event.path("group_openid").asText("") + " 添加了机器人。");
            }
            case C2C_MESSAGE_CREATE -> {
                String reply = message(event, bind, openid);
                result.put("reply", reply);
                if (!reply.isBlank()) {
                    result.put("delivered", deliver(bot, openid, reply, event.path("id").asText("")).toView());
                }
            }
            default -> result.put("reply", "");
        }
        return result;
    }

    /** 被动回复：带 msg_id 走官方接口；失败只体现在返回值里，不影响事件本身。 */
    private QqApiClient.Result deliver(QqBotSettings bot, String openid, String reply, String msgId) {
        String secret = secrets.read(bot.appSecretRef()).orElse("");
        if (secret.isBlank()) {
            return new QqApiClient.Result(false, 0, "", "未配置 AppSecret", "");
        }
        return api.sendC2c(bot.appId(), secret, bot.apiBase(), openid, reply, msgId);
    }

    private String message(JsonNode event, BindSettings bind, String openid) {
        String content = event.path("content").asText("").trim();
        if (content.isBlank()) {
            return "";
        }
        String keyword = bind.bindKeyword();
        if (content.equals(keyword) || content.startsWith(keyword + " ") || content.startsWith(keyword + "　")) {
            String player = content.substring(keyword.length()).trim();
            if (player.isBlank()) {
                return "请发送「" + keyword + " 游戏ID」，例如「" + keyword + " Steve」。";
            }
            if (bind.forbidChinese() && player.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp)
                    == Character.UnicodeScript.HAN)) {
                return "游戏 ID 不能包含中文。";
            }
            BindCodeService.PendingCode code = codes.issue(player, "", openid, "", bind);
            return "验证码 " + code.code() + "，请在 " + bind.codeTtlMinutes()
                    + " 分钟内进入服务器，在聊天栏输入「" + code.code() + "」完成绑定。";
        }
        if (content.startsWith("查询") || content.startsWith("card")) {
            String player = content.replaceFirst("^(查询|card)\\s*", "").trim();
            if (!player.isBlank() && templates != null) {
                return "已生成 " + player + " 的信息卡。";
            }
        }
        return "";
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
}
