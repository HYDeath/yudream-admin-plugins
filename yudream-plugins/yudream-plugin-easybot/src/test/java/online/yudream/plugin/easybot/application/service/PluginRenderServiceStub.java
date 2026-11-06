package online.yudream.plugin.easybot.application.service;

import online.yudream.base.plugin.spi.system.render.PluginRenderedImage;
import online.yudream.base.plugin.spi.system.render.PluginRenderService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class PluginRenderServiceStub implements PluginRenderService {
    private final RuntimeException failure;

    PluginRenderServiceStub(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public CompletionStage<PluginRenderedImage> html(String html) {
        return CompletableFuture.failedFuture(failure);
    }

    @Override
    public CompletionStage<PluginRenderedImage> html(String html, String selector) {
        return html(html);
    }

    @Override
    public CompletionStage<PluginRenderedImage> markdown(String markdown) {
        return CompletableFuture.failedFuture(failure);
    }

    @Override
    public CompletionStage<PluginRenderedImage> url(String url) {
        return CompletableFuture.failedFuture(failure);
    }
}
