package online.yudream.plugin.qqrobot.application.service;

import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.system.user.PluginMessagingIdentity;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;
import online.yudream.plugin.qqrobot.domain.QqIntegrationSettings;

import java.lang.reflect.Method;
import java.util.Optional;

/** 可选的注册审核联动，provider 缺失时 qqrobot 仍可独立运行。 */
public final class RegistrationReviewIntegration {
    private static final String PROVIDER = "registration-review";
    private static final String API_TYPE =
            "online.yudream.base.plugin.registrationreview.api.RegistrationReviewApi";

    private final PluginContext context;
    private final SettingsService settings;

    public RegistrationReviewIntegration(PluginContext context, SettingsService settings) {
        this.context = context;
        this.settings = settings;
    }

    public boolean providerAvailable() {
        if (context == null || !context.dependencyAvailable(PROVIDER)) {
            return false;
        }
        try {
            Class<?> api = Class.forName(API_TYPE, false, context.getClass().getClassLoader());
            return context.service(PROVIDER, api).isPresent();
        } catch (ClassNotFoundException | LinkageError | RuntimeException exception) {
            return false;
        }
    }

    public QqIntegrationSettings settings() {
        return settings.integrationSettings();
    }

    public Status status(String platform, String identity, String groupId) {
        return status(identity(platform, identity, groupId));
    }

    public Status status(PluginMessagingIdentity identity) {
        QqIntegrationSettings config = settings();
        if (!config.registrationReviewEnabled()) {
            return Status.disabled(config.registrationUrl());
        }
        if (!providerAvailable()) {
            return Status.unavailable(config.registrationUrl());
        }
        Optional<PluginUserProfile> profile = findUser(identity);
        if (profile.isEmpty() || profile.get().id() == null) {
            return Status.unbound(config.registrationUrl());
        }
        try {
            Class<?> apiType = Class.forName(API_TYPE, false, context.getClass().getClassLoader());
            Object api = context.service(PROVIDER, apiType).orElse(null);
            if (api == null) {
                return Status.unavailable(config.registrationUrl());
            }
            Method method = apiType.getMethod("admission", String.class);
            Object value = method.invoke(api, String.valueOf(profile.get().id()));
            if (!(value instanceof Optional<?> optional) || optional.isEmpty()) {
                return Status.notStarted(config.registrationUrl());
            }
            Object admission = optional.get();
            boolean approved = bool(admission, "approved");
            boolean passed = bool(admission, "passed");
            String state = text(admission, "status", "UNKNOWN");
            return new Status(approved ? "approved" : passed ? "passed" : state.toLowerCase(),
                    approved, passed, config.registrationUrl());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return Status.unavailable(config.registrationUrl());
        }
    }

    /** 群聊准入闸门：开启后只有审核通过的消息身份才会执行其它指令。 */
    public Optional<String> groupGate(String platform, String identity, String groupId) {
        return groupGate(identity(platform, identity, groupId));
    }

    public Optional<String> groupGate(PluginMessagingIdentity identity) {
        QqIntegrationSettings config = settings();
        if (!config.requireApprovedForGroup() || !config.registrationReviewEnabled()) {
            return Optional.empty();
        }
        Status state = status(identity);
        if (state.approved()) {
            return Optional.empty();
        }
        return Optional.of(switch (state.state()) {
            case "unavailable" -> "注册审核插件当前不可用，请联系管理员。";
            case "unbound" -> "请先在官网绑定当前消息身份，再发送「注册」查看审核状态。";
            case "not_started" -> "请先在官网发起注册审核：" + state.registrationUrl();
            case "passed" -> "问卷已通过，等待管理员确认。";
            default -> "当前注册审核状态：" + state.state() + "。发送「注册」查看详情。";
        });
    }

    private Optional<PluginUserProfile> findUser(PluginMessagingIdentity identity) {
        if (context == null || identity == null || identity.identity() == null || identity.identity().isBlank()) {
            return Optional.empty();
        }
        try {
            Optional<PluginUserProfile> result = context.framework().users().findByMessagingIdentity(identity);
            if (result.isPresent() || !"milky".equalsIgnoreCase(identity.protocol())) {
                return result;
            }
            return context.framework().users().findByQq(identity.identity());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static PluginMessagingIdentity identity(String platform, String value, String groupId) {
        String protocol = "milky".equalsIgnoreCase(platform) ? "milky" : "official";
        String type = "milky".equals(protocol)
                ? "qq" : (groupId == null || groupId.isBlank() ? "user_openid" : "member_openid");
        return new PluginMessagingIdentity(protocol, type, value, null, groupId, null);
    }

    private static boolean bool(Object target, String method) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(target.getClass().getMethod(method).invoke(target));
    }

    private static String text(Object target, String method, String fallback) throws ReflectiveOperationException {
        Object value = target.getClass().getMethod(method).invoke(target);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    public record Status(String state, boolean approved, boolean passed, String registrationUrl) {
        static Status disabled(String url) { return new Status("disabled", false, false, url); }
        static Status unavailable(String url) { return new Status("unavailable", false, false, url); }
        static Status unbound(String url) { return new Status("unbound", false, false, url); }
        static Status notStarted(String url) { return new Status("not_started", false, false, url); }
    }
}
