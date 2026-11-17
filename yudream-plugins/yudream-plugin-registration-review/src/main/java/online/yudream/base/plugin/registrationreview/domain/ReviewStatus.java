package online.yudream.base.plugin.registrationreview.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** 注册审核申请状态。 */
public enum ReviewStatus {
    QUESTIONNAIRE_ONGOING,
    QUESTIONNAIRE_PENDING_REVIEW,
    PASSED,
    FAILED,
    APPROVED,
    REJECTED;

    public static ReviewStatus fromDoc(String raw) {
        if (raw == null) {
            return QUESTIONNAIRE_ONGOING;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        }
        catch (IllegalArgumentException e) {
            return QUESTIONNAIRE_ONGOING;
        }
    }

    public String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
