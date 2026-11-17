package online.yudream.base.plugin.registrationreview.domain;

import java.util.List;

/** 审核模式。triple / triple_quiz 由后续 B站插件补齐；本插件先明确返回待接入状态。 */
public enum ReviewMode {
    QUIZ,
    TRIPLE,
    BOTH,
    TRIPLE_QUIZ;

    public static ReviewMode from(String raw) {
        if (raw == null) {
            return QUIZ;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "triple" -> TRIPLE;
            case "both" -> BOTH;
            case "triple_quiz" -> TRIPLE_QUIZ;
            default -> QUIZ;
        };
    }

    public String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean requiresBilibili() {
        return this == TRIPLE || this == BOTH || this == TRIPLE_QUIZ;
    }
}
