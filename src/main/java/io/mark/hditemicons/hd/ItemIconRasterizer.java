package io.mark.hditemicons.hd;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.runelite.api.Model;
import net.runelite.api.TextureProvider;

import static io.mark.hditemicons.hd.IconMath.ceilToInt;
import static io.mark.hditemicons.hd.IconMath.clampInt;
import static io.mark.hditemicons.hd.IconMath.decodeHsl;
import static io.mark.hditemicons.hd.IconMath.floorToInt;
import static io.mark.hditemicons.hd.IconMath.wrap;

class ItemIconRasterizer {
	static final int ICON_WIDTH = 36;
	static final int ICON_HEIGHT = 32;

	static final int PROJECTION_SCALE = 512;
	private static final int PROJECTION_ORIGIN = 16;
	private static final int FIT_ITERATIONS = 10;
	private static final double FIT_TOLERANCE = 0.001;
	private static final int MAX_MISMATCHED_PIXELS = 3;
	private static final double MAX_MISMATCHED_FRACTION = 0.1;
	private static final int MAX_CHANNEL_ERROR = 32;

	/**
	 * A snapshot of everything the rasterizer needs from a {@link Model}, copied up front so
	 * rendering can happen off the client thread.
	 */
	static final class IconModel {
		final float[] vertexX, vertexY, vertexZ;
		final int[] triangleA, triangleB, triangleC;
		final int[] shadeA, shadeB, shadeC;
		@Nullable final byte[] alpha;
		@Nullable final byte[] paintLayer;
		@Nullable final int[][] texelsByFace;
		@Nullable final int[] uvA, uvB, uvC;

		private IconModel(float[] vertexX, float[] vertexY, float[] vertexZ,
						int[] triangleA, int[] triangleB, int[] triangleC,
						int[] shadeA, int[] shadeB, int[] shadeC,
						@Nullable byte[] alpha, @Nullable byte[] paintLayer,
						@Nullable int[][] texelsByFace, @Nullable int[] uvA, @Nullable int[] uvB, @Nullable int[] uvC) {
			this.vertexX = vertexX;
			this.vertexY = vertexY;
			this.vertexZ = vertexZ;
			this.triangleA = triangleA;
			this.triangleB = triangleB;
			this.triangleC = triangleC;
			this.shadeA = shadeA;
			this.shadeB = shadeB;
			this.shadeC = shadeC;
			this.alpha = alpha;
			this.paintLayer = paintLayer;
			this.texelsByFace = texelsByFace;
			this.uvA = uvA;
			this.uvB = uvB;
			this.uvC = uvC;
		}

		/**
		 * Client thread only.
		 */
		@Nullable
		static IconModel capture(Model model, TextureProvider textures) {
			int faceCount = model.getFaceCount();
			int[][] texelsByFace = null;
			int[] uvA = null, uvB = null, uvC = null;
			short[] faceTextures = model.getFaceTextures();
			if (faceTextures != null) {
				texelsByFace = new int[faceCount][];
				uvA = Arrays.copyOf(model.getFaceIndices1(), faceCount);
				uvB = Arrays.copyOf(model.getFaceIndices2(), faceCount);
				uvC = Arrays.copyOf(model.getFaceIndices3(), faceCount);
				byte[] textureFaces = model.getTextureFaces();
				Map<Integer, int[]> loadedTextures = new HashMap<>();
				for (int f = 0; f < faceCount; f++) {
					if (faceTextures[f] == -1)
						continue;
					int textureId = faceTextures[f];
					int[] texels = loadedTextures.computeIfAbsent(textureId,
						id -> {
							int[] loaded = textures.load(id);
							return loaded == null ? null : loaded.clone();
						});
					if (texels == null)
						return null;
					texelsByFace[f] = texels;
					if (textureFaces != null && textureFaces[f] != -1) {
						int t = textureFaces[f] & 0xFF;
						uvA[f] = model.getTexIndices1()[t];
						uvB[f] = model.getTexIndices2()[t];
						uvC[f] = model.getTexIndices3()[t];
					}
				}
			}

			int vertexCount = model.getVerticesCount();
			return new IconModel(
				Arrays.copyOf(model.getVerticesX(), vertexCount),
				Arrays.copyOf(model.getVerticesY(), vertexCount),
				Arrays.copyOf(model.getVerticesZ(), vertexCount),
				Arrays.copyOf(model.getFaceIndices1(), faceCount),
				Arrays.copyOf(model.getFaceIndices2(), faceCount),
				Arrays.copyOf(model.getFaceIndices3(), faceCount),
				Arrays.copyOf(model.getFaceColors1(), faceCount),
				Arrays.copyOf(model.getFaceColors2(), faceCount),
				Arrays.copyOf(model.getFaceColors3(), faceCount),
				model.getFaceTransparencies() == null ? null : Arrays.copyOf(model.getFaceTransparencies(), faceCount),
				model.getFaceRenderPriorities() == null ? null : Arrays.copyOf(model.getFaceRenderPriorities(), faceCount),
				texelsByFace, uvA, uvB, uvC
			);
		}
	}

	private final IconModel model;
	private final int supersample;
	private final double[] posX, posY, posZ;
	private double centerOffsetX, centerOffsetY, cameraDistance;
	// The baseline every camera placement starts from, before any pan is layered on top.
	private final double naturalOffsetX, naturalOffsetY;

	// Scratch buffers reused across the many renderSamples() calls a single fit performs
	// (up to FIT_ITERATIONS wide renders plus the final render), to avoid re-allocating
	// vertex-sized and face-sized arrays on every call.
	private final double[] camX, camY, camZ, screenX, screenY;
	private final long[] sortKeysScratch;
	private int[] samplesScratch;

	private static final int UNITY_RESIZE = 128;

	/**
	 * @param supersample how many samples per axis (so {@code supersample * supersample} samples
	 *                    per pixel) to render before downsampling - the plugin's configurable
	 *                    icon quality setting.
	 */
	ItemIconRasterizer(IconModel model, int pitchJau, int yawJau, int rollJau, int supersample) {
		this(model, pitchJau, yawJau, rollJau, UNITY_RESIZE, UNITY_RESIZE, UNITY_RESIZE, supersample);
	}

	/**
	 * @param resizeX128 per-axis model scale in the cache's resizeX/Y/Z units, 128 being 100%.
	 */
	ItemIconRasterizer(IconModel model, int pitchJau, int yawJau, int rollJau,
						double resizeX128, double resizeY128, double resizeZ128, int supersample) {
		this.model = model;
		this.supersample = supersample;
		int vertexCount = model.vertexX.length;
		posX = new double[vertexCount];
		posY = new double[vertexCount];
		posZ = new double[vertexCount];

		double roll = rollJau / IconMath.TURN * 2 * Math.PI;
		double yaw = yawJau / IconMath.TURN * 2 * Math.PI;
		double pitch = pitchJau / IconMath.TURN * 2 * Math.PI;
		double resizeX = resizeX128 / UNITY_RESIZE;
		double resizeY = resizeY128 / UNITY_RESIZE;
		double resizeZ = resizeZ128 / UNITY_RESIZE;

		// Resize applies in local space, before rotation, the same order the client uses
		for (int i = 0; i < vertexCount; i++) {
			double x = model.vertexX[i] * resizeX;
			double y = model.vertexY[i] * resizeY;
			double z = model.vertexZ[i] * resizeZ;

			double[] step1 = rotate(x, y, -roll);
			x = step1[0];
			y = step1[1];

			double[] step2 = rotate(x, z, -yaw);
			x = step2[0];
			z = step2[1];

			double[] step3 = rotate(y, z, pitch);
			y = step3[0];
			z = step3[1];

			posX[i] = x;
			posY[i] = y;
			posZ[i] = z;
		}

		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
		for (int i = 0; i < vertexCount; i++) {
			minX = Math.min(minX, posX[i]);
			minY = Math.min(minY, posY[i]);
			maxX = Math.max(maxX, posX[i]);
			maxY = Math.max(maxY, posY[i]);
		}
		naturalOffsetX = -(minX + maxX) / 2;
		naturalOffsetY = -(minY + maxY) / 2;

		camX = new double[vertexCount];
		camY = new double[vertexCount];
		camZ = new double[vertexCount];
		screenX = new double[vertexCount];
		screenY = new double[vertexCount];
		sortKeysScratch = new long[model.triangleA.length];
	}

	private static double[] rotate(double u, double v, double angle) {
		double s = Math.sin(angle), c = Math.cos(angle);
		return new double[]{u * c - v * s, u * s + v * c};
	}

	/**
	 * Places the camera explicitly instead of fitting it to a reference icon. {@code offsetX}/
	 * {@code offsetY} are xOffset2d/yOffset2d-style nudges on top of the model's own centering,
	 * not the whole pan - the cache's values are only ever a few units off centre.
	 */
	void placeExplicitly(double cameraDistance, double offsetX, double offsetY) {
		this.cameraDistance = cameraDistance;
		this.centerOffsetX = naturalOffsetX + offsetX;
		this.centerOffsetY = naturalOffsetY + offsetY;
	}

	/**
	 * Solves for a camera distance and 2D offset whose rendered silhouette matches
	 * {@code reference} (the game's own border-less icon pixels for the same item), then
	 * confirms the match is close enough to trust. Returns false if no plausible fit is found.
	 */
	boolean fitToReferenceSilhouette(int[] reference, int[] palette) {
		double[] targetCoverage = new double[reference.length];
		for (int i = 0; i < reference.length; i++)
			targetCoverage[i] = reference[i] == 0 ? 0 : 1;
		Silhouette target = measureSilhouette(targetCoverage, ICON_WIDTH, 0, 0);
		if (target.coverage == 0)
			return false;

		double maxRadius = 0;
		for (int i = 0; i < posX.length; i++)
			maxRadius = Math.max(maxRadius, Math.sqrt(posX[i] * posX[i] + posY[i] * posY[i] + posZ[i] * posZ[i]));

		// Fitted by spread (the silhouette's radius of gyration) rather than raw area, since
		// thin shapes render thicker in the game's icons than their true silhouette area implies.
		cameraDistance = maxRadius + PROJECTION_SCALE * maxRadius / Math.sqrt(target.coverage / Math.PI);
		centerOffsetX = naturalOffsetX;
		centerOffsetY = naturalOffsetY;

		for (int pass = 0; pass < FIT_ITERATIONS; pass++) {
			int[] wide = renderSamples(cameraDistance, 1, 1, -ICON_WIDTH, -ICON_HEIGHT, ICON_WIDTH * 3, ICON_HEIGHT * 3, 0, palette);
			double[] wideCoverage = new double[wide.length];
			for (int i = 0; i < wide.length; i++)
				wideCoverage[i] = (wide[i] >>> 24) / 255.0;
			Silhouette current = measureSilhouette(wideCoverage, ICON_WIDTH * 3, -ICON_WIDTH, -ICON_HEIGHT);
			if (current.coverage == 0)
				return false;

			double scale = current.spread / target.spread;
			cameraDistance *= scale;
			centerOffsetX += (target.centerX - current.centerX) * cameraDistance / PROJECTION_SCALE;
			centerOffsetY += (target.centerY - current.centerY) * cameraDistance / PROJECTION_SCALE;
			if (cameraDistance <= maxRadius)
				return false;

			boolean converged = Math.abs(scale - 1) < FIT_TOLERANCE
				&& Math.abs(target.centerX - current.centerX) < FIT_TOLERANCE * 10
				&& Math.abs(target.centerY - current.centerY) < FIT_TOLERANCE * 10;
			if (converged)
				break;
		}

		int[] fitted = renderSamples(cameraDistance, 1, 1, 0, 0, ICON_WIDTH, ICON_HEIGHT, 0, palette);
		return verifiesAgainst(fitted, reference, target.coverage);
	}

	private static final class Silhouette {
		final double coverage, centerX, centerY, spread;

		Silhouette(double coverage, double centerX, double centerY, double spread) {
			this.coverage = coverage;
			this.centerX = centerX;
			this.centerY = centerY;
			this.spread = spread;
		}
	}

	private static Silhouette measureSilhouette(double[] coverage, int width, int left, int top) {
		double area = 0, momentX = 0, momentY = 0, momentR2 = 0;
		for (int i = 0; i < coverage.length; i++) {
			double px = i % width + left + 0.5;
			double py = i / width + top + 0.5;
			area += coverage[i];
			momentX += coverage[i] * px;
			momentY += coverage[i] * py;
			momentR2 += coverage[i] * (px * px + py * py);
		}
		if (area == 0)
			return new Silhouette(0, 0, 0, 0);
		double centerX = momentX / area;
		double centerY = momentY / area;
		double variance = Math.max(0, momentR2 / area - centerX * centerX - centerY * centerY);
		return new Silhouette(area, centerX, centerY, Math.sqrt(variance));
	}

	/**
	 * Confirms a solved camera placement is actually a good match: few disagreeing pixels
	 * (allowing for antialiasing noise near edges) and similar average colour.
	 */
	private boolean verifiesAgainst(int[] fitted, int[] reference, double referenceCoverage) {
		boolean[] weDrew = new boolean[fitted.length];
		boolean[] weDrewFaintly = new boolean[fitted.length];
		boolean[] theyDrew = new boolean[fitted.length];
		for (int i = 0; i < fitted.length; i++) {
			weDrew[i] = fitted[i] >>> 24 >= 128;
			weDrewFaintly[i] = fitted[i] >>> 24 > 0;
			theyDrew[i] = reference[i] != 0;
		}

		int disagreements = 0;
		double ourCoverage = 0;
		double[] ourColor = new double[3];
		double[] theirColor = new double[3];
		for (int i = 0; i < fitted.length; i++) {
			if (theyDrew[i] && !hasNearbyMark(weDrewFaintly, i) || weDrew[i] && !hasNearbyMark(theyDrew, i))
				disagreements++;
			ourCoverage += (fitted[i] >>> 24) / 255.0;
			for (int c = 0; c < 3; c++) {
				ourColor[c] += fitted[i] >> c * 8 & 0xFF;
				if (theyDrew[i])
					theirColor[c] += reference[i] >> c * 8 & 0xFF;
			}
		}

		if (disagreements > Math.max(MAX_MISMATCHED_PIXELS, MAX_MISMATCHED_FRACTION * referenceCoverage))
			return false;
		for (int c = 0; c < 3; c++)
			if (Math.abs(ourColor[c] / ourCoverage - theirColor[c] / referenceCoverage) > MAX_CHANNEL_ERROR)
				return false;
		return true;
	}

	private static boolean hasNearbyMark(boolean[] mask, int index) {
		int x = index % ICON_WIDTH;
		int y = index / ICON_WIDTH;
		for (int ny = Math.max(0, y - 2); ny <= Math.min(ICON_HEIGHT - 1, y + 2); ny++)
			for (int nx = Math.max(0, x - 2); nx <= Math.min(ICON_WIDTH - 1, x + 2); nx++)
				if (mask[ny * ICON_WIDTH + nx])
					return true;
		return false;
	}

	/**
	 * Renders the fitted camera placement into a border-margin-sized icon buffer.
	 */
	int[] render(int marginPixels, int outlineWidth, int[] palette) {
		double distance = outlineWidth == 2 ? cameraDistance * 1.04 : cameraDistance;
		int width = ICON_WIDTH + 2 * marginPixels;
		int height = ICON_HEIGHT + 2 * marginPixels;
		return renderSamples(distance, 1, 1, -marginPixels, -marginPixels, width, height, outlineWidth, palette);
	}

	/**
	 * Alpha-composites {@code top} over {@code bottom}, both straight (non-premultiplied) ARGB.
	 */
	static int[] compositeOver(int[] top, int[] bottom) {
		int[] result = new int[top.length];
		for (int i = 0; i < top.length; i++) {
			int remaining = 255 - (top[i] >>> 24);
			for (int shift = 0; shift < 32; shift += 8) {
				int blended = (top[i] >>> shift & 0xFF) + ((bottom[i] >>> shift & 0xFF) * remaining + 127) / 255;
				result[i] |= Math.min(255, blended) << shift;
			}
		}
		return result;
	}

	private int[] renderSamples(double distance, double scaleX, double scaleY, double left, double top,
								int width, int height, int outlineWidth, int[] palette) {
		int vertexCount = posX.length;
		for (int i = 0; i < vertexCount; i++) {
			camX[i] = posX[i] + centerOffsetX;
			camY[i] = posY[i] + centerOffsetY;
			camZ[i] = posZ[i] + distance;
			screenX[i] = (PROJECTION_ORIGIN + PROJECTION_SCALE * camX[i] / camZ[i] - left) * scaleX * supersample;
			screenY[i] = (PROJECTION_ORIGIN + PROJECTION_SCALE * camY[i] / camZ[i] - top) * scaleY * supersample;
		}
		double rayScaleX = 1 / (scaleX * supersample), rayOffsetX = left - PROJECTION_ORIGIN;
		double rayScaleY = 1 / (scaleY * supersample), rayOffsetY = top - PROJECTION_ORIGIN;

		int samplesWide = width * supersample;
		int samplesHigh = height * supersample;
		int[] samples = obtainSamplesBuffer(samplesWide * samplesHigh);
		for (int face : paintOrder(screenX, screenY, camZ))
			paintFace(face, screenX, screenY, camX, camY, camZ, rayScaleX, rayOffsetX, rayScaleY, rayOffsetY,
				samplesWide, samplesHigh, palette, samples);
		if (outlineWidth > 0)
			growOutline(samples, samplesWide, samplesHigh, scaleX, scaleY, outlineWidth);

		return downsample(samples, samplesWide, width, height);
	}

	private int[] obtainSamplesBuffer(int size) {
		if (samplesScratch == null || samplesScratch.length != size)
			samplesScratch = new int[size];
		else
			Arrays.fill(samplesScratch, 0);
		return samplesScratch;
	}

	private int[] downsample(int[] samples, int samplesWide, int width, int height) {
		int[] pixels = new int[width * height];
		int samplesPerPixel = supersample * supersample;
		int rounding = samplesPerPixel / 2;
		for (int py = 0; py < height; py++) {
			for (int px = 0; px < width; px++) {
				int covered = 0, r = 0, g = 0, b = 0;
				for (int sy = 0; sy < supersample; sy++) {
					int rowStart = (py * supersample + sy) * samplesWide + px * supersample;
					for (int sx = 0; sx < supersample; sx++) {
						int rgb = samples[rowStart + sx];
						if (rgb != 0) {
							covered++;
							r += rgb >> 16 & 0xFF;
							g += rgb >> 8 & 0xFF;
							b += rgb & 0xFF;
						}
					}
				}
				pixels[py * width + px] =
					(covered * 255 + rounding) / samplesPerPixel << 24 |
					(r + rounding) / samplesPerPixel << 16 |
					(g + rounding) / samplesPerPixel << 8 |
					(b + rounding) / samplesPerPixel;
			}
		}
		return pixels;
	}

	/**
	 * Back-to-front face draw order (painter's algorithm), with faces that have an explicit
	 * render priority (0-11) grouped and interleaved the way the game does: priorities 0-9 in
	 * strict priority order, with priorities 10 and 11 slotted in among them by depth.
	 */
	private int[] paintOrder(double[] screenX, double[] screenY, double[] depth) {
		int faceCount = model.triangleA.length;
		long[] sortKeys = sortKeysScratch;
		int visibleCount = 0;
		for (int f = 0; f < faceCount; f++) {
			if (model.shadeC[f] == -2)
				continue; // hidden face
			int a = model.triangleA[f], b = model.triangleB[f], c = model.triangleC[f];
			if (isBackFacing(screenX, screenY, a, b, c))
				continue;
			int faceDepth = floorToInt((depth[a] + depth[b] + depth[c]) / 3);
			// Pack so sorting by the key sorts by depth (descending) with the face index as a
			// tie-breaker, without boxing into Integer[].
			sortKeys[visibleCount] = ((long) -faceDepth << 32) | (f & 0xFFFFFFFFL);
			visibleCount++;
		}
		Arrays.sort(sortKeys, 0, visibleCount);
		int[] depthOrder = new int[visibleCount];
		int[] depthOrderDepth = new int[visibleCount];
		for (int i = 0; i < visibleCount; i++) {
			depthOrder[i] = (int) sortKeys[i];
			depthOrderDepth[i] = (int) -(sortKeys[i] >> 32);
		}

		if (model.paintLayer == null)
			return depthOrder;
		return interleaveByPriority(depthOrder, depthOrderDepth);
	}

	private static boolean isBackFacing(double[] screenX, double[] screenY, int a, int b, int c) {
		return (screenX[a] - screenX[b]) * (screenY[c] - screenY[b]) <= (screenX[c] - screenX[b]) * (screenY[a] - screenY[b]);
	}

	/**
	 * {@code depthOrder}/{@code depthOrderDepth} are aligned by index (already sorted
	 * back-to-front); this groups them by the model's explicit render priority instead.
	 */
	private int[] interleaveByPriority(int[] depthOrder, int[] depthOrderDepth) {
		double[] depthTotal = new double[12];
		int[] countByPriority = new int[12];
		for (int i = 0; i < depthOrder.length; i++) {
			int priority = model.paintLayer[depthOrder[i]];
			depthTotal[priority] += depthOrderDepth[i];
			countByPriority[priority]++;
		}
		double[] slotDepth = new double[10];
		Arrays.fill(slotDepth, Double.MAX_VALUE);
		slotDepth[0] = averageDepth(depthTotal, countByPriority, 1, 2);
		slotDepth[3] = averageDepth(depthTotal, countByPriority, 3, 4);
		slotDepth[5] = averageDepth(depthTotal, countByPriority, 6, 8);

		int[] deferredFace = new int[countByPriority[10] + countByPriority[11]];
		int[] deferredDepth = new int[deferredFace.length];
		int deferredCount = 0;
		for (int priority = 10; priority <= 11; priority++) {
			for (int i = 0; i < depthOrder.length; i++) {
				if (model.paintLayer[depthOrder[i]] == priority) {
					deferredFace[deferredCount] = depthOrder[i];
					deferredDepth[deferredCount] = depthOrderDepth[i];
					deferredCount++;
				}
			}
		}

		int[] order = new int[depthOrder.length];
		int next = 0, nextDeferred = 0;
		for (int priority = 0; priority < 10; priority++) {
			while (nextDeferred < deferredCount && deferredDepth[nextDeferred] > slotDepth[priority])
				order[next++] = deferredFace[nextDeferred++];
			for (int i = 0; i < depthOrder.length; i++)
				if (model.paintLayer[depthOrder[i]] == priority)
					order[next++] = depthOrder[i];
		}
		while (nextDeferred < deferredCount)
			order[next++] = deferredFace[nextDeferred++];
		return order;
	}

	private static double averageDepth(double[] depthTotal, int[] count, int priorityA, int priorityB) {
		int n = count[priorityA] + count[priorityB];
		return n == 0 ? 0 : (depthTotal[priorityA] + depthTotal[priorityB]) / n;
	}

	private void paintFace(int face, double[] screenX, double[] screenY, double[] camX, double[] camY, double[] camZ,
							double rayScaleX, double rayOffsetX, double rayScaleY, double rayOffsetY,
							int width, int height, int[] palette, int[] samples) {
		int a = model.triangleA[face], b = model.triangleB[face], c = model.triangleC[face];
		double xa = screenX[a], ya = screenY[a];
		double xb = screenX[b], yb = screenY[b];
		double xc = screenX[c], yc = screenY[c];
		double area = (xb - xa) * (yc - ya) - (xc - xa) * (yb - ya);
		if (area == 0)
			return;
		double inverseArea = 1 / area;

		int minX = clampInt(ceilToInt(Math.min(xa, Math.min(xb, xc)) - 0.5), 0, width - 1);
		int maxX = clampInt(floorToInt(Math.max(xa, Math.max(xb, xc)) - 0.5), 0, width - 1);
		int minY = clampInt(ceilToInt(Math.min(ya, Math.min(yb, yc)) - 0.5), 0, height - 1);
		int maxY = clampInt(floorToInt(Math.max(ya, Math.max(yb, yc)) - 0.5), 0, height - 1);

		boolean flatShaded = model.shadeC[face] == -1;
		int shadeA = model.shadeA[face], shadeB = model.shadeB[face], shadeC = model.shadeC[face];
		if (flatShaded)
			shadeB = shadeC = shadeA;
		int transparency = model.alpha == null ? 0 : model.alpha[face] & 0xFF;

		int[] texels = model.texelsByFace == null ? null : model.texelsByFace[face];
		int textureSize = 0;
		double[] planeU = null, planeV = null, planeW = null;
		if (texels != null) {
			textureSize = (int) Math.sqrt(texels.length);
			double[] origin = corner(camX, camY, camZ, model.uvA[face]);
			double[] edgeM = subtract(corner(camX, camY, camZ, model.uvB[face]), origin);
			double[] edgeN = subtract(corner(camX, camY, camZ, model.uvC[face]), origin);
			planeU = cross(edgeN, origin);
			planeV = cross(origin, edgeM);
			planeW = cross(edgeM, edgeN);
		}

		for (int sy = minY; sy <= maxY; sy++) {
			double py = sy + 0.5;
			for (int sx = minX; sx <= maxX; sx++) {
				double px = sx + 0.5;
				double wa = ((xb - px) * (yc - py) - (xc - px) * (yb - py)) * inverseArea;
				double wb = ((xc - px) * (ya - py) - (xa - px) * (yc - py)) * inverseArea;
				double wc = 1 - wa - wb;
				if (wa < 0 || wb < 0 || wc < 0)
					continue;

				int color;
				if (texels == null) {
					int shade = clampInt(floorToInt(wa * shadeA + wb * shadeB + wc * shadeC), 0, 0xFFFF);
					color = palette[flatShaded ? shadeA : shade];
				} else {
					double rayX = px * rayScaleX + rayOffsetX;
					double rayY = py * rayScaleY + rayOffsetY;
					double along = rayX * planeW[0] + rayY * planeW[1] + PROJECTION_SCALE * planeW[2];
					double u = (rayX * planeU[0] + rayY * planeU[1] + PROJECTION_SCALE * planeU[2]) / along;
					double v = (rayX * planeV[0] + rayY * planeV[1] + PROJECTION_SCALE * planeV[2]) / along;
					int texel = texels[
						wrap(floorToInt(v * textureSize), textureSize) * textureSize +
							clampInt(floorToInt(u * textureSize), 0, textureSize - 1)];
					if (texel == 0)
						continue;
					int shade = (int) ((wa * shadeA + wb * shadeB + wc * shadeC) * 2);
					color = Math.max(1, applyShade(texel, shade));
				}

				int i = sy * width + sx;
				samples[i] = transparency == 0 ? color : blend(color, samples[i], transparency);
			}
		}
	}

	private static double[] corner(double[] camX, double[] camY, double[] camZ, int i) {
		return new double[]{camX[i], camY[i], camZ[i]};
	}

	private static double[] subtract(double[] a, double[] b) {
		return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	private static int applyShade(int rgb, int shade) {
		return ((rgb & 0xFF00FF) * shade >> 8 & 0xFF00FF) + ((rgb & 0xFF00) * shade >> 8 & 0xFF00);
	}

	private static int blend(int color, int behind, int transparency) {
		int opacity = 256 - transparency;
		return ((color & 0xFF00FF) * opacity + (behind & 0xFF00FF) * transparency >> 8 & 0xFF00FF)
			+ ((color & 0xFF00) * opacity + (behind & 0xFF00) * transparency >> 8 & 0xFF00);
	}

	/**
	 * Grows a border of the given width around the drawn silhouette: ring 1 is near-black
	 * (colour 1, since 0 means untouched), ring 2 (if requested) is white outside that.
	 */
	private void growOutline(int[] samples, int width, int height, double scaleX, double scaleY, int outlineWidth) {
		boolean[] drawn = new boolean[samples.length];
		for (int i = 0; i < samples.length; i++)
			drawn[i] = samples[i] != 0;

		for (int ring = 1; ring <= outlineWidth; ring++) {
			int color = ring == 1 ? 1 : 0xFFFFFF;
			int reachX = floorToInt(ring * scaleX * supersample);
			int reachY = floorToInt(ring * scaleY * supersample);
			int[] offsetsX = new int[(reachX * 2 + 1) * (reachY * 2 + 1)];
			int[] offsetsY = new int[offsetsX.length];
			int offsetCount = 0;
			for (int dy = -reachY; dy <= reachY; dy++) {
				for (int dx = -reachX; dx <= reachX; dx++) {
					double nx = dx / (scaleX * supersample), ny = dy / (scaleY * supersample);
					if (nx * nx + ny * ny <= (double) ring * ring) {
						offsetsX[offsetCount] = dx;
						offsetsY[offsetCount] = dy;
						offsetCount++;
					}
				}
			}

			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					if (!drawn[y * width + x] || isFullyEnclosed(drawn, x, y, width, height))
						continue;
					for (int o = 0; o < offsetCount; o++) {
						int sx = x + offsetsX[o], sy = y + offsetsY[o];
						if (sx >= 0 && sx < width && sy >= 0 && sy < height && samples[sy * width + sx] == 0)
							samples[sy * width + sx] = color;
					}
				}
			}
		}
	}

	private static boolean isFullyEnclosed(boolean[] drawn, int x, int y, int width, int height) {
		return x > 0 && drawn[y * width + x - 1]
			&& x < width - 1 && drawn[y * width + x + 1]
			&& y > 0 && drawn[(y - 1) * width + x]
			&& y < height - 1 && drawn[(y + 1) * width + x];
	}

	/**
	 * Precomputes an RGB lookup for every possible packed-HSL value, gamma-corrected by
	 * {@code brightness} the same way the game darkens its item icon colours (rounding to
	 * 8-bit precision before the gamma curve is applied, matching the game's own precision).
	 */
	static int[] buildPalette(double brightness) {
		int[] palette = new int[0x10000];
		for (int packed = 0; packed < palette.length; packed++) {
			double[] srgb = decodeHsl(packed);
			int rgb = 0;
			for (double channel : srgb) {
				double rounded = ((int) (channel * 256)) / 256.0;
				int component = clampInt((int) (Math.pow(rounded, brightness) * 256), 0, 255);
				rgb = rgb << 8 | component;
			}
			palette[packed] = Math.max(1, rgb); // 0 means nothing was drawn
		}
		return palette;
	}
}
