package io.mark.globes;

import com.google.inject.Provides;
import io.mark.globes.model.LevelUpGlobe;
import io.mark.globes.model.XpGlobe;
import io.mark.globes.model.quest.QuestData;
import io.mark.globes.model.quest.QuestUnlockResult;
import io.mark.globes.model.skill.SkillData;
import io.mark.globes.overlay.LevelUpGlobesOverlay;
import io.mark.globes.overlay.XpGlobesOverlay;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.xptracker.XpTrackerPlugin;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@PluginDescriptor(
		name = "Remastered Xp Globes",
		description = "Improves xp globes and adds level up notifications like 2011",
		tags = {"2011", "xp", "globes", "rs2", "level", "skill", "experience"}
)
@PluginDependency(XpTrackerPlugin.class)
public class RemasteredXpGlobes extends Plugin {

	private static final long XP_SYNC_PENDING_MS = 1500;
	private static final int STATS_TAB_CHILD_COUNT = 25;

	private XpGlobe[] globeCache = new XpGlobe[Skill.values().length];
	private int[] previousLevels = new int[Skill.values().length];
	private boolean[] skillInitialized = new boolean[Skill.values().length];
	private int initializedSkillCount = 0;
	private boolean levelsInitialized = false;
	private long xpDropSyncPendingTime = 0;

	@Getter
	private final List<XpGlobe> xpGlobes = new ArrayList<>();

	@Getter
	private final List<LevelUpGlobe> levelUpQueue = new ArrayList<>();

	@Getter
	private LevelUpGlobe currentLevelUp;

	@Inject
	private Client client;

	@Inject
	private RemasteredXpGlobesConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private XpGlobesOverlay overlay;

	@Inject
	private LevelUpGlobesOverlay levelUpOverlay;

	@Inject
	private QuestData questData;

	@Inject
	private SkillData skillData;

	@Inject
	@Named("developerMode")
	boolean developerMode;

	@Provides
	RemasteredXpGlobesConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(RemasteredXpGlobesConfig.class);
	}

	@Override
	protected void startUp() {
		overlayManager.add(overlay);
		overlayManager.add(levelUpOverlay);
		levelsInitialized = false;
		questData.load();
		skillData.load();

		handeSkillPulseGlowState();
		if (client.getGameState() == GameState.LOGGED_IN) {
			initializeLevelTrackingFromClient();
			overlay.initPreviousXp();
			xpDropSyncPendingTime = System.currentTimeMillis();
		}
	}

	@Override
	public void shutDown() {
		resetGlobeState();
		overlay.clearCache();
		overlay.clearXpDrops();
		levelUpOverlay.clearCache();
		handeSkillPulseGlowState();
		overlayManager.remove(overlay);
		overlayManager.remove(levelUpOverlay);
		globeCache = new XpGlobe[Skill.values().length];
		previousLevels = new int[Skill.values().length];
		skillInitialized = new boolean[Skill.values().length];
		initializedSkillCount = 0;
		levelsInitialized = false;
		xpDropSyncPendingTime = 0;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged) {
		if (Objects.equals(configChanged.getKey(), "skillIconMode")) {
			handeSkillPulseGlowState();
		}
		if (Objects.equals(configChanged.getKey(), "customSpritesPath") || Objects.equals(configChanged.getKey(), "globeStyle")) {
			overlay.clearCache();
			levelUpOverlay.clearCache();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged statChanged) {
		Skill skill = statChanged.getSkill();
		int currentXp = statChanged.getXp();
		int currentLevel = statChanged.getLevel();
		int skillIdx = skill.ordinal();
		Instant now = Instant.now();

		overlay.onStatChanged(skill, currentXp);

		if (!levelsInitialized) {
			if (!skillInitialized[skillIdx]) {
				skillInitialized[skillIdx] = true;
				initializedSkillCount++;
			}

			previousLevels[skillIdx] = currentLevel;

			if (initializedSkillCount == Skill.values().length) {
				levelsInitialized = true;
			}

			return;
		}

		int previousLevel = previousLevels[skillIdx];

		if (currentLevel > previousLevel && !config.disableLevelUpPopups()) {
			Map<Skill, Integer> playerLevels = new HashMap<>();
			for (Skill s : Skill.values()) {
				playerLevels.put(s, client.getRealSkillLevel(s));
			}
			playerLevels.put(skill, currentLevel);

			QuestUnlockResult questUnlockResult = questData.checkQuestUnlocks(skill, currentLevel, previousLevel, playerLevels);

			LevelUpGlobe levelUp = new LevelUpGlobe(
					skill,
					currentLevel,
					now,
					questUnlockResult,
					skillData,
					previousLevel,
					config.maxMilestones(),
					config.questRequirementMode(),
					config.skillUnlockRequirementMode(),
					config.showSkillLevelUps(),
					playerLevels
			);
			levelUpQueue.add(levelUp);
			processLevelUpQueue();
		}

		if (currentLevelUp != null && !config.disableLevelUpPopups()) {
			previousLevels[skillIdx] = currentLevel;
			return;
		}

		XpGlobe cachedGlobe = globeCache[skillIdx];
		if (cachedGlobe != null && cachedGlobe.getCurrentXp() >= currentXp) {
			previousLevels[skillIdx] = currentLevel;
			return;
		}

		int displayLevel = currentLevel;
		if (displayLevel >= Experience.MAX_REAL_LEVEL) {
			if (config.hideMaxed()) {
				previousLevels[skillIdx] = currentLevel;
				return;
			}
			if (config.showVirtualLevel()) {
				displayLevel = Experience.getLevelForXp(currentXp);
			}
		}

		if (cachedGlobe == null) {
			globeCache[skillIdx] = new XpGlobe(skill, currentXp, displayLevel, now);
			previousLevels[skillIdx] = currentLevel;
			cachedGlobe = globeCache[skillIdx];
		}

		cachedGlobe.setSkill(skill);
		cachedGlobe.setCurrentXp(currentXp);
		cachedGlobe.setCurrentLevel(displayLevel);
		cachedGlobe.setTime(now);
		addXpGlobe(cachedGlobe);
		previousLevels[skillIdx] = currentLevel;
	}

	private void processLevelUpQueue() {
		if (currentLevelUp == null && !levelUpQueue.isEmpty()) {
			currentLevelUp = levelUpQueue.remove(0);
		}
	}

	private void addXpGlobe(XpGlobe xpGlobe) {
		XpGlobe existingGlobe = xpGlobes.stream()
				.filter(globe -> globe.getSkill() == xpGlobe.getSkill())
				.findFirst()
				.orElse(null);

		if (existingGlobe != null) {
			existingGlobe.setCurrentXp(xpGlobe.getCurrentXp());
			existingGlobe.setCurrentLevel(xpGlobe.getCurrentLevel());
			existingGlobe.setTime(xpGlobe.getTime());
		} else {
			xpGlobes.add(xpGlobe);
			if (xpGlobes.size() > config.maximumGlobes()) {
				xpGlobes.stream().min(Comparator.comparing(XpGlobe::getTime)).ifPresent(xpGlobes::remove);
			}
		}
	}

	@Schedule(period = 1, unit = ChronoUnit.SECONDS)
	public void removeExpiredXpGlobes() {
		if (xpDropSyncPendingTime > 0 && System.currentTimeMillis() - xpDropSyncPendingTime >= XP_SYNC_PENDING_MS) {
			overlay.syncPreviousXpFromClient();
			xpDropSyncPendingTime = 0;
		}

		if (!xpGlobes.isEmpty()) {
			Instant expireTime = Instant.now().minusSeconds(config.xpOrbDuration());
			xpGlobes.removeIf(globe -> globe.getTime().isBefore(expireTime));
		}

		if (currentLevelUp != null) {
			long levelUpDurationSec = getLevelUpDurationSeconds(currentLevelUp);
			Instant expireTime = Instant.now().minusSeconds(levelUpDurationSec);
			if (currentLevelUp.getTime().isBefore(expireTime)) {
				currentLevelUp = null;
				processLevelUpQueue();
			}
		}
	}

	private long getLevelUpDurationSeconds(LevelUpGlobe levelUp) {
		int maxMilestones = 0;
		if (config.showMilestones() && levelUp.getMilestones() != null) {
			maxMilestones = Math.min(config.maxMilestones(), levelUp.getMilestones().size());
		}
		return (3300 + maxMilestones * 3000L + 2000) / 1000;
	}

	private void resetGlobeState() {
		xpGlobes.clear();
		globeCache = new XpGlobe[Skill.values().length];
		levelUpQueue.clear();
		currentLevelUp = null;
		Arrays.fill(skillInitialized, false);
		Arrays.fill(previousLevels, 0);
		initializedSkillCount = 0;
		levelsInitialized = false;
	}

	private void initializeLevelTrackingFromClient() {
		for (Skill skill : Skill.values()) {
			int idx = skill.ordinal();
			previousLevels[idx] = client.getRealSkillLevel(skill);
			skillInitialized[idx] = true;
		}
		initializedSkillCount = Skill.values().length;
		levelsInitialized = true;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		switch (event.getGameState()) {
			case HOPPING:
			case LOGGING_IN:
				resetGlobeState();
				overlay.clearXpDrops();
				xpDropSyncPendingTime = 0;
				break;
			case LOGGED_IN:
				overlay.initPreviousXp();
				xpDropSyncPendingTime = System.currentTimeMillis();
				break;
		}
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted event) {
		if (!developerMode) return;

		String command = event.getCommand();
		String[] args = event.getArguments();

		try {
			if ("orb".equals(command)) {
				Skill skill;
				if (args != null && args.length == 1) {
					skill = Skill.valueOf(args[0].toUpperCase());
				} else {
					Skill[] skills = Skill.values();
					skill = skills[(int) (Math.random() * skills.length)];
				}
				int idx = skill.ordinal();
				int xp = client.getSkillExperience(skill);
				int level = client.getRealSkillLevel(skill);

				XpGlobe globe = new XpGlobe(skill, xp, level, Instant.now());
				globeCache[idx] = globe;
				addXpGlobe(globe);

			} else if ("orball".equals(command)) {
				for (Skill skill : Skill.values()) {
					int idx = skill.ordinal();
					int xp = client.getSkillExperience(skill);
					int level = client.getRealSkillLevel(skill);

					XpGlobe globe = new XpGlobe(skill, xp, level, Instant.now());
					globeCache[idx] = globe;
					addXpGlobe(globe);
				}

			} else if ("olevel".equals(command)) {
				if (args == null || args.length < 2) return;

				Skill skill = Skill.valueOf(args[0].toUpperCase());
				int newLevel = Integer.parseInt(args[1]);

				Map<Skill, Integer> playerLevels = new EnumMap<>(Skill.class);
				for (Skill s : Skill.values()) {
					playerLevels.put(s, client.getRealSkillLevel(s));
				}
				int previousLevel = playerLevels.getOrDefault(skill, 0);
				playerLevels.put(skill, newLevel);

				QuestUnlockResult questUnlockResult =
						questData.checkQuestUnlocks(skill, newLevel, previousLevel, playerLevels);

				LevelUpGlobe levelUp = new LevelUpGlobe(skill, newLevel, Instant.now(), questUnlockResult,
						skillData,
						previousLevel,
						config.maxMilestones(),
						config.questRequirementMode(),
						config.skillUnlockRequirementMode(),
						config.showSkillLevelUps(),
						playerLevels
				);

				levelUpQueue.add(levelUp);
				processLevelUpQueue();
			}
		} catch (IllegalArgumentException ignored) {
			// ignore invalid skill or number format
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		if (event.getGroupId() == InterfaceID.STATS) {
			handeSkillPulseGlowState();
		}
	}

	public void handeSkillPulseGlowState() {
		boolean hideGlow = config.skillIconMode() == SkillIconMode.ALPHA_PULSE;

		for (int i = 1; i <= STATS_TAB_CHILD_COUNT; i++) {
			Widget parent = client.getWidget(InterfaceID.STATS, i);

			if (parent == null) {
				continue;
			}

			Widget glowIcon = parent.getChild(2);

			if (glowIcon != null) {
				glowIcon.setHidden(hideGlow);
			}
		}
	}
}