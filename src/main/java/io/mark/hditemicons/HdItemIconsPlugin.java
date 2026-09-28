package io.mark.hditemicons;

import com.google.inject.Provides;
import io.mark.hditemicons.hd.HdItemIcons;
import java.io.IOException;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "HQ Item Icons",
	description = "Sharper, high quality item icons",
	tags = {"hq", "hd", "item", "icons", "inventory", "bank", "antialiasing"},
	internalName = "hd-item-icons"
)
public class HdItemIconsPlugin extends Plugin {

	@Inject
	private HdItemIcons hdItemIcons;

	@Provides
	HdItemIconsConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(HdItemIconsConfig.class);
	}

	@Override
	protected void startUp() throws IOException {
		hdItemIcons.startUp(getPluginDirectory());
	}

	@Override
	protected void shutDown() {
		hdItemIcons.shutDown();
	}
}
