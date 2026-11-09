package online.yudream.plugin.qqrobot.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqIntegrationSettingsTest {
    @Test
    void defaultsKeepRegistrationIntegrationOptionalAtRuntime() {
        QqIntegrationSettings settings = QqIntegrationSettings.defaults();

        assertTrue(settings.registrationReviewEnabled());
        assertEquals("/platform/plugins/registration-review", settings.registrationUrl());
        assertEquals("/platform/plugins/registration-review", settings.normalized().registrationUrl());
    }

    @Test
    void normalizesExternalFormAndIgnoresViewOnlyFields() {
        QqIntegrationSettings settings = QqIntegrationSettings.fromDocument(Map.of(
                "registrationReviewEnabled", false,
                "requireApprovedForGroup", true,
                "registrationUrl", "platform/plugins/registration-review",
                "providerAvailable", true));

        assertTrue(!settings.registrationReviewEnabled());
        assertTrue(settings.requireApprovedForGroup());
        assertEquals("/platform/plugins/registration-review", settings.registrationUrl());
    }
}
