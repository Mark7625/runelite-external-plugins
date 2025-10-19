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
}