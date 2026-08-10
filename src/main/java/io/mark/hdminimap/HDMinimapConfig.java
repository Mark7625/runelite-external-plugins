package io.mark.hdminimap;

import io.mark.hdminimap.render.MinimapStyle;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

/**
 * Configuration interface for the HD Minimap plugin
 */
@ConfigGroup(HDMinimapConfig.CONFIG_GROUP)
public interface HDMinimapConfig extends Config {
	String CONFIG_GROUP = "hdminimap";

	@ConfigItem(
			keyName = "minimapStyle",
			name = "Minimap Style",
			description = "Choose the rendering style for the minimap",
			position = 1
	)
	default MinimapStyle minimapStyle() {
		return MinimapStyle.HD;
	}

	@ConfigItem(
			keyName = "minimapSideBar",
			name = "Map Elements Sidebar",
			description = "Toggle visibility of map elements via the sidebar",
			position = 2
	)
	default boolean displaySidebar() {
		return true;
	}

	public enum MapElementView
	{
		GRID,
		LIST
	}

	@ConfigItem(
			keyName = "mapElementView",
			name = "Map Elements View",
			description = "Choose how map elements are displayed in the sidebar",
			position = 3
	)
	default MapElementView mapElementView() {
		return MapElementView.GRID;
	}
}