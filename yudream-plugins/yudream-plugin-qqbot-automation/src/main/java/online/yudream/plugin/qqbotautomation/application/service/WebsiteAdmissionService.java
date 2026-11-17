package online.yudream.plugin.qqbotautomation.application.service;

import java.util.Optional;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.system.messaging.PluginEvent;
import online.yudream.base.plugin.spi.system.user.PluginMessagingIdentity;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;

/** 可选 provider 每次动态解析，禁用或重载后不保留旧服务引用。 */
public final class WebsiteAdmissionService implements java.util.function.Predicate<PluginEvent> {
    private final PluginContext context;

    public WebsiteAdmissionService(PluginContext context) { this.context = context; }

    @Override
    public boolean test(PluginEvent event) {
        if (!context.dependencyAvailable("registration-review")) return false;
        try {
            var referrer = event.referrer();
            String protocol = String.valueOf(referrer.getOrDefault("protocol", "milky"));
            String appId = String.valueOf(referrer.getOrDefault("appId", ""));
            var identity = new PluginMessagingIdentity(protocol, "official".equals(protocol) ? "member_openid" : "qq",
                    event.userId(), appId, event.channelId(), event.connectionId());
            Optional<PluginUserProfile> user = context.framework().users().findByMessagingIdentity(identity);
            if (user.isEmpty() && "official".equals(protocol) && !appId.isBlank()) {
                Object oauth = service("external-auth-qq", "online.yudream.base.plugin.externalauthqq.api.QqOAuthIdentityApi");
                if (oauth != null) {
                    Class<?> api = apiType("online.yudream.base.plugin.externalauthqq.api.QqOAuthIdentityApi");
                    Object found = api.getMethod("findUser", String.class, String.class).invoke(oauth, appId, event.userId());
                    if (found instanceof Optional<?> optional && optional.orElse(null) instanceof PluginUserProfile profile) user = Optional.of(profile);
                }
            }
            if (user.isEmpty() || user.get().id() == null) return false;
            String type = "online.yudream.base.plugin.registrationreview.api.RegistrationReviewApi";
            Object provider = service("registration-review", type);
            if (provider == null) return false;
            Object result = apiType(type).getMethod("admission", String.class).invoke(provider, String.valueOf(user.get().id()));
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) return false;
            Object admission = optional.get();
            return Boolean.TRUE.equals(admission.getClass().getMethod("approved").invoke(admission));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            System.getLogger("qqbot-automation").log(System.Logger.Level.WARNING, "官网准入查询不可用，保留人工审核", error);
            return false;
        }
    }

    private Object service(String code, String type) throws ClassNotFoundException {
        if (!context.dependencyAvailable(code)) return null;
        return context.service(code, apiType(type)).orElse(null);
    }

    private static Class<?> apiType(String type) throws ClassNotFoundException {
        return Class.forName(type, false, WebsiteAdmissionService.class.getClassLoader());
    }
}
