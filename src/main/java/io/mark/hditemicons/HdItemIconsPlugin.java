package io.mark.hditemicons;

import com.google.inject.Provides;
import io.mark.hditemicons.hd.HdItemIcons;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
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

	private static final String NAMESPACE = "hq-item-icons";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

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
		eventBus.post(new PluginMessage(NAMESPACE, "startup", Map.of("plugin", getName())));
	}

	@Override
	protected void shutDown() {
		keyManager.unregisterKeyListener(editRotationHotkeyListener);
		hdItemIcons.shutDown();
		eventBus.post(new PluginMessage(NAMESPACE, "shutdown", Map.of("plugin", getName())));
	}

	@Subscribe
	public void onPluginMessage(PluginMessage event) {
		if (!NAMESPACE.equals(event.getNamespace()))
			return;

		Object value;
		if ("update-icon".equals(event.getName()))
			value = event.getData().get("itemId");
		else if ("update-icons".equals(event.getName()))
			value = event.getData().get("itemIds");
		else
			return;

		// No ids at all means every icon; an empty or malformed one means none, so that a bad
		// message can't wipe the whole cache by accident
		if (value == null) {
			clientThread.invoke(() -> hdItemIcons.clearRenderCache());
			return;
		}

		List<Integer> itemIds = itemIdsOf(value);
		clientThread.invoke(() -> hdItemIcons.clearRenderCache(itemIds));
	}

	/** Another plugin owns what it sent, so this copies eagerly and ignores anything unusable. */
	private static List<Integer> itemIdsOf(Object value) {
		if (value instanceof Number)
			return List.of(((Number) value).intValue());
		if (!(value instanceof Collection))
			return List.of();

		List<Integer> itemIds = new ArrayList<>();
		for (Object element : (Collection<?>) value)
			if (element instanceof Number)
				itemIds.add(((Number) element).intValue());
		return itemIds;
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
