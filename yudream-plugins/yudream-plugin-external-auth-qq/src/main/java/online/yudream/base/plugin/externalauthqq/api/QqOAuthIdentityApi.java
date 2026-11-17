package online.yudream.base.plugin.externalauthqq.api;

import java.util.Optional;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;

/** 仅允许相同 QQ 应用作用域内的 OpenID 查询，不按昵称或群申请答案匹配账号。 */
public interface QqOAuthIdentityApi {
    Optional<PluginUserProfile> findUser(String appId, String openId);
}
