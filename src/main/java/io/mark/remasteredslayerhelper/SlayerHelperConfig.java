package io.mark.remasteredslayerhelper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(SlayerHelperConfig.GROUP)
public interface SlayerHelperConfig extends Config {

	String GROUP = "remastered-slayer-helper";

	@ConfigItem(
		keyName = "repositoryLayout",
		name = "Task Repository Layout",
		description = "Normal shows the task list and selected task's info as separate views. Compact shows both together in a split pane.",
		position = 0
	)
	default RepositoryLayout repositoryLayout() {
		return RepositoryLayout.NORMAL;
	}
}
