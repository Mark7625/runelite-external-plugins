package io.mark.hditemicons;

import com.google.inject.Provides;
import io.mark.hditemicons.hd.HdItemIcons;
import java.io.File;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "HQ Item Icons",
	description = "Sharper, high quality item icons",
	tags = {"hq", "hd", "item", "icons", "inventory", "bank", "antialiasing"}
)
public class HdItemIconsPlugin extends Plugin {

	@Inject
	private HdItemIcons hdItemIcons;

	@Provides
	HdItemIconsConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(HdItemIconsConfig.class);
	}

	@Override
	protected void startUp() {
		File dataDirectory = new File(new File(RuneLite.RUNELITE_DIR, "plugin-data"), "hd-item-icons");
		hdItemIcons.startUp(dataDirectory);
	}

	@Override
	protected void shutDown() {
		hdItemIcons.shutDown();
	}
}
