package online.yudream.base.plugin.registrationreview.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.plugin.registrationreview.application.ReviewService;
import online.yudream.base.plugin.registrationreview.application.SettingsService;
import online.yudream.base.plugin.registrationreview.application.UserLabels;
import online.yudream.base.plugin.registrationreview.infrastructure.JsonSupport;
import online.yudream.base.plugin.registrationreview.infrastructure.ReviewApplicationRepository;
import online.yudream.base.plugin.registrationreview.interfaces.ReviewAdminController;
import online.yudream.base.plugin.registrationreview.interfaces.ReviewMeController;
import online.yudream.base.plugin.spi.annotation.PluginFrontend;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginPermissions;
import online.yudream.base.plugin.spi.annotation.PluginRoute;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;

/** 注册审核插件入口。 */
@PluginSpec(code = RegistrationReviewPlugin.CODE, name = "注册审核", version = RegistrationReviewPlugin.VERSION,
        description = "独立注册准入问卷与人工审核流程，复用 questionbank 抽题和判分，预留 B站三连插件接入")
@PluginPermissions({
        @PluginPermission(code = RegistrationReviewPlugin.USE_PERMISSION, name = "申请注册审核", module = "注册审核",
                description = "查看审核要求、发起问卷、查看自己的申请并刷新结果"),
        @PluginPermission(code = RegistrationReviewPlugin.MANAGE_PERMISSION, name = "管理注册审核", module = "注册审核",
                description = "查看申请、人工通过/拒绝、配置审核模式和题库范围")
})
@PluginFrontend(moduleName = "registration-review", menuTitle = "注册审核", menuIcon = "i-ri:profile-approval-line", menuSort = 78,
        styles = {"style.css"}, routes = {
        @PluginRoute(path = "/platform/plugins/registration-review", name = "platform-plugin-registration-review-apply",
                title = "注册审核", icon = "i-ri:profile-approval-line", component = "registration-review/Apply",
                permission = RegistrationReviewPlugin.USE_PERMISSION, sort = 10),
        @PluginRoute(path = "/platform/plugins/registration-review/admin", name = "platform-plugin-registration-review-admin",
                title = "申请管理", icon = "i-ri:inbox-archive-line", component = "registration-review/Admin",
                permission = RegistrationReviewPlugin.MANAGE_PERMISSION, sort = 90),
        @PluginRoute(path = "/platform/plugins/registration-review/admin/settings", name = "platform-plugin-registration-review-settings",
                title = "审核设置", icon = "i-ri:settings-3-line", component = "registration-review/Settings",
                permission = RegistrationReviewPlugin.MANAGE_PERMISSION, sort = 91)
})
public final class RegistrationReviewPlugin implements YuDreamPlugin {
    public static final String CODE = "registration-review";
    public static final String VERSION = "0.4.0";
    public static final String USE_PERMISSION = "plugin:registration-review:use";
    public static final String MANAGE_PERMISSION = "plugin:registration-review:manage";

    @Override
    public void onEnable(PluginContext context) {
        JsonSupport json = new JsonSupport(new ObjectMapper());
        SettingsService settings = new SettingsService(context.documents(), json);
        ReviewApplicationRepository repository = new ReviewApplicationRepository(context.documents());
        ReviewService reviews = new ReviewService(context, repository, settings);
        UserLabels labels = new UserLabels(context.framework());

        context.exposeService(online.yudream.base.plugin.registrationreview.api.RegistrationReviewApi.class, reviews);
        context.registerAiTool(new online.yudream.base.plugin.registrationreview.application.ReviewStatusAiTool(reviews));
        context.registerHttpController(new ReviewMeController(reviews, settings, labels, json));
        context.registerHttpController(new ReviewAdminController(reviews, settings, json, context));
    }
}
