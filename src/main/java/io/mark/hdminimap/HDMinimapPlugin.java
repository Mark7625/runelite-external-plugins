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
package io.mark.hdminimap;

import com.google.inject.Provides;
import io.mark.hdminimap.render.MinimapStyle;
import io.mark.hdminimap.render.impl.HDRenderer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;

import javax.inject.Inject;
import java.util.Objects;

@PluginDescriptor(
	name = "HD Minimap",
	description = "Adds a HD Minimap from 2008!",
	tags = {"hd", "minimap"}
)

@Slf4j
public class HDMinimapPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private HDRenderer hdRenderer;

    @Inject
    private HDMinimapConfig config;

    private MinimapStyle currentStyle;

    @Provides
    HDMinimapConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HDMinimapConfig.class);
    }

    @Override
	protected void startUp() {
        currentStyle = config.minimapStyle();
        setMinimapDrawer();
        reloadGame();
	}

	@Inject
	private PluginManager pluginManager;

	@Override
	public void shutDown() {
        client.setMinimapTileDrawer(null);
        client.getObjectCompositionCache().reset();
        reloadGame();
	}

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (event.getGroup().equals(HDMinimapConfig.CONFIG_GROUP)) {
            if (Objects.equals(event.getKey(), "minimapStyle")) {
                currentStyle = config.minimapStyle();
                setMinimapDrawer();
                log.debug("Minimap style changed to: {}", currentStyle);
            }
        }
    }

    public void setMinimapDrawer() {
        if (currentStyle != MinimapStyle.DEFAULT) {
            client.setMinimapTileDrawer(this::drawMapTile);
        } else {
            client.setMinimapTileDrawer(null);
        }
    }

    /**
     * Refreshes the game state if needed when switching to HD117 minimap style
     */
    private void reloadGame() {
        clientThread.invoke(() -> {
            if (client.getGameState() == GameState.LOGGED_IN) {
                client.setGameState(GameState.LOADING);
            }
        });
    }

    /**
     * Draws a minimap tile using the currently selected renderer
     *
     * @param tile the tile to draw
     * @param tx   tile x coordinate
     * @param ty   tile y coordinate
     * @param px0  pixel x start coordinate
     * @param py0  pixel y start coordinate
     * @param px1  pixel x end coordinate
     * @param py1  pixel y end coordinate
     */
    public void drawMapTile(Tile tile, int tx, int ty, int px0, int py0, int px1, int py1) {
        hdRenderer.drawMapTile(tile, tx, ty, px0, py0, px1, py1);
    }


}
