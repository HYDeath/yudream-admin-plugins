package online.yudream.plugin.schematic.interfaces;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.*;
public final class SchematicPublicController {
    private final SchematicHttp http;
    public SchematicPublicController(SchematicHttp http) { this.http = http; }
    @PluginHttpEndpoint(method="GET", path="/public/shared/{token}") public PluginHttpResponse shared(PluginHttpRequest r) { return http.shared(r); }
    @PluginHttpEndpoint(method="GET", path="/public/previews/{token}", wrapResult=false) public PluginHttpResponse preview(PluginHttpRequest r) { return http.preview(r); }
}
