package io.mark.fishingspots;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.Renderable;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

@Singleton
@Slf4j
public class ClassicFishingSpotReplacer {

	private static final int CLASSIC_FISHING_SPOT_MODEL_ID = 1491;

	private static final int HIDDEN_POSE_FRAME = 6;

	private static final int LIGHT_AMBIENT = 128;
	private static final int LIGHT_CONTRAST = 768;
	private static final int LIGHT_X = -50;
	private static final int LIGHT_Y = -10;
	private static final int LIGHT_Z = -50;

	private static final String HD_PLUGIN_NAME = "117 HD";
	private static final String HD_WARNING =
		"Classic Fishing Spots does not support 117 HD, which draws its own fishing spots.";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	private final Map<NPC, RuneLiteObject> spots = new HashMap<>();

	private final RenderCallback renderCallback = new RenderCallback() {
		@Override
		public boolean addEntity(Renderable renderable, boolean drawingUI) {
			pinHiddenFrame(renderable);
			return true;
		}
	};

	private Model classicModel;

	private boolean hdActive;
	private boolean hdWarningSent;

	public void startUp() {
		eventBus.register(this);
		renderCallbackManager.register(renderCallback);
		clientThread.invoke(() -> {
			hdActive = isHdEnabled();
			warnAboutHd();
			spawnForNpcsInScene();
		});
	}

	public void shutDown() {
		eventBus.unregister(this);
		renderCallbackManager.unregister(renderCallback);
		clientThread.invoke(() -> {
			despawnAll();
			classicModel = null;
			hdWarningSent = false;
		});
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned npcSpawned) {
		spawn(npcSpawned.getNpc());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned npcDespawned) {
		RuneLiteObject object = spots.remove(npcDespawned.getNpc());
		if (object != null) {
			object.setActive(false);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		GameState state = gameStateChanged.getGameState();
		if (state == GameState.LOADING || state == GameState.HOPPING || state == GameState.LOGIN_SCREEN) {
			despawnAll();
		} else if (state == GameState.LOGGED_IN) {
			warnAboutHd();
			spawnForNpcsInScene();
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick) {
		if (spots.isEmpty()) {
			return;
		}

		int plane = client.getTopLevelWorldView().getPlane();
		for (Map.Entry<NPC, RuneLiteObject> spot : spots.entrySet()) {
			spot.getValue().setLocation(spot.getKey().getLocalLocation(), plane);
		}
	}

	@Subscribe
	public void onPluginChanged(PluginChanged pluginChanged) {
		if (!HD_PLUGIN_NAME.equals(pluginChanged.getPlugin().getName())) {
			return;
		}

		boolean active = pluginChanged.isLoaded();
		clientThread.invoke(() -> setHdActive(active));
	}

	private void pinHiddenFrame(Renderable renderable) {
		if (spots.isEmpty() || !(renderable instanceof NPC)) {
			return;
		}

		NPC npc = (NPC) renderable;
		if (FishingSpots.isFishingSpot(npc.getId())) {
			npc.setPoseAnimationFrame(HIDDEN_POSE_FRAME);
		}
	}

	private void setHdActive(boolean active) {
		if (active == hdActive) {
			return;
		}

		hdActive = active;
		hdWarningSent = false;

		if (active) {
			despawnAll();
			warnAboutHd();
		} else {
			spawnForNpcsInScene();
		}
	}

	private void warnAboutHd() {
		if (!hdActive || hdWarningSent || client.getGameState() != GameState.LOGGED_IN) {
			return;
		}

		client.addChatMessage(ChatMessageType.CONSOLE, "", HD_WARNING, null);
		hdWarningSent = true;
	}

	private boolean isHdEnabled() {
		for (Plugin plugin : pluginManager.getPlugins()) {
			if (HD_PLUGIN_NAME.equals(plugin.getName())) {
				return pluginManager.isPluginEnabled(plugin);
			}
		}
		return false;
	}

	private void spawnForNpcsInScene() {
		if (hdActive || client.getGameState() != GameState.LOGGED_IN) {
			return;
		}

		for (NPC npc : client.getTopLevelWorldView().npcs()) {
			spawn(npc);
		}
	}

	private void spawn(NPC npc) {
		if (hdActive || !FishingSpots.isFishingSpot(npc.getId()) || spots.containsKey(npc)) {
			return;
		}

		Model model = getClassicModel();
		if (model == null) {
			return;
		}

		RuneLiteObject fishingSpot = client.createRuneLiteObject();
		fishingSpot.setModel(model);
		fishingSpot.setLocation(npc.getLocalLocation(), client.getTopLevelWorldView().getPlane());
		fishingSpot.setActive(true);

		spots.put(npc, fishingSpot);
	}

	private void despawnAll() {
		for (RuneLiteObject object : spots.values()) {
			object.setActive(false);
		}
		spots.clear();
	}

	private Model getClassicModel() {
		if (classicModel != null) {
			return classicModel;
		}

		ModelData modelData = client.loadModelData(CLASSIC_FISHING_SPOT_MODEL_ID);
		if (modelData == null) {
			log.debug("Classic fishing spot model {} could not be loaded", CLASSIC_FISHING_SPOT_MODEL_ID);
			return null;
		}

		classicModel = modelData.light(LIGHT_AMBIENT, LIGHT_CONTRAST, LIGHT_X, LIGHT_Y, LIGHT_Z);
		return classicModel;
	}
}
