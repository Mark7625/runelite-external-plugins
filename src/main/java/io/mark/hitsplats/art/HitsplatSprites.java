package io.mark.hitsplats.art;

import io.mark.hitsplats.combat.CombatStyle;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
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

	private final AtomicInteger generation = new AtomicInteger();
	private volatile Map<HitsplatSkin, BufferedImage> images;
	private volatile Map<CombatStyle, BufferedImage> icons;

	@Inject
	private HitsplatSprites(ScheduledExecutorService executor)
	{
		this.executor = executor;
	}

	public void load(HitsplatStyle style)
	{
		final int gen = generation.incrementAndGet();
		images = null;
		executor.execute(() ->
		{
			Map<CombatStyle, BufferedImage> loadedIcons = icons == null ? loadIcons() : icons;
			Map<HitsplatSkin, BufferedImage> loaded = loadPack(style);
			if (generation.get() == gen)
			{
				icons = loadedIcons;
				images = loaded;
			}
		});
	}

	public void clear()
	{
		generation.incrementAndGet();
		images = null;
		icons = null;
	}

	public BufferedImage get(HitsplatSkin skin)
	{
		Map<HitsplatSkin, BufferedImage> pack = images;
		return pack == null ? null : pack.get(skin);
	}

	public BufferedImage getIcon(CombatStyle style)
	{
		Map<CombatStyle, BufferedImage> pack = icons;
		return pack == null ? null : pack.get(style);
	}

	private static Map<CombatStyle, BufferedImage> loadIcons()
	{
		Map<CombatStyle, BufferedImage> pack = new EnumMap<>(CombatStyle.class);
		for (CombatStyle style : CombatStyle.values())
		{
			BufferedImage image = loadImage(ICON_DIRECTORY, style.getFileName());
			if (image != null)
			{
				pack.put(style, image);
			}
		}

		log.debug("Loaded {} of {} combat style icons", pack.size(), CombatStyle.values().length);
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
