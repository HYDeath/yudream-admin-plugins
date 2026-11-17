package online.yudream.plugin.schematic;
import online.yudream.plugin.schematic.application.SchematicParser;
import online.yudream.plugin.schematic.infrastructure.*;
import online.yudream.plugin.schematic.interfaces.SchematicMessages;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PreviewTest {
    @Test void complexSurfaceCoarsensOnlyThePreview() {
        var blocks = new java.util.ArrayList<online.yudream.plugin.schematic.domain.Schematic.Block>();
        for(int x=0;x<32;x++)for(int y=0;y<32;y++)for(int z=0;z<32;z++)if((x+y+z)%2==0)blocks.add(new online.yudream.plugin.schematic.domain.Schematic.Block(x,y,z,NbtFixtures.mc("stone")));
        var model = new online.yudream.plugin.schematic.domain.Schematic("surface","author",0,5,3839,32,32,32,32768,blocks,Map.of());
        String svg = new IsometricScene().svg(model,false);
        assertTrue(svg.contains("data-voxel-step=\"2\"")); assertTrue(svg.length()<1_900_000); assertEquals(16384,model.blocks().size());
    }
    @Test void alphaRecoveryPreservesOpaqueWhiteBlackAndSemitransparentEdges() {
        var black = new BufferedImage(4, 1, BufferedImage.TYPE_INT_RGB); var white = new BufferedImage(4, 1, BufferedImage.TYPE_INT_RGB);
        black.setRGB(0, 0, 0); white.setRGB(0, 0, 0xffffff);
        black.setRGB(1, 0, 0xffffff); white.setRGB(1, 0, 0xffffff);
        black.setRGB(2, 0, 0); white.setRGB(2, 0, 0);
        black.setRGB(3, 0, 0x804020); white.setRGB(3, 0, 0xffbf9f);
        var png = BrowserPreview.unmatte(black, white);
        assertEquals(0, png.getRGB(0, 0) >>> 24); assertEquals(0xffffffff, png.getRGB(1, 0)); assertEquals(0xff000000, png.getRGB(2, 0)); assertEquals(128, png.getRGB(3, 0) >>> 24);
    }
    @Test void twoViewsAreDifferentAndUntrustedNamesNeverEnterHtml() throws Exception {
        var model = new SchematicParser().parse("<script>evil</script>.nbt", NbtFixtures.building());
        var scene = new IsometricScene(); String front = scene.svg(model, false), back = scene.svg(model, true);
        assertNotEquals(front, back); assertFalse(front.contains("script")); assertFalse(front.contains("http://evil")); assertTrue(front.contains("<path"));
    }
    @Test void recognizesMilkyFileSegmentsAndOfficialAttachmentsWithoutFetchingChatUrls() {
        var milky = Map.of("segments", List.of(Map.of("type", "file", "data", Map.of("file_name", "建筑.litematic", "file_id", "qq-file"))));
        assertEquals("qq-file", SchematicMessages.attachments(milky).getFirst().fileId());
        assertEquals("a.nbt", SchematicMessages.attachments(Map.of("attachments", List.of(Map.of("filename", "a.nbt", "url", "https://qq.com/file")))).getFirst().name());
        assertTrue(SchematicMessages.attachments(Map.of("content", "https://example.com/a.nbt")).isEmpty());
        assertTrue(SchematicMessages.attachments(Map.of("file_name", "a.exe", "url", "https://qq.com/file")).isEmpty());
    }
    @Test void downloaderRejectsLocalUrlsAndNonQqOriginsBeforeConnecting() {
        try (var downloader = new QqFileDownload()) {
            for (String url : List.of("http://127.0.0.1/a.nbt", "file:///tmp/a.nbt", "https://qq.com.evil.test/a", "https://qq.com:8443/a", "http://[::1]/")) assertThrows(IllegalArgumentException.class, () -> downloader.download(url));
        }
    }
}
