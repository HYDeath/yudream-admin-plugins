package online.yudream.base.plugin.registrationreview.application;

import java.util.Optional;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;

/** 用户展示名按读取时解析，审核文档只存 userId。 */
public final class UserLabels {
    private final FrameworkServices framework;

    public UserLabels(FrameworkServices framework) {
        this.framework = framework;
    }

    public String label(String userId) {
        if (userId == null || userId.isBlank()) {
            return "";
        }
        try {
            Long id = Long.parseLong(userId.trim());
            Optional<PluginUserProfile> profile = framework.users().findById(id);
            if (profile.isPresent()) {
                PluginUserProfile user = profile.get();
                if (user.nickname() != null && !user.nickname().isBlank()) {
                    return user.nickname();
                }
                if (user.username() != null && !user.username().isBlank()) {
                    return user.username();
                }
            }
        }
        catch (RuntimeException ignored) {
            // 已删除用户或用户服务短暂不可用时走稳定回退
        }
        return "用户 " + userId;
    }
}
