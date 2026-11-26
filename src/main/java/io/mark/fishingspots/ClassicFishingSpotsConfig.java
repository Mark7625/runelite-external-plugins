package io.mark.fishingspots;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;

/**
 * Configuration interface for the Classic Fishing Spots plugin
 */
@ConfigGroup(ClassicFishingSpotsConfig.CONFIG_GROUP)
public interface ClassicFishingSpotsConfig extends Config {
	String CONFIG_GROUP = "classicfishingspots";
}
