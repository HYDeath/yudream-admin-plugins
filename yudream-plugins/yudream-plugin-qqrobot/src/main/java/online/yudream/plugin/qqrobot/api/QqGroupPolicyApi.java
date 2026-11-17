package online.yudream.plugin.qqrobot.api;

/** QQ 业务插件共用的群职责闸门。私聊由调用方独立处理。 */
public interface QqGroupPolicyApi {
    boolean allows(String groupId, String action);
}
