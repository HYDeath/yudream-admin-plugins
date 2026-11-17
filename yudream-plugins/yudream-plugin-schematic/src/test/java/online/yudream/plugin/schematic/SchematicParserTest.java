package online.yudream.plugin.schematic;
import online.yudream.plugin.schematic.application.SchematicParser;
import online.yudream.plugin.schematic.infrastructure.NbtReader;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static online.yudream.plugin.schematic.NbtFixtures.*;

class SchematicParserTest {
    private final SchematicParser parser = new SchematicParser();
    @Test void rawGzipAndZlibStructureFilesExcludeAir() throws Exception {
        byte[] nbt = structure();
        for (byte[] value : List.of(nbt, gzip(nbt), zlib(nbt))) {
            var model = parser.parse("小屋.nbt", value);
            assertEquals(2, model.blocks().size()); assertEquals(6, model.volume()); assertEquals(3, model.width()); assertEquals(3839, model.dataVersion());
            assertEquals(1L, model.materials().get(mc("white_concrete"))); assertFalse(model.materials().containsKey(mc("air")));
        }
    }
    @Test void litematicaReadsContinuousCrossWordBitPackingAndNegativeRegionSize() throws Exception {
        var palette = new ArrayList<Map<String, Object>>(); palette.add(state("air")); for (int i = 1; i < 19; i++) palette.add(state("block_" + i));
        int[] indices = new int[39]; Arrays.fill(indices, 18); indices[12] = 17; indices[13] = 0;
        var root = Map.<String, Object>of("Version", 5, "MinecraftDataVersion", 3839, "Metadata", Map.of("Name", "交错位测试", "Author", "玩家", "TimeCreated", 1720000000000L), "Regions", Map.of("r", region(new int[]{-13, 1, 3}, new int[]{5, 2, -4}, palette, indices)));
        var model = parser.parse("test.litematic", gzip(encode(root)));
        assertEquals(38, model.blocks().size()); assertEquals(13, model.width()); assertEquals(3, model.length()); assertEquals("玩家", model.author()); assertEquals(5, model.formatVersion());
        assertTrue(model.blocks().stream().anyMatch(b -> b.x() == 12 && b.z() == 0 && b.state().equals(mc("block_17"))));
        assertFalse(model.blocks().stream().anyMatch(b -> b.x() == 0 && b.z() == 1));
    }
    @Test void overlappingRegionsResolveToActualBlocksRatherThanTrustingMetadata() throws Exception {
        var palette = List.of(state("air"), state("stone"));
        var region = region(new int[]{1, 1, 1}, new int[]{0, 0, 0}, palette, new int[]{1});
        var root = Map.<String, Object>of("Metadata", Map.of("TotalBlocks", 9999), "Regions", new LinkedHashMap<>(Map.of("a", region, "b", region)));
        assertEquals(1, parser.parse("a.litematic", encode(root)).blocks().size());
    }
    @Test void rejectsWrongExtensionsTruncatedPalettesDimensionsAndHostileNbt() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("file.txt", structure()));
        var root = new LinkedHashMap<String, Object>(); root.put("Regions", Map.of("r", region(new int[]{3, 1, 1}, new int[]{0, 0, 0}, List.of(state("air"), state("stone")), new int[]{1, 3, 1})));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("a.litematic", encode(root)));
        assertThrows(IllegalArgumentException.class, () -> NbtReader.read(new byte[]{10, 0, 0, 12, 0, 0, 127, -1, -1, -1}));
        assertThrows(IllegalArgumentException.class, () -> NbtReader.read(new byte[]{10, 0}));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("a.nbt", encode(Map.of("size", List.of(4096, 4096, 4096), "palette", List.of(state("stone")), "blocks", List.of()))));
    }
}
