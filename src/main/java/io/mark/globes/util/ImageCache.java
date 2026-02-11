package io.mark.globes.util;

import net.runelite.client.util.ImageUtil;

import java.awt.image.BufferedImage;

public class ImageCache {
	private final Class<?> resourceClass;
	private final String resourcePath;
	private final int baseWidth;
	private final int baseHeight;

	private BufferedImage baseImage;
	private BufferedImage[] baseImages;

	private BufferedImage cachedScaledImage;
	private BufferedImage[] cachedScaledImages;
	private int cachedScale = -1;

	public ImageCache(Class<?> resourceClass, String resourcePath, int baseWidth, int baseHeight) {
		this.resourceClass = resourceClass;
		this.resourcePath = resourcePath;
		this.baseWidth = baseWidth;
		this.baseHeight = baseHeight;
		loadBaseImage();
	}

	public ImageCache(Class<?> resourceClass, String resourcePathPrefix, int count, int baseWidth, int baseHeight) {
		this.resourceClass = resourceClass;
		this.resourcePath = resourcePathPrefix;
		this.baseWidth = baseWidth;
		this.baseHeight = baseHeight;
		this.baseImages = new BufferedImage[count];
		this.cachedScaledImages = new BufferedImage[count];
		loadBaseImages(count);
	}

	private void loadBaseImage() {
		if (baseImage != null) {
			return;
		}

		baseImage = ImageUtil.loadImageResource(resourceClass, resourcePath);
	}

	private void loadBaseImages(int count) {
		for (int i = 0; i < count; i++) {
			if (baseImages[i] != null) {
				continue;
			}

			String path = resourcePath + i + ".png";
			baseImages[i] = ImageUtil.loadImageResource(resourceClass, path);
		}
	}

	public BufferedImage getScaledImage(int scale) {
		if (baseImage == null) {
			return null;
		}

		if (scale == cachedScale && cachedScaledImage != null) {
			return cachedScaledImage;
		}

		double scaleFactor = scale / 100.0;
		int scaledWidth = (int) (baseWidth * scaleFactor);
		int scaledHeight = (int) (baseHeight * scaleFactor);

		cachedScaledImage = ImageUtil.resizeImage(baseImage, scaledWidth, scaledHeight, true);
		cachedScale = scale;

		return cachedScaledImage;
	}

	public BufferedImage getScaledImage(int index, int scale) {
		if (baseImages == null || index < 0 || index >= baseImages.length || baseImages[index] == null) {
			return null;
		}

		if (cachedScaledImages == null) {
			cachedScaledImages = new BufferedImage[baseImages.length];
		}

		if (scale == cachedScale && cachedScaledImages[index] != null) {
			return cachedScaledImages[index];
		}

		if (scale != cachedScale) {
			double scaleFactor = scale / 100.0;

			int scaledWidth = (int) (baseWidth * scaleFactor);
			int scaledHeight = (int) (baseHeight * scaleFactor);

			for (int i = 0; i < baseImages.length; i++) {
				BufferedImage img = baseImages[i];
				if (img == null) {
					continue;
				}

				cachedScaledImages[i] = ImageUtil.resizeImage(img, scaledWidth, scaledHeight, true);
			}

			cachedScale = scale;
		}

		return cachedScaledImages[index];
	}

	public BufferedImage[] getAllScaledImages(int scale) {
		if (baseImages == null) {
			return null;
		}

		getScaledImage(0, scale);
		return cachedScaledImages;
	}

	public void clearCache() {
		cachedScaledImage = null;
		if (baseImages != null) {
			cachedScaledImages = new BufferedImage[baseImages.length];
		} else {
			cachedScaledImages = null;
		}
		cachedScale = -1;
	}
}
