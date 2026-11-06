package online.yudream.plugin.easybot.application.service;

import online.yudream.plugin.easybot.domain.BindSettings;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一次性绑定码。不落库：过期即失效，服务重启后全部作废，
 * 避免旧码在配置变更后继续生效。
 */
public class BindCodeService {
    private static final String NUMERIC = "0123456789";
    private static final String ALNUM = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingCode> pending = new ConcurrentHashMap<>();

    public record PendingCode(String code, String playerName, String serverId, String socialId,
                              String socialName, Instant expiresAt, BindSettings settings) {
    }

    public synchronized PendingCode issue(String playerName, String serverId, String socialId, String socialName,
                                          BindSettings settings) {
        purgeExpired();
        pending.entrySet().removeIf(entry -> entry.getValue().playerName().equalsIgnoreCase(playerName)
                && entry.getValue().serverId().equals(serverId));
        String code = generate(settings);
        PendingCode value = new PendingCode(code, playerName, serverId, socialId, socialName,
                Instant.now().plus(Duration.ofMinutes(settings.codeTtlMinutes())), settings);
        pending.put(code, value);
        return value;
    }

    public Optional<PendingCode> take(String code) {
        purgeExpired();
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        PendingCode value = pending.remove(code.trim().toUpperCase());
        if (value == null || value.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public Optional<PendingCode> peek(String code) {
        purgeExpired();
        PendingCode value = pending.get(code == null ? "" : code.trim().toUpperCase());
        return Optional.ofNullable(value);
    }

    public int pendingCount() {
        purgeExpired();
        return pending.size();
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        pending.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private String generate(BindSettings settings) {
        String alphabet = "alnum".equals(settings.codeCharset()) ? ALNUM : NUMERIC;
        for (int attempt = 0; attempt < 64; attempt++) {
            StringBuilder builder = new StringBuilder(settings.codeLength());
            for (int index = 0; index < settings.codeLength(); index++) {
                builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            String code = builder.toString();
            if (!pending.containsKey(code)) {
                return code;
            }
        }
        throw new IllegalStateException("绑定码生成失败，请重试");
    }
}
