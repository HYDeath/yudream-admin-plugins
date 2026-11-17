package online.yudream.plugin.schematic.application;

import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.registrationreview.api.RegistrationReviewApi;
import java.util.Optional;

/**
 * 建筑投影的使用门槛：登录账号必须已通过注册审核。
 *
 * <p>只有 {@code APPROVED} 放行；问卷通过但等待管理员确认（{@code PASSED}）、
 * 进行中、被拒绝以及没有申请记录的账号都不能生成投影。审核插件未安装或
 * 查询异常时按未通过处理，避免缺少审核链路时静默放行。</p>
 */
public final class SchematicAdmission {
    public static final String REJECTED_MESSAGE = "建筑投影需注册审核通过后使用，请先在注册审核提交申请并等待管理员确认";
    /** 登录成功但账号尚未通过注册审核；与未登录的 401 区分。 */
    public static final class Rejected extends RuntimeException {
        public Rejected() { super(REJECTED_MESSAGE); }
    }
    private final PluginContext context;
    private volatile PluginContext cachedContext;
    private volatile RegistrationReviewApi cachedApi;

    public SchematicAdmission(PluginContext context) { this.context = context; }

    public boolean permits(String owner) {
        if (owner == null || !owner.startsWith("user:")) return false;
        String userId = owner.substring("user:".length());
        try {
            return api().flatMap(api -> api.admission(userId))
                    .map(RegistrationReviewApi.Admission::approved)
                    .orElse(false);
        }
        catch (RuntimeException e) {
            cachedContext = null; cachedApi = null;
            return false;
        }
    }

    public void require(String owner) { if (!permits(owner)) throw new Rejected(); }

    private Optional<RegistrationReviewApi> api() {
        if (cachedApi != null && cachedContext == context) return Optional.of(cachedApi);
        Optional<RegistrationReviewApi> found = context.service("registration-review", RegistrationReviewApi.class);
        found.ifPresent(api -> { cachedApi = api; cachedContext = context; });
        return found;
    }
}
