package io.mark.hitsplats;

import com.google.inject.Provides;
import io.mark.hitsplats.art.HitsplatSkin;
import io.mark.hitsplats.art.HitsplatSprites;
import io.mark.hitsplats.combat.CombatStyle;
import io.mark.hitsplats.combat.CombatStyleResolver;
import io.mark.hitsplats.combat.CombatStyleTables;
import io.mark.hitsplats.config.HealSplatMode;
import io.mark.hitsplats.config.HitsplatStylesConfig;
import io.mark.hitsplats.heal.HealSource;
import io.mark.hitsplats.heal.HealTracker;
import io.mark.hitsplats.hit.HitsplatTracker;
import io.mark.hitsplats.overlay.HitsplatStylesOverlay;
import io.mark.hitsplats.overlay.NpcHeights;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.SpritePixels;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.itemstats.ItemStatPlugin;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Hitsplat Styles",
	description = "Replaces hitsplats with art from an earlier era - 2002, 2010 or 2011",
	tags = {"hitsplat", "hitsplats", "damage", "heal", "combat", "retro", "fade", "style", "styles", "2002", "2010", "2011"},
	internalName = "hitsplat-styles"
)
@PluginDependency(ItemStatPlugin.class)
public class HitsplatStylesPlugin extends Plugin {
	public static final boolean DEBUG_SLOTS = false;

	private static final int NATIVE_HEAL_WINDOW_CYCLES = 30;
	private static final int NO_NATIVE_HEAL = Integer.MIN_VALUE;
	private static final int NATURAL_REGEN_AMOUNT = 1;

	private static final int[] SUPPORTED_HITMARK_SPRITE_IDS = {
		SpriteID.Hitmark.HITSPLAT_BLUE_MISS,
		SpriteID.Hitmark._9,
		SpriteID.Hitmark._1,
		SpriteID.Hitmark._10,
		SpriteID.Hitmark._24,
		SpriteID.Hitmark._27,
		SpriteID.Hitmark.HITSPLAT_GREEN_POISON,
		SpriteID.Hitmark.HITSPLAT_DARK_GREEN_VENOM,
		SpriteID.Hitmark._3,
		SpriteID.Hitmark._12,
		SpriteID.Hitmark._35,
		SpriteID.Hitmark.BURN_DAMAGE,
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

	@Inject
	private HealTracker healTracker;

	@Inject
	private HealSource healSource;

	@Inject
	private NpcHeights npcHeights;

	private SpritePixels blankSprite;

	private int hitpoints = -1;
	private int pendingHeal;
	private int nativeHealCycle = NO_NATIVE_HEAL;

	@Provides
	HitsplatStylesConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(HitsplatStylesConfig.class);
	}

	@Override
	protected void startUp() {
		overlayManager.add(overlay);
		sprites.load(config.shadowWidth(), config.splatSize());
		combatStyleTables.load();
		npcHeights.load();

		if (DEBUG_SLOTS) {
			return;
		}

		hideNativeHitsplats();

		if (client.getGameState() == GameState.LOGGED_IN) {
			queueRelogMessage("Hitsplat Styles: log out and back in to hide the default hitsplats.");
		}
	}

	@Override
	protected void shutDown() {
		overlayManager.remove(overlay);
		tracker.clear();
		healTracker.clear();
		healSource.clear();
		hitpoints = -1;
		pendingHeal = 0;
		combatStyleResolver.clear();
		combatStyleTables.clear();
		npcHeights.clear();
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
	public void onProjectileMoved(ProjectileMoved event) {
		combatStyleResolver.onProjectileMoved(event.getProjectile());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		GameState state = event.getGameState();

		if (state == GameState.LOADING || state == GameState.LOGIN_SCREEN) {
			combatStyleResolver.clear();
			tracker.clear();
			healTracker.clear();
			healSource.clear();
			pendingHeal = 0;
		}

		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING) {
			hitpoints = -1;
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event) {
		Actor actor = event.getActor();
		Hitsplat hitsplat = event.getHitsplat();
		int cycle = client.getGameCycle();
		boolean local = actor == client.getLocalPlayer();

		int type = hitsplat.getHitsplatType();
		boolean block = HitsplatSkin.isBlock(type);
		HitsplatSkin skin = HitsplatSkin.isSupported(type)
			? HitsplatSkin.forType(type, local)
			: null;
		CombatStyle combatStyle = skin == null ? null
			: block ? CombatStyle.DEFENCE : combatStyleResolver.resolve(actor, cycle);

		if (log.isDebugEnabled()) {
			log.debug("Hitsplat type {} for {} on {}{}", type, hitsplat.getAmount(), actor.getName(),
				skin == null ? " - not supported, left as the game drew it" : " -> " + skin);
		}

		int slot = tracker.add(actor, cycle, hitsplat.getDisappearsOnGameCycle(), skin, combatStyle,
			String.valueOf(hitsplat.getAmount()), block);

		if (!local) {
			return;
		}

		if (type == HitsplatID.HEAL) {
			nativeHealCycle = cycle;
		}

		healTracker.vacate(slot);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!HitsplatStylesConfig.GROUP.equals(event.getGroup())) {
			return;
		}

		if (HitsplatStylesConfig.KEY_SHADOW_WIDTH.equals(event.getKey())
			|| HitsplatStylesConfig.KEY_SPLAT_SIZE.equals(event.getKey())) {
			sprites.load(config.shadowWidth(), config.splatSize());
		}

		if (!config.healSplatMode().isEnabled()) {
			healTracker.clear();
			healSource.clear();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		if (config.healSplatMode().isEnabled()) {
			healSource.onMenuOptionClicked(event);
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event) {
		if (event.getSkill() != Skill.HITPOINTS) {
			return;
		}

		int current = event.getBoostedLevel();
		int previous = hitpoints;
		hitpoints = current;

		if (previous > 0 && current > previous) {
			pendingHeal += current - previous;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		int healed = pendingHeal;
		pendingHeal = 0;

		HealSplatMode mode = config.healSplatMode();
		if (healed <= 0 || !mode.isEnabled()) {
			return;
		}

		int cycle = client.getGameCycle();
		if (nativeHealCycle != NO_NATIVE_HEAL && cycle - nativeHealCycle <= NATIVE_HEAL_WINDOW_CYCLES) {
			return;
		}

		Player local = client.getLocalPlayer();
		if (local == null) {
			return;
		}

		HealSource.UsedItem used = healSource.claim();
		if (used == null && healed <= NATURAL_REGEN_AMOUNT) {
			return;
		}

		int itemId = used != null && mode.isItem() ? used.getItemId() : -1;
		int amount = used != null && config.healOverheal() ? used.getRestored() : healed;

		int busySlots = tracker.busySlots(local, cycle, config.fadeOut() ? config.fadeLength() : 0);
		healTracker.add(cycle, busySlots, amount, itemId);
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
