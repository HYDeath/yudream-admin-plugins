package online.yudream.plugin.schematic.interfaces;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.*;
import online.yudream.plugin.schematic.bootstrap.SchematicPlugin;
public final class SchematicAdminController {
    private final SchematicHttp http;
    public SchematicAdminController(SchematicHttp http) { this.http = http; }
    @PluginHttpEndpoint(method="GET", path="/admin/jobs", permission=SchematicPlugin.MANAGE) public PluginHttpResponse page(PluginHttpRequest r) { return http.page(r, true); }
    @PluginHttpEndpoint(method="GET", path="/admin/jobs/{id}", permission=SchematicPlugin.MANAGE) public PluginHttpResponse detail(PluginHttpRequest r) { return http.detail(r, true); }
    @PluginHttpEndpoint(method="DELETE", path="/admin/jobs/{id}", permission=SchematicPlugin.MANAGE) public PluginHttpResponse delete(PluginHttpRequest r) { return http.delete(r, true); }
}
