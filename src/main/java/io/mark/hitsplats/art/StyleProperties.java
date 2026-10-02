package io.mark.hitsplats.art;

import java.awt.Color;
import java.util.Properties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class StyleProperties
{
	public static final String FILE_NAME = "style.properties";

	private static final int DEFAULT_SHADOW_ALPHA = 120;

	private final int size;
	private final boolean disableSplatIconScale;
	private final int textOffsetY;
	private final Color textColor;
	private final Color textShadowColor;
	private final int shadowRgb;
	private final int shadowAlpha;

	private StyleProperties(int size, boolean disableSplatIconScale, int textOffsetY, Color textColor,
		Color textShadowColor, int shadowRgb, int shadowAlpha)
	{
		this.size = size;
		this.disableSplatIconScale = disableSplatIconScale;
		this.textOffsetY = textOffsetY;
		this.textColor = textColor;
		this.textShadowColor = textShadowColor;
		this.shadowRgb = shadowRgb;
		this.shadowAlpha = shadowAlpha;
	}

	public static StyleProperties defaults(HitsplatStyle style)
	{
		return new StyleProperties(style.getScale(), style.isDisableScale(), style.getTextOffsetY(),
			Color.WHITE, Color.BLACK, 0, DEFAULT_SHADOW_ALPHA);
	}

	public static StyleProperties read(HitsplatStyle style, Properties properties)
	{
		StyleProperties defaults = defaults(style);

		return new StyleProperties(
			readInt(properties, "size", defaults.size, 10, 400),
			readBoolean(properties, "disableSplatIconScale", defaults.disableSplatIconScale),
			readInt(properties, "textOffsetY", defaults.textOffsetY, -32, 32),
			readColor(properties, "textColor", defaults.textColor),
			readColor(properties, "textShadowColor", defaults.textShadowColor),
			readColor(properties, "shadowColor", Color.BLACK).getRGB() & 0xFFFFFF,
			readInt(properties, "shadowAlpha", defaults.shadowAlpha, 0, 255));
	}

	private static boolean readBoolean(Properties properties, String key, boolean fallback)
	{
		String value = properties.getProperty(key);
		if (value == null)
		{
			return fallback;
		}

		String trimmed = value.trim();
		if ("true".equalsIgnoreCase(trimmed))
		{
			return true;
		}

		if ("false".equalsIgnoreCase(trimmed))
		{
			return false;
		}

		log.debug("Hitsplat style property {} is not true or false: {}", key, value);
		return fallback;
	}

	private static int readInt(Properties properties, String key, int fallback, int min, int max)
	{
		String value = properties.getProperty(key);
		if (value == null)
		{
			return fallback;
		}

		try
		{
			return Math.max(min, Math.min(max, Integer.parseInt(value.trim())));
		}
		catch (NumberFormatException e)
		{
			log.debug("Hitsplat style property {} is not a number: {}", key, value);
			return fallback;
		}
	}

	private static Color readColor(Properties properties, String key, Color fallback)
	{
		String value = properties.getProperty(key);
		if (value == null)
		{
			return fallback;
		}

		String hex = value.trim();
		if (hex.startsWith("#"))
		{
			hex = hex.substring(1);
		}

		try
		{
			long rgb = Long.parseLong(hex, 16);
			return new Color((int) rgb, hex.length() > 6);
		}
		catch (NumberFormatException e)
		{
			log.debug("Hitsplat style property {} is not a colour: {}", key, value);
			return fallback;
		}
	}
}
