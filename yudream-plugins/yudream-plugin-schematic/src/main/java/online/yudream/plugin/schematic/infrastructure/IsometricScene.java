package online.yudream.plugin.schematic.infrastructure;

import online.yudream.plugin.schematic.domain.Schematic;
import online.yudream.plugin.schematic.domain.Schematic.Block;
import java.util.*;

/** Dependency-free voxel overview. No HTML, scripts or external resource URLs come from NBT. */
public final class IsometricScene {
    private record Cell(int x, int y, int z) { }
    private record Voxel(int x, int y, int z, String state) { }
    public String svg(Schematic model, boolean reverse) {
        for (int step = 1; step <= 512; step *= 2) {
            try { return project(model, reverse, step); } catch (SceneLimit limit) { /* Coarsen the preview only; metadata remains exact. */ }
        }
        throw new IllegalArgumentException("建筑场景过于复杂，请按区域拆分后上传");
    }
    private String project(Schematic model, boolean reverse, int step) {
        int widthBlocks = (model.width() + step - 1) / step, lengthBlocks = (model.length() + step - 1) / step, heightBlocks = (model.height() + step - 1) / step;
        Map<Cell, Block> samples = new HashMap<>();
        for (Block block : model.blocks()) samples.merge(new Cell(block.x() / step, block.y() / step, block.z() / step), block,
                (a, b) -> b.y() > a.y() || b.y() == a.y() && b.state().compareTo(a.state()) < 0 ? b : a);
        List<Voxel> blocks = samples.entrySet().stream().map(entry -> {
            Cell p = entry.getKey(); return new Voxel(reverse ? widthBlocks - 1 - p.x : p.x, p.y, reverse ? lengthBlocks - 1 - p.z : p.z, entry.getValue().state());
        })
                .sorted(Comparator.comparingInt((Voxel b) -> b.x + b.z + b.y).thenComparingInt(Voxel::y)).toList();
        Set<Cell> occupied = new HashSet<>();
        blocks.forEach(b -> occupied.add(new Cell(b.x, b.y, b.z)));
        Map<String, StringBuilder> paths = new LinkedHashMap<>();
        for (Voxel b : blocks) {
            double x = (b.x - b.z) * .8660254, y = (b.x + b.z) * .5 - b.y;
            int color = color(b.state);
            if (!occupied.contains(new Cell(b.x, b.y + 1, b.z))) polygon(paths, shade(color, 1.08), x, y - 1, .8660254, .5, 0, 1, -.8660254, .5);
            if (!occupied.contains(new Cell(b.x + 1, b.y, b.z))) polygon(paths, shade(color, .82), x + .8660254, y - .5, -.8660254, .5, -.8660254, 1.5, 0, 1);
            if (!occupied.contains(new Cell(b.x, b.y, b.z + 1))) polygon(paths, shade(color, .63), x, y, -.8660254, -.5, -.8660254, .5, 0, 1);
        }
        // Preserve painter order: paths may only merge consecutive faces with the same material.
        double left = -lengthBlocks * .8660254, top = -heightBlocks;
        double width = (widthBlocks + lengthBlocks) * .8660254, height = (widthBlocks + lengthBlocks) * .5 + heightBlocks;
        StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" data-voxel-step=\"").append(step).append("\" width=\"600\" height=\"420\" viewBox=\"");
        svg.append(f(left - width * .05)).append(' ').append(f(top - height * .05)).append(' ').append(f(width * 1.1)).append(' ').append(f(height * 1.1)).append("\">");
        paths.forEach((key, path) -> svg.append("<path fill=\"#").append(key.substring(0, 6)).append("\" d=\"").append(path).append("\"/>"));
        svg.append("</svg>");
        if (svg.length() > 1_900_000) throw new SceneLimit();
        return svg.toString();
    }
    private void polygon(Map<String, StringBuilder> paths, String color, double x, double y, double ax, double ay, double bx, double by, double cx, double cy) {
        // A new group per face keeps occlusion correct even when colors repeat later.
        StringBuilder d = new StringBuilder();
        d.append('M').append(f(x)).append(' ').append(f(y)).append('l').append(f(ax)).append(' ').append(f(ay)).append(' ').append(f(bx - ax)).append(' ').append(f(by - ay)).append(' ').append(f(cx - bx)).append(' ').append(f(cy - by)).append('z');
        paths.put(color + ':' + paths.size(), d);
        if (paths.size() > 28_000) throw new SceneLimit();
    }
    private static final class SceneLimit extends RuntimeException { }
    private static String f(double v) { return String.format(Locale.ROOT, "%.2f", v); }
    private static String shade(int rgb, double factor) { int r = Math.min(255, (int) ((rgb >> 16 & 255) * factor)), g = Math.min(255, (int) ((rgb >> 8 & 255) * factor)), b = Math.min(255, (int) ((rgb & 255) * factor)); return String.format("%02x%02x%02x", r, g, b); }
    static int color(String name) {
        String[] names = {"light_blue", "light_gray", "white", "orange", "magenta", "yellow", "lime", "pink", "gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        int[] colors = {0x66b9d8,0xa3a39b,0xe4e4dd,0xd98129,0xb751b7,0xe3cb4a,0x80b82e,0xd995ae,0x51575b,0x298b9d,0x8745af,0x3d55a4,0x775138,0x53752c,0xb54032,0x27282d};
        for (int i = 0; i < names.length; i++) if (name.contains(names[i] + "_")) return colors[i];
        if (name.contains("water") || name.contains("ice")) return 0x538ebc;
        if (name.contains("lava") || name.contains("magma")) return 0xe78628;
        if (name.contains("glass")) return 0xb2d3ce;
        if (name.contains("leaves") || name.contains("grass") || name.contains("moss")) return 0x70954c;
        if (name.contains("sand") || name.contains("birch")) return 0xd7c99a;
        if (name.contains("quartz") || name.contains("snow") || name.contains("calcite")) return 0xe1dfd4;
        if (name.contains("deepslate") || name.contains("coal") || name.contains("obsidian")) return 0x474750;
        if (name.contains("copper")) return 0xb77453;
        if (name.contains("gold")) return 0xe4be4b;
        if (name.contains("iron")) return 0xc4c2bb;
        if (name.contains("brick") || name.contains("granite")) return 0xa77566;
        if (name.contains("dirt") || name.contains("mud")) return 0x89674d;
        if (name.contains("wood") || name.contains("log") || name.contains("planks") || name.contains("fence") || name.contains("chest")) return 0xa48a59;
        return 0x92918b;
    }
}
