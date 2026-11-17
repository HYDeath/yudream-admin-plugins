package online.yudream.plugin.schematic;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.plugin.schematic.application.SchematicParser;
import online.yudream.plugin.schematic.infrastructure.DetailedPreview;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DetailedPreviewTest {
    @Test @EnabledIfSystemProperty(named="schematic.modelRenderer",matches="http://127\\.0\\.0\\.1:[0-9]+")
    void rendersActualModelsTexturesAndAlphaUsingIsolatedBrowser() throws Exception {
        Path output=Path.of("target/model-verification");Files.createDirectories(output);
        String source=System.getProperty("schematic.source");
        byte[] bytes=source==null?NbtFixtures.building():Files.readAllBytes(Path.of(source));
        var model=new SchematicParser().parse(source==null?"house.nbt":Path.of(source).getFileName().toString(),bytes);
        try(var renderer=new DetailedPreview(URI.create(System.getProperty("schematic.modelRenderer")+"/render"))) {
            byte[] png=renderer.render(model);Files.write(output.resolve("preview.png"),png);
            var image=ImageIO.read(new ByteArrayInputStream(png));
            assertEquals(1600,image.getWidth());assertEquals(640,image.getHeight());
            assertEquals(0,image.getRGB(0,0)>>>24);assertEquals(0,image.getRGB(1599,639)>>>24);
            Set<Integer> colors=new HashSet<>();long nonTransparent=0,translucent=0;
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
                int color=image.getRGB(x,y),alpha=color>>>24;
                if(alpha>0){colors.add(color&0xffffff);nonTransparent++;if(alpha<255)translucent++;}
            }
            assertTrue(nonTransparent>10000,"visible pixels="+nonTransparent);
            assertTrue(colors.size()>100,"texture colors="+colors.size());
            new ObjectMapper().writeValue(output.resolve("scene.json").toFile(),Map.of("size",new int[]{model.width(),model.height(),model.length()},"blocks",model.blocks()));
            new ObjectMapper().writeValue(output.resolve("report.json").toFile(),Map.of("blocks",model.blocks().size(),"textureColors",colors.size(),"nonTransparentPixels",nonTransparent,"translucentPixels",translucent));
        }
    }
}
