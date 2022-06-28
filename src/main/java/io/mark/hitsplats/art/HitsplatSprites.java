package io.mark.hitsplats.art;

import io.mark.hitsplats.combat.CombatStyle;
import io.mark.hitsplats.config.HitsplatIconSet;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.ImageUtil;

@Slf4j
@Singleton
public class HitsplatSprites
{
	private final ScheduledExecutorService executor;

	private static final String RESOURCE_ROOT = "/io/mark/hitsplats/";
	private static final String ICON_DIRECTORY = "style_icons";
	private static final int SHADOW_RADIUS = 2;
	private static final int SHADOW_ALPHA = 120;

	private volatile Map<HitsplatStyle, Map<HitsplatSkin, BufferedImage>> packs;
	private volatile Map<HitsplatStyle, Map<HitsplatSkin, BufferedImage>> packShadows;
	private volatile Map<HitsplatIconSet, Map<CombatStyle, BufferedImage>> icons;
	private volatile Map<HitsplatIconSet, Map<CombatStyle, BufferedImage>> iconShadows;
	private volatile Map<HitsplatIconSet, Integer> iconSlotWidths;

	@Inject
	private HitsplatSprites(ScheduledExecutorService executor)
	{
		this.executor = executor;
	}

	public void load()
	{
		executor.execute(() ->
		{
			Map<HitsplatStyle, Map<HitsplatSkin, BufferedImage>> loaded = new EnumMap<>(HitsplatStyle.class);
			Map<HitsplatStyle, Map<HitsplatSkin, BufferedImage>> loadedShadows = new EnumMap<>(HitsplatStyle.class);
			for (HitsplatStyle style : HitsplatStyle.values())
			{
				Map<HitsplatSkin, BufferedImage> pack = loadPack(style);
				loaded.put(style, pack);
				loadedShadows.put(style, outlinePack(pack));
			}

			Map<HitsplatIconSet, Map<CombatStyle, BufferedImage>> loadedIcons = new EnumMap<>(HitsplatIconSet.class);
			Map<HitsplatIconSet, Map<CombatStyle, BufferedImage>> loadedIconShadows = new EnumMap<>(HitsplatIconSet.class);
			Map<HitsplatIconSet, Integer> widths = new EnumMap<>(HitsplatIconSet.class);

			for (HitsplatIconSet set : HitsplatIconSet.values())
			{
				Map<CombatStyle, BufferedImage> setIcons = loadIcons(set);
				Map<CombatStyle, BufferedImage> shadows = new EnumMap<>(CombatStyle.class);
				int widest = 0;

				for (Map.Entry<CombatStyle, BufferedImage> entry : setIcons.entrySet())
				{
					shadows.put(entry.getKey(), outline(entry.getValue(), SHADOW_RADIUS, SHADOW_ALPHA));
					widest = Math.max(widest, entry.getValue().getWidth());
				}

				loadedIcons.put(set, setIcons);
				loadedIconShadows.put(set, shadows);
				widths.put(set, widest);
			}

			iconSlotWidths = widths;
			iconShadows = loadedIconShadows;
			icons = loadedIcons;
			packShadows = loadedShadows;
			packs = loaded;
		});
	}

	public void clear()
	{
		packs = null;
		packShadows = null;
		icons = null;
		iconShadows = null;
		iconSlotWidths = null;
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

	private static Map<HitsplatSkin, BufferedImage> outlinePack(Map<HitsplatSkin, BufferedImage> pack)
	{
		Map<HitsplatSkin, BufferedImage> shadows = new EnumMap<>(HitsplatSkin.class);
		// Skins share images through their fallback chains, so outline each distinct one once.
		Map<BufferedImage, BufferedImage> byImage = new IdentityHashMap<>();

		for (Map.Entry<HitsplatSkin, BufferedImage> entry : pack.entrySet())
		{
			shadows.put(entry.getKey(), byImage.computeIfAbsent(entry.getValue(),
				image -> outline(image, SHADOW_RADIUS, SHADOW_ALPHA)));
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

	private static Map<CombatStyle, BufferedImage> loadIcons(HitsplatIconSet set)
	{
		Map<CombatStyle, BufferedImage> pack = new EnumMap<>(CombatStyle.class);
		for (CombatStyle style : CombatStyle.values())
		{
			BufferedImage image = loadImage(ICON_DIRECTORY + "/" + set.getDirectory(), style.getFileName());
			if (image != null)
			{
				pack.put(style, trim(image));
			}
		}

		log.debug("Loaded {} of {} {} combat style icons", pack.size(), CombatStyle.values().length, set.getDirectory());
		return pack;
	}

	private static Map<HitsplatSkin, BufferedImage> loadPack(HitsplatStyle style)
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
					image = loadImage(style.getDirectory(), fileName);
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

	private static BufferedImage outline(BufferedImage image, int radius, int maxAlpha)
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
				out.setRGB(index % width, index / width, alpha[index] << 24);
			}
		}

		return out;
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

	private static BufferedImage loadImage(String directory, String fileName)
	{
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
}
