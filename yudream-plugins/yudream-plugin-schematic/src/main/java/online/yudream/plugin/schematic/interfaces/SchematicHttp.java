package online.yudream.plugin.schematic.interfaces;

import online.yudream.base.plugin.spi.http.*;
import online.yudream.plugin.schematic.application.SchematicJobs;
import online.yudream.plugin.schematic.application.SchematicAdmission;
import java.util.*;
import java.util.function.Supplier;

public final class SchematicHttp {
    private final SchematicJobs jobs;
    private final SchematicAdmission admission;
    public SchematicHttp(SchematicJobs jobs, SchematicAdmission admission) { this.jobs = jobs; this.admission = admission; }
    public PluginHttpResponse upload(PluginHttpRequest request) {
        return call(() -> {
            String owner = owner(request); admission.require(owner); var file = request.parts().get("file");
            if (file == null || !file.isFile()) throw new IllegalArgumentException("请选择投影文件");
            return PluginHttpResponse.ok(jobs.submit(owner, file.filename(), file.data(), null));
        });
    }
    public PluginHttpResponse page(PluginHttpRequest request, boolean admin) { return call(() -> PluginHttpResponse.ok(jobs.page(owner(request), admin, integer(request, "page", 1), integer(request, "size", 12)))); }
    public PluginHttpResponse detail(PluginHttpRequest request, boolean admin) { return call(() -> PluginHttpResponse.ok(jobs.get(owner(request), last(request), admin))); }
    public PluginHttpResponse delete(PluginHttpRequest request, boolean admin) { return call(() -> { jobs.delete(owner(request), last(request), admin); return PluginHttpResponse.ok(Map.of("deleted", true)); }); }
    public PluginHttpResponse shared(PluginHttpRequest request) { return call(() -> PluginHttpResponse.ok(jobs.shared(last(request)))); }
    public PluginHttpResponse preview(PluginHttpRequest request) {
        return call(() -> new PluginHttpResponse(200, Map.of("Cache-Control", "private, no-store", "X-Content-Type-Options", "nosniff"), "image/png", jobs.preview(last(request).replaceFirst("\\.png$", "")), false));
    }
    private static PluginHttpResponse call(Supplier<PluginHttpResponse> action) {
        try { return action.get(); }
        catch (SecurityException e) { return PluginHttpResponse.rawJson(401, Map.of("message", "请先登录后使用建筑投影")); }
        catch (SchematicAdmission.Rejected e) { return PluginHttpResponse.rawJson(403, Map.of("message", e.getMessage())); }
        catch (NoSuchElementException e) { return PluginHttpResponse.rawJson(404, Map.of("message", "投影不存在或已删除")); }
        catch (IllegalArgumentException e) { return PluginHttpResponse.rawJson(400, Map.of("message", e.getMessage())); }
    }
    private static String owner(PluginHttpRequest request) { if (request.principal() == null || request.principal().userId() == null) throw new SecurityException(); return "user:" + request.principal().userId(); }
    private static String last(PluginHttpRequest request) { String path = request.path(); return path.substring(path.lastIndexOf('/') + 1); }
    private static int integer(PluginHttpRequest request, String key, int fallback) { try { return Integer.parseInt(request.query().get(key).getFirst()); } catch (RuntimeException e) { return fallback; } }
}
