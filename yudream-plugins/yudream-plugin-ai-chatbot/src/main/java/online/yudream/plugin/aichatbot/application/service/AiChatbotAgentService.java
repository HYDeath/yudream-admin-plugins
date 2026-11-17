package online.yudream.plugin.aichatbot.application.service;

import online.yudream.base.plugin.spi.system.ai.PluginAiChatRequest;
import online.yudream.base.plugin.spi.system.ai.PluginAiChatResponse;
import online.yudream.base.plugin.spi.system.ai.PluginAiService;
import online.yudream.plugin.aichatbot.application.dto.AiChatbotGroupPolicy;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class AiChatbotAgentService {
    private final PluginAiService ai;

    public AiChatbotAgentService(PluginAiService ai) {
        this.ai = Objects.requireNonNull(ai, "ai");
    }

    public CompletionStage<PluginAiChatResponse> run(AiChatbotGroupPolicy policy, PluginAiChatRequest request) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(request, "request");
        String prompt = (request.systemPrompt() == null ? "" : request.systemPrompt()) + "\n\n"
                + AiChatbotScope.prompt(request.toolCallingEnabled(), java.time.Clock.systemUTC());
        // 宿主运行时开关只控制工具注入，不能移除工作流显式工具。
        // 固定前缀只由调用方置于首部，内置工作流在模型判断前直接走无工具分支。
        String input = !request.toolCallingEnabled() && AiChatbotGroupPolicy.BUILTIN_AGENT_CODE.equals(policy.agentCode())
                ? "YUDREAM_CHATBOT_TOOLS_DISABLED\n" + request.userPrompt() : request.userPrompt();
        return ai.runAgent(policy.agentCode(), new PluginAiChatRequest(prompt, input,
                request.providerCode(), request.modelCode(), request.history(), request.executionContext(),
                request.toolCallingEnabled(), request.sessionId()));
    }
}
