package online.yudream.plugin.schematic.infrastructure;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/** Big-endian Java NBT, including gzip/zlib. All lengths, nesting and decompressed bytes are bounded. */
public final class NbtReader {
    public static final int MAX_FILE = 16 * 1024 * 1024;
    private static final int MAX_EXPANDED = 96 * 1024 * 1024;
    private static final int MAX_ELEMENTS = 4_000_000;
    private int elements;
    private final DataInputStream input;

    private NbtReader(InputStream source) {
        input = new DataInputStream(new FilterInputStream(source) {
            private int remaining = MAX_EXPANDED;
            @Override public int read() throws IOException {
                if (--remaining < 0) throw new IOException("NBT 解压后超过 96 MiB");
                return super.read();
            }
            @Override public int read(byte[] b, int off, int len) throws IOException {
                int count = super.read(b, off, Math.min(len, Math.max(1, remaining)));
                if (count > 0 && (remaining -= count) < 0) throw new IOException("NBT 解压后超过 96 MiB");
                return count;
            }
        });
    }

    public static Map<String, Object> read(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_FILE) throw new IllegalArgumentException("文件须为 1 字节至 16 MiB");
        try {
            InputStream source = new ByteArrayInputStream(bytes);
            if (bytes.length > 1 && (bytes[0] & 255) == 31 && (bytes[1] & 255) == 139) source = new GZIPInputStream(source);
            else if (bytes.length > 1 && (bytes[0] & 15) == 8 && (((bytes[0] & 255) << 8) + (bytes[1] & 255)) % 31 == 0) source = new InflaterInputStream(source);
            try (var reader = new DataInputStream(source)) {
                var nbt = new NbtReader(reader);
                if (nbt.input.readUnsignedByte() != 10) throw new IOException("根节点必须为 Compound");
                nbt.input.readUTF();
                return compound(nbt.tag(10, 0));
            }
        } catch (IOException | ClassCastException e) { throw new IllegalArgumentException("无法读取 Java NBT：" + e.getMessage(), e); }
    }

    private int length(int bytesPerElement) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_EXPANDED / bytesPerElement) throw new IOException("无效的 NBT 长度");
        return length;
    }

    private Object tag(int type, int depth) throws IOException {
        if (depth > 48 || ++elements > MAX_ELEMENTS) throw new IOException("NBT 结构超过限制");
        return switch (type) {
            case 1 -> input.readByte(); case 2 -> input.readShort(); case 3 -> input.readInt();
            case 4 -> input.readLong(); case 5 -> input.readFloat(); case 6 -> input.readDouble();
            case 7 -> { byte[] data = new byte[length(1)]; input.readFully(data); yield data; }
            case 8 -> input.readUTF();
            case 9 -> {
                int child = input.readUnsignedByte(), count = length(1);
                if (count > MAX_ELEMENTS - elements || (child == 0 && count > 0)) throw new IOException("NBT 列表过大或类型无效");
                List<Object> list = new ArrayList<>(Math.min(count, 4096));
                for (int i = 0; i < count; i++) list.add(tag(child, depth + 1));
                yield list;
            }
            case 10 -> {
                Map<String, Object> map = new LinkedHashMap<>();
                int child;
                while ((child = input.readUnsignedByte()) != 0) { String name = input.readUTF(); map.put(name, tag(child, depth + 1)); }
                yield map;
            }
            case 11 -> { int[] data = new int[length(4)]; for (int i = 0; i < data.length; i++) data[i] = input.readInt(); yield data; }
            case 12 -> { long[] data = new long[length(8)]; for (int i = 0; i < data.length; i++) data[i] = input.readLong(); yield data; }
            default -> throw new IOException("未知 NBT 标签：" + type);
        };
    }
    @SuppressWarnings("unchecked") public static Map<String, Object> compound(Object value) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("缺少 Compound");
        return (Map<String, Object>) value;
    }
}
