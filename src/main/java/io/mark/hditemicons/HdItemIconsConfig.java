package io.mark.hditemicons;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

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

	@Range(min = 1, max = 8)
	@ConfigItem(
		keyName = "renderThreadCount",
		name = "Render threads",
		description = "How many background threads render icons at once. Higher can render icons faster"
			+ " when many are queued at once (e.g. opening a full bank), at the cost of more CPU usage.",
		position = 2
	)
	default int renderThreadCount() {
		return 2;
	}

	@ConfigItem(
		keyName = "customRotationsEnabled",
		name = "Custom icon rotations",
		description = "Turn off to render every item's default icon rotation and ignore any custom rotations"
			+ " you've saved. Saved rotations aren't deleted, so re-enabling restores them.",
		position = 3
	)
	default boolean customRotationsEnabled() {
		return true;
	}

	@ConfigItem(
		keyName = "hideDraggedItemIcon",
		name = "Hide dragged item icon",
		description = "While an item with a custom rotation is being dragged, blank the game's own icon for it, which"
			+ " the client otherwise draws over ours. Does this by emptying the item's model, so turn it off if it"
			+ " upsets another plugin.",
		position = 4
	)
	default boolean hideDraggedItemIcon() {
		return true;
	}

	@ConfigItem(
		keyName = "editRotationHotkey",
		name = "Edit icon rotation hotkey",
		description = "Hold this while right-clicking an item to add an \"Edit icon rotation\" option to its menu.",
		position = 5
	)
	default Keybind editRotationHotkey() {
		return Keybind.SHIFT;
	}
}
