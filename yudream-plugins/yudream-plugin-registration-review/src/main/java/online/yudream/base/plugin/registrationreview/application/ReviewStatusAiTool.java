package online.yudream.base.plugin.registrationreview.application;

import java.util.Map;
import java.util.Set;
import online.yudream.base.plugin.registrationreview.api.RegistrationReviewApi;
import online.yudream.base.plugin.registrationreview.bootstrap.RegistrationReviewPlugin;
import online.yudream.base.plugin.spi.system.ai.*;

public final class ReviewStatusAiTool implements PluginAiTool {
    private final RegistrationReviewApi reviews;
    public ReviewStatusAiTool(RegistrationReviewApi reviews) { this.reviews = reviews; }
    @Override public PluginAiToolDescriptor descriptor() {
        return new PluginAiToolDescriptor("registration_review.my_status", "我的注册审核", "查询当前用户的问卷和最终审核状态",
                RegistrationReviewPlugin.USE_PERMISSION, PluginAiToolRisk.READ, false, Set.of("MENTION"), Map.of());
    }
    @Override public PluginAiToolResult execute(PluginAiExecutionContext context, PluginAiToolCall call) {
        if (context == null || context.userId() == null) return new PluginAiToolResult("denied", "请先登录官网或绑定当前 QQ 消息身份", Map.of());
        return reviews.admission(String.valueOf(context.userId())).map(admission -> new PluginAiToolResult("status", "已查询当前用户审核状态",
                Map.of("status", admission.status(), "passed", admission.passed(), "approved", admission.approved())))
                .orElseGet(() -> new PluginAiToolResult("status", "尚未发起注册审核", Map.of("status", "NOT_STARTED")));
    }
}
