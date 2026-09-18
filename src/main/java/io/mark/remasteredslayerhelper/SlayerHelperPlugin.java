package io.mark.remasteredslayerhelper;

import io.mark.remasteredslayerhelper.data.SlayerTaskRepository;
import io.mark.remasteredslayerhelper.ui.panels.SlayerPluginPanel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import javax.inject.Inject;
import java.awt.image.BufferedImage;

@Slf4j
@PluginDescriptor(
	name = "Remastered Slayer Helper"
)
public class SlayerHelperPlugin extends Plugin {
	private static final String ICON_PATH = "/images/icon.png";

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private SlayerTaskRepository slayerTaskRepository;

	private SlayerPluginPanel slayerPanel;
	private NavigationButton navButton;
	private boolean loaded;

	@Override
	protected void startUp() {
		loaded = false;
		slayerPanel = injector.getInstance(SlayerPluginPanel.class);
		navButton = getNavButton();
		clientToolbar.addNavigation(navButton);

		if (client.getGameState() == GameState.LOGGED_IN) {
			slayerTaskRepository.load();
			loaded = true;
		}
	}

	@Override
	protected void shutDown() {
		if (navButton != null) {
			clientToolbar.removeNavigation(navButton);
		}
		slayerTaskRepository.clear();
		loaded = false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGGED_IN && !loaded) {
			slayerTaskRepository.load();
			loaded = true;
		}
	}

	private NavigationButton getNavButton() {
		BufferedImage bufferedImage = ImageUtil.loadImageResource(getClass(), ICON_PATH);
		if (bufferedImage == null) {
			log.error("Can't find image @ " + ICON_PATH);
		}

		return NavigationButton.builder()
				.tooltip("Slayer Helper")
				.icon(bufferedImage)
				.priority(10)
				.panel(slayerPanel)
				.build();
	}
}

