package io.mark.globes.util;

import net.runelite.client.util.ImageUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.Supplier;

public class ImageCache {
	private final Class<?> resourceClass;
	private final String resourcePath;
	private final int baseWidth;
	private final int baseHeight;
	private final Supplier<String> customBasePath;

	private BufferedImage baseImage;
	private BufferedImage[] baseImages;

	private BufferedImage cachedScaledImage;
	private BufferedImage[] cachedScaledImages;
	private int cachedScale = -1;

	public ImageCache(Class<?> resourceClass, String resourcePath, int baseWidth, int baseHeight) {
		this(resourceClass, resourcePath, baseWidth, baseHeight, null);
	}

	public ImageCache(Class<?> resourceClass, String resourcePath, int baseWidth, int baseHeight, Supplier<String> customBasePath) {
		this.resourceClass = resourceClass;
		this.resourcePath = resourcePath;
		this.baseWidth = baseWidth;
		this.baseHeight = baseHeight;
		this.customBasePath = customBasePath != null ? customBasePath : () -> "";
	}

	public ImageCache(Class<?> resourceClass, String resourcePathPrefix, int count, int baseWidth, int baseHeight) {
		this(resourceClass, resourcePathPrefix, count, baseWidth, baseHeight, null);
	}

	public ImageCache(Class<?> resourceClass, String resourcePathPrefix, int count, int baseWidth, int baseHeight, Supplier<String> customBasePath) {
		this.resourceClass = resourceClass;
		this.resourcePath = resourcePathPrefix;
		this.baseWidth = baseWidth;
		this.baseHeight = baseHeight;
		this.customBasePath = customBasePath != null ? customBasePath : () -> "";
		this.baseImages = new BufferedImage[count];
		this.cachedScaledImages = new BufferedImage[count];
	}

	/**
	 * Load image from file only if dimensions match expected size.
	 * Returns null on any error so callers can fall back to internal sprites.
	 */
	public static BufferedImage loadImageFromFile(File file, int expectedWidth, int expectedHeight) {
		if (file == null || !file.isFile()) {
			return null;
		}
		try {
			BufferedImage img = ImageIO.read(file);
			if (img != null && img.getWidth() == expectedWidth && img.getHeight() == expectedHeight) {
				return img;
			}
		} catch (Throwable ignored) {
			// any error: invalid path, IO, security, etc. -> fall back to internal
		}
		return null;
	}

	private void loadBaseImage() {
		if (baseImage != null) {
			return;
		}
		try {
			String path = customBasePath.get();
			if (path != null && !path.trim().isEmpty()) {
				File file = new File(path.trim(), resourcePath.replace('/', File.separatorChar));
				BufferedImage custom = loadImageFromFile(file, baseWidth, baseHeight);
				if (custom != null) {
					baseImage = custom;
					return;
				}
			}
		} catch (Throwable ignored) {
			// any error -> fall back to internal
		}
		baseImage = ImageUtil.loadImageResource(resourceClass, resourcePath);
	}

	private void loadBaseImages(int count) {
		for (int i = 0; i < count; i++) {
			if (baseImages[i] != null) {
				continue;
			}
			String relativePath = resourcePath + i + ".png";
			try {
				String path = customBasePath.get();
				if (path != null && !path.trim().isEmpty()) {
					File file = new File(path.trim(), relativePath.replace('/', File.separatorChar));
					BufferedImage custom = loadImageFromFile(file, baseWidth, baseHeight);
					if (custom != null) {
						baseImages[i] = custom;
						continue;
					}
				}
			} catch (Throwable ignored) {
				// any error -> fall back to internal for this image
			}
			baseImages[i] = ImageUtil.loadImageResource(resourceClass, relativePath);
		}
	}

	public BufferedImage getScaledImage(int scale) {
		if (baseImage == null) {
			try {
				loadBaseImage();
			} catch (Throwable ignored) {
				// fall back to internal only
				baseImage = ImageUtil.loadImageResource(resourceClass, resourcePath);
			}
		}
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
		if (baseImages == null || index < 0 || index >= baseImages.length) {
			return null;
		}
		if (baseImages[index] == null) {
			try {
				loadBaseImages(baseImages.length);
			} catch (Throwable ignored) {
				// fall back to internal for any failed slot
				for (int i = 0; i < baseImages.length; i++) {
					if (baseImages[i] == null) {
						baseImages[i] = ImageUtil.loadImageResource(resourceClass, resourcePath + i + ".png");
					}
				}
			}
		}
		if (baseImages[index] == null) {
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
		baseImage = null;
		if (baseImages != null) {
			for (int i = 0; i < baseImages.length; i++) {
				baseImages[i] = null;
			}
		}
		cachedScaledImage = null;
		if (baseImages != null) {
			cachedScaledImages = new BufferedImage[baseImages.length];
		} else {
			cachedScaledImages = null;
		}
		cachedScale = -1;
	}
}
