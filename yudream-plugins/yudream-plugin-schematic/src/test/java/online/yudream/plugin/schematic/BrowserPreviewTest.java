package online.yudream.plugin.schematic;
import online.yudream.base.plugin.spi.system.render.*;
import online.yudream.plugin.schematic.application.SchematicParser;
import online.yudream.plugin.schematic.infrastructure.BrowserPreview;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class BrowserPreviewTest {
    @Test void dualMatteKeepsSolidBlackWhiteAndAntialiasedTransparency() {
        var black = new BufferedImage(4, 1, BufferedImage.TYPE_INT_RGB); var white = new BufferedImage(4, 1, BufferedImage.TYPE_INT_RGB);
        // Transparent pixel, opaque white, opaque black, half-alpha red.
        int[] b = {0, 0xffffff, 0, 0x800000}, w = {0xffffff, 0xffffff, 0, 0xff7f7f};
        for (int x = 0; x < 4; x++) { black.setRGB(x,0,b[x]); white.setRGB(x,0,w[x]); }
        var result = BrowserPreview.unmatte(black, white);
        assertEquals(0, result.getRGB(0,0)); assertEquals(0xffffffff, result.getRGB(1,0)); assertEquals(0xff000000, result.getRGB(2,0)); assertEquals(0x80ff0000, result.getRGB(3,0));
    }
    @Test @EnabledIfSystemProperty(named="schematic.browser", matches="http://127\\.0\\.0\\.1:[0-9]+")
    void rendersRealBrowserPngAndExportsAHouseFixture() throws Exception {
        var json = new ObjectMapper(); var http = HttpClient.newHttpClient();
        PluginRenderService renderer = new PluginRenderService() {
            public CompletionStage<PluginRenderedImage> html(String html) { return html(html, "#scene"); }
            public CompletionStage<PluginRenderedImage> html(String html, String selector) {
                try {
                    var request = HttpRequest.newBuilder(URI.create(System.getProperty("schematic.browser") + "/render")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("html",html,"selector",selector)))).build();
                    return http.sendAsync(request,HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> { assertEquals(200,response.statusCode()); return new PluginRenderedImage("image/png",response.body(),600,420); });
                } catch(Exception e) { return CompletableFuture.failedFuture(e); }
            }
            public CompletionStage<PluginRenderedImage> markdown(String markdown) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
            public CompletionStage<PluginRenderedImage> url(String url) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
        };
        byte[] fixture = NbtFixtures.building(); var model = new SchematicParser().parse("透明双视角小屋.nbt", fixture);
        byte[] png = new BrowserPreview(renderer).render(model);
        var image = ImageIO.read(new ByteArrayInputStream(png)); assertEquals(1200,image.getWidth()); assertEquals(420,image.getHeight()); assertEquals(0,image.getRGB(0,0) >>> 24);
        long transparent = 0, opaque = 0, white = 0, black = 0;
        for (int y=0;y<420;y++) for(int x=0;x<1200;x++) {
            int color=image.getRGB(x,y), alpha=color >>> 24;
            if(alpha==0)transparent++; if(alpha==255) { opaque++; if((color&0xffffff)>0xdddddd)white++; if((color&0xffffff)<0x303030)black++; }
        }
        assertTrue(transparent>200_000, "transparent="+transparent); assertTrue(opaque>10_000,"opaque="+opaque); assertTrue(white>20,"white="+white); assertTrue(black>20,"black="+black);
        Path target=Path.of("target","browser-verification"); java.nio.file.Files.createDirectories(target);
        java.nio.file.Files.write(target.resolve("house.nbt"),fixture); java.nio.file.Files.write(target.resolve("house.png"),png);
        java.nio.file.Files.writeString(target.resolve("metadata.json"),json.writeValueAsString(Map.of("blocks",model.blocks().size(),"width",model.width(),"height",model.height(),"length",model.length(),"dataVersion",model.dataVersion(),"transparentPixels",transparent,"opaquePixels",opaque)));
        http.close();
    }
}
