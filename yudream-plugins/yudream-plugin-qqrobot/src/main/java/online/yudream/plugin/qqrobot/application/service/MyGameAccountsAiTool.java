package online.yudream.plugin.qqrobot.application.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import online.yudream.base.plugin.spi.system.ai.*;
import online.yudream.base.plugin.spi.system.user.PluginUserService;

/** 官网与 QQ 复用当前登录账号；调用参数不能指定其他用户。 */
public final class MyGameAccountsAiTool implements PluginAiTool {
    private final PluginUserService users;
    private final BindingService bindings;
    private final SettingsService settings;

    public MyGameAccountsAiTool(PluginUserService users, BindingService bindings, SettingsService settings) {
        this.users = users;
        this.bindings = bindings;
        this.settings = settings;
    }

    @Override public PluginAiToolDescriptor descriptor() {
        return new PluginAiToolDescriptor("qqrobot.my_accounts", "我的游戏账号", "查询当前登录用户绑定的游戏账号，不接受其他用户标识",
                "plugin:qqrobot:view", PluginAiToolRisk.READ, false, Set.of("MENTION"), Map.of());
    }

    @Override public PluginAiToolResult execute(PluginAiExecutionContext execution, PluginAiToolCall call) {
        if (execution == null || execution.userId() == null) return new PluginAiToolResult("denied", "请先登录官网或绑定当前 QQ 消息身份", Map.of());
        if (execution.channelId() != null && !execution.channelId().isBlank()) {
            var group = settings.group(execution.channelId());
            if (!group.enabled() || !group.queryEnabled()) return new PluginAiToolResult("denied", "当前群未开启游戏查询", Map.of());
        }
        var identities = users.listMessagingIdentities(execution.userId());
        var accounts = new java.util.LinkedHashMap<String, Map<String, String>>();
        for (var identity : identities) {
            for (int page = 1; ; page++) {
                var batch = bindings.bySocial(identity.identity(), page, 100);
                for (var record : batch) {
                    if ("official".equalsIgnoreCase(identity.protocol())
                            && (!java.util.Objects.equals(identity.connectionId(), record.connectionId())
                            || !java.util.Objects.equals(identity.groupOpenid(), record.groupId()))) continue;
                    accounts.put(record.playerName(), Map.of("player", record.playerName(), "serverId", record.serverId()));
                }
                if (batch.size() < 100) break;
            }
        }
        return new PluginAiToolResult("accounts", "已查询当前账号的游戏绑定", Map.of("accounts", List.copyOf(accounts.values())));
    }
}
