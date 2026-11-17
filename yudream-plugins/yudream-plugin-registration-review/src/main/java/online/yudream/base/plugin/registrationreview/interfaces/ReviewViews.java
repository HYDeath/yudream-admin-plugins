package online.yudream.base.plugin.registrationreview.interfaces;

import java.util.LinkedHashMap;
import java.util.Map;
import online.yudream.base.plugin.registrationreview.domain.ReviewApplication;

/** 公开字段与管理字段装配。 */
final class ReviewViews {
    private ReviewViews() {
    }

    static Map<String, Object> application(ReviewApplication application) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", application.id());
        view.put("userId", application.userId());
        view.put("userLabel", application.userLabel());
        view.put("mode", application.mode());
        view.put("questionBankSessionId", application.questionBankSessionId());
        view.put("questionBankSessionPath", "/platform/plugins/questionbank/session?id="
                + application.questionBankSessionId());
        view.put("status", application.status().code());
        view.put("totalCount", application.totalCount());
        view.put("correctCount", application.correctCount());
        view.put("scorePercent", application.scorePercent());
        view.put("message", application.message());
        view.put("reviewerId", application.reviewerId());
        view.put("reviewedAt", application.reviewedAt());
        view.put("attemptNumber", application.attemptNumber());
        view.put("createdAt", application.createdAt());
        view.put("updatedAt", application.updatedAt());
        view.put("passingPercent", application.passingPercent());
        view.put("tripleBvid", application.tripleBvid());
        view.put("tripleVerifiedAt", application.tripleVerifiedAt());
        return view;
    }
}
