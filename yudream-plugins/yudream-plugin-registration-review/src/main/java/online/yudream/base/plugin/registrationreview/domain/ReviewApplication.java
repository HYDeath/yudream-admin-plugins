package online.yudream.base.plugin.registrationreview.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 注册审核申请。题库会话本体归 questionbank；本记录只保存申请快照和审核结论。
 * userId 来自宿主 principal，禁止从请求体接收 owner。
 */
public record ReviewApplication(
        String id,
        String userId,
        String userLabel,
        String mode,
        String questionBankSessionId,
        ReviewStatus status,
        int totalCount,
        int correctCount,
        int scorePercent,
        String message,
        String reviewerId,
        long reviewedAt,
        int attemptNumber,
        long createdAt,
        long updatedAt,
        int passingPercent,
        String tripleMid,
        String tripleBvid,
        long tripleVerifiedAt
) {
    public ReviewApplication {
        id = id == null ? "" : id;
        userId = userId == null ? "" : userId;
        userLabel = userLabel == null ? "" : userLabel;
        mode = mode == null ? "quiz" : mode;
        questionBankSessionId = questionBankSessionId == null ? "" : questionBankSessionId;
        status = status == null ? ReviewStatus.QUESTIONNAIRE_ONGOING : status;
        message = message == null ? "" : message;
        reviewerId = reviewerId == null ? "" : reviewerId;
        tripleMid = tripleMid == null ? "" : tripleMid;
        tripleBvid = tripleBvid == null ? "" : tripleBvid;
        passingPercent = passingPercent > 0 ? passingPercent : 80;
    }

    public Map<String, Object> toDoc() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", id);
        doc.put("userId", userId);
        doc.put("userLabel", userLabel);
        doc.put("mode", mode);
        doc.put("questionBankSessionId", questionBankSessionId);
        doc.put("status", status.code());
        doc.put("totalCount", totalCount);
        doc.put("correctCount", correctCount);
        doc.put("scorePercent", scorePercent);
        doc.put("message", message);
        doc.put("reviewerId", reviewerId);
        doc.put("reviewedAt", reviewedAt);
        doc.put("attemptNumber", attemptNumber);
        doc.put("createdAt", createdAt);
        doc.put("updatedAt", updatedAt);
        doc.put("passingPercent", passingPercent);
        doc.put("tripleMid", tripleMid);
        doc.put("tripleBvid", tripleBvid);
        doc.put("tripleVerifiedAt", tripleVerifiedAt);
        return doc;
    }

    public static ReviewApplication fromDoc(String id, Map<String, Object> doc) {
        return new ReviewApplication(id,
                DocValues.strOr(doc, "userId", ""),
                DocValues.strOr(doc, "userLabel", ""),
                DocValues.strOr(doc, "mode", "quiz"),
                DocValues.strOr(doc, "questionBankSessionId", ""),
                ReviewStatus.fromDoc(DocValues.str(doc, "status")),
                DocValues.integer(doc, "totalCount"),
                DocValues.integer(doc, "correctCount"),
                DocValues.integer(doc, "scorePercent"),
                DocValues.strOr(doc, "message", ""),
                DocValues.strOr(doc, "reviewerId", ""),
                DocValues.lng(doc, "reviewedAt"),
                DocValues.integer(doc, "attemptNumber"),
                DocValues.lng(doc, "createdAt"),
                DocValues.lng(doc, "updatedAt"),
                DocValues.integer(doc, "passingPercent"),
                DocValues.strOr(doc, "tripleMid", ""),
                DocValues.strOr(doc, "tripleBvid", ""),
                DocValues.lng(doc, "tripleVerifiedAt"));
    }

    public boolean active() {
        return status == ReviewStatus.QUESTIONNAIRE_ONGOING
                || status == ReviewStatus.QUESTIONNAIRE_PENDING_REVIEW
                || status == ReviewStatus.PASSED;
    }
}
