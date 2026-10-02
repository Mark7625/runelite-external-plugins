package io.mark.hitsplats.art;

import io.mark.hitsplats.combat.CombatStyle;
import io.mark.hitsplats.config.HitsplatIconSet;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;

@Slf4j
@Singleton
public class HitsplatSprites
{
	private final ScheduledExecutorService executor;

	private static final String RESOURCE_ROOT = "/io/mark/hitsplats/";
	private static final String ICON_DIRECTORY = "style_icons";
	private static final int SHADOW_ALPHA = 120;
	private static final int SUPERSAMPLE = 8;

	private volatile Map<HitsplatStyle, Map<HitsplatSkin, BufferedImage>> packs;
	private volatile Map<HitsplatStyle, Map<HitsplatSkin, BufferedImage>> packShadows;
	private volatile Map<HitsplatIconSet, Map<CombatStyle, BufferedImage>> icons;
	private volatile Map<HitsplatIconSet, Map<CombatStyle, BufferedImage>> iconShadows;
	private volatile Map<HitsplatIconSet, Integer> iconSlotWidths;
	private volatile Map<HitsplatStyle, StyleProperties> styleProperties;

	@Inject
	private HitsplatSprites(ScheduledExecutorService executor)
	{
		this.executor = executor;
	}

	public void load(int shadowRadius, Supplier<Filepath> overrides, IntConsumer onLoaded)
	{
		executor.execute(() ->
		{
			Overrides overrideRoot = new Overrides(prepareOverrideDirectory(overrides));

			Map<HitsplatStyle, StyleProperties> loadedProperties = new EnumMap<>(HitsplatStyle.class);
			Map<HitsplatStyle, Map<HitsplatSkin, BufferedImage>> loaded = new EnumMap<>(HitsplatStyle.class);
			Map<HitsplatStyle, Map<HitsplatSkin, BufferedImage>> loadedShadows = new EnumMap<>(HitsplatStyle.class);
			for (HitsplatStyle style : HitsplatStyle.values())
			{
				StyleProperties properties = overrideRoot.loadProperties(style);
				loadedProperties.put(style, properties);

				Map<HitsplatSkin, BufferedImage> pack = loadPack(style, properties.getSize(), overrideRoot);
				loaded.put(style, pack);
				loadedShadows.put(style, outlinePack(pack, shadowRadius, properties));
			}

			Map<HitsplatIconSet, Map<CombatStyle, BufferedImage>> loadedIcons = new EnumMap<>(HitsplatIconSet.class);
			Map<HitsplatIconSet, Map<CombatStyle, BufferedImage>> loadedIconShadows = new EnumMap<>(HitsplatIconSet.class);
			Map<HitsplatIconSet, Integer> widths = new EnumMap<>(HitsplatIconSet.class);

			for (HitsplatIconSet set : HitsplatIconSet.values())
			{
				Map<CombatStyle, BufferedImage> setIcons = loadIcons(set, overrideRoot);
				Map<CombatStyle, BufferedImage> shadows = new EnumMap<>(CombatStyle.class);
				int widest = 0;

				for (Map.Entry<CombatStyle, BufferedImage> entry : setIcons.entrySet())
				{
					if (shadowRadius > 0)
					{
						shadows.put(entry.getKey(), outline(entry.getValue(), shadowRadius, SHADOW_ALPHA, 0));
					}

					widest = Math.max(widest, entry.getValue().getWidth());
				}

				loadedIcons.put(set, setIcons);
				loadedIconShadows.put(set, shadows);
				widths.put(set, widest);
			}

			iconSlotWidths = widths;
			iconShadows = loadedIconShadows;
			icons = loadedIcons;
			styleProperties = loadedProperties;
			packShadows = loadedShadows;
			packs = loaded;

			onLoaded.accept(overrideRoot.getApplied());
		});
	}

	public void clear()
	{
		packs = null;
		packShadows = null;
		icons = null;
		iconShadows = null;
		iconSlotWidths = null;
		styleProperties = null;
	}

	public StyleProperties getProperties(HitsplatStyle style)
	{
		Map<HitsplatStyle, StyleProperties> loaded = styleProperties;
		StyleProperties properties = loaded == null ? null : loaded.get(style);
		return properties == null ? StyleProperties.defaults(style) : properties;
	}

	public BufferedImage get(HitsplatStyle style, HitsplatSkin skin)
	{
		Map<HitsplatStyle, Map<HitsplatSkin, BufferedImage>> loaded = packs;
		if (loaded == null)
		{
			return null;
		}

		Map<HitsplatSkin, BufferedImage> pack = loaded.get(style);
		return pack == null ? null : pack.get(skin);
	}

	public BufferedImage getShadow(HitsplatStyle style, HitsplatSkin skin)
	{
		Map<HitsplatStyle, Map<HitsplatSkin, BufferedImage>> loaded = packShadows;
		if (loaded == null)
		{
			return null;
		}

		Map<HitsplatSkin, BufferedImage> pack = loaded.get(style);
		return pack == null ? null : pack.get(skin);
	}

	private static Map<HitsplatSkin, BufferedImage> outlinePack(Map<HitsplatSkin, BufferedImage> pack, int shadowRadius,
		StyleProperties properties)
	{
		Map<HitsplatSkin, BufferedImage> shadows = new EnumMap<>(HitsplatSkin.class);
		if (shadowRadius <= 0 || properties.getShadowAlpha() <= 0)
		{
			return shadows;
		}

		// Skins share images through their fallback chains, so outline each distinct one once.
		Map<BufferedImage, BufferedImage> byImage = new IdentityHashMap<>();

		for (Map.Entry<HitsplatSkin, BufferedImage> entry : pack.entrySet())
		{
			shadows.put(entry.getKey(), byImage.computeIfAbsent(entry.getValue(),
				image -> outline(image, shadowRadius, properties.getShadowAlpha(), properties.getShadowRgb())));
		}

		return shadows;
	}

	public BufferedImage getIcon(HitsplatIconSet set, CombatStyle style)
	{
		return lookup(icons, set, style);
	}

	public BufferedImage getIconShadow(HitsplatIconSet set, CombatStyle style)
	{
		return lookup(iconShadows, set, style);
	}

	public int getIconSlotWidth(HitsplatIconSet set)
	{
		Map<HitsplatIconSet, Integer> widths = iconSlotWidths;
		if (widths == null)
		{
			return 0;
		}

		Integer width = widths.get(set);
		return width == null ? 0 : width;
	}

	private static BufferedImage lookup(Map<HitsplatIconSet, Map<CombatStyle, BufferedImage>> loaded,
		HitsplatIconSet set, CombatStyle style)
	{
		if (loaded == null)
		{
			return null;
		}

		Map<CombatStyle, BufferedImage> pack = loaded.get(set);
		return pack == null ? null : pack.get(style);
	}

	private static Map<CombatStyle, BufferedImage> loadIcons(HitsplatIconSet set, Overrides overrideRoot)
	{
		Map<CombatStyle, BufferedImage> pack = new EnumMap<>(CombatStyle.class);
		for (CombatStyle style : CombatStyle.values())
		{
			BufferedImage image = loadImage(overrideRoot, ICON_DIRECTORY + "/" + set.getDirectory(), style.getFileName());
			if (image != null)
			{
				pack.put(style, trim(image));
			}
		}

		log.debug("Loaded {} of {} {} combat style icons", pack.size(), CombatStyle.values().length, set.getDirectory());
		return pack;
	}

	private static Map<HitsplatSkin, BufferedImage> loadPack(HitsplatStyle style, int scalePercent, Overrides overrideRoot)
	{
		Map<HitsplatSkin, BufferedImage> pack = new EnumMap<>(HitsplatSkin.class);
		Map<String, BufferedImage> byFileName = new HashMap<>();

		for (HitsplatSkin skin : HitsplatSkin.values())
		{
			for (String fileName : skin.getFileNames())
			{
				BufferedImage image;
				if (byFileName.containsKey(fileName))
				{
					image = byFileName.get(fileName);
				}
				else
				{
					image = scale(loadImage(overrideRoot, style.getDirectory(), fileName), scalePercent);
					byFileName.put(fileName, image);
				}

				if (image != null)
				{
					pack.put(skin, image);
					break;
				}
			}
		}

		log.debug("Loaded hitsplat style {}: {} of {} skins", style.getDirectory(), pack.size(), HitsplatSkin.values().length);
		return pack;
	}

	private static BufferedImage outline(BufferedImage image, int radius, int maxAlpha, int rgb)
	{
		int width = image.getWidth() + radius * 2;
		int height = image.getHeight() + radius * 2;
		int[] alpha = new int[width * height];

		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if ((image.getRGB(x, y) >>> 24) == 0)
				{
					continue;
				}

				for (int dy = -radius; dy <= radius; dy++)
				{
					for (int dx = -radius; dx <= radius; dx++)
					{
						double distance = Math.sqrt(dx * dx + dy * dy);
						if (distance > radius)
						{
							continue;
						}

						int value = (int) Math.round(maxAlpha * (1d - distance / (radius + 1d)));
						int index = (y + radius + dy) * width + x + radius + dx;
						if (value > alpha[index])
						{
							alpha[index] = value;
						}
					}
				}
			}
		}

		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int index = 0; index < alpha.length; index++)
		{
			if (alpha[index] > 0)
			{
				out.setRGB(index % width, index / width, (alpha[index] << 24) | rgb);
			}
		}

		return out;
	}

	private static BufferedImage scale(BufferedImage image, int percent)
	{
		if (image == null || percent == 100)
		{
			return image;
		}

		int width = Math.max(1, image.getWidth() * percent / 100);
		int height = Math.max(1, image.getHeight() * percent / 100);

		// A whole number of pixels per pixel is already perfect, and nearest neighbour keeps it razor sharp.
		if (percent % 100 == 0)
		{
			return nearest(image, width, height);
		}

		// Anything else lands between pixels. Blowing the art up by a whole number first means every source pixel is
		// still the same size, and averaging that back down to the target spreads the leftovers evenly instead of
		// leaving some rows a pixel fatter than their neighbours.
		BufferedImage supersampled = nearest(image,
			image.getWidth() * SUPERSAMPLE, image.getHeight() * SUPERSAMPLE);

		return average(supersampled, width, height);
	}

	private static BufferedImage nearest(BufferedImage image, int width, int height)
	{
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = out.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		graphics.drawImage(image, 0, 0, width, height, null);
		graphics.dispose();

		return out;
	}

	private static BufferedImage average(BufferedImage image, int width, int height)
	{
		int sourceWidth = image.getWidth();
		int sourceHeight = image.getHeight();
		int[] pixels = image.getRGB(0, 0, sourceWidth, sourceHeight, null, 0, sourceWidth);
		int[] out = new int[width * height];

		for (int y = 0; y < height; y++)
		{
			int fromY = y * sourceHeight / height;
			int toY = Math.max(fromY + 1, (y + 1) * sourceHeight / height);

			for (int x = 0; x < width; x++)
			{
				int fromX = x * sourceWidth / width;
				int toX = Math.max(fromX + 1, (x + 1) * sourceWidth / width);

				long alpha = 0;
				long red = 0;
				long green = 0;
				long blue = 0;
				int count = 0;

				for (int sourceY = fromY; sourceY < toY; sourceY++)
				{
					for (int sourceX = fromX; sourceX < toX; sourceX++)
					{
						int pixel = pixels[sourceY * sourceWidth + sourceX];
						int pixelAlpha = pixel >>> 24;

						// Weighted by alpha, so the transparent pixels around the splat don't drag its edges dark.
						alpha += pixelAlpha;
						red += ((pixel >> 16) & 0xFF) * pixelAlpha;
						green += ((pixel >> 8) & 0xFF) * pixelAlpha;
						blue += (pixel & 0xFF) * pixelAlpha;
						count++;
					}
				}

				int outAlpha = (int) (alpha / count);
				out[y * width + x] = alpha == 0 ? 0 : (outAlpha << 24)
					| ((int) (red / alpha) << 16)
					| ((int) (green / alpha) << 8)
					| (int) (blue / alpha);
			}
		}

		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		scaled.setRGB(0, 0, width, height, out, 0, width);
		return scaled;
	}

	private static BufferedImage trim(BufferedImage image)
	{
		int minX = image.getWidth();
		int minY = image.getHeight();
		int maxX = -1;
		int maxY = -1;

		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if ((image.getRGB(x, y) >>> 24) == 0)
				{
					continue;
				}

				minX = Math.min(minX, x);
				minY = Math.min(minY, y);
				maxX = Math.max(maxX, x);
				maxY = Math.max(maxY, y);
			}
		}

		if (maxX < 0 || (minX == 0 && minY == 0 && maxX == image.getWidth() - 1 && maxY == image.getHeight() - 1))
		{
			return image;
		}

		return image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}

	private static Filepath prepareOverrideDirectory(Supplier<Filepath> overrides)
	{
		Filepath root;
		try
		{
			root = overrides.get();
		}
		catch (RuntimeException e)
		{
			log.debug("Unable to open the hitsplat override directory", e);
			return null;
		}

		if (root == null)
		{
			return null;
		}

		for (HitsplatStyle style : HitsplatStyle.values())
		{
			createDirectory(root, style.getDirectory());
		}

		for (HitsplatIconSet set : HitsplatIconSet.values())
		{
			createDirectory(root, ICON_DIRECTORY + "/" + set.getDirectory());
		}

		return root;
	}

	private static void createDirectory(Filepath root, String directory)
	{
		try
		{
			resolve(root, directory).createDirectories();
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("Unable to create hitsplat override directory {}", directory, e);
		}
	}

	private static Filepath resolve(Filepath root, String directory)
	{
		Filepath path = root;
		for (String segment : directory.split("/"))
		{
			path = path.joinSegment(segment);
		}

		return path;
	}

	private static BufferedImage loadImage(Overrides overrideRoot, String directory, String fileName)
	{
		BufferedImage override = overrideRoot.load(directory, fileName);
		if (override != null)
		{
			return override;
		}

		String path = RESOURCE_ROOT + directory + "/" + fileName + ".png";
		if (HitsplatSprites.class.getResource(path) == null)
		{
			return null;
		}

		try
		{
			return ImageUtil.loadImageResource(HitsplatSprites.class, path);
		}
		catch (RuntimeException e)
		{
			log.debug("Unable to read hitsplat image {}", path, e);
			return null;
		}
	}

	private static final class Overrides
	{
		private final Filepath root;
		private int applied;

		private Overrides(Filepath root)
		{
			this.root = root;
		}

		private int getApplied()
		{
			return applied;
		}

		private StyleProperties loadProperties(HitsplatStyle style)
		{
			if (root == null)
			{
				return StyleProperties.defaults(style);
			}

			Filepath path;
			try
			{
				path = resolve(root, style.getDirectory()).joinSegment(StyleProperties.FILE_NAME);
				if (!path.isFile())
				{
					return StyleProperties.defaults(style);
				}
			}
			catch (RuntimeException e)
			{
				log.debug("Unable to resolve hitsplat style properties for {}", style.getDirectory(), e);
				return StyleProperties.defaults(style);
			}

			try (Reader reader = path.openReader())
			{
				Properties properties = new Properties();
				properties.load(reader);

				log.debug("Using hitsplat style properties {}", path);
				applied++;
				return StyleProperties.read(style, properties);
			}
			catch (IOException | RuntimeException e)
			{
				log.debug("Unable to read hitsplat style properties {}", path, e);
				return StyleProperties.defaults(style);
			}
		}

		private BufferedImage load(String directory, String fileName)
		{
			if (root == null)
			{
				return null;
			}

			Filepath path;
			try
			{
				path = resolve(root, directory).joinSegment(fileName + ".png");
				if (!path.isFile())
				{
					return null;
				}
			}
			catch (RuntimeException e)
			{
				log.debug("Unable to resolve hitsplat override {}/{}", directory, fileName, e);
				return null;
			}

			try (InputStream in = path.openInputStream())
			{
				BufferedImage image = ImageIO.read(in);
				if (image == null)
				{
					log.debug("Hitsplat override {} is not an image", path);
					return null;
				}

				log.debug("Using hitsplat override {}", path);
				applied++;
				return toArgb(image);
			}
			catch (IOException | RuntimeException e)
			{
				log.debug("Unable to read hitsplat override {}", path, e);
				return null;
			}
		}
	}

	private static BufferedImage toArgb(BufferedImage image)
	{
		if (image.getType() == BufferedImage.TYPE_INT_ARGB)
		{
			return image;
		}

		BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = out.createGraphics();
		graphics.drawImage(image, 0, 0, null);
		graphics.dispose();
		return out;
	}
}
