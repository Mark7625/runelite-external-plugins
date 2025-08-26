package io.mark.runecast;

import com.google.inject.Provides;
import io.mark.runecast.gui.RuneCastSidebar;
import io.mark.runecast.pages.PageRegistry;
import io.mark.runecast.pages.impl.StatusBars;
import io.mark.runecast.resourcepacks.ResourcePackManager;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ClientTick;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;

@PluginDescriptor(
	name = "RuneCast OBS",
	description = "Provides HP information for OBS browser sources",
	tags = {"obs", "streaming", "hp", "health"}
)
public class RuneCastPlugin extends Plugin
{
	@Inject
	@Getter
	private Client client;

	@Inject
	@Getter
	private RuneCastConfig config;
	
	@Inject
	@Getter
	private Notifier notifier;
	
	@Provides
    RuneCastConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneCastConfig.class);
	}

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private StatusBars statusBars;

	@Inject
	private PageRegistry pageRegistry;

	private HttpServer httpServer;
    private SSEManager sseManager;

	@Inject
	private ResourcePackManager resourcePackManager;

	@Getter
	private RuneCastSidebar sidebar;

	@Override
	protected void startUp()
	{
		resourcePackManager.startUp();
		
		// Register all pages with the PageRegistry
		pageRegistry.registerPage(statusBars);

		if (config.enableHttpServer()) {
            PageManager pageManager = new PageManager(config, resourcePackManager);
			sseManager = new SSEManager(config,pageRegistry);

			
			httpServer = new HttpServer(pageManager, sseManager, config.httpPort(),pageRegistry);

			statusBars.init(sseManager);
			
			sseManager.start();
			httpServer.start();
			String packsInfo = config.resourcePacks().isEmpty() ? " (default theme)" : " with resource packs: " + config.resourcePacks();
			notifier.notify("RuneCast - HTTP server started at: http://localhost:" + config.httpPort() + "/hp" + packsInfo, TrayIcon.MessageType.INFO);
		}
		SwingUtilities.invokeLater(() -> sidebar = injector.getInstance(RuneCastSidebar.class));
	}

	@Override
	public void shutDown()
	{
		if (sidebar != null)
			sidebar.destroy();
		sidebar = null;

		if (httpServer != null) {
			httpServer.stop();
		}
		if (sseManager != null) {
			sseManager.stop();
		}
		resourcePackManager.shutDown();
	}

	@Subscribe
	public void onClientTick(ClientTick clientTick) {
		pageRegistry.updateAllPages();
		statusBars.onGameTick();
	}

}
