package io.mark.hdminimap.utils;

import net.runelite.api.IndexedSprite;

import java.awt.image.BufferedImage;

public class ImageUtils {

    public static BufferedImage toBufferedImage(IndexedSprite indexedSprite) {
        int width = indexedSprite.getWidth();
        int height = indexedSprite.getHeight();

        if (width <= 0 || height <= 0) {
            return null;
        }

        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] palette = indexedSprite.getPalette();
        byte[] pixels = indexedSprite.getPixels();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int i = x + y * width;
                int colour = palette[pixels[i] & 0xFF];
                if (colour != 0) {
                    bi.setRGB(x, y, 0xFF000000 | colour);
                }
            }
        }

        return bi;
    }
}
