package online.yudream.base.plugin.registrationreview.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 请求体 JSON 解析。 */
public final class JsonSupport {
    private final ObjectMapper mapper;

    public JsonSupport(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JsonNode readTree(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        try {
            return mapper.readTree(body);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("请求体不是合法的 JSON：" + e.getMessage());
        }
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        }
        catch (Exception e) {
            throw new IllegalStateException("序列化失败：" + e.getMessage(), e);
        }
    }
}
