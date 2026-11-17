package online.yudream.plugin.schematic.domain;

import java.util.List;
import java.util.Map;

/** Coordinates are normalized across regions; air is omitted, overlapping cells are resolved. */
public record Schematic(String name, String author, long createdAt, int formatVersion, int dataVersion,
                        int width, int height, int length, long volume, List<Block> blocks,
                        Map<String, Long> materials) {
    public record Block(int x, int y, int z, String state) { }
}
