package online.yudream.plugin.aichatbot.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.plugin.aichatbot.interfaces.tool.AiChatbotKnowledgeTools;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AiChatbotWorkflowDefinitionTest {
    @Test
    void everyRouteReachesEndAndOnlyRetrievalBranchesRequireTheirOwnSources() throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream("agents/group-chatbot.json")) {
            JsonNode graph = new ObjectMapper().readTree(input);
            Map<String, JsonNode> nodes = new HashMap<>();
            graph.path("nodes").forEach(node -> assertNull(nodes.put(node.path("id").asText(), node.path("data"))));
            assertFalse(nodes.get("intent").path("strictJson").asBoolean());
            assertEquals("NONE", nodes.get("intent").path("toolMode").asText());
            Set<String> toolCodes = new HashSet<>();
            nodes.get("research-reply").path("toolCodes").forEach(code -> toolCodes.add(code.asText()));
            assertEquals(Set.of(AiChatbotKnowledgeTools.SEARCH, AiChatbotKnowledgeTools.UPDATES, AiChatbotKnowledgeTools.WIKI), toolCodes);
            assertEquals("wiki.search", nodes.get("wiki-reply").path("toolCodes").path(0).asText());
            assertEquals("REQUIRED", nodes.get("research-reply").path("toolMode").asText());
            assertEquals("REQUIRED", nodes.get("wiki-reply").path("toolMode").asText());
            assertEquals("NONE", nodes.get("chat-reply").path("toolMode").asText());
            assertTrue(nodes.values().stream().noneMatch(data -> "tool".equals(data.path("kind").asText())), "使用原生模型工具回调，不能让外网失败的旧式工具节点中断降级回复");

            for (String route : List.of("refuse", "wiki", "research", "tool", "chat", "unknown")) {
                List<String> path = path(graph, nodes, route);
                assertEquals("end", path.getLast());
                String expected = switch (route) {
                    case "refuse" -> "refusal";
                    case "wiki" -> "wiki-reply";
                    case "research" -> "research-reply";
                    case "tool" -> "tool-reply";
                    default -> "chat-reply";
                };
                assertTrue(path.contains(expected), route + path);
                assertEquals(1, path.stream().filter(id -> List.of("refusal", "wiki-reply", "research-reply", "tool-reply", "chat-reply").contains(id)).count());
            }
            for (String route : List.of("wiki", "research", "tool")) {
                List<String> disabled = path(graph, nodes, route, true);
                assertEquals(List.of("start", "tools-disabled", "chat-task", "chat-reply", "end"), disabled,
                        "关闭工具必须在模型意图判断前确定性绕过检索");
            }
            try (var manifest = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
                String text = new String(manifest.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                toolCodes.forEach(code -> assertTrue(text.contains("- " + code), "工具必须有应用级授权：" + code));
            }
            assertTrue(nodes.get("wiki-reply").path("prompt").asText().contains("generatedCaption"));
            assertTrue(nodes.get("intent").path("prompt").asText().contains("谐音梗本身不构成拒绝理由"));
            assertTrue(nodes.get("wiki-reply").path("prompt").asText().contains("用简体中文回答"));
            assertTrue(nodes.get("research-reply").path("prompt").asText().contains("简体中文"));
            assertTrue(nodes.get("chat-reply").path("prompt").asText().contains("谐音梗本身不是拒绝理由"));
        }
    }

    private List<String> path(JsonNode graph, Map<String, JsonNode> nodes, String route) {
        return path(graph, nodes, route, false);
    }

    private List<String> path(JsonNode graph, Map<String, JsonNode> nodes, String route, boolean disabled) {
        List<String> visited = new ArrayList<>();
        String current = "start";
        while (visited.size() <= nodes.size()) {
            assertTrue(nodes.containsKey(current));
            assertFalse(visited.contains(current), "工作流不能有环");
            visited.add(current);
            if ("end".equals(current)) return visited;
            JsonNode data = nodes.get(current);
            String condition = data.path("condition").asText();
            String input = (disabled ? "YUDREAM_CHATBOT_TOOLS_DISABLED\n" : "") + "最新快照与硬件比较";
            boolean matched = "tools-disabled".equals(current)
                    ? input.matches(condition.substring("query matches '".length(), condition.length() - 1))
                    : condition.equals("intent.route == '" + route + "'");
            String branch = "condition".equals(data.path("kind").asText()) ? String.valueOf(matched) : "";
            String source = current;
            List<JsonNode> edges = new ArrayList<>();
            graph.path("edges").forEach(edge -> {
                assertTrue(nodes.containsKey(edge.path("source").asText()));
                assertTrue(nodes.containsKey(edge.path("target").asText()));
                if (source.equals(edge.path("source").asText()) && branch.equals(edge.path("sourceHandle").asText(""))) edges.add(edge);
            });
            assertEquals(1, edges.size(), current + " 的出边必须唯一：" + route);
            current = edges.getFirst().path("target").asText();
        }
        fail("未到达结束节点");
        return visited;
    }
}
