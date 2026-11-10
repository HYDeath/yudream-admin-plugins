package online.yudream.plugin.qqrobot.bootstrap;

import online.yudream.base.plugin.spi.annotation.PluginFrontend;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginPermissions;
import online.yudream.base.plugin.spi.annotation.PluginRoute;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.spi.ws.PluginWsHandshake;
import online.yudream.base.plugin.spi.ws.PluginWsHandler;
import online.yudream.base.plugin.spi.ws.PluginWsSession;
import online.yudream.base.plugin.spi.system.messaging.PluginInteractionFilter;
import online.yudream.plugin.qqrobot.application.service.BindCodeService;
import online.yudream.plugin.qqrobot.application.service.AvatarService;
import online.yudream.plugin.qqrobot.application.service.BindingService;
import online.yudream.plugin.qqrobot.application.service.GroupService;
import online.yudream.plugin.qqrobot.application.service.ItemCardRenderer;
import online.yudream.plugin.qqrobot.application.service.InventoryPreview;
import online.yudream.plugin.qqrobot.application.service.MinecraftServerDirectory;
import online.yudream.plugin.qqrobot.application.service.NoticeService;
import online.yudream.plugin.qqrobot.application.service.QqBotService;
import online.yudream.plugin.qqrobot.application.service.RegistrationReviewIntegration;
import online.yudream.plugin.qqrobot.application.service.RenderSupport;
import online.yudream.plugin.qqrobot.application.service.ResourceService;
import online.yudream.plugin.qqrobot.application.service.ServerListData;
import online.yudream.plugin.qqrobot.application.service.SettingsService;
import online.yudream.plugin.qqrobot.application.service.TemplateService;
import online.yudream.plugin.qqrobot.bridge.BridgeHub;
import online.yudream.plugin.qqrobot.interfaces.controller.QqRobotController;
import online.yudream.plugin.qqrobot.interfaces.controller.QqRobotHttpFacade;
import online.yudream.plugin.qqrobot.interfaces.controller.QqBotController;

import java.util.List;

@PluginSpec(code = QqRobotPlugin.CODE, name = "QQ机器人", version = QqRobotPlugin.VERSION,
        description = "QQ 机器人接入、绑定与群组管理、图片模板、资源市场、背包与潜影盒渲染，并为服务端插件提供桥接",
        icon = "i-ri:robot-2-line")
@PluginPermissions({
        @PluginPermission(code = QqRobotPlugin.MANAGE_PERMISSION, name = "管理 QQ机器人", module = "平台插件",
                description = "维护 QQ 机器人接入、绑定设置、群组策略、图片模板、资源市场与服务端桥接")
})
@PluginFrontend(moduleName = "qqrobot", menuTitle = "QQ机器人", menuIcon = "i-ri:robot-2-line", menuSort = 64,
        styles = {"style.css"}, routes = {
        @PluginRoute(path = "/platform/plugins/qqrobot/bind", name = "platform-plugin-qqrobot-bind",
                title = "绑定设置", icon = "i-ri:link", component = "qqrobot/BindSettings",
                permission = QqRobotPlugin.MANAGE_PERMISSION, sort = 10),
        @PluginRoute(path = "/platform/plugins/qqrobot/bindings", name = "platform-plugin-qqrobot-bindings",
                title = "绑定管理", icon = "i-ri:links-line", component = "qqrobot/Bindings",
                permission = QqRobotPlugin.MANAGE_PERMISSION, sort = 11),
        @PluginRoute(path = "/platform/plugins/qqrobot/bot", name = "platform-plugin-qqrobot-bot",
                title = "机器人设置", icon = "i-ri:robot-2-line", component = "qqrobot/BotSettings",
                permission = QqRobotPlugin.MANAGE_PERMISSION, sort = 12),
        @PluginRoute(path = "/platform/plugins/qqrobot/commands", name = "platform-plugin-qqrobot-commands",
                title = "指令设置", icon = "i-ri:terminal-box-line", component = "qqrobot/Commands",
                permission = QqRobotPlugin.MANAGE_PERMISSION, sort = 15),
        @PluginRoute(path = "/platform/plugins/qqrobot/groups", name = "platform-plugin-qqrobot-groups",
                title = "群组管理", icon = "i-ri:team-line", component = "qqrobot/Groups",
                permission = QqRobotPlugin.MANAGE_PERMISSION, sort = 20),
        @PluginRoute(path = "/platform/plugins/qqrobot/market", name = "platform-plugin-qqrobot-market",
                title = "资源市场", icon = "i-ri:store-3-line", component = "qqrobot/Market",
                permission = QqRobotPlugin.MANAGE_PERMISSION, sort = 30),
        @PluginRoute(path = "/platform/plugins/qqrobot/bridge", name = "platform-plugin-qqrobot-bridge",
                title = "服务端桥接", icon = "i-ri:server-line", component = "qqrobot/Bridge",
                permission = QqRobotPlugin.MANAGE_PERMISSION, sort = 40),
        @PluginRoute(path = "/platform/plugins/qqrobot/preview", name = "platform-plugin-qqrobot-preview",
                title = "渲染预览", icon = "i-ri:image-line", component = "qqrobot/Preview",
                permission = QqRobotPlugin.MANAGE_PERMISSION, sort = 50),
        @PluginRoute(path = "/platform/plugins/qqrobot/templates", name = "platform-plugin-qqrobot-templates",
                title = "图片模板", icon = "i-ri:image-2-line", component = "qqrobot/Templates",
                permission = QqRobotPlugin.MANAGE_PERMISSION, sort = 45)
})
public class QqRobotPlugin implements YuDreamPlugin {
    public static final String CODE = "qqrobot";
    public static final String VERSION = "0.6.2";
    public static final String MANAGE_PERMISSION = "plugin:qqrobot:manage";

    private BridgeHub bridge;

    @Override
    public void onEnable(PluginContext context) {
        SettingsService settings = new SettingsService(context.documents());
        BindCodeService codes = new BindCodeService();
        BindingService bindings = new BindingService(context.documents(), settings);
        GroupService groups = new GroupService(settings, context.framework().messaging(),
                context.framework().messagingRaw());
        // 出图统一走宿主「消息渲染」能力（独立 Headless Chromium），插件内不启动浏览器。
        RenderSupport render = new RenderSupport(context.framework().render());
        ResourceService resources = new ResourceService(context.documents(), context.files(), render);
        ItemCardRenderer itemRenderer = new ItemCardRenderer(render);
        AvatarService avatars = new AvatarService(true);
        InventoryPreview preview = new InventoryPreview(itemRenderer, avatars);
        NoticeService notices = new NoticeService(context.documents());
        bridge = new BridgeHub(settings, codes, bindings, groups, itemRenderer, context.secrets(),
                message -> {
                    // BridgeHub 用 "类别|正文" 传递语义，避免所有事件都顶着“令牌变更”的标题。
                    int split = message == null ? -1 : message.indexOf('|');
                    String category = split > 0 ? message.substring(0, split) : "通知";
                    String body = split > 0 ? message.substring(split + 1) : message;
                    String title = "令牌".equals(category) ? "QQ机器人 令牌变更" : "QQ机器人 桥接状态";
                    notices.publish("令牌".equals(category) ? "warning" : "info", title, body);
                });
        TemplateService templates = new TemplateService(context.documents(), context.files(), render,
                "/api/plugins/qqrobot/templates", avatars);
        templates.seedDefaults();
        templates.attachBridge(bridge);
        // 服务器在线列表的数据来自 minecraft-server 插件；桥接只补玩家名册。
        ServerListData serverData = new ServerListData(new MinecraftServerDirectory(context));
        serverData.attachBridge(bridge);
        templates.attachServers(serverData);
        // 模板列表在首次渲染前没有封面，启动时后台补一遍，避免市场里是空卡片。
        templates.warmCovers();

        // QQ 官方机器人事件回调（好友添加/群添加/消息）：生成绑定码或以图片回复。
        QqBotService.SecretReader secretReader = name -> {
            try {
                return context.secrets().get(name)
                        .map(bytes -> new String(bytes, java.nio.charset.StandardCharsets.UTF_8))
                        .filter(value -> !value.isBlank());
            } catch (RuntimeException exception) {
                return java.util.Optional.empty();
            }
        };
        RegistrationReviewIntegration registration = new RegistrationReviewIntegration(context, settings);
        QqBotService qqBot = new QqBotService(settings, codes, notices, templates, bindings, secretReader,
                new online.yudream.plugin.qqrobot.application.service.QqApiClient(), registration);
        qqBot.attachBridge(bridge);
        qqBot.attachGroups(groups);
        qqBot.attachMessaging(context.framework().messaging());
        // 自有快捷词未命中时，把消息交给宿主统一命令总线，复用其它插件的 @PluginCommand。
        qqBot.attachCommandService(context.framework().commands(), context.framework().users());
        // 入站统一由宿主消息端口分发：私聊/群聊指令、好友添加、入群通知。
        // 官方 Webhook 仍保留，兼容只在开放平台回调里落事件的老部署。
        context.onDispose(context.interactions().onMessage(
                new PluginInteractionFilter(java.util.Set.of("message_receive"), null, null, null),
                qqBot::onEvent));
        context.onDispose(context.interactions().onNative(
                new PluginInteractionFilter(java.util.Set.of("friend_add", "group_request", "group_member_decrease"), null, null, null),
                qqBot::onEvent));
        context.registerHttpController(new QqBotController(qqBot, settings, secretReader));

        context.registerHttpController(new QqRobotController(
                new QqRobotHttpFacade(settings, bindings, codes, groups, resources, bridge, preview, render, notices,
                        templates, context.secrets(), serverData, registration)));
        context.registerWebSocketHandler(BridgeHub.PATH, new BridgeHandler(bridge));
    }

    public BridgeHub bridge() {
        return bridge;
    }

    private static final class BridgeHandler implements PluginWsHandler {
        private final BridgeHub hub;

        private BridgeHandler(BridgeHub hub) {
            this.hub = hub;
        }

        @Override
        public void onOpen(PluginWsSession session, PluginWsHandshake handshake) {
            List<String> tokens = handshake.query().get("token");
            String token = tokens == null || tokens.isEmpty() ? "" : tokens.getFirst();
            if (!hub.authorized(token)) {
                session.close(4401, "桥接令牌无效");
                return;
            }
            String serverId = first(handshake.query(), "serverId");
            String serverName = first(handshake.query(), "serverName");
            hub.open(session, serverId, serverName);
        }

        @Override
        public void onText(PluginWsSession session, String payload) {
            hub.onText(session.id(), payload);
        }

        @Override
        public void onClose(PluginWsSession session, int statusCode, String reason) {
            hub.close(session.id());
        }

        @Override
        public void onError(PluginWsSession session, Throwable error) {
            hub.close(session.id());
        }

        private static String first(java.util.Map<String, List<String>> query, String key) {
            List<String> values = query.get(key);
            return values == null || values.isEmpty() ? "" : values.getFirst();
        }
    }
}
