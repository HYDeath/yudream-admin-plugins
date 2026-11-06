package online.yudream.plugin.easybot.application.service;

import online.yudream.base.plugin.spi.system.render.PluginRenderService;
import online.yudream.base.plugin.spi.system.render.PluginRenderedImage;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 后台「消息渲染」能力的唯一入口。
 *
 * <p>渲染不在这里实现：宿主已有独立的 Headless Chromium 服务（能力编码
 * {@code message-render}），插件只通过 {@link PluginRenderService} 调用。
 * 这里负责把宿主的失败翻译成可操作的信息——能力未启用、服务不可达、
 * 超时——前端据此引导用户去「能力目录」启用能力并检查服务地址。</p>
 */
public class RenderSupport {
    public static final String CAPABILITY_CODE = "message-render";
    public static final String CAPABILITY_NAME = "消息渲染";
    private static final long DEFAULT_TIMEOUT_SECONDS = 30;

    private final PluginRenderService render;

    public RenderSupport(PluginRenderService render) {
        this.render = render;
    }

    public PluginRenderedImage html(String html, String selector) {
        return html(html, selector, "资源卡片");
    }

    public PluginRenderedImage html(String html, String selector, String scene) {
        return await(() -> render.html(html, selector), scene);
    }

    public PluginRenderedImage html(String html) {
        return await(() -> render.html(html), "资源卡片");
    }

    /**
     * 轻量自检：真的跑一次最小 HTML 渲染，避免只看开关状态。
     */
    public Probe probe() {
        long started = System.nanoTime();
        try {
            PluginRenderedImage image = await(() -> render.html(
                    "<!doctype html><html><head><meta charset=\"utf-8\"></head>"
                            + "<body style=\"margin:0\"><div id=\"probe\" style=\"width:4px;height:4px;background:#34c77b\"></div>"
                            + "</body></html>", "#probe"), "渲染自检");
            long cost = (System.nanoTime() - started) / 1_000_000;
            return new Probe(true, "渲染服务可用", image.width() + "×" + image.height(), cost);
        } catch (EasyBotException exception) {
            long cost = (System.nanoTime() - started) / 1_000_000;
            return new Probe(false, exception.getMessage(), "", cost);
        }
    }

    private PluginRenderedImage await(RenderCall call, String scene) {
        try {
            PluginRenderedImage image = call.get().toCompletableFuture()
                    .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (image == null || image.content() == null || image.content().length == 0) {
                throw EasyBotException.unavailable(CAPABILITY_NAME + "服务返回了空图片（" + scene
                        + "），请检查渲染容器日志");
            }
            return image;
        } catch (TimeoutException exception) {
            throw EasyBotException.unavailable(CAPABILITY_NAME + "服务响应超时（" + scene + "，"
                    + DEFAULT_TIMEOUT_SECONDS + " 秒），渲染容器可能过载或未启动");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw EasyBotException.unavailable("渲染被中断（" + scene + "）");
        } catch (ExecutionException exception) {
            throw translate(rootCause(exception), scene);
        } catch (EasyBotException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw translate(exception, scene);
        }
    }

    private static EasyBotException translate(Throwable error, String scene) {
        String message = error == null ? "" : String.valueOf(error.getMessage());
        String lower = message.toLowerCase();
        if (lower.contains("未启用") || lower.contains("未开启") || lower.contains("disabled")) {
            return EasyBotException.unavailable(CAPABILITY_NAME + "能力未启用。请到「平台 → 能力目录 → "
                    + CAPABILITY_NAME + "」启用后再试（" + scene + "）");
        }
        if (lower.contains("不可用") || lower.contains("unavailable")
                || lower.contains("connection refused") || lower.contains("connect timed out")
                || lower.contains("unknown host") || lower.contains("no route")
                || lower.contains("render-server")) {
            return EasyBotException.unavailable(CAPABILITY_NAME + "服务不可达。请确认渲染容器已启动，"
                    + "且能力配置的服务地址（如 http://render-server:3000）可从后台访问（" + scene + "）");
        }
        String detail = message.isBlank() ? error == null ? "未知错误" : error.getClass().getSimpleName() : message;
        return EasyBotException.unavailable(CAPABILITY_NAME + "渲染失败（" + scene + "）：" + limit(detail));
    }

    private static String limit(String value) {
        return value.length() <= 180 ? value : value.substring(0, 180) + "…";
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null && current.getCause() != current
                && (current instanceof CompletionException || current instanceof ExecutionException)) {
            current = current.getCause();
        }
        return current;
    }

    @FunctionalInterface
    private interface RenderCall {
        java.util.concurrent.CompletionStage<PluginRenderedImage> get();
    }

    public record Probe(boolean ready, String message, String size, long latencyMs) {
    }
}
