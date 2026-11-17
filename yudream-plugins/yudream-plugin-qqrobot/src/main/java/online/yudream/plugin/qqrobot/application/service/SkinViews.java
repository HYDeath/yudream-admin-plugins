package online.yudream.plugin.qqrobot.application.service;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;

/** 从原版皮肤 UV 绘制正面和背面；不把正面镜像冒充背面。 */
final class SkinViews {
    private SkinViews() { }

    static String render(String dataUri) {
        if (dataUri == null || !dataUri.startsWith("data:image/") || !dataUri.contains(";base64,")) return "";
        try {
            BufferedImage skin = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(dataUri.substring(dataUri.indexOf(',') + 1))));
            if (skin == null || skin.getWidth() != 64 || (skin.getHeight() != 32 && skin.getHeight() != 64)) return "";
            BufferedImage output = new BufferedImage(64, 34, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = output.createGraphics();
            person(graphics, skin, 4, false);
            person(graphics, skin, 44, true);
            graphics.dispose();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(output, "png", bytes);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (java.io.IOException | IllegalArgumentException error) { return ""; }
    }

    private static void person(Graphics2D graphics, BufferedImage skin, int x, boolean back) {
        part(graphics, skin, x + 4, 0, 8, 8, back ? 24 : 8, 8);
        part(graphics, skin, x + 4, 0, 8, 8, back ? 56 : 40, 8);
        part(graphics, skin, x + 4, 8, 8, 12, back ? 32 : 20, 20);
        part(graphics, skin, x, 8, 4, 12, back ? 52 : 44, 20);
        part(graphics, skin, x + 4, 20, 4, 12, back ? 12 : 4, 20);
        if (skin.getHeight() == 64) {
            part(graphics, skin, x + 12, 8, 4, 12, back ? 44 : 36, 52);
            part(graphics, skin, x + 8, 20, 4, 12, back ? 28 : 20, 52);
            part(graphics, skin, x + 4, 8, 8, 12, back ? 32 : 20, 36);
            part(graphics, skin, x, 8, 4, 12, back ? 52 : 44, 36);
            part(graphics, skin, x + 12, 8, 4, 12, back ? 60 : 52, 52);
            part(graphics, skin, x + 4, 20, 4, 12, back ? 12 : 4, 36);
            part(graphics, skin, x + 8, 20, 4, 12, back ? 12 : 4, 52);
        } else {
            part(graphics, skin, x + 12, 8, 4, 12, back ? 52 : 44, 20);
            part(graphics, skin, x + 8, 20, 4, 12, back ? 12 : 4, 20);
        }
    }

    private static void part(Graphics2D graphics, BufferedImage skin, int x, int y, int width, int height, int u, int v) {
        graphics.drawImage(skin, x, y, x + width, y + height, u, v, u + width, v + height, null);
    }
}
