package io.mark.hdminimap.render;

public final class MinimapRenderUtils {

    public static int blend(int baseColor, int blendColor) {
        int saturation = (baseColor & 127) * blendColor >> 7;
        saturation = Math.max(2, saturation);
        saturation = Math.min(126, saturation);
        return (baseColor & 0xFF80) + saturation;
    }
}