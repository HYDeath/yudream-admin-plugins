package online.yudream.plugin.qqrobot.interfaces.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.plugin.qqrobot.application.service.QqBotService;

import java.util.Map;

/**
 * QQ 官方机器人 Webhook 入口。
 *
 * <p>官方回调的 payload 可能带 {@code op=13} 的验证包（plain token），
 * 也可能直接是事件体；这里两种都接受：验证包回 {@code {"plain_token":...}}，
 * 事件包交给 {@link QqBotService} 处理并回文本。</p>
 */
public class QqBotController {
    private final ObjectMapper mapper = new ObjectMapper();
    private final QqBotService bot;

    public QqBotController(QqBotService bot) {
        this.bot = bot;
    }

    @PluginHttpEndpoint(method = "POST", path = "/qq/webhook", permission = "")
    public PluginHttpResponse webhook(PluginHttpRequest request) {
        try {
            String body = request.body();
            JsonNode payload = body == null || body.isBlank() ? mapper.createObjectNode() : mapper.readTree(body);
            if (payload.path("op").asInt(0) == 13) {
                // 回调地址验证：原样回显 plain_token。
                return PluginHttpResponse.rawJson(200, Map.of("plain_token", payload.path("d").path("plain_token").asText("")));
            }
            return PluginHttpResponse.ok(bot.handle(payload));
        } catch (Exception exception) {
            return PluginHttpResponse.json(400, Map.of("message", "事件解析失败：" + exception.getMessage()));
        }
    }
}
