package online.yudream.base.plugin.externalauthqq.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

/** Provider discovery reads only a cached snapshot; the AppKey is never returned to clients. */
public final class SettingsService {
    private static final String COLLECTION = "external_auth_qq_settings", DOC_ID = "global", KEY = "qq-app-key";
    private final PluginContext context;
    private final PluginDocumentStore docs;
    private final ObjectMapper mapper;
    private volatile Map<String,Object> cached;
    private volatile String secret;
    public SettingsService(PluginContext context, ObjectMapper mapper) {
        this.context = context; this.docs = context.documents(); this.mapper = mapper;
        cached = Map.copyOf(docs.findById(COLLECTION, DOC_ID).orElse(Map.of()));
        secret = context.secrets().get(KEY).map(bytes -> new String(bytes, StandardCharsets.UTF_8)).orElse("");
    }
    public boolean enabled() { return Boolean.TRUE.equals(cached.get("enabled")) && configured(); }
    public boolean configured() { return !appId().isBlank() && !redirectUri().isBlank() && !secret.isBlank(); }
    public String appId() { return text("appId", ""); }
    public String redirectUri() { return text("redirectUri", ""); }
    public String displayName() { return text("displayName", "QQ 登录"); }
    public int sort() { return ((Number) cached.getOrDefault("sort", 80)).intValue(); }
    public Optional<String> appKey() { return secret.isBlank() ? Optional.empty() : Optional.of(secret); }
    public Map<String,Object> effective() {
        return Map.of("enabled", Boolean.TRUE.equals(cached.get("enabled")), "appId", appId(), "redirectUri", redirectUri(), "displayName", displayName(), "sort", sort(), "appKeyConfigured", !secret.isBlank(), "configured", configured(), "active", enabled());
    }
    public synchronized Map<String,Object> update(Map<String,Object> patch) {
        Map<String,Object> next = new LinkedHashMap<>(cached);
        String nextKey = secret;
        for (var entry : patch.entrySet()) {
            String key = entry.getKey(); Object value = entry.getValue();
            switch (key) {
                case "enabled" -> { if (!(value instanceof Boolean)) throw new IllegalArgumentException("开关必须为布尔值"); next.put(key, value); }
                case "sort" -> { if (!(value instanceof Number n) || n.doubleValue() != n.intValue() || n.intValue() < 1 || n.intValue() > 999) throw new IllegalArgumentException("排序必须为 1~999 的整数"); next.put(key, n.intValue()); }
                case "appId", "redirectUri", "displayName", "appKey" -> {
                    if (!(value instanceof String)) throw new IllegalArgumentException("配置必须为文本");
                    String text = ((String) value).trim();
                    if (key.equals("appKey")) { if (!text.isBlank()) nextKey = text; }
                    else next.put(key, text);
                }
                default -> throw new IllegalArgumentException("未知配置项：" + key);
            }
        }
        String id = String.valueOf(next.getOrDefault("appId", ""));
        if (!id.isBlank() && !id.matches("[0-9]{3,32}")) throw new IllegalArgumentException("QQ AppID 格式不正确");
        String redirect = String.valueOf(next.getOrDefault("redirectUri", ""));
        if (!redirect.isBlank()) {
            URI uri;
            try { uri = URI.create(redirect); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("回调地址无效"); }
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null || uri.getRawQuery() != null || !"/external-login/callback".equals(uri.getPath())) throw new IllegalArgumentException("请使用站点的 /external-login/callback 页面地址，并在 QQ 平台登记相同地址");
        }
        if (String.valueOf(next.getOrDefault("displayName", "")).length() > 40 || nextKey.length() > 512) throw new IllegalArgumentException("配置长度超限");
        if (!nextKey.equals(secret)) context.secrets().put(KEY, nextKey.getBytes(StandardCharsets.UTF_8));
        docs.save(COLLECTION, DOC_ID, next);
        cached = Map.copyOf(next); secret = nextKey;
        return effective();
    }
    private String text(String key, String fallback) { return String.valueOf(cached.getOrDefault(key, fallback)); }
    /** Plain Map so the host wraps it as {code:200,data}; the admin client rejects unwrapped bodies. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> schema() {
        try (var in = getClass().getResourceAsStream("/settings-schema.json")) {
            if (in == null) throw new IllegalStateException("缺少设置定义");
            return mapper.readValue(in, Map.class);
        } catch (java.io.IOException e) { throw new IllegalStateException("无法读取设置定义", e); }
    }
}
