package online.yudream.plugin.aichatbot.interfaces.tool;

import online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext;
import online.yudream.base.plugin.spi.system.ai.PluginAiTool;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolCall;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolResult;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolRisk;
import online.yudream.plugin.aichatbot.infrastructure.PublicKnowledgeClient;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** 通过已发布 SPI 注册，由宿主继续校验模块开关、触发方式与本轮授权。 */
public final class AiChatbotKnowledgeTools {
    public static final String SEARCH = "ai_chatbot.public_search";
    public static final String WIKI = "ai_chatbot.minecraft_wiki";
    public static final String UPDATES = "ai_chatbot.minecraft_updates";
    private AiChatbotKnowledgeTools() { }

    public static List<PluginAiTool> create(PublicKnowledgeClient client) {
        return List.of(
                tool(SEARCH, "检索硬件与服务器公开资料", "真实检索电脑硬件、性能评测和其他服务器公开信息，返回网页链接、摘要、检索时间和来源可用状态；搜索片段不等于测试数据或全文。", query -> client.search(query, "public")),
                tool(WIKI, "检索 Minecraft 原版百科", "从 Minecraft 社区百科公开 API 检索原版合成与玩法，返回页面原文、修订时间和来源；不是本服玩家 Wiki，配方需核对游戏版本。", client::minecraftWiki),
                tool(UPDATES, "检索 Minecraft 官方版本更新", "实时读取 Mojang Java 最新版本清单并检索 Minecraft 官方更新文章，供中文翻译总结；明确区分清单、摘要、正文与不可用来源，不伪造快照或变更。", client::minecraftUpdates)
        );
    }

    private static PluginAiTool tool(String name, String title, String description,
                                      Function<String, Map<String, Object>> execute) {
        return new PluginAiTool() {
            @Override public PluginAiToolDescriptor descriptor() {
                return new PluginAiToolDescriptor(name, title, description, "", PluginAiToolRisk.READ, false,
                        Set.of("MENTION", "RANDOM"), Map.of("query", Map.of("type", "string", "description", "明确检索词，最多320字；Minecraft 请注明 Java/基岩版、目标版本或快照，以及具体物品/问题")));
            }

            @Override public PluginAiToolResult execute(PluginAiExecutionContext context, PluginAiToolCall call) {
                Object value = call == null || call.arguments() == null ? null : call.arguments().get("query");
                Map<String, Object> payload = execute.apply(value == null ? "" : String.valueOf(value));
                return new PluginAiToolResult(name, title + "：" + payload.get("status"), payload);
            }
        };
    }
}
