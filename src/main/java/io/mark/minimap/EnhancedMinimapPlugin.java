/*
 * Copyright (c) 2022, Abex
 * Copyright (c) 2022, Mark
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.mark.minimap;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;

@PluginDescriptor(
	name = "Enhanced Minimap",
	configName = "HDMinimapPlugin",
	description = "Adds a HD Minimap from 2008!",
	tags = {"hd", "minimap", "Icons"}
)

@Slf4j
public class EnhancedMinimapPlugin extends Plugin
{

	@Inject
	private Client client;

	@Inject
	private EnhancedMinimapConfig config;

	@Inject
	private MinimapRender minimapRender;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private SpriteManager spriteManager;

	private boolean needsIconUpdate = false;

	private double cacheMinimapZoom = 0.0;

	@Provides
	EnhancedMinimapConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(EnhancedMinimapConfig.class);
	}

	@Override
	protected void startUp()
	{
		if (config.hdMinimap()) {
			client.setMinimapTileDrawer(minimapRender::drawTile);
		}
	}

	@Subscribe
	public void onBeforeRender(BeforeRender beforeRender) {
		client.setMinimapTileDrawer(!config.hdMinimap() ? null : minimapRender::drawTile);
	}

	@Override
	public void shutDown() {
		client.setMinimapTileDrawer(null);
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged gameStateChanged) {
		if (gameStateChanged.getGameState() == GameState.LOGGING_IN) {
			cacheMinimapZoom = client.getMinimapZoom();
			needsIconUpdate = true;
		}
	}

	@Subscribe
	private void onClientTick(ClientTick tick) {
		if (cacheMinimapZoom != client.getMinimapZoom()) {
			cacheMinimapZoom = client.getMinimapZoom();
			System.out.println(cacheMinimapZoom);
			needsIconUpdate = true;
		}
		if (needsIconUpdate) {
			updateIcons();
		}
	}

	public void updateIcons() {
		if (cacheMinimapZoom <= 2.75 && config.scaleMinimapIcons()) {
			for (MinimapFunctions value : MinimapFunctions.values()) {
				System.out.println("DFSDF");
				String file = 1453 + ".png";
				SpritePixels spritePixels = getFileSpritePixels(file);
			}

		}
		needsIconUpdate = false;
	}

	private SpritePixels getFileSpritePixels(String file)
	{
		try
		{
			log.debug("Loading: {}", file);
			BufferedImage image = ImageUtil.loadImageResource(this.getClass(), file);
			return ImageUtil.getImageSpritePixels(image, client);
		}
		catch (RuntimeException ex)
		{
			log.debug("Unable to load image: ", ex);
		}

		return null;
	}

}
