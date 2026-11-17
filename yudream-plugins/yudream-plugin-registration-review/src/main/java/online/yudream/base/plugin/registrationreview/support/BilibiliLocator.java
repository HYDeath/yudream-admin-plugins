package online.yudream.base.plugin.registrationreview.support;

import java.util.Optional;
import online.yudream.base.plugin.bilibilitriple.api.BilibiliTripleApi;
import online.yudream.base.plugin.spi.core.PluginContext;

/** Optional provider types are resolved only when the declared dependency is enabled. */
public final class BilibiliLocator {
    private BilibiliLocator() { }
    public record Proof(String mid, String bvid, long verifiedAt) { }
    public static boolean available(PluginContext context) {
        if (!context.dependencyAvailable("bilibili-triple")) return false;
        try { return context.service("bilibili-triple", BilibiliTripleApi.class).map(BilibiliTripleApi::enabled).orElse(false); }
        catch (LinkageError e) { return false; }
    }
    public static Optional<Proof> evidence(PluginContext context, String userId) {
        if (!available(context)) return Optional.empty();
        return context.service("bilibili-triple", BilibiliTripleApi.class).flatMap(api -> api.evidence(userId))
                .map(e -> new Proof(e.mid(), e.bvid(), e.verifiedAt()));
    }
}
