package io.mark.remasteredslayerhelper;

import com.google.inject.Provides;
import io.mark.remasteredslayerhelper.data.QuestStateCache;
import io.mark.remasteredslayerhelper.data.SlayerTaskRepository;
import io.mark.remasteredslayerhelper.ui.panels.SlayerPluginPanel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
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

	@Inject
	private QuestStateCache questStateCache;

	@Inject
	private SlayerHelperConfig config;

	// Refreshed periodically on the client thread so quest-gated master unlocks (e.g. Mortimer)
	// pick up completions without needing a relog; quest state rarely changes so this doesn't
	// need to run every tick.
	private static final int QUEST_STATE_REFRESH_INTERVAL_TICKS = 50;

	private SlayerPluginPanel slayerPanel;
	private NavigationButton navButton;
	private boolean loaded;
	private int ticksSinceQuestStateRefresh;

	@Override
	protected void startUp() {
		loaded = false;
		slayerPanel = injector.getInstance(SlayerPluginPanel.class);
		navButton = getNavButton();
		clientToolbar.addNavigation(navButton);

		if (client.getGameState() == GameState.LOGGED_IN) {
			slayerTaskRepository.load();
			questStateCache.refresh(client);
			loaded = true;
			slayerPanel.onTasksLoaded();
		} else {
			slayerPanel.onLoggedOut();
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
		GameState gameState = event.getGameState();
		if (gameState == GameState.LOGGED_IN && !loaded) {
			slayerTaskRepository.load();
			questStateCache.refresh(client);
			loaded = true;
			slayerPanel.onTasksLoaded();
		} else if (gameState == GameState.LOGIN_SCREEN && loaded) {
			slayerTaskRepository.clear();
			loaded = false;
			slayerPanel.onLoggedOut();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (event.getGroup().equals(SlayerHelperConfig.GROUP) && slayerPanel != null) {
			slayerPanel.setRepositoryLayout(config.repositoryLayout());
		}
	}

	@Provides
	SlayerHelperConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(SlayerHelperConfig.class);
	}

	@Subscribe
	public void onGameTick(GameTick tick) {
		if (!loaded) {
			return;
		}
		if (++ticksSinceQuestStateRefresh >= QUEST_STATE_REFRESH_INTERVAL_TICKS) {
			ticksSinceQuestStateRefresh = 0;
			questStateCache.refresh(client);
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

