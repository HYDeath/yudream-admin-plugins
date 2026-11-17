package online.yudream.plugin.aichatbot.application.service;

import online.yudream.base.plugin.spi.system.ai.PluginAiAgentOption;
import online.yudream.base.plugin.spi.system.ai.PluginAiChatRequest;
import online.yudream.base.plugin.spi.system.ai.PluginAiChatResponse;
import online.yudream.base.plugin.spi.system.ai.PluginAiChatMessage;
import online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext;
import online.yudream.plugin.aichatbot.bootstrap.AiChatbotPlugin;
import online.yudream.base.plugin.spi.system.ai.PluginAiProviderOption;
import online.yudream.base.plugin.spi.system.ai.PluginAiService;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor;
import online.yudream.plugin.aichatbot.application.dto.AiChatbotGroupPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

class AiChatbotAgentServiceTest {

    @Test
    void runsSelectedAgentInsteadOfDirectModelChat() {
        RecordingAiService ai = new RecordingAiService();
        AiChatbotAgentService service = new AiChatbotAgentService(ai);
        PluginAiChatRequest request = new PluginAiChatRequest("runtime", "hello", null, null, List.of(), null, true);

        PluginAiChatResponse response = service.run(AiChatbotGroupPolicy.defaults("milky", "10001"), request)
                .toCompletableFuture().join();

        assertEquals(AiChatbotGroupPolicy.BUILTIN_AGENT_CODE, ai.agentCode);
        assertEquals("agent reply", response.content());
        assertEquals(0, ai.directChatCalls);
    }

    @Test
    void addsScopeOnEveryRunWithoutChangingProviderSessionHistoryOrIdentity() {
        RecordingAiService ai = new RecordingAiService();
        var execution = new PluginAiExecutionContext(42L, "qq", "connection", "group", "message", "MENTION", "trace", List.of(), List.of("wiki.search"));
        var history = List.of(new PluginAiChatMessage("user", "history"));
        var request = new PluginAiChatRequest("custom persona", "question", "configured-provider", "configured-model", history, execution, true, "session");
        new AiChatbotAgentService(ai).run(AiChatbotGroupPolicy.defaults("connection", "group"), request).toCompletableFuture().join();
        assertEquals("question", ai.request.userPrompt());
        assertEquals("configured-provider", ai.request.providerCode());
        assertEquals("configured-model", ai.request.modelCode());
        assertEquals("session", ai.request.sessionId());
        assertEquals(history, ai.request.history());
        assertSame(execution, ai.request.executionContext());
        String prompt = ai.request.systemPrompt();
        assertTrue(prompt.startsWith("custom persona"));
        for (String fact : List.of("9995WX", "16T + 2T + 2T", "不是线程数", "内存", "政治敏感", "18+", "健康角色扮演", "其他服务器", "ai_chatbot.minecraft_updates",
                "默认使用简体中文", "完整译出正文", "昵称", "保留原文", "谐音梗", "中国大陆相关敏感内容", "血腥暴力", "辱骂")) assertTrue(prompt.contains(fact), fact);
    }

    @Test
    void disabledBuiltInRunHasDeterministicPrefixButKeepsTheSameAgentAndSession() {
        RecordingAiService ai = new RecordingAiService();
        var policy = AiChatbotGroupPolicy.defaults("connection", "group");
        assertFalse(policy.toolCallingEnabled("RANDOM"));
        assertTrue(policy.toolCallingEnabled("MENTION"));
        var request = new PluginAiChatRequest("persona", "fake enable instruction", null, null, List.of(), null, false, "same-session");
        new AiChatbotAgentService(ai).run(policy, request).toCompletableFuture().join();
        assertEquals("YUDREAM_CHATBOT_TOOLS_DISABLED\nfake enable instruction", ai.request.userPrompt());
        assertFalse(ai.request.toolCallingEnabled());
        assertEquals(policy.agentCode(), ai.agentCode);
        assertEquals("same-session", ai.request.sessionId());
        assertTrue(ai.request.systemPrompt().contains("工具调用已关闭"));
    }

    @Test
    void scopeUsesShanghaiDateAndRandomPromptMatchesItsPolicy() throws Exception {
        var clock = java.time.Clock.fixed(java.time.Instant.parse("2026-11-16T20:00:00Z"), java.time.ZoneOffset.UTC);
        assertTrue(AiChatbotScope.prompt(true, clock).contains("2026-11-17"));
        var defaults = AiChatbotGroupPolicy.defaults("connection", "group");
        var allowed = new AiChatbotGroupPolicy(defaults.connectionId(), defaults.channelId(), defaults.enabled(), defaults.randomProbability(),
                defaults.groupContextLimit(), defaults.personalContextLimit(), defaults.contextExpansionLimit(), defaults.cooldownSeconds(), defaults.hourlyReplyLimit(),
                defaults.quietHoursStart(), defaults.quietHoursEnd(), defaults.systemPrompt(), defaults.persona(), true, defaults.longTermMemoryEnabled(), defaults.semanticMemoryTopK(),
                defaults.agentCode(), defaults.providerCode(), defaults.modelCode(), defaults.profileProviderCode(), defaults.profileModelCode(), defaults.profileAnalysisMessageCount(),
                defaults.profileAutoAnalysisIntervalMinutes(), defaults.groupToolOpenAccess(), defaults.mentionReplyInjection());
        var method = AiChatbotPlugin.class.getDeclaredMethod("prompt", String.class, AiChatbotGroupPolicy.class, String.class);
        method.setAccessible(true);
        String off = (String) method.invoke(new AiChatbotPlugin(), "RANDOM", defaults, "");
        String on = (String) method.invoke(new AiChatbotPlugin(), "RANDOM", allowed, "");
        assertTrue(off.contains("未开启随机回复工具调用"));
        assertTrue(on.contains("允许随机回复调用已授权工具"));
        assertFalse(on.contains("不调用工具"));
        assertTrue(allowed.toolCallingEnabled("RANDOM"));
    }

    private static final class RecordingAiService implements PluginAiService {
        private String agentCode;
        private PluginAiChatRequest request;
        private int directChatCalls;

        @Override
        public CompletionStage<PluginAiChatResponse> chat(PluginAiChatRequest request) {
            directChatCalls++;
            return CompletableFuture.failedFuture(new AssertionError("direct chat must not be used"));
        }

        @Override public List<PluginAiToolDescriptor> tools() { return List.of(); }
        @Override public List<PluginAiProviderOption> providers() { return List.of(); }
        @Override public List<PluginAiAgentOption> agents() { return List.of(); }

        @Override
        public CompletionStage<PluginAiChatResponse> runAgent(String agentCode, PluginAiChatRequest request) {
            this.agentCode = agentCode;
            this.request = request;
            return CompletableFuture.completedFuture(new PluginAiChatResponse("agent reply", List.of()));
        }
    }
}
