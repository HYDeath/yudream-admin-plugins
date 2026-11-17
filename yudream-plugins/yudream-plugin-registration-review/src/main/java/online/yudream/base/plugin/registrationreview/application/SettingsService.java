package online.yudream.base.plugin.registrationreview.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import online.yudream.base.plugin.registrationreview.domain.DocValues;
import online.yudream.base.plugin.registrationreview.infrastructure.JsonSupport;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

/** 注册审核设置：声明式 schema + 强类型 getter。 */
public final class SettingsService {
    public static final String COLLECTION = "registration_review_settings";
    public static final String DOC_ID = "global";

    private final PluginDocumentStore documents;
    private final JsonSupport json;

    public SettingsService(PluginDocumentStore documents, JsonSupport json) {
        this.documents = documents;
        this.json = json;
    }

    private Map<String, Object> raw() {
        return documents.findById(COLLECTION, DOC_ID).orElse(Map.of());
    }

    public boolean enabled() {
        return DocValues.bool(raw(), "enabled", true);
    }

    public String mode() {
        String value = DocValues.strOr(raw(), "mode", "quiz");
        return List.of("quiz", "triple", "both", "triple_quiz").contains(value) ? value : "quiz";
    }

    public int passingPercent() {
        return boundedInt("passingPercent", 80, 1, 100);
    }

    public int drawCount() {
        return boundedInt("drawCount", 10, 1, 50);
    }

    public int maxAttempts() {
        return boundedInt("maxAttempts", 3, 0, 100);
    }

    public int cooldownSeconds() {
        return boundedInt("cooldownSeconds", 60, 0, 86400);
    }

    public String subjectiveMode() {
        String value = DocValues.strOr(raw(), "subjectiveMode", "REVIEW").toUpperCase(java.util.Locale.ROOT);
        return List.of("SELF", "REVIEW", "AI").contains(value) ? value : "REVIEW";
    }

    public String categoryId() {
        return DocValues.strOr(raw(), "categoryId", "").trim();
    }

    public String tripleCategoryId() {
        return DocValues.strOr(raw(), "tripleCategoryId", "").trim();
    }

    public List<String> tags() {
        return DocValues.strings(raw(), "tags");
    }

    public List<String> types() {
        return DocValues.strings(raw(), "types");
    }

    public List<Integer> difficulties() {
        List<Integer> result = new ArrayList<>();
        for (String value : DocValues.strings(raw(), "difficulties")) {
            try {
                result.add(Integer.parseInt(value));
            }
            catch (NumberFormatException ignored) {
                // 设置页输入错误项忽略，不让一次坏配置拖垮插件
            }
        }
        return result;
    }

    public Map<String, Object> effective() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", enabled());
        result.put("mode", mode());
        result.put("passingPercent", passingPercent());
        result.put("drawCount", drawCount());
        result.put("maxAttempts", maxAttempts());
        result.put("cooldownSeconds", cooldownSeconds());
        result.put("subjectiveMode", subjectiveMode());
        result.put("categoryId", categoryId());
        result.put("tripleCategoryId", tripleCategoryId());
        result.put("tags", tags());
        result.put("types", types());
        result.put("difficulties", difficulties().stream().map(String::valueOf).toList());
        return result;
    }

    public Map<String, Object> update(Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) {
            throw new IllegalArgumentException("没有需要保存的设置项");
        }
        Map<String, Object> current = new LinkedHashMap<>(raw());
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            current.put(entry.getKey(), normalize(entry.getKey(), entry.getValue()));
        }
        documents.save(COLLECTION, DOC_ID, current);
        return effective();
    }

    /** Plain Map so the host wraps it as {code:200,data}; the admin client rejects unwrapped bodies. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> schema() {
        try (var stream = getClass().getResourceAsStream("/settings-schema.json")) {
            if (stream == null) {
                throw new IllegalStateException("缺少 settings-schema.json");
            }
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(stream, Map.class);
        }
        catch (java.io.IOException e) {
            throw new IllegalStateException("读取设置 schema 失败：" + e.getMessage(), e);
        }
    }

    private int boundedInt(String key, int fallback, int min, int max) {
        int value = (int) DocValues.lng(raw(), key);
        if (!raw().containsKey(key)) {
            value = fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private Object normalize(String key, Object value) {
        if (List.of("enabled").contains(key)) {
            return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
        }
        if (List.of("passingPercent", "drawCount", "maxAttempts", "cooldownSeconds").contains(key)) {
            int number;
            try {
                number = value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value));
            }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("设置项 " + key + " 需要整数");
            }
            int min = switch (key) {
                case "passingPercent" -> 1;
                case "drawCount" -> 1;
                default -> 0;
            };
            int max = switch (key) {
                case "passingPercent" -> 100;
                case "drawCount" -> 50;
                case "maxAttempts" -> 100;
                default -> 86400;
            };
            if (number < min || number > max) {
                throw new IllegalArgumentException("设置项 " + key + " 超出范围");
            }
            return number;
        }
        if (List.of("tags", "types", "difficulties").contains(key)) {
            if (value instanceof List<?> list) {
                return list.stream().filter(item -> item != null && !String.valueOf(item).isBlank())
                        .map(item -> String.valueOf(item).trim()).toList();
            }
            return String.valueOf(value).split("[,，\\s]+", -1).length == 0
                    ? List.of() : java.util.Arrays.stream(String.valueOf(value).split("[,，\\s]+"))
                    .filter(item -> !item.isBlank()).map(String::trim).toList();
        }
        if (key.equals("mode")) {
            String mode = String.valueOf(value).trim();
            if (!List.of("quiz", "triple", "both", "triple_quiz").contains(mode)) {
                throw new IllegalArgumentException("未知审核模式：" + mode);
            }
            return mode;
        }
        if (key.equals("subjectiveMode")) {
            String mode = String.valueOf(value).trim().toUpperCase(java.util.Locale.ROOT);
            if (!List.of("SELF", "REVIEW", "AI").contains(mode)) {
                throw new IllegalArgumentException("未知简答判定方式：" + mode);
            }
            return mode;
        }
        if (key.equals("categoryId") || key.equals("tripleCategoryId")) {
            return String.valueOf(value).trim();
        }
        throw new IllegalArgumentException("未知的设置项：" + key);
    }
}
