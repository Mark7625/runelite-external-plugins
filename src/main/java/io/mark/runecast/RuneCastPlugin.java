package io.mark.runecast;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.ClientTick;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.awt.*;
import java.util.Random;

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

	private HttpServer httpServer;
	private PageManager pageManager;
	private SSEManager sseManager;

	@Override
	protected void startUp()
	{
		if (config.enableHttpServer())
		{
			pageManager = new PageManager(config);
			sseManager = new SSEManager(config);
			httpServer = new HttpServer(pageManager, sseManager, config.httpPort());
			
			sseManager.start();
			httpServer.start();
			
			notifier.notify("RuneCast OBS - HTTP server started at: http://localhost:" + config.httpPort() + "/hp", TrayIcon.MessageType.INFO);
			System.out.println("http://localhost:" + config.httpPort() + "/hp");
		}
	}

	@Override
	public void shutDown()
	{
		if (httpServer != null) {
			httpServer.stop();
		}
		if (sseManager != null) {
			sseManager.stop();
		}
	}

	@Subscribe
	public void onClientTick(ClientTick clientTick)
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null && sseManager != null)
		{
			int newHp = new Random().nextInt(99) + 1;
			int newMaxHp = client.getBoostedSkillLevel(Skill.HITPOINTS);
			sseManager.updateHp(newHp, newMaxHp);
			sseManager.forceUpdate();
		}
	}
}
