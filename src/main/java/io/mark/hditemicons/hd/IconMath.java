package io.mark.hditemicons.hd;


final class IconMath {

	static final double TURN = 2048;

	static int floorToInt(double v) {
		return (int) Math.floor(v);
	}

	static int ceilToInt(double v) {
		return (int) Math.ceil(v);
	}

	static int clampInt(int v, int lo, int hi) {
		return v < lo ? lo : Math.min(v, hi);
	}

	static double clampDouble(double v, double lo, double hi) {
		return v < lo ? lo : Math.min(v, hi);
	}

	static int wrap(int v, int modulus) {
		int r = v % modulus;
		return r < 0 ? r + modulus : r;
	}

	/**
	 * Packed-HSL layout: bits 15-10 hue (6 bits), 9-7 saturation (3 bits), 6-0 lightness
	 * (7 bits). Each band is offset by half a quantisation step so it decodes to the centre of
	 * the band rather than its low edge. Returns linear-order sRGB components in 0..1.
	 */
	static double[] decodeHsl(int packed) {
		double hue = ((packed >>> 10) & 0x3F) / 64.0 + 1.0 / 128;
		double sat = ((packed >>> 7) & 0x7) / 8.0 + 1.0 / 16;
		double lum = (packed & 0x7F) / 128.0;

		double chroma = sat * (1 - Math.abs(2 * lum - 1));
		double h6 = (hue - Math.floor(hue)) * 6;
		double mid = lum - chroma / 2;

		double r = clampDouble(Math.abs(h6 - 3) - 1, 0, 1) * chroma + mid;
		double g = clampDouble(2 - Math.abs(h6 - 2), 0, 1) * chroma + mid;
		double b = clampDouble(2 - Math.abs(h6 - 4), 0, 1) * chroma + mid;
		return new double[]{r, g, b};
	}
}
