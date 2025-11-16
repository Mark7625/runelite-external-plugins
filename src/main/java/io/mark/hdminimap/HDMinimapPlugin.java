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
import io.mark.hdminimap.mapelement.MapElementCategories;
import io.mark.hdminimap.mapelement.MapElementManager;
import io.mark.hdminimap.mapelement.MapElementSetting;
import io.mark.hdminimap.render.MinimapStyle;
import io.mark.hdminimap.render.impl.HDRenderer;
import io.mark.hdminimap.ui.MinimapPanel;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.Objects;

@PluginDescriptor(
	name = "HD Minimap",
	description = "Adds a HD Minimap from 2008, as well as the ability to remove icons and scenery from the maps!",
	tags = {"hd", "minimap", "map", "scenery", "icons"}
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

    @Inject
    private MapElementManager mapElementManager;

    private MinimapStyle currentStyle;

    @Setter(AccessLevel.PACKAGE)
    private MinimapPanel panel;

    private NavigationButton button;

    @Inject
    private ClientToolbar clientToolbar;

    private Double lastZoom = null;


    @Provides
    HDMinimapConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HDMinimapConfig.class);
    }

    @Override
	protected void startUp() {
		clientThread.invoke(this::setupPanel);

        currentStyle = config.minimapStyle();
        setMinimapDrawer();
        reloadGame();
        lastZoom = client.getMinimapZoom();
	}

	private void setupPanel()
	{
		mapElementManager.start(client);

		panel = injector.getInstance(MinimapPanel.class);
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		button = NavigationButton.builder()
			.tooltip("Clean Maps")
			.icon(icon)
			.priority(3)
			.panel(panel)
			.build();

		if (config.displaySidebar()) {
			clientToolbar.addNavigation(button);
		}
	}

	@Inject
	private PluginManager pluginManager;

	@Override
	public void shutDown() {
        clientToolbar.removeNavigation(button);
        client.setMinimapTileDrawer(null);
        client.getObjectCompositionCache().reset();
        reloadGame();
        mapElementManager.end();
	}

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (event.getGroup().equals(HDMinimapConfig.CONFIG_GROUP)) {
            if (Objects.equals(event.getKey(), "minimapStyle")) {
                currentStyle = config.minimapStyle();
                setMinimapDrawer();
                log.debug("Minimap style changed to: {}", currentStyle);
            }
            if (Objects.equals(event.getKey(), "minimapSideBar")) {
                if (config.displaySidebar()) {
                    clientToolbar.addNavigation(button);
                } else {
                    clientToolbar.removeNavigation(button);
                }
            }
        }
        if (event.getGroup().equals(MapElementManager.CONFIG_GROUP)) {
			reloadGame();
        }
    }

    public void setMinimapDrawer() {
        if (currentStyle != MinimapStyle.DEFAULT) {
            client.setMinimapTileDrawer(this::drawMapTile);
        } else {
            client.setMinimapTileDrawer(null);
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        if (!config.displaySidebar()) return;
        double zoom = client.getMinimapZoom();
        if (lastZoom != zoom) {
            lastZoom = zoom;

			for (MapElementCategories entry : MapElementCategories.values())
			{
				MapElementSetting setting = mapElementManager.getSetting(entry.getDefaultName());
				if (setting.isDisabled() && setting.getScale() != null) {
					mapElementManager.updateIcon(entry.getDefaultName());
				}
			}
			reloadGame();
        }
    }

    /**
     * Refreshes the game state if needed when switching to HD117 minimap style
     */
    private void reloadGame() {
        clientThread.invoke(() -> {
            if (client.getGameState() == GameState.LOGGED_IN) {
                client.setGameState(GameState.LOADING);
                if (client.getWorldMap().getWorldMapRenderer().isLoaded()) {
                    client.getWorldMap().initializeWorldMap(client.getWorldMap().getWorldMapData());
                }
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
