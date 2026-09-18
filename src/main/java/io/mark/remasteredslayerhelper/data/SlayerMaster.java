package io.mark.remasteredslayerhelper.data;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

import java.util.ArrayList;
import java.util.List;

public enum SlayerMaster {
	TURAEL(1, "Turael", 0, 0, null, true),
	AYA(1, "Aya", 0, 0, Quest.WHILE_GUTHIX_SLEEPS, true),
	MAZCHNA(2, "Mazchna", 20, 0, Quest.PRIEST_IN_PERIL, true),
	ACHTRYN(2, "Achtryn", 20, 0, Quest.PRIEST_IN_PERIL, true),
	VANNAKA(3, "Vannaka", 40, 0, null, true),
	CHAELDAR(4, "Chaeldar", 70, 0, Quest.LOST_CITY, true),
	DURADEL(5, "Duradel", 100, 50, Quest.SHILO_VILLAGE, true),
	NIEVE(6, "Nieve", 85, 0, null, true),
	STEVE(6, "Steve", 85, 0, Quest.MONKEY_MADNESS_II, true),
	KRYSTILIA(7, "Krystilia", 0, 0, null, true),
	KONAR(8, "Konar quo Maten", 75, 0, null, true),
	SPIRA(9, "Spria", 0, 0, Quest.A_PORCINE_OF_INTEREST, true),
	MORTIMER(10, "Mortimer", 100, 70, Quest.FALLEN_FROM_GRACE, false);

	private final int id;
	private final String displayName;
	private final int minCombatLevel;
	private final int minSlayerLevel;
	private final Quest requiredQuest;
	private final boolean requiredQuestMustBeFinished;

	SlayerMaster(int id, String displayName, int minCombatLevel, int minSlayerLevel, Quest requiredQuest, boolean requiredQuestMustBeFinished) {
		this.id = id;
		this.displayName = displayName;
		this.minCombatLevel = minCombatLevel;
		this.minSlayerLevel = minSlayerLevel;
		this.requiredQuest = requiredQuest;
		this.requiredQuestMustBeFinished = requiredQuestMustBeFinished;
	}

	public int getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public Quest getRequiredQuest() {
		return requiredQuest;
	}

	/**
	 * Quest completion state comes from the given cache rather than a live Quest.getState(client)
	 * call, since the latter requires the client thread and this is called from UI code.
	 */
	public boolean isUnlocked(Client client, QuestStateCache questStateCache) {
		Player localPlayer = client.getLocalPlayer();
		if (minCombatLevel > 0 && (localPlayer == null || localPlayer.getCombatLevel() < minCombatLevel)) {
			return false;
		}

		if (minSlayerLevel > 0 && client.getRealSkillLevel(Skill.SLAYER) < minSlayerLevel) {
			return false;
		}

		if (requiredQuest != null) {
			QuestState state = questStateCache.getState(requiredQuest);
			if (requiredQuestMustBeFinished) {
				return state == QuestState.FINISHED;
			}
			return state != QuestState.NOT_STARTED;
		}

		return true;
	}

	/**
	 * Lookup all master variants by ID
	 */
	public static List<SlayerMaster> fromId(int id) {
		List<SlayerMaster> result = new ArrayList<>();
		for (SlayerMaster master : values()) {
			if (master.id == id) {
				result.add(master);
			}
		}
		return result;
	}
}
