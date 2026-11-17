package online.yudream.base.plugin.registrationreview.domain;

import java.util.List;
import java.util.Map;

/** 文档字段安全读取。 */
public final class DocValues {
    private DocValues() {
    }

    public static String str(Map<String, Object> doc, String key) {
        Object value = doc.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static String strOr(Map<String, Object> doc, String key, String fallback) {
        String value = str(doc, key);
        return value == null ? fallback : value;
    }

    public static long lng(Map<String, Object> doc, String key) {
        Object value = doc.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        }
        catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static int integer(Map<String, Object> doc, String key) {
        return (int) lng(doc, key);
    }

    public static boolean bool(Map<String, Object> doc, String key, boolean fallback) {
        Object value = doc.get(key);
        return value == null ? fallback : value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }

    public static List<String> strings(Map<String, Object> doc, String key) {
        Object value = doc.get(key);
        if (value instanceof List<?> list) {
            return list.stream().filter(item -> item != null).map(String::valueOf).toList();
        }
        return List.of();
    }
}
