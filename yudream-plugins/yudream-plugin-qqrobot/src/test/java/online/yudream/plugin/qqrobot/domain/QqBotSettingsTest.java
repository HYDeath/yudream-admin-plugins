package online.yudream.plugin.qqrobot.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqBotSettingsTest {
    @Test
    void defaultsToSandboxWithSignatureCheck() {
        QqBotSettings settings = QqBotSettings.defaults();

        assertFalse(settings.enabled());
        assertTrue(settings.sandbox());
        assertTrue(settings.verifySignature());
        assertEquals(QqBotSettings.PRODUCTION_API, settings.apiBase());
        assertEquals("/api/plugins/qqrobot/qq/webhook", settings.webhookPath());
    }

    @Test
    void blankFieldsFallBackToDefaults() {
        QqBotSettings settings = QqBotSettings.fromDocument(Map.of(
                "enabled", true,
                "appId", "  ",
                "apiBase", "  ",
                "webhookPath", "",
                "secretRef", ""
        )).normalized();

        assertEquals("", settings.appId());
        assertEquals(QqBotSettings.PRODUCTION_API, settings.apiBase());
        assertEquals("/api/plugins/qqrobot/qq/webhook", settings.webhookPath());
        assertEquals("qq-bot-secret", settings.appSecretRef());
    }

    @Test
    void stripsTrailingSlashFromApiBase() {
        QqBotSettings settings = new QqBotSettings(true, "102000000", "ref", "/hook",
                "https://sandbox.api.sgroup.qq.com/", true, true, true, "a", "b", true)
                .normalized();

        assertEquals("https://sandbox.api.sgroup.qq.com", settings.apiBase());
    }

    @Test
    void viewNeverExposesSecretFlagFromDocument() {
        QqBotSettings settings = QqBotSettings.defaults();

        assertTrue(settings.toView().containsKey("secretConfigured"));
        assertFalse(settings.toDocument().containsKey("secretConfigured"));
        assertFalse(settings.toDocument().containsKey("appSecret"));
    }
}
