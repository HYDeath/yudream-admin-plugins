package online.yudream.base.plugin.registrationreview.infrastructure;

import java.security.SecureRandom;

/** 文档主键生成器，与其他公开插件保持同样的倒置时间排序习惯。 */
public final class Ids {
    private static final long MAX_MILLIS = 9_999_999_999_999L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static synchronized String newId() {
        long inverted = MAX_MILLIS - System.currentTimeMillis();
        return String.format("%013d-%08x", inverted, RANDOM.nextInt());
    }
}
