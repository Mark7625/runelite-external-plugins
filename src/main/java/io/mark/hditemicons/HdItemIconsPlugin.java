package io.mark.hditemicons;

import com.google.inject.Provides;
import io.mark.hditemicons.hd.HdItemIcons;
import java.io.IOException;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "HQ Item Icons",
	description = "Sharper, high quality item icons",
	tags = {"hq", "hd", "item", "icons", "inventory", "bank", "antialiasing"},
	internalName = "hq-item-icons"
)
public class HdItemIconsPlugin extends Plugin {

	@Inject
	private Client client;

	@Inject
	private HdItemIcons hdItemIcons;

	@Inject
	private KeyManager keyManager;

	@Inject
	private EditRotationHotkeyListener editRotationHotkeyListener;

	@Inject
	private HdItemIconsConfig config;

	@Provides
	HdItemIconsConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(HdItemIconsConfig.class);
	}

	@Override
	protected void startUp() throws IOException {
		hdItemIcons.startUp(getPluginDirectory());
		keyManager.registerKeyListener(editRotationHotkeyListener);
	}

	@Override
	protected void shutDown() {
		keyManager.unregisterKeyListener(editRotationHotkeyListener);
		hdItemIcons.shutDown();
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event) {
		int itemId = event.getItemId();
		if (itemId == -1
			|| event.getMenuEntry().getWidget() == null
			|| !"Examine".equals(event.getOption())
			|| !editRotationHotkeyListener.isPressed()
			|| !config.customRotationsEnabled())
			return;

		client.getMenu().createMenuEntry(-1)
			.setOption("Edit icon rotation")
			.setTarget(event.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(e -> hdItemIcons.openRotationEditor(itemId));
	}
}
