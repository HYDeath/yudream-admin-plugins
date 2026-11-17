package online.yudream.plugin.schematic;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import java.util.zip.DeflaterOutputStream;

final class NbtFixtures {
    static String mc(String name) { return "minecraft" + ":" + name; }
    static Map<String, Object> state(String name) { return Map.of("Name", mc(name)); }
    static Map<String, Object> xyz(int x, int y, int z) { return Map.of("x", x, "y", y, "z", z); }
    static byte[] encode(Map<String, Object> value) throws IOException {
        var out = new ByteArrayOutputStream(); var data = new DataOutputStream(out); data.writeByte(10); data.writeUTF(""); write(data, value); return out.toByteArray();
    }
    static byte[] gzip(byte[] value) throws IOException { var out = new ByteArrayOutputStream(); try (var zip = new GZIPOutputStream(out)) { zip.write(value); } return out.toByteArray(); }
    static byte[] zlib(byte[] value) throws IOException { var out = new ByteArrayOutputStream(); try (var zip = new DeflaterOutputStream(out)) { zip.write(value); } return out.toByteArray(); }
    static int type(Object v) { return v instanceof Integer ? 3 : v instanceof Long ? 4 : v instanceof String ? 8 : v instanceof List ? 9 : v instanceof Map ? 10 : v instanceof long[] ? 12 : 0; }
    static void write(DataOutputStream out, Object value) throws IOException {
        switch (type(value)) {
            case 3 -> out.writeInt((Integer) value); case 4 -> out.writeLong((Long) value); case 8 -> out.writeUTF((String) value);
            case 9 -> { var list = (List<?>) value; out.writeByte(list.isEmpty() ? 0 : type(list.getFirst())); out.writeInt(list.size()); for (Object v : list) write(out, v); }
            case 10 -> { for (var entry : ((Map<?, ?>) value).entrySet()) { out.writeByte(type(entry.getValue())); out.writeUTF((String) entry.getKey()); write(out, entry.getValue()); } out.writeByte(0); }
            case 12 -> { var array = (long[]) value; out.writeInt(array.length); for (long v : array) out.writeLong(v); }
            default -> throw new IOException("invalid fixture");
        }
    }
    static Map<String, Object> region(int[] dimensions, int[] position, List<Map<String, Object>> palette, int[] indices) {
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        long[] packed = new long[(indices.length * bits + 63) / 64];
        for (int i = 0; i < indices.length; i++) { int bit = i * bits, word = bit / 64, offset = bit % 64; packed[word] |= (long) indices[i] << offset; if (offset + bits > 64) packed[word + 1] |= (long) indices[i] >>> (64 - offset); }
        return Map.of("Size", xyz(dimensions[0], dimensions[1], dimensions[2]), "Position", xyz(position[0], position[1], position[2]), "BlockStatePalette", palette, "BlockStates", packed);
    }
    static byte[] structure() throws IOException { return encode(Map.of("size", List.of(3, 2, 1), "DataVersion", 3839, "palette", List.of(state("air"), state("white_concrete"), state("black_concrete")), "blocks", List.of(Map.of("state", 1, "pos", List.of(0, 0, 0)), Map.of("state", 2, "pos", List.of(1, 0, 0)), Map.of("state", 0, "pos", List.of(2, 0, 0))))); }
    static byte[] building() throws IOException {
        List<Map<String, Object>> blocks = new ArrayList<>();
        int width = 23, height = 16, length = 19;
        for (int y = 0; y < height; y++) for (int z = 0; z < length; z++) for (int x = 0; x < width; x++) {
            int state = -1;
            if (y == 0) state = 0;
            if (y >= 1 && y <= 9 && x >= 3 && x <= 19 && z >= 3 && z <= 15 && (x == 3 || x == 19 || z == 3 || z == 15)) state = (y == 4 || y == 5) && x % 4 != 0 && z % 4 != 0 ? 2 : 1;
            if (y == 10 && x >= 2 && x <= 20 && z >= 2 && z <= 16) state = 3;
            if (y > 10 && Math.abs(x - 11) <= 15 - y && z >= 2 && z <= 16) state = 3;
            if ((x == 3 || x == 19) && (z == 3 || z == 15) && y >= 1 && y <= 10) state = 4;
            if (x >= 10 && x <= 12 && z == 3 && y >= 1 && y <= 3) state = -1;
            if (x == 17 && z == 12 && y >= 11) state = 5;
            if (y == 1 && x == 1 && z == 1) state = 1;
            if (y == 1 && x == 21 && z == 17) state = 5;
            if (state >= 0) blocks.add(Map.of("state", state, "pos", List.of(x, y, z)));
        }
        return gzip(encode(Map.of("size", List.of(width, height, length), "DataVersion", 3839, "palette", List.of(state("grass_block"), state("white_concrete"), state("light_blue_stained_glass"), state("red_terracotta"), state("oak_log"), state("black_concrete")), "blocks", blocks)));
    }
}
