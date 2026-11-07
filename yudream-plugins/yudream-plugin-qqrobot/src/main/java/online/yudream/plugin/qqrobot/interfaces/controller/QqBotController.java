package online.yudream.plugin.qqrobot.interfaces.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.plugin.qqrobot.application.service.QqBotService;
import online.yudream.plugin.qqrobot.application.service.QqEd25519;
import online.yudream.plugin.qqrobot.application.service.QqSignatureVerifier;
import online.yudream.plugin.qqrobot.application.service.SettingsService;

import java.util.Map;

/**
 * QQ 官方机器人 Webhook 入口。
 *
 * <p>官方回调的 payload 可能带 {@code op=13} 的验证包（plain token），
 * 也可能直接是事件体；这里两种都接受：验证包用 AppSecret 派生的 Ed25519
 * 私钥对 {@code event_ts + plain_token} 签名后回 {@code plain_token + signature}，
 * 事件包交给 {@link QqBotService} 处理并回文本。</p>
 *
 * <p>验签公钥同样由 AppSecret 派生（官方约定），因此只要 Secret 正确就不需要
 * 额外配置；手工配置的公钥优先，用于 Secret 与开放平台不一致时的排障。</p>
 */
public class QqBotController {
    private static final String PUBLIC_KEY_REF = "qq-webhook-public-key";

    private final ObjectMapper mapper = new ObjectMapper();
    private final QqBotService bot;
    private final SettingsService settings;
    private final QqBotService.SecretReader secrets;

    public QqBotController(QqBotService bot, SettingsService settings, QqBotService.SecretReader secrets) {
        this.bot = bot;
        this.settings = settings;
        this.secrets = secrets == null ? name -> java.util.Optional.empty() : secrets;
    }

    @PluginHttpEndpoint(method = "POST", path = "/qq/webhook", permission = "")
    public PluginHttpResponse webhook(PluginHttpRequest request) {
        try {
            String body = request.body();
            JsonNode payload = body == null || body.isBlank() ? mapper.createObjectNode() : mapper.readTree(body);
            var botSettings = settings.botSettings();
            if (payload.path("op").asInt(0) == 13) {
                String plain = payload.path("d").path("plain_token").asText("");
                String eventTs = payload.path("d").path("event_ts").asText("");
                String secret = secrets.read(botSettings.appSecretRef()).orElse("");
                String signature = secret.isBlank()
                        ? "" : QqEd25519.signValidation(secret, eventTs, plain);
                return PluginHttpResponse.rawJson(200, Map.of("plain_token", plain,
                        "signature", signature));
            }
            if (botSettings.verifySignature()) {
                String publicKey = secrets.read(PUBLIC_KEY_REF).orElse("");
                if (publicKey.isBlank()) {
                    String secret = secrets.read(botSettings.appSecretRef()).orElse("");
                    publicKey = secret.isBlank() ? "" : QqEd25519.publicKeyHex(secret);
                }
                String signature = header(request, "X-Signature-Ed25519");
                String timestamp = header(request, "X-Signature-Timestamp");
                if (!QqSignatureVerifier.verify(signature, timestamp, body, publicKey)) {
                    return PluginHttpResponse.json(401, Map.of("message", "回调签名校验失败"));
                }
            }
            return PluginHttpResponse.ok(bot.handle(payload));
        } catch (Exception exception) {
            return PluginHttpResponse.json(400, Map.of("message", "事件解析失败：" + exception.getMessage()));
        }
    }

    private static String header(PluginHttpRequest request, String name) {
        var values = request.headers().get(name);
        if (values == null || values.isEmpty()) {
            values = request.headers().get(name.toLowerCase());
        }
        return values == null || values.isEmpty() ? "" : values.getFirst();
    }
}
