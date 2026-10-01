package io.mark.hitsplats;

import com.google.inject.Provides;
import io.mark.hitsplats.art.HitsplatSkin;
import io.mark.hitsplats.art.HitsplatSprites;
import io.mark.hitsplats.combat.CombatStyle;
import io.mark.hitsplats.combat.CombatStyleResolver;
import io.mark.hitsplats.combat.CombatStyleTables;
import io.mark.hitsplats.config.HitsplatStylesConfig;
import io.mark.hitsplats.hit.HitsplatTracker;
import io.mark.hitsplats.overlay.HitsplatStylesOverlay;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.SpritePixels;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Hitsplat Styles",
	description = "Replaces hitsplats with art from an earlier era - 2002, 2010 or 2011",
	tags = {"hitsplat", "hitsplats", "damage", "combat", "retro", "2002", "2010", "2011"},
	internalName = "hitsplat-styles"
)
public class HitsplatStylesPlugin extends Plugin {
	private static final int[] SUPPORTED_HITMARK_SPRITE_IDS = {
		SpriteID.Hitmark._0,
		SpriteID.Hitmark._1,
		SpriteID.Hitmark._2,
		SpriteID.Hitmark._3,
		SpriteID.Hitmark._4,
		SpriteID.Hitmark._5,
		SpriteID.Hitmark.HITSPLAT_DARK_GREEN_VENOM,
	};

	@Inject
	private Client client;

	@Inject
	private HitsplatStylesConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private HitsplatStylesOverlay overlay;

	@Inject
	private HitsplatSprites sprites;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private CombatStyleResolver combatStyleResolver;

	@Inject
	private CombatStyleTables combatStyleTables;

	@Inject
	private HitsplatTracker tracker;

	private SpritePixels blankSprite;

	@Provides
	HitsplatStylesConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(HitsplatStylesConfig.class);
	}

	@Override
	protected void startUp() {
		overlayManager.add(overlay);
		sprites.load(config.style());
		combatStyleTables.load();
		hideNativeHitsplats();

		if (client.getGameState() == GameState.LOGGED_IN) {
			queueRelogMessage("Hitsplat Styles: log out and back in to hide the default hitsplats.");
		}
	}

	@Override
	protected void shutDown() {
		overlayManager.remove(overlay);
		tracker.clear();
		combatStyleResolver.clear();
		combatStyleTables.clear();
		sprites.clear();
		showNativeHitsplats();
	}

	private void hideNativeHitsplats() {
		blankSprite = client.createSpritePixels(new int[]{0}, 1, 1);

		Map<Integer, SpritePixels> overrides = client.getSpriteOverrides();
		for (int spriteId : SUPPORTED_HITMARK_SPRITE_IDS) {
			overrides.put(spriteId, blankSprite);
		}
	}

	private void showNativeHitsplats() {
		if (blankSprite == null) {
			return;
		}

		Map<Integer, SpritePixels> overrides = client.getSpriteOverrides();
		for (int spriteId : SUPPORTED_HITMARK_SPRITE_IDS) {
			overrides.remove(spriteId, blankSprite);
		}

		blankSprite = null;
	}

	private void queueRelogMessage(String message) {
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (HitsplatStylesConfig.GROUP.equals(event.getGroup()) && HitsplatStylesConfig.KEY_STYLE.equals(event.getKey())) {
			sprites.load(config.style());
		}
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event) {
		combatStyleResolver.onProjectileMoved(event.getProjectile());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOADING || event.getGameState() == GameState.LOGIN_SCREEN) {
			combatStyleResolver.clear();
			tracker.clear();
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event) {
		Actor actor = event.getActor();
		Hitsplat hitsplat = event.getHitsplat();
		int cycle = client.getGameCycle();

		int type = hitsplat.getHitsplatType();
		boolean block = HitsplatSkin.isBlock(type);
		HitsplatSkin skin = HitsplatSkin.isSupported(type)
			? HitsplatSkin.forType(type, actor == client.getLocalPlayer())
			: null;
		CombatStyle combatStyle = skin == null ? null
			: block ? CombatStyle.DEFENCE : combatStyleResolver.resolve(actor, cycle);

		tracker.add(actor, cycle, skin, combatStyle, String.valueOf(hitsplat.getAmount()), block);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event) {
		tracker.remove(event.getActor());
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event) {
		tracker.remove(event.getActor());
	}
}
