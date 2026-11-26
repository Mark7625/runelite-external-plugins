package io.mark.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

/**
 * Configuration interface for the Example plugin
 */
@ConfigGroup(ExampleConfig.CONFIG_GROUP)
public interface ExampleConfig extends Config {

	// Must be specific to this plugin. Renaming it later silently resets
	// everyone's saved settings, so pick the real name before release.
	String CONFIG_GROUP = "example";

	@ConfigItem(
			keyName = "exampleToggle",
			name = "Example toggle",
			description = "Placeholder config item - replace or delete this",
			position = 1
	)
	default boolean exampleToggle() {
		return true;
	}
}
