package io.mark.globes;

import io.mark.globes.model.LevelUpGlobe;
import io.mark.globes.model.quest.QuestData;
import io.mark.globes.model.quest.QuestUnlockResult;
import io.mark.globes.model.XpGlobe;
import io.mark.globes.model.skill.SkillData;
import io.mark.globes.overlay.LevelUpGlobesOverlay;
import io.mark.globes.overlay.XpGlobesOverlay;
import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@PluginDescriptor(
		name = "Remastered Xp Globes",
		description = "Improves xp globes and adds level up notifications like 2011",
		tags = {"2011", "xp", "globes", "rs2", "level", "skill", "experience"}
)
@PluginDependency(XpTrackerPlugin.class)
public class RemasteredXpGlobes extends Plugin {
	private static final String CMD_TEST_ORB = "orb";
	private static final String CMD_TEST_LEVEL = "olevel";
	private static final String CMD_TEST_ALL_ORBS = "orball";

	private XpGlobe[] globeCache = new XpGlobe[Skill.values().length];
	private final int[] previousLevels = new int[Skill.values().length];
	private boolean levelsInitialized;
	private Instant levelsInitializedTime;
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
	private SpriteManager spriteManager;

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
	}

	@Override
	public void shutDown() {
		resetGlobeState();
		overlay.clearCache();
		overlay.clearXpDrops();
		levelUpOverlay.clearCache();
		overlayManager.remove(overlay);
		overlayManager.remove(levelUpOverlay);
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
			return;
		}

		if (levelsInitializedTime != null
				&& now.minusSeconds(2).isBefore(levelsInitializedTime)) {
			previousLevels[skillIdx] = currentLevel;
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
					config.showQuestRequirementsMet(),
					config.showAllQuestRequirementsMet(),
					config.showSkillLevelUps()
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
			return;
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

		xpGlobes.sort(Comparator.comparing(XpGlobe::getTime));
	}

	@Schedule(period = 1, unit = ChronoUnit.SECONDS)
	public void removeExpiredXpGlobes() {
		if (xpDropSyncPendingTime > 0 && System.currentTimeMillis() - xpDropSyncPendingTime >= 1500) {
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
			maxMilestones = Math.min(config.maxMilestones(), levelUp.getMilestones().length);
		}
		return (3300 + maxMilestones * 3000L + 2000) / 1000;
	}

	private void resetGlobeState() {
		xpGlobes.clear();
		globeCache = new XpGlobe[Skill.values().length];
		levelUpQueue.clear();
		currentLevelUp = null;
		levelsInitialized = false;
		levelsInitializedTime = null;
	}

	private void initializePreviousLevels() {
		for (Skill skill : Skill.values()) {
			int skillIdx = skill.ordinal();
			previousLevels[skillIdx] = client.getRealSkillLevel(skill);
		}
		levelsInitialized = true;
		levelsInitializedTime = Instant.now();
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
				initializePreviousLevels();
				overlay.initPreviousXp();
				xpDropSyncPendingTime = System.currentTimeMillis();
				break;
		}
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted commandExecuted) {
		String[] args = commandExecuted.getArguments();

		String command = commandExecuted.getCommand();
		if (CMD_TEST_ORB.equals(command) && developerMode) {
			Skill randomSkill;
			if (args.length == 1) {
				String skillName = args[0].toUpperCase();
				randomSkill = Skill.valueOf(skillName.toUpperCase());
			} else  {
				Skill[] skills = Skill.values();
				randomSkill = skills[(int) (Math.random() * skills.length)];
			}
			int skillIdx = randomSkill.ordinal();
			int currentXp = client.getSkillExperience(randomSkill);
			int currentLevel = client.getRealSkillLevel(randomSkill);

			XpGlobe testGlobe = new XpGlobe(randomSkill, currentXp, currentLevel, Instant.now());
			globeCache[skillIdx] = testGlobe;
			addXpGlobe(testGlobe);
		} else if (CMD_TEST_LEVEL.equals(command) && developerMode) {
			if (args == null || args.length < 2) {
				return;
			}

			try {
				String skillName = args[0].toUpperCase();
				int level = Integer.parseInt(args[1]);
				Skill skill = Skill.valueOf(skillName);

				Map<Skill, Integer> playerLevels = new HashMap<>();
				for (Skill s : Skill.values()) {
					playerLevels.put(s, client.getRealSkillLevel(s));
				}
				int previousLevel = playerLevels.getOrDefault(skill, 0);
				playerLevels.put(skill, level);

				QuestUnlockResult questUnlockResult = questData.checkQuestUnlocks(skill, level, previousLevel, playerLevels);

				LevelUpGlobe levelUp = new LevelUpGlobe(skill, level, Instant.now(), questUnlockResult, skillData,
						previousLevel, config.maxMilestones(),
						config.showQuestRequirementsMet(), config.showAllQuestRequirementsMet(), config.showSkillLevelUps());
				levelUpQueue.add(levelUp);
				processLevelUpQueue();
			} catch (IllegalArgumentException ignored) {
			}
		} else if (CMD_TEST_ALL_ORBS.equals(command) && developerMode) {
			for (Skill skill : Skill.values()) {
				int skillIdx = skill.ordinal();
				int currentXp = client.getSkillExperience(skill);
				int currentLevel = client.getRealSkillLevel(skill);

				XpGlobe testGlobe = new XpGlobe(skill, currentXp, currentLevel, Instant.now());
				globeCache[skillIdx] = testGlobe;
				addXpGlobe(testGlobe);
			}
		}
	}

}
