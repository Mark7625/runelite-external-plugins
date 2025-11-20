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
import io.mark.hdminimap.mapelement.MapElementManager;
import io.mark.hdminimap.mapelement.MapElementSetting;
import io.mark.hdminimap.render.MinimapStyle;
import io.mark.hdminimap.render.impl.HD117Renderer;
import io.mark.hdminimap.render.impl.HDRenderer;
import io.mark.hdminimap.ui.MinimapPanel;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.Map;
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
    private HD117Renderer hd117Renderer;


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

    @Getter
    @Setter
    private MinimapStyle fallback;

    private Double lastZoom = null;


    @Provides
    HDMinimapConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HDMinimapConfig.class);
    }

    @Override
	protected void startUp() {
        clientThread.invoke((() -> {
            mapElementManager.start();

            panel = injector.getInstance(MinimapPanel.class);

            final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");

            button = NavigationButton.builder()
                    .tooltip("Enhanced Minimap")
                    .icon(icon)
                    .priority(3)
                    .panel(panel)
                    .build();

            if (config.displaySidebar()) {
                clientToolbar.addNavigation(button);
            }
        }));

        currentStyle = config.minimapStyle();
        setMinimapDrawer();
        reloadGame();
        lastZoom = client.getMinimapZoom();
	}


	@Inject
	private PluginManager pluginManager;

	@Override
	public void shutDown() {
        clientToolbar.removeNavigation(button);
        client.setMinimapTileDrawer(null);
        client.getObjectCompositionCache().reset();
        reloadGame();
        mapElementManager.clear();
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
                client.getObjectCompositionCache().reset();
                reloadGame();
            }
        }
        if (event.getGroup().equals(MapElementManager.CONFIG_GROUP)) {
            if (mapElementManager.isCategoryInCurrentArea(event.getKey())) {
                client.getObjectCompositionCache().reset();
                reloadGame();
            }
        }
    }

    public void setMinimapDrawer()
    {
        MinimapStyle style = config.minimapStyle();

        if (style == MinimapStyle.DEFAULT)
        {
            client.setMinimapTileDrawer(null);
            return;
        }

        client.setMinimapTileDrawer((tile, tx, ty, px0, py0, px1, py1) ->
        {
            boolean handled = drawCustomMinimap(tile, tx, ty, px0, py0, px1, py1);

            if (!handled)
            {
                client.setMinimapTileDrawer(null);
            }
        });
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        if (!config.displaySidebar()) return;
        double zoom = client.getMinimapZoom();
        if (lastZoom != zoom) {
            lastZoom = zoom;

            for (String category : mapElementManager.getCurrentAreaCategories()) {
                MapElementSetting setting = mapElementManager.getSetting(category);
                if (setting.isDisabled() && setting.getScale() != null) {
                    client.getObjectCompositionCache().reset();
                    reloadGame();
                    return;
                }
            }
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged stateChanged) {
        if (stateChanged.getGameState() == GameState.LOGGED_IN && config.displaySidebar()) {
            mapElementManager.updateIcons();
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
     * Attempts to draw a minimap tile using the currently selected minimap style.
     *
     * <p>If the selected style fails to draw the tile due to an exception,
     * the failure is logged, and this method returns {@code false} to indicate
     * that the tile was not drawn.</p>
     *
     * @param tile the tile to draw
     * @param tx   tile X coordinate
     * @param ty   tile Y coordinate
     * @param px0  pixel X start coordinate
     * @param py0  pixel Y start coordinate
     * @param px1  pixel X end coordinate
     * @param py1  pixel Y end coordinate
     * @return {@code true} if the tile was drawn successfully,
     *         {@code false} if an exception occurred during drawing
     */
    public boolean drawCustomMinimap(Tile tile, int tx, int ty, int px0, int py0, int px1, int py1) {
        try {
            switch (config.minimapStyle()) {
                case HD117 -> hd117Renderer.drawMapTile(tile, tx, ty, px0, py0, px1, py1);
                case HD    -> hdRenderer.drawMapTile(tile, tx, ty, px0, py0, px1, py1);
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to draw minimap tile at ({}, {}) with style {}", tx, ty, config.minimapStyle(), e);
            return false;
        }
    }


    @Subscribe
    public void onPluginMessage(PluginMessage pluginMessage) {
        if ("minimap".equals(pluginMessage.getName()) && "117hd".equals(pluginMessage.getNamespace())) {
            Map<String, Object> payload = pluginMessage.getData();

            if (payload != null) {
                int[][][][] paintColors = (int[][][][]) payload.get("paintColors");
                int[][][][][] modelColors = (int[][][][][]) payload.get("modelColors");
                hd117Renderer.setMinimapTileModelColorsLighting(modelColors);
                hd117Renderer.setMinimapTilePaintColorsLighting(paintColors);

            }
        }
    }

}
