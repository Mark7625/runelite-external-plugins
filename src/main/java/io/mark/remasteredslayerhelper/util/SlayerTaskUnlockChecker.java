package io.mark.remasteredslayerhelper.util;

import io.mark.remasteredslayerhelper.data.QuestStateCache;
import io.mark.remasteredslayerhelper.data.SlayerMaster;
import io.mark.remasteredslayerhelper.domain.SlayerTask;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Skill;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class SlayerTaskUnlockChecker {

	private SlayerTaskUnlockChecker() {
	}

	public static boolean isUnlocked(Client client, QuestStateCache questStateCache, SlayerTask task) {
		return getLockReason(client, questStateCache, task) == null;
	}

	/**
	 * Returns a short description of why the task is locked (e.g. "Combat 100", "Ranged 70 or
	 * Strength 60", "Master required"), or null if it's unlocked.
	 */
	public static String getLockReason(Client client, QuestStateCache questStateCache, SlayerTask task) {
		Player localPlayer = client.getLocalPlayer();
		if (task.getCombatLevel() > 0 && localPlayer != null && localPlayer.getCombatLevel() < task.getCombatLevel()) {
			return "Combat " + task.getCombatLevel();
		}

		for (Map.Entry<Skill, Integer> requirement : task.getRequirementsNeedsAll().entrySet()) {
			if (client.getRealSkillLevel(requirement.getKey()) < requirement.getValue()) {
				return describe(requirement);
			}
		}

		Map<Skill, Integer> needsAny = task.getRequirementsNeedsAny();
		if (!needsAny.isEmpty()) {
			boolean anyStatMet = needsAny.entrySet().stream()
				.anyMatch(requirement -> client.getRealSkillLevel(requirement.getKey()) >= requirement.getValue());
			if (!anyStatMet) {
				return needsAny.entrySet().stream()
					.map(SlayerTaskUnlockChecker::describe)
					.collect(Collectors.joining(" or "));
			}
		}

		List<SlayerMaster> masters = task.getSlayerMasters();
		if (masters != null && !masters.isEmpty()) {
			boolean anyMasterUnlocked = masters.stream().anyMatch(master -> master.isUnlocked(client, questStateCache));
			if (!anyMasterUnlocked) {
				return "Master required";
			}
		}

		return null;
	}

	private static String describe(Map.Entry<Skill, Integer> requirement) {
		return requirement.getKey().getName() + " " + requirement.getValue();
	}
}
