package online.yudream.plugin.qqrobot.application.service;

import online.yudream.base.plugin.spi.system.render.PluginRenderedImage;
import online.yudream.base.plugin.spi.system.render.PluginRenderService;
import online.yudream.plugin.qqrobot.domain.ResourceEntry;

import java.util.concurrent.CompletableFuture;

/**
 * 资源市场卡片。版式对齐参考图：深色封面、右上角免费/价格、
 * 标题与说明、平台标签、底部作者与统计。
 */
public class ResourceCardRenderer {
    private final RenderSupport render;

    public ResourceCardRenderer(PluginRenderService render) {
        this.render = new RenderSupport(render);
    }

    public ResourceCardRenderer(RenderSupport render) {
        this.render = render;
    }

    /** 封面已由 ResourceService 下载并内联为 data URI；渲染容器会拦截一切外链子资源。 */
    public CompletableFuture<PluginRenderedImage> card(ResourceEntry entry) {
        return card(entry, "");
    }

    public CompletableFuture<PluginRenderedImage> card(ResourceEntry entry, String coverDataUri) {
        try {
            return CompletableFuture.completedFuture(render.html(cardHtml(entry, coverDataUri), "#resource-card"));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private String cardHtml(ResourceEntry raw) {
        return cardHtml(raw, "");
    }

    private String cardHtml(ResourceEntry raw, String coverDataUri) {
        ResourceEntry entry = raw.normalized();
        String cover = coverDataUri != null && coverDataUri.startsWith("data:image/")
                ? "<div class=\"cover\" style=\"background-image:url(" + coverDataUri + ")\"></div>"
                : "<div class=\"cover placeholder\"><span class=\"placeholder-mark\">资源封面</span></div>";
        String platforms = entry.platforms().stream()
                .map(platform -> "<span class=\"chip\">" + ItemCardRenderer.escape(platform) + "</span>")
                .reduce("", String::concat);
        String tags = entry.tags().stream().limit(4)
                .map(tag -> "<span class=\"tag\">" + ItemCardRenderer.escape(tag) + "</span>")
                .reduce("", String::concat);
        String price = entry.free() ? "免费" : "付费";
        return """
                <!doctype html><html><head><meta charset="utf-8"><style>
                *{box-sizing:border-box;margin:0;padding:0}
                body{background:transparent;font-family:"Microsoft YaHei","Noto Sans SC",sans-serif}
                .card{width:430px;border-radius:18px;overflow:hidden;background:#17171d;color:#e9e9f2;
                  border:1px solid #2c2c36}
                .cover{height:190px;background-size:cover;background-position:center;position:relative}
                .cover.placeholder{background:linear-gradient(135deg,#2b2b3a,#191922);display:flex;
                  align-items:center;justify-content:center}
                .placeholder-mark{font-size:13px;letter-spacing:3px;color:#4d4d63}
                .badge{position:absolute;right:14px;top:14px;padding:6px 12px;border-radius:999px;
                  background:rgba(22,120,74,.9);font-size:13px;font-weight:700;color:#d7ffe8}
                .body{padding:18px 20px 16px}
                .title{font-size:22px;font-weight:800;line-height:1.25}
                .subtitle{margin-top:6px;color:#8f8fa5;font-size:13px;line-height:1.5}
                .desc{margin-top:12px;color:#c6c6d6;font-size:13px;line-height:1.6;
                  display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
                .chips{display:flex;gap:8px;margin-top:14px}
                .chip{padding:4px 10px;border-radius:6px;background:#262633;color:#c9c9dc;font-size:12px}
                .tags{display:flex;gap:8px;margin-top:10px;flex-wrap:wrap}
                .tag{font-size:11px;color:#7ee2a8;background:rgba(52,199,123,.12);padding:3px 8px;border-radius:6px}
                .foot{display:flex;align-items:center;justify-content:space-between;margin-top:16px;
                  padding-top:14px;border-top:1px solid #262630;color:#86869b;font-size:12px}
                .author{display:flex;align-items:center;gap:8px;color:#b9b9cc}
                .dot{width:22px;height:22px;border-radius:50%;background:linear-gradient(135deg,#5aa9ff,#9a6cff)}
                .stats{display:flex;gap:14px}
                </style></head><body>
                <div id="resource-card" class="card">
                  <div style="position:relative">
                """ + cover + """
                <div class="badge">""" + price + """
                </div></div>
                  <div class="body">
                    <div class="title">""" + ItemCardRenderer.escape(entry.title()) + """
                </div>
                    <div class="subtitle">""" + ItemCardRenderer.escape(entry.subtitle()) + """
                </div>
                    <div class="desc">""" + ItemCardRenderer.escape(entry.description()) + """
                </div>
                    <div class="chips">""" + platforms + """
                </div>
                    <div class="tags">""" + tags + """
                </div>
                    <div class="foot">
                      <div class="author"><span class="dot"></span>""" + ItemCardRenderer.escape(entry.author()) + """
                </div>
                      <div class="stats"><span>◉ """ + entry.views() + """
                </span><span>♥ """ + entry.likes() + """
                </span><span>↓ """ + entry.downloads() + """
                </span></div>
                    </div>
                  </div>
                </div>
                </body></html>
                """;
    }
}
