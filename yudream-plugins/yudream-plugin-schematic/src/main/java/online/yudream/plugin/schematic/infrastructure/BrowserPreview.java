package online.yudream.plugin.schematic.infrastructure;

import online.yudream.base.plugin.spi.system.render.PluginRenderService;
import online.yudream.plugin.schematic.domain.Schematic;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.TimeUnit;

public final class BrowserPreview {
    private final PluginRenderService browser;
    public BrowserPreview(PluginRenderService browser) { this.browser = browser; }
    public byte[] render(Schematic model) throws Exception {
        IsometricScene scene = new IsometricScene();
        // Validate both scenes before spending browser capacity.
        String front = scene.svg(model, false), back = scene.svg(model, true);
        BufferedImage image = new BufferedImage(1200, 420, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { graphics.drawImage(view(front), 0, 0, null); graphics.drawImage(view(back), 600, 0, null); }
        finally { graphics.dispose(); }
        ByteArrayOutputStream output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output); return output.toByteArray();
    }
    private BufferedImage view(String svg) throws Exception {
        // Published SPI has no transparent screenshot option. Dual matte recovers alpha without
        // deleting white/black blocks, including antialiased edges; no chroma-key threshold.
        return unmatte(capture(svg, "#000000"), capture(svg, "#ffffff"));
    }
    private BufferedImage capture(String svg, String background) throws Exception {
        String html = "<!doctype html><meta charset=\"utf-8\"><style>html,body{margin:0}#scene{width:600px;height:420px;background:" + background + "}</style><div id=\"scene\">" + svg + "</div>";
        var result = browser.html(html, "#scene").toCompletableFuture().get(35, TimeUnit.SECONDS);
        if (result == null || result.content() == null) throw new IOException("浏览器未返回渲染图片，请启用消息渲染能力");
        var image = ImageIO.read(new ByteArrayInputStream(result.content()));
        if (image == null || image.getWidth() != 600 || image.getHeight() != 420) throw new IOException("浏览器图片尺寸异常");
        return image;
    }
    public static BufferedImage unmatte(BufferedImage black, BufferedImage white) {
        if (black.getWidth() != white.getWidth() || black.getHeight() != white.getHeight()) throw new IllegalArgumentException("双底图尺寸不一致");
        var result = new BufferedImage(black.getWidth(), black.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < result.getHeight(); y++) for (int x = 0; x < result.getWidth(); x++) {
            int b = black.getRGB(x, y), w = white.getRGB(x, y);
            int delta = 0;
            for (int shift : new int[]{16, 8, 0}) delta += Math.max(0, (w >> shift & 255) - (b >> shift & 255));
            int alpha = Math.max(0, Math.min(255, 255 - Math.round(delta / 3f)));
            int rgb = alpha << 24;
            if (alpha > 0) for (int shift : new int[]{16, 8, 0}) rgb |= Math.min(255, Math.round((b >> shift & 255) * 255f / alpha)) << shift;
            result.setRGB(x, y, rgb);
        }
        return result;
    }
}
