package io.mark.globes.model.quest;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class QuestData {

	@Inject
	private ClientThread clientThread;

	@Inject
	private Client client;

	private final Map<Skill, List<Quest>> skillToQuests = new HashMap<>();

	public void load() {
		clientThread.invoke(() -> {
			skillToQuests.clear();
			for (Skill skill : Skill.values()) {
				skillToQuests.put(skill, new ArrayList<>());
			}

			for (int dbTableRow : client.getDBTableRows(DBTableID.Quest.COL_ID)) {
				String questName = (String) client.getDBTableField(dbTableRow, DBTableID.Quest.COL_DISPLAYNAME, 0)[0];
				if (questName == null || questName.trim().isEmpty() || ".".equals(questName)) {
					continue;
				}

				int reqCombatLevel = (Integer) client.getDBTableField(dbTableRow, DBTableID.Quest.COL_REQUIREMENT_COMBAT, 0)[0];
				Object[] reqStatId = client.getDBTableField(dbTableRow, DBTableID.Quest.COL_REQUIREMENT_STATS, 0);
				Object[] reqStatLevel = client.getDBTableField(dbTableRow, DBTableID.Quest.COL_REQUIREMENT_STATS, 1);

				Quest quest = new Quest(questName, reqCombatLevel);

				int len = Math.min(reqStatId.length, reqStatLevel.length);
				for (int i = 0; i < len; i++) {
					Object statIdObj = reqStatId[i];
					if (statIdObj == null) {
						break;
					}

					int skillId = (int) statIdObj;
					int requiredLevel = (int) reqStatLevel[i];
					Skill reqSkill = Skill.values()[skillId];
					quest.addSkillRequirement(reqSkill, requiredLevel);
					skillToQuests.get(reqSkill).add(quest);
				}
			}
		});
	}

	public List<Quest> getQuestsForSkill(Skill skill) {
		return skillToQuests.getOrDefault(skill, Collections.emptyList());
	}

	public QuestUnlockResult checkQuestUnlocks(Skill skill, int newLevel, int previousLevel, Map<Skill, Integer> playerLevels) {
		QuestUnlockResult result = new QuestUnlockResult();
		List<Quest> questsForSkill = getQuestsForSkill(skill);

		for (Quest quest : questsForSkill) {
			Integer requiredLevel = quest.getSkillRequirements().get(skill);
			if (requiredLevel == null || previousLevel >= requiredLevel || newLevel < requiredLevel) {
				continue;
			}

			if (quest.hasAllSkillRequirements(playerLevels)) {
				result.getFullyUnlockedQuests().add(quest);
				continue;
			}

			result.getNewlyUnlockedQuests().add(quest);
		}

		return result;
	}
}