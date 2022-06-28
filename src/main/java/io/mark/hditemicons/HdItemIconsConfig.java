package io.mark.hditemicons;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(HdItemIconsConfig.GROUP)
public interface HdItemIconsConfig extends Config {

	String GROUP = "hd-item-icons";

	@ConfigItem(
		keyName = "iconCacheStorage",
		name = "Icon cache storage",
		description = "Memory only keeps rendered icons for the current session. Disk also saves them"
			+ " to your plugin data folder so they don't need to be re-rendered next time you log in.",
		position = 0
	)
	default IconCacheStorage iconCacheStorage() {
		return IconCacheStorage.MEMORY;
	}

	@ConfigItem(
		keyName = "iconQuality",
		name = "Icon quality",
		description = "Controls how much antialiasing supersampling is used when rendering icons."
			+ " Higher quality looks smoother but takes longer to render each icon.",
		position = 1
	)
	default IconQuality iconQuality() {
		return IconQuality.MEDIUM;
	}
}
