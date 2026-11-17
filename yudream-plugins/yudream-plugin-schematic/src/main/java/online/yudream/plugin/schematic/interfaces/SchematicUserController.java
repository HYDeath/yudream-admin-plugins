package online.yudream.plugin.schematic.interfaces;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.*;
import online.yudream.plugin.schematic.bootstrap.SchematicPlugin;
public final class SchematicUserController {
    private final SchematicHttp http;
    public SchematicUserController(SchematicHttp http) { this.http = http; }
    @PluginHttpEndpoint(method="POST", path="/me/jobs", permission=SchematicPlugin.USE) public PluginHttpResponse upload(PluginHttpRequest r) { return http.upload(r); }
    @PluginHttpEndpoint(method="GET", path="/me/jobs", permission=SchematicPlugin.USE) public PluginHttpResponse page(PluginHttpRequest r) { return http.page(r, false); }
    @PluginHttpEndpoint(method="GET", path="/me/jobs/{id}", permission=SchematicPlugin.USE) public PluginHttpResponse detail(PluginHttpRequest r) { return http.detail(r, false); }
    @PluginHttpEndpoint(method="DELETE", path="/me/jobs/{id}", permission=SchematicPlugin.USE) public PluginHttpResponse delete(PluginHttpRequest r) { return http.delete(r, false); }
}
