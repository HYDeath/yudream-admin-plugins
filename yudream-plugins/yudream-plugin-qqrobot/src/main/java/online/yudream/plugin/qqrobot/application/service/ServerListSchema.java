package online.yudream.plugin.qqrobot.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「服务器在线列表」模板的结构化配置。
 *
 * <p>后台不再让运营直接改整段 HTML，而是填一组表单（标题、主题、分辨率、
 * 显示开关、服务器筛选、头像与背景来源、自定义 CSS、缓存），由本类把配置
 * 编译成模板 HTML。HTML 仍存进 {@code image-template.html}，这样渲染链路
 * 保持单一出口，导出/复制模板也不会丢样式。</p>
 *
 * <p>配置文件用 {@code Map<String,Object>} 存文档库，避免为每个开关加一层
 * record 序列化；读取时一律带默认值，旧模板（没有 settings）也能正常出图。</p>
 */
public final class ServerListSchema {
    private ServerListSchema() {
    }

    static final List<String> THEMES = List.of("liquid-glass", "solid", "flat");
    static final List<String> RESOLUTIONS = List.of("standard", "high", "ultra", "xlarge", "custom");
    static final List<String> AVATAR_SOURCES = List.of("mojang", "none");
    static final List<String> BACKGROUND_SOURCES = List.of("url", "random", "none");

    static Map<String, Object> defaults() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("title", "服务器在线列表");
        settings.put("site", "璇玑之境");
        settings.put("theme", "liquid-glass");
        settings.put("resolution", "standard");
        settings.put("outputWidth", 900);
        settings.put("font", "noto-sans-sc");
        settings.put("glass", "clear");
        settings.put("refraction", 100);
        settings.put("lightAngle", 315);
        settings.put("darken", 45);
        settings.put("showServerCount", true);
        settings.put("showTotalOnline", true);
        settings.put("showTime", true);
        settings.put("showStatus", true);
        settings.put("showOnlyOnline", true);
        settings.put("showEdition", true);
        settings.put("showPlayerList", true);
        settings.put("includedServers", List.of());
        settings.put("excludedServers", List.of());
        settings.put("avatarSource", "mojang");
        settings.put("avatarStyle", "hat");
        settings.put("backgroundSource", "none");
        settings.put("backgroundCategory", "二次元");
        settings.put("backgroundApi", "");
        settings.put("customCss", "");
        settings.put("cacheSeconds", 30);
        settings.put("avatarCacheSeconds", 3600);
        settings.put("debug", false);
        return settings;
    }

    /** 把页面表单合并到已有配置上；未知字段忽略，缺失字段取默认值。 */
    static Map<String, Object> merge(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> result = defaults();
        if (existing != null) {
            result.putAll(existing);
        }
        if (incoming != null) {
            incoming.forEach((key, value) -> {
                if (value != null) {
                    result.put(key, value);
                }
            });
        }
        return result;
    }

    static int widthOf(Map<String, Object> settings) {
        String resolution = text(settings, "resolution", "standard");
        return switch (resolution) {
            case "ultra" -> 1440;
            case "xlarge" -> 1800;
            case "custom" -> number(settings, "outputWidth", 1200, 900, 1920);
            case "high" -> 1080;
            default -> 900;
        };
    }

    static String html(Map<String, Object> settings) {
        Map<String, Object> config = merge(settings, null);
        boolean glass = "liquid-glass".equals(text(config, "theme", "liquid-glass"));
        boolean clear = "clear".equals(text(config, "glass", "clear"));
        int refraction = number(config, "refraction", 100, 0, 100);
        int lightAngle = number(config, "lightAngle", 315, 0, 360);
        int darken = number(config, "darken", 45, 0, 100);
        int width = widthOf(config);
        String font = switch (text(config, "font", "noto-sans-sc")) {
            case "source-han-sans" -> "'Source Han Sans SC','Noto Sans SC',sans-serif";
            case "system" -> "system-ui,'Microsoft YaHei',sans-serif";
            default -> "'Noto Sans SC','Microsoft YaHei',sans-serif";
        };
        String panel = glass
                ? (clear ? "rgba(18,20,28,.44)" : "rgba(20,22,30,.62)")
                : "rgba(16,17,24,.82)";
        String panelBlur = glass ? (clear ? "blur(14px)" : "blur(24px)") : "none";
        String border = glass ? "rgba(255,255,255,.18)" : "rgba(255,255,255,.08)";
        String glare = glass
                ? "linear-gradient(" + lightAngle + "deg,rgba(255,255,255,.22),rgba(255,255,255,0) 46%)"
                : "none";

        StringBuilder extra = new StringBuilder();
        if (!flag(config, "showServerCount", true)) {
            extra.append(".stat.count{display:none}");
        }
        if (!flag(config, "showTotalOnline", true)) {
            extra.append(".online{display:none}");
        }
        if (!flag(config, "showTime", true)) {
            extra.append(".stat.time{display:none}");
        }
        if (!flag(config, "showStatus", true)) {
            extra.append(".stat.status{display:none}");
        }
        if (!flag(config, "showEdition", true)) {
            extra.append(".edition{display:none}");
        }
        if (!flag(config, "showPlayerList", true)) {
            extra.append(".players{display:none}");
        }
        Object css = config.get("customCss");
        if (css != null && !String.valueOf(css).isBlank()) {
            // 自定义 CSS 由运营填写，渲染容器隔离，不执行脚本。
            extra.append("\n").append(String.valueOf(css));
        }

        return """
                <!doctype html><html><head><meta charset="utf-8"><style>
                *{box-sizing:border-box;margin:0;padding:0}
                body{font-family:%FONT%;background:#0c0d12}
                #template-root{position:relative;width:%WIDTH%px;min-height:140px;padding:34px 30px 36px;
                  color:#f4f4f8;overflow:hidden;font-family:%FONT%}
                .bg{position:absolute;inset:0;background-image:var(--bg);background-size:cover;
                  background-position:center;z-index:0}
                .scrim{position:absolute;inset:0;z-index:0;background:linear-gradient(160deg,
                  rgba(12,13,18,calc(%DARKEN% / 100 * .9)),rgba(8,9,13,calc(%DARKEN% / 100)));}
                .content{position:relative;z-index:1}
                .head{display:flex;justify-content:space-between;align-items:flex-start;gap:20px}
                .site{font-size:13px;letter-spacing:2px;color:rgba(255,255,255,.7)}
                .title{font-size:42px;font-weight:800;letter-spacing:1px;margin-top:6px;
                  text-shadow:0 4px 18px rgba(0,0,0,.55)}
                .online{min-width:118px;padding:13px 20px;border-radius:18px;text-align:center;
                  background:%PANEL%;border:1px solid %BORDER%;backdrop-filter:%BLUR%;
                  -webkit-backdrop-filter:%BLUR%;position:relative;overflow:hidden}
                .online::after{content:"";position:absolute;inset:0;background:%GLARE%;pointer-events:none}
                .online span{display:block;font-size:12px;color:rgba(255,255,255,.62);position:relative;z-index:1}
                .online strong{font-size:36px;font-weight:800;position:relative;z-index:1}
                .stats{display:flex;gap:12px;margin:18px 0 20px}
                .stat{flex:1;padding:11px 15px;border-radius:14px;background:%PANEL%;
                  border:1px solid %BORDER%;backdrop-filter:%BLUR%;-webkit-backdrop-filter:%BLUR%}
                .stat span{display:block;font-size:11px;letter-spacing:1px;color:rgba(255,255,255,.55)}
                .stat strong{font-size:19px;font-weight:700}
                .server{margin-bottom:14px;padding:14px 16px;border-radius:18px;background:%PANEL%;
                  border:1px solid %BORDER%;backdrop-filter:%BLUR%;-webkit-backdrop-filter:%BLUR%;
                  position:relative;overflow:hidden}
                .server::after{content:"";position:absolute;inset:0;background:%GLARE%;
                  opacity:.55;pointer-events:none;border-radius:inherit}
                .server-head{display:flex;justify-content:space-between;align-items:center;
                  gap:12px;margin-bottom:10px;position:relative;z-index:1}
                .server-name{font-size:21px;font-weight:700;display:flex;align-items:center;gap:8px}
                .edition{font-size:10px;font-weight:600;padding:2px 7px;border-radius:5px;
                  background:rgba(255,255,255,.12);color:rgba(255,255,255,.78)}
                .badge{font-size:12px;padding:4px 11px;border-radius:999px;
                  background:rgba(255,255,255,.12);border:1px solid rgba(255,255,255,.1)}
                .badge.on{background:rgba(0,180,42,.2);color:#b8ffcb}
                .badge.off{background:rgba(245,63,63,.18);color:#ffc9c9}
                .players{display:grid;grid-template-columns:repeat(3,1fr);gap:9px;position:relative;z-index:1}
                .player{display:flex;align-items:center;gap:9px;padding:7px 11px;border-radius:12px;
                  background:rgba(24,26,36,.62);border:1px solid rgba(255,255,255,.07);min-width:0}
                .avatar{width:30px;height:30px;border-radius:6px;image-rendering:pixelated;
                  background:#24252f;flex:none}
                .avatar:not([src]),.avatar[src=""]{visibility:hidden}
                .player b{font-size:14px;font-weight:600;overflow:hidden;text-overflow:ellipsis;
                  white-space:nowrap}
                .empty{font-size:12px;color:rgba(255,255,255,.45);position:relative;z-index:1}
                %EXTRA%
                </style></head><body>
                <div id="template-root" style="--bg:url('{{{background}}}')">
                  <div class="bg"></div><div class="scrim"></div>
                  <div class="content">
                    <div class="head">
                      <div><div class="site">{{site}}</div><div class="title">{{title}}</div></div>
                      <div class="online"><span>在线</span><strong>{{total}}</strong></div>
                    </div>
                    <div class="stats">
                      <div class="stat count"><span>服务器</span><strong>{{serverCount}}</strong></div>
                      <div class="stat time"><span>更新时间</span><strong>{{updated}}</strong></div>
                      <div class="stat status"><span>状态</span><strong>{{status}}</strong></div>
                    </div>
                    {{#each servers}}
                    <div class="server">
                      <div class="server-head">
                        <div class="server-name">{{name}}<span class="edition">{{edition}}</span></div>
                        <div class="badge {{stateClass}}">{{online}} 人在线</div>
                      </div>
                      <div class="players">
                        {{#each players}}<div class="player">
                          <img class="avatar" src="{{{avatar}}}" alt=""><b>{{name}}</b></div>{{/each}}
                        {{emptyHint}}
                      </div>
                    </div>
                    {{/each}}
                  </div>
                </div></body></html>
                """
                .replace("%WIDTH%", String.valueOf(width))
                .replace("%DARKEN%", String.valueOf(darken))
                .replace("%PANEL%", panel)
                .replace("%BORDER%", border)
                .replace("%BLUR%", panelBlur)
                .replace("%GLARE%", glare)
                .replace("%FONT%", font)
                .replace("%EXTRA%", extra.toString())
                .replace("%REFRACTION%", String.valueOf(refraction));
    }

    /** 供前端渲染预览时填充的下拉选项。 */
    public static Map<String, Object> view() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("themes", named(THEMES, Map.of("liquid-glass", "液态玻璃", "solid", "半透明", "flat", "纯色")));
        schema.put("resolutions", named(RESOLUTIONS,
                Map.of("standard", "标准 1×（900px）", "high", "高清 1.2×（1080px）", "ultra", "极清 1.6×（1440px）",
                        "xlarge", "超清 2×（1800px）", "custom", "自定义宽度（900–1920px）")));
        schema.put("avatarSources", named(AVATAR_SOURCES, Map.of("mojang", "正版头像", "none", "不显示头像")));
        schema.put("backgroundSources", named(BACKGROUND_SOURCES,
                Map.of("url", "固定图片", "random", "随机图片 API", "none", "不设置")));
        return schema;
    }

    private static List<Map<String, String>> named(List<String> values, Map<String, String> labels) {
        List<Map<String, String>> rows = new ArrayList<>(values.size());
        for (String value : values) {
            rows.add(Map.of("value", value, "label", labels.getOrDefault(value, value)));
        }
        return rows;
    }

    private static String text(Map<String, Object> settings, String key, String fallback) {
        Object value = settings.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private static int number(Map<String, Object> settings, String key, int fallback, int min, int max) {
        Object value = settings.get(key);
        int parsed = value instanceof Number number ? number.intValue()
                : parse(String.valueOf(value), fallback);
        return Math.max(min, Math.min(max, parsed));
    }

    private static int parse(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static boolean flag(Map<String, Object> settings, String key, boolean fallback) {
        Object value = settings.get(key);
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
