package online.yudream.base.plugin.registrationreview.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import online.yudream.base.plugin.questionbank.api.QuestionBankApi;
import online.yudream.base.plugin.registrationreview.domain.ReviewApplication;
import online.yudream.base.plugin.registrationreview.domain.ReviewMode;
import online.yudream.base.plugin.registrationreview.domain.ReviewStatus;
import online.yudream.base.plugin.registrationreview.infrastructure.Ids;
import online.yudream.base.plugin.registrationreview.infrastructure.ReviewApplicationRepository;
import online.yudream.base.plugin.registrationreview.support.QuestionBankLocator;
import online.yudream.base.plugin.registrationreview.support.BilibiliLocator;
import online.yudream.base.plugin.registrationreview.api.RegistrationReviewApi;
import online.yudream.base.plugin.spi.core.PluginContext;

/**
 * 注册审核用例。
 *
 * <p>题目与作答过程完全委托 questionbank；本插件只负责准入规则、申请状态和管理员审核。
 * 这样题库可以独立公开发布，注册审核也不会复制题目判分逻辑。</p>
 */
public final class ReviewService implements RegistrationReviewApi {
    private final PluginContext context;
    private final ReviewApplicationRepository repository;
    private final SettingsService settings;

    public ReviewService(PluginContext context, ReviewApplicationRepository repository, SettingsService settings) {
        this.context = context;
        this.repository = repository;
        this.settings = settings;
    }

    @Override
    public Optional<Admission> admission(String userId) {
        // 审核结论优先：账号一旦被管理员批准或拒绝，后续重新发起问卷不能把
        // 结论改回“进行中”，否则已通过的用户会再次被准入检查拦下。
        List<ReviewApplication> mine = repository.listAll().stream()
                .filter(application -> userId.equals(application.userId())).toList();
        return mine.stream().filter(application -> application.status() == ReviewStatus.APPROVED)
                        .max(Comparator.comparingLong(ReviewApplication::updatedAt))
                .or(() -> mine.stream().filter(application -> application.status() == ReviewStatus.REJECTED)
                        .max(Comparator.comparingLong(ReviewApplication::updatedAt)))
                .or(() -> mine.stream().filter(ReviewApplication::active)
                        .max(Comparator.comparingLong(ReviewApplication::updatedAt)))
                .or(() -> mine.stream().max(Comparator.comparingLong(ReviewApplication::updatedAt)))
                .map(application -> new Admission(application.userId(), application.status().code(),
                        application.status() == ReviewStatus.PASSED || application.status() == ReviewStatus.APPROVED,
                        application.status() == ReviewStatus.APPROVED, application.mode(), application.updatedAt()));
    }

    public Map<String, Object> meta() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", settings.enabled());
        data.put("mode", settings.mode());
        data.put("passingPercent", settings.passingPercent());
        data.put("drawCount", settings.drawCount());
        data.put("questionbankAvailable", QuestionBankLocator.find(context).isPresent());
        data.put("bilibiliIntegration", BilibiliLocator.available(context));
        data.put("bilibiliPath", "/platform/plugins/bilibili-triple");
        data.put("bilibiliMessage", "请先在 B站三连页完成扫码和核验，再返回申请");
        return data;
    }

    public ReviewApplication start(String userId, String userLabel) {
        return start(userId, userLabel, "quiz");
    }

    public synchronized ReviewApplication start(String userId, String userLabel, String path) {
        requireEnabled();
        ReviewMode configured = ReviewMode.from(settings.mode());
        ReviewMode mode = configured == ReviewMode.BOTH ? ("triple".equals(path) ? ReviewMode.TRIPLE : ReviewMode.QUIZ) : configured;
        Optional<BilibiliLocator.Proof> triple = Optional.empty();
        if (mode == ReviewMode.TRIPLE || mode == ReviewMode.TRIPLE_QUIZ) {
            triple = BilibiliLocator.evidence(context, userId);
            if (triple.isEmpty()) throw new IllegalStateException("请先完成 B站三连核验；核验结果短时有效，过期需重新检测");
        }
        Optional<QuestionBankApi> questionBank = QuestionBankLocator.find(context);
        if (mode != ReviewMode.TRIPLE && questionBank.isEmpty()) {
            throw new IllegalStateException("题库插件未启用，暂时不能开始问卷");
        }
        Optional<ReviewApplication> active = repository.activeForUser(userId);
        if (active.isPresent()) {
            return refresh(active.get());
        }
        List<ReviewApplication> mine = repository.listAll().stream()
                .filter(application -> userId.equals(application.userId()))
                .sorted(Comparator.comparingLong(ReviewApplication::createdAt).reversed())
                .toList();
        if (settings.maxAttempts() > 0 && mine.size() >= settings.maxAttempts()) {
            throw new IllegalStateException("已达到问卷申请次数上限");
        }
        if (!mine.isEmpty() && settings.cooldownSeconds() > 0) {
            long elapsed = System.currentTimeMillis() - mine.getFirst().createdAt();
            long waitMillis = settings.cooldownSeconds() * 1000L;
            if (elapsed < waitMillis) {
                long wait = Math.max(1, (waitMillis - elapsed + 999) / 1000);
                throw new IllegalStateException("申请过于频繁，请 " + wait + " 秒后再试");
            }
        }

        QuestionBankApi.QuestionBankAttempt attempt = mode == ReviewMode.TRIPLE ? null : questionBank.orElseThrow().attempt(userId,
                new QuestionBankApi.QuestionBankDrawRule(mode == ReviewMode.TRIPLE_QUIZ ? settings.tripleCategoryId() : settings.categoryId(), settings.tags(),
                        settings.types(), settings.difficulties(), settings.drawCount(),
                        "SELF".equals(settings.subjectiveMode()) ? "REVIEW" : settings.subjectiveMode()));
        long now = System.currentTimeMillis();
        ReviewApplication application = new ReviewApplication(Ids.newId(), userId, userLabel,
                mode.code(), attempt == null ? "" : attempt.sessionId(),
                attempt == null ? ReviewStatus.PASSED : ReviewStatus.QUESTIONNAIRE_ONGOING,
                attempt == null ? 0 : attempt.totalCount(), 0, attempt == null ? 100 : 0,
                attempt == null ? "三连已核验，等待管理员确认" : "请完成问卷后刷新审核状态", "", 0, mine.size() + 1, now, now,
                settings.passingPercent(), triple.map(BilibiliLocator.Proof::mid).orElse(""),
                triple.map(BilibiliLocator.Proof::bvid).orElse(""), triple.map(BilibiliLocator.Proof::verifiedAt).orElse(0L));
        repository.save(application);
        return application;
    }

    public ReviewApplication refreshMine(String userId, String id) {
        ReviewApplication application = requireMine(userId, id);
        return refresh(application);
    }

    public List<ReviewApplication> listMine(String userId) {
        return repository.listAll().stream()
                .filter(application -> userId.equals(application.userId()))
                .sorted(Comparator.comparingLong(ReviewApplication::createdAt).reversed())
                .toList();
    }

    public PageResult queryAdmin(String status, String keyword, int page, int size) {
        String statusFilter = status == null ? "" : status.trim().toLowerCase(java.util.Locale.ROOT);
        String needle = keyword == null ? "" : keyword.trim().toLowerCase(java.util.Locale.ROOT);
        List<ReviewApplication> filtered = repository.listAll().stream()
                .filter(application -> statusFilter.isEmpty() || "all".equals(statusFilter)
                        || application.status().code().equals(statusFilter))
                .filter(application -> needle.isEmpty()
                        || application.userId().toLowerCase(java.util.Locale.ROOT).contains(needle)
                        || application.userLabel().toLowerCase(java.util.Locale.ROOT).contains(needle))
                .toList();
        int from = Math.min(Math.max(0, page - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        return new PageResult(filtered.subList(from, to), filtered.size());
    }

    public ReviewApplication approve(String id, String reviewerId) {
        requireEnabled();
        ReviewApplication application = refresh(require(id));
        if (application.status() != ReviewStatus.PASSED) {
            throw new IllegalStateException("请先完成判分并达到及格线，再批准申请");
        }
        ReviewApplication updated = withReview(application, ReviewStatus.APPROVED, "审核已通过", reviewerId);
        repository.save(updated);
        return updated;
    }

    public ReviewApplication reject(String id, String reviewerId, String reason) {
        ReviewApplication application = require(id);
        if (application.status() == ReviewStatus.APPROVED || application.status() == ReviewStatus.REJECTED) {
            throw new IllegalStateException("该申请已经处理过");
        }
        String message = reason == null || reason.isBlank() ? "未通过审核" : reason.trim();
        ReviewApplication updated = withReview(application, ReviewStatus.REJECTED, message, reviewerId);
        repository.save(updated);
        return updated;
    }

    public ReviewApplication require(String id) {
        if (id == null || id.isBlank()) {
            throw new NotFoundException("审核申请不存在");
        }
        return repository.findById(id).orElseThrow(() -> new NotFoundException("审核申请不存在"));
    }

    private ReviewApplication requireMine(String userId, String id) {
        ReviewApplication application = require(id);
        if (!userId.equals(application.userId())) {
            throw new NotFoundException("审核申请不存在");
        }
        return application;
    }

    private ReviewApplication refresh(ReviewApplication application) {
        if (application.status() != ReviewStatus.QUESTIONNAIRE_ONGOING
                && application.status() != ReviewStatus.QUESTIONNAIRE_PENDING_REVIEW) {
            return application;
        }
        Optional<QuestionBankApi> questionBank = QuestionBankLocator.find(context);
        if (questionBank.isEmpty()) {
            return application;
        }
        Optional<QuestionBankApi.QuestionBankResult> result = questionBank.get()
                .result(application.userId(), application.questionBankSessionId());
        if (result.isEmpty()) {
            throw new NotFoundException("题库作答会话不存在或已失效");
        }
        QuestionBankApi.QuestionBankResult quiz = result.get();
        int total = Math.max(application.totalCount(), quiz.totalCount());
        int correct = Math.max(0, quiz.correctCount());
        if (quiz.pendingReview()) {
            ReviewApplication updated = updateResult(application, ReviewStatus.QUESTIONNAIRE_PENDING_REVIEW,
                    total, correct, percent(correct, total), "问卷已提交，等待简答题人工判分");
            repository.save(updated);
            return updated;
        }
        if (!quiz.finished()) {
            return application;
        }
        int score = percent(correct, total);
        boolean passed = total > 0 && (long) correct * 100 >= (long) total * application.passingPercent();
        ReviewApplication updated = updateResult(application, passed ? ReviewStatus.PASSED : ReviewStatus.FAILED,
                total, correct, score, passed ? "问卷达到及格线，等待管理员确认" : "问卷未达到及格线");
        repository.save(updated);
        return updated;
    }

    private ReviewApplication updateResult(ReviewApplication application, ReviewStatus status, int total,
                                           int correct, int score, String message) {
        return new ReviewApplication(application.id(), application.userId(), application.userLabel(),
                application.mode(), application.questionBankSessionId(), status, total, correct, score, message,
                application.reviewerId(), application.reviewedAt(), application.attemptNumber(),
                application.createdAt(), System.currentTimeMillis(), application.passingPercent(),
                application.tripleMid(), application.tripleBvid(), application.tripleVerifiedAt());
    }

    private static ReviewApplication withReview(ReviewApplication application, ReviewStatus status,
                                                String message, String reviewerId) {
        return new ReviewApplication(application.id(), application.userId(), application.userLabel(),
                application.mode(), application.questionBankSessionId(), status, application.totalCount(),
                application.correctCount(), application.scorePercent(), message, reviewerId,
                System.currentTimeMillis(), application.attemptNumber(), application.createdAt(),
                System.currentTimeMillis(), application.passingPercent(), application.tripleMid(),
                application.tripleBvid(), application.tripleVerifiedAt());
    }

    private void requireEnabled() {
        if (!settings.enabled()) {
            throw new IllegalStateException("注册审核当前未启用");
        }
    }

    private static int percent(int correct, int total) {
        return total <= 0 ? 0 : Math.max(0, Math.min(100, (int) Math.round(correct * 100.0 / total)));
    }

    public record PageResult(List<ReviewApplication> records, long total) {
    }
}
