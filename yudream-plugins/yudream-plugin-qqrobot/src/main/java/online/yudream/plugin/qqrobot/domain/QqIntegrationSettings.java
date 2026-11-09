package online.yudream.plugin.qqrobot.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.Map;

/** QQ 机器人与其它业务插件的联动开关。凭证仍由 QQ 消息平台管理。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QqIntegrationSettings(
        boolean registrationReviewEnabled,
        boolean requireApprovedForGroup,
        String registrationUrl
) {
    public static QqIntegrationSettings defaults() {
        return new QqIntegrationSettings(true, false, "/platform/plugins/registration-review");
    }

    public QqIntegrationSettings normalized() {
        String url = registrationUrl == null || registrationUrl.isBlank()
                ? "/platform/plugins/registration-review" : registrationUrl.trim();
        if (!url.startsWith("/")) {
            url = "/" + url;
        }
        return new QqIntegrationSettings(registrationReviewEnabled, requireApprovedForGroup, url);
    }

    public Map<String, Object> toDocument() {
        QqIntegrationSettings value = normalized();
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("registrationReviewEnabled", value.registrationReviewEnabled());
        document.put("requireApprovedForGroup", value.requireApprovedForGroup());
        document.put("registrationUrl", value.registrationUrl());
        return document;
    }

    public static QqIntegrationSettings fromDocument(Map<String, Object> document) {
        QqIntegrationSettings defaults = defaults();
        if (document == null) {
            return defaults;
        }
        return new QqIntegrationSettings(
                bool(document.get("registrationReviewEnabled"), defaults.registrationReviewEnabled()),
                bool(document.get("requireApprovedForGroup"), defaults.requireApprovedForGroup()),
                text(document.get("registrationUrl"), defaults.registrationUrl())
        ).normalized();
    }

    public Map<String, Object> toView(boolean providerAvailable) {
        Map<String, Object> view = new LinkedHashMap<>(toDocument());
        view.put("providerAvailable", providerAvailable);
        return view;
    }

    private static String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean flag ? flag : fallback;
    }
}
