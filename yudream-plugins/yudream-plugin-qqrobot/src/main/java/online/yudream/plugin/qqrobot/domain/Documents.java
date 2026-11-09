package online.yudream.plugin.qqrobot.domain;

import java.util.List;
import java.util.Map;

/**
 * 文档字段的容错读取。
 *
 * <p>插件文档存储里的值可能来自 JSON、前端表单或旧版本数据，类型不总是
 * 严格一致（数字可能是字符串、布尔可能是 "true"）。这里统一兜底，
 * 避免某个字段类型不对就整份设置读不出来。</p>
 */
final class Documents {
    private Documents() {
    }

    static String string(Map<String, Object> document, String key, String fallback) {
        Object value = document.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    static int integer(Map<String, Object> document, String key, int fallback) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static boolean bool(Map<String, Object> document, String key, boolean fallback) {
        Object value = document.get(key);
        return value instanceof Boolean flag ? flag
                : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    static List<String> strings(Map<String, Object> document, String key) {
        Object value = document.get(key);
        if (value instanceof List<?> items) {
            return items.stream().filter(item -> item != null)
                    .map(item -> String.valueOf(item).trim())
                    .filter(item -> !item.isEmpty())
                    .toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> maps(Map<String, Object> document, String key) {
        Object value = document.get(key);
        if (value instanceof List<?> items) {
            return items.stream()
                    .filter(item -> item instanceof Map<?, ?>)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }
}
