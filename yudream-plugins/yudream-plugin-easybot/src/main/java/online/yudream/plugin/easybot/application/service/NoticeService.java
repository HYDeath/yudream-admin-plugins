package online.yudream.plugin.easybot.application.service;

import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 插件内的临时通知。
 *
 * <p>桥接令牌轮换、密钥不可持久化这类“其他程序/管理员需要立刻知道”的事件
 * 写入短期通知，前端轮询后以横幅展示；默认 30 分钟后自动过期，避免历史噪音。
 * 需要外部程序消费时直接读 {@code GET /api/plugins/easybot/notices}（公开只读）。</p>
 */
public class NoticeService {
    private static final String COLLECTION = "easybot-notice";
    private static final int DEFAULT_TTL_MINUTES = 30;

    private final PluginDocumentStore documents;

    public NoticeService(PluginDocumentStore documents) {
        this.documents = documents;
    }

    public Map<String, Object> publish(String level, String title, String message) {
        return publish(level, title, message, DEFAULT_TTL_MINUTES);
    }

    public Map<String, Object> publish(String level, String title, String message, int ttlMinutes) {
        Instant now = Instant.now();
        Map<String, Object> notice = new LinkedHashMap<>();
        notice.put("id", UUID.randomUUID().toString());
        notice.put("level", level == null || level.isBlank() ? "info" : level);
        notice.put("title", title == null ? "" : title);
        notice.put("message", message == null ? "" : message);
        notice.put("createdAt", now.toString());
        notice.put("expiresAt", now.plus(Math.max(1, ttlMinutes), ChronoUnit.MINUTES).toString());
        documents.save(COLLECTION, String.valueOf(notice.get("id")), notice);
        return notice;
    }

    /** 未过期通知，按时间倒序；顺带清理过期记录。 */
    public List<Map<String, Object>> active() {
        Instant now = Instant.now();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> document : documents.findAll(COLLECTION, 1, 200)) {
            String expires = document.get("expiresAt") == null ? "" : String.valueOf(document.get("expiresAt"));
            try {
                if (Instant.parse(expires).isBefore(now)) {
                    documents.delete(COLLECTION, String.valueOf(document.get("id")));
                    continue;
                }
            } catch (RuntimeException ignored) {
                // 时间损坏时按有效处理，避免丢失重要提示。
            }
            result.add(document);
        }
        result.sort(Comparator.comparing(
                (Map<String, Object> item) -> String.valueOf(item.getOrDefault("createdAt", ""))).reversed());
        return result;
    }
}
