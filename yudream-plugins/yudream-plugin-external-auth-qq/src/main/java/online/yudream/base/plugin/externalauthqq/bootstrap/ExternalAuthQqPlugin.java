package online.yudream.base.plugin.externalauthqq.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.plugin.externalauthqq.application.QqExternalLoginProvider;
import online.yudream.base.plugin.externalauthqq.application.SettingsService;
import online.yudream.base.plugin.externalauthqq.interfaces.SettingsController;
import online.yudream.base.plugin.spi.annotation.PluginFrontend;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginPermissions;
import online.yudream.base.plugin.spi.annotation.PluginRoute;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginProvider;

/** QQ Graph OAuth2 外部登录插件入口。 */
@PluginSpec(code = ExternalAuthQqPlugin.CODE, name = "QQ 登录", version = ExternalAuthQqPlugin.VERSION,
        description = "QQ Graph OAuth2 外部登录提供方，宿主统一处理登录态和外部账号绑定")
@PluginPermissions({
        @PluginPermission(code = ExternalAuthQqPlugin.MANAGE_PERMISSION, name = "管理 QQ 登录", module = "QQ 登录",
                description = "配置 QQ AppID、AppKey、回调地址和登录入口")
})
@PluginFrontend(moduleName = "external-auth-qq", menuTitle = "QQ 登录", menuIcon = "i-ri:qq-line", menuSort = 77,
        styles = {"style.css"}, routes = {
        @PluginRoute(path = "/platform/plugins/external-auth-qq/settings", name = "platform-plugin-external-auth-qq-settings",
                title = "QQ 登录设置", icon = "i-ri:settings-3-line", component = "external-auth-qq/Settings",
                permission = ExternalAuthQqPlugin.MANAGE_PERMISSION, sort = 10)
})
public final class ExternalAuthQqPlugin implements YuDreamPlugin {
    public static final String CODE = "external-auth-qq";
    public static final String VERSION = "0.2.0";
    public static final String MANAGE_PERMISSION = "plugin:external-auth-qq:manage";

    @Override
    public void onEnable(PluginContext context) {
        ObjectMapper mapper = new ObjectMapper();
        SettingsService settings = new SettingsService(context, mapper);
        QqExternalLoginProvider provider = new QqExternalLoginProvider(settings, mapper);
        context.onDispose(provider);
        context.registerExtension(PluginExternalLoginProvider.class, provider);
        context.exposeService(online.yudream.base.plugin.externalauthqq.api.QqOAuthIdentityApi.class, (appId, openId) -> {
            if (!settings.enabled() || appId == null || !settings.appId().equals(appId)
                    || openId == null || openId.isBlank()) return java.util.Optional.empty();
            return context.framework().users().findByExternalIdentity(QqExternalLoginProvider.PROVIDER_CODE,
                    QqExternalLoginProvider.TYPE, openId);
        });
        context.registerHttpController(new SettingsController(settings, mapper));
    }
}
