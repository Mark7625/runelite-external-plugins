package io.mark.globes.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import io.mark.globes.RequirementDisplayMode;
import io.mark.globes.model.quest.Quest;
import io.mark.globes.model.quest.QuestUnlockResult;
import io.mark.globes.model.skill.SkillData;
import io.mark.globes.model.skill.SkillFeature;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Skill;

@Getter
@Setter
@Data
public class LevelUpGlobe {
	private Skill skill;
	private int newLevel;
	private Instant time;
	private List<MilestoneDisplay> milestones;

	public LevelUpGlobe(Skill skill, int newLevel, Instant time, QuestUnlockResult questUnlockResult, SkillData skillData,
						int previousLevel, int maxMilestones,
						RequirementDisplayMode questRequirementMode, RequirementDisplayMode skillUnlockRequirementMode,
						boolean showSkillLevelUps, Map<Skill, Integer> playerLevels) {
		this.skill = skill;
		this.newLevel = newLevel;
		this.time = time;
		populateLevelupMilestones(questUnlockResult, skillData, previousLevel, maxMilestones,
				questRequirementMode, skillUnlockRequirementMode, showSkillLevelUps, playerLevels);
	}

	private void populateLevelupMilestones(QuestUnlockResult questUnlockResult, SkillData skillData,
										   int previousLevel, int maxMilestones,
										   RequirementDisplayMode questRequirementMode, RequirementDisplayMode skillUnlockRequirementMode,
										   boolean showSkillLevelUps, Map<Skill, Integer> playerLevels) {
		List<MilestoneEntry> questMilestones = new ArrayList<>();
		List<MilestoneEntry> skillMilestones = new ArrayList<>();
		boolean questAllMet = questRequirementMode == RequirementDisplayMode.ALL_MET;
		boolean skillAllMet = skillUnlockRequirementMode == RequirementDisplayMode.ALL_MET;

		if (questUnlockResult != null) {
			if (questAllMet) {
				for (Quest quest : questUnlockResult.getFullyUnlockedQuests()) {
					int lvl = quest.getSkillRequirements().getOrDefault(skill, newLevel);
					questMilestones.add(new MilestoneEntry(lvl, "You now have all the levels for " + quest.getName(), MilestoneDisplay.QUEST_ICON_ID));
				}
			} else {
				for (Quest quest : questUnlockResult.getNewlyUnlockedQuests()) {
					int lvl = quest.getSkillRequirements().getOrDefault(skill, newLevel);
					questMilestones.add(new MilestoneEntry(lvl, "You meet one of the requirements for " + quest.getName(), MilestoneDisplay.QUEST_ICON_ID));
				}
			}
		}

		if (showSkillLevelUps && skillData != null && playerLevels != null) {
			int startLevel = Math.max(1, previousLevel + 1);
			for (int lvl = startLevel; lvl <= newLevel; lvl++) {
				for (SkillFeature feature : skillData.getEntriesForLevel(skill, lvl, playerLevels, skillAllMet)) {
					String msg = feature.getDisplayMessage();
					if (msg != null && !msg.isEmpty()) {
						skillMilestones.add(new MilestoneEntry(lvl, msg, feature.getItemId()));
					}
				}
			}
		}

		questMilestones.sort(Comparator.comparingInt(MilestoneEntry::getLevel).reversed());
		skillMilestones.sort(Comparator.comparingInt(MilestoneEntry::getLevel).reversed());

		int cap = maxMilestones <= 0 ? Integer.MAX_VALUE : maxMilestones;
		List<MilestoneDisplay> out = new ArrayList<>(Math.min(cap, questMilestones.size() + skillMilestones.size()));

		if (cap == Integer.MAX_VALUE) {
			for (MilestoneEntry e : questMilestones) out.add(new MilestoneDisplay(e.getMessage(), e.getIconItemId()));
			for (MilestoneEntry e : skillMilestones) out.add(new MilestoneDisplay(e.getMessage(), e.getIconItemId()));
			milestones = out;
			return;
		}

		int questSlots;
		int skillSlots;
		if (questMilestones.isEmpty()) {
			questSlots = 0;
			skillSlots = cap;
		} else if (skillMilestones.isEmpty()) {
			skillSlots = 0;
			questSlots = cap;
		} else {
			questSlots = cap / 2;
			skillSlots = cap - questSlots;
		}

		int qTake = Math.min(questSlots, questMilestones.size());
		int sTake = Math.min(skillSlots, skillMilestones.size());
		int remaining = cap - (qTake + sTake);

		if (remaining > 0) {
			int qRemaining = questMilestones.size() - qTake;
			int sRemaining = skillMilestones.size() - sTake;
			if (qRemaining == 0 && sRemaining > 0) {
				sTake += Math.min(remaining, sRemaining);
			} else if (sRemaining == 0 && qRemaining > 0) {
				qTake += Math.min(remaining, qRemaining);
			} else if (qRemaining > 0 && sRemaining > 0) {
				int addS = Math.min(remaining, sRemaining);
				sTake += addS;
				remaining -= addS;
				if (remaining > 0) {
					qTake += Math.min(remaining, qRemaining);
				}
			}
		}

		for (int i = 0; i < qTake; i++) {
			MilestoneEntry e = questMilestones.get(i);
			out.add(new MilestoneDisplay(e.getMessage(), e.getIconItemId()));
		}
		for (int i = 0; i < sTake; i++) {
			MilestoneEntry e = skillMilestones.get(i);
			out.add(new MilestoneDisplay(e.getMessage(), e.getIconItemId()));
		}

		milestones = out;
	}

}
