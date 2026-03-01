/*
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
package io.mark.f2p;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import io.mark.f2p.config.ActiveType;
import io.mark.f2p.overlay.WorldMapOverlay;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.PostItemComposition;
import net.runelite.api.events.WorldChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.http.api.worlds.WorldType;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@PluginDescriptor(
	name = "F2P Utilities",
	description = "Overlay members items with customizable visual effects (grayscale, outline, or fill)",
	tags = {"f2p", "members", "items", "overlay", "highlight", "grand exchange"}
)
@Slf4j
public class F2pPlugin extends Plugin
{
	private static final String EXCLUDED_ITEMS_KEY = "excludedItemIds";

	@Inject
	@Getter
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	@Inject
	@Getter
	private F2pConfig config;

	@Inject
	@Getter
	private ItemManager itemManager;

	@Provides
	F2pConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(F2pConfig.class);
	}

	@Inject
	private ClientThread clientThread;

	@Inject
	WorldService worldService;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ItemOverlay overlay;

    @Inject
    private WorldMapOverlay worldMapOverlay;

	@Inject
	private KeyManager keyManager;

	private volatile boolean excludeKeyHeld = false;

	private final HotkeyListener excludeKeyListener = new HotkeyListener(() -> config.excludeKeybind())
	{
		@Override
		public void hotkeyPressed()
		{
			excludeKeyHeld = true;
		}

		@Override
		public void hotkeyReleased()
		{
			excludeKeyHeld = false;
		}
	};

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.LOGGING_IN) {
			overlay.invalidateAllCaches();
		}
	}

    @Override
    protected void startUp() {
        worldMapOverlay.loadPointsFromJson();
        overlayManager.add(overlay);
        overlayManager.add(worldMapOverlay);
        keyManager.registerKeyListener(excludeKeyListener);
    }

    @Override
    protected void shutDown() {
        keyManager.unregisterKeyListener(excludeKeyListener);
        excludeKeyHeld = false;
        overlayManager.remove(overlay);
        overlayManager.remove(worldMapOverlay);
        overlay.invalidateAllCaches();
        client.getItemCompositionCache().reset();
    }

	void saveExcludedItems(Set<Integer> itemIds) {
		if (itemIds == null || itemIds.isEmpty()) {
			configManager.unsetConfiguration(F2pConfig.GROUP, EXCLUDED_ITEMS_KEY);
			return;
		}
		String json = gson.toJson(List.copyOf(itemIds));
		configManager.setConfiguration(F2pConfig.GROUP, EXCLUDED_ITEMS_KEY, json);
	}

	public Set<Integer> getExcludedItemIds() {
		String json = configManager.getConfiguration(F2pConfig.GROUP, EXCLUDED_ITEMS_KEY);
		if (json == null || json.isBlank()) {
			return Collections.emptySet();
		}
		try {
			List<Integer> list = gson.fromJson(json, new TypeToken<List<Integer>>(){}.getType());
			return list != null ? new HashSet<>(list) : Collections.emptySet();
		} catch (Exception e) {
			log.warn("Failed to parse excluded items JSON", e);
			return Collections.emptySet();
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event) {
		if (!isActive()) {
			return;
		}
		Keybind keybind = config.excludeKeybind();
		if (keybind == null || Keybind.NOT_SET.equals(keybind) || !excludeKeyHeld) {
			return;
		}
		MenuEntry entry = event.getMenuEntry();
		int itemId = getItemIdFromEntry(entry);
		if (itemId <= 0) {
			return;
		}
		boolean isMembers = client.getItemDefinition(itemId).isMembers();
		if (!isMembers) {
			return;
		}
		// Only add for Examine option to avoid duplicate entries per item
		if (!"Examine".equals(entry.getOption())) {
			return;
		}
		Set<Integer> excluded = getExcludedItemIds();
		boolean isExcluded = excluded.contains(itemId);
		MenuEntry parentEntry = client.createMenuEntry(-1)
			.setOption("F2P Utilities")
			.setTarget(entry.getTarget())
			.setType(MenuAction.RUNELITE);
		Menu submenu = parentEntry.createSubMenu();
		String subOption = isExcluded ? "Include in overlay" : "Exclude from overlay";
		submenu.createMenuEntry(-1)
			.setOption(subOption)
			.setType(MenuAction.RUNELITE)
			.onClick(e -> toggleExcludeItem(itemId));
	}

	private int getItemIdFromEntry(MenuEntry entry) {
		int itemId = entry.getItemId();
		if (itemId > 0) {
			return itemId;
		}
		if (entry.getWidget() != null && entry.getWidget().getItemId() > 0) {
			return entry.getWidget().getItemId();
		}
		return -1;
	}

	private void toggleExcludeItem(int itemId) {
		Set<Integer> excluded = new HashSet<>(getExcludedItemIds());
		if (excluded.contains(itemId)) {
			excluded.remove(itemId);
		} else {
			excluded.add(itemId);
		}
		saveExcludedItems(excluded);
		overlay.invalidateCache();
		clientThread.invokeLater(() -> client.getItemCompositionCache().reset());
	}

	@Subscribe
	public void onPostItemComposition(PostItemComposition event) {
		ItemComposition item = event.getItemComposition();
		if (item.isMembers() && config.icon() != -1 && shouldShowOverlayForItem(item.getId())) {
			event.getItemComposition().setName(formatName(event.getItemComposition().getName()));
		}
	}

	boolean shouldShowOverlayForItem(int itemId) {
		return isActive() && !overlay.isExcluded(itemId);
	}

    @Subscribe
    public void onWorldChanged(WorldChanged event) {
        try {
            if (client == null || worldService == null) {
                return;
            }
            overlay.invalidateAllCaches();
        } catch (Exception e) {
            log.warn("Error handling world change", e);
            overlay.invalidateAllCaches();
        }
    }

	private String formatName(String name) {
		return "<img=" + config.icon() + ">" + name;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{

		if (!event.getGroup().equals("f2p"))
		{
			return;
		}

		String key = event.getKey();

		switch (key) {
			case "overlayMode":
			case "overlayColor":
			case "overlayAlpha":
				overlay.invalidateCache();
				break;
			case "excludedItemIds":
				overlay.invalidateCache();
				clientThread.invokeLater(() -> client.getItemCompositionCache().reset());
				break;
			case "excludeKeybind":
				excludeKeyHeld = false;
				break;
		}

	}

	public boolean isActive() {
        ActiveType type = config.overlayActive();
		if (type == null) {
			return false;
		}
		
		if (type == ActiveType.ALWAYS) {
			return true;
		}
		
		if (type == ActiveType.NEVER) {
			return false;
		}
		
		if (type == ActiveType.FREE_WORLDS_ONLY) {
			try {
				if (client == null || worldService == null) {
					return false;
				}
				int world = client.getWorld();
				return !Objects.requireNonNull(worldService.getWorlds()).findWorld(world).getTypes().contains(WorldType.MEMBERS);
			} catch (Exception e) {
				return false;
			}
		}
		
		return false;
	}

}
