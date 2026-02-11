package io.mark.globes.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.mark.globes.model.quest.Quest;
import io.mark.globes.model.quest.QuestUnlockResult;
import io.mark.globes.model.skill.SkillData;
import io.mark.globes.model.skill.SkillGuideEntry;
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
	private String[] milestones;

	public LevelUpGlobe(Skill skill, int newLevel, Instant time, QuestUnlockResult questUnlockResult, SkillData skillData,
						int previousLevel, int maxMilestones,
						boolean showQuestRequirementsMet, boolean showAllQuestRequirementsMet, boolean showSkillLevelUps) {
		this.skill = skill;
		this.newLevel = newLevel;
		this.time = time;
		populateLevelupMilestones(questUnlockResult, skillData, previousLevel, maxMilestones,
				showQuestRequirementsMet, showAllQuestRequirementsMet, showSkillLevelUps);
	}

	private void populateLevelupMilestones(QuestUnlockResult questUnlockResult, SkillData skillData,
										   int previousLevel, int maxMilestones,
										   boolean showQuestRequirementsMet, boolean showAllQuestRequirementsMet, boolean showSkillLevelUps) {
		List<MilestoneEntry> questMilestones = new ArrayList<>();
		List<MilestoneEntry> skillMilestones = new ArrayList<>();

		if (questUnlockResult != null) {
			if (showAllQuestRequirementsMet) {
				for (Quest quest : questUnlockResult.getFullyUnlockedQuests()) {
					int lvl = quest.getSkillRequirements().getOrDefault(skill, newLevel);
					questMilestones.add(new MilestoneEntry(lvl, "You now have all the levels for " + quest.getName()));
				}
			}
			if (showQuestRequirementsMet) {
				for (Quest quest : questUnlockResult.getNewlyUnlockedQuests()) {
					int lvl = quest.getSkillRequirements().getOrDefault(skill, newLevel);
					questMilestones.add(new MilestoneEntry(lvl, skill.getName() + " is one of the requirements for " + quest.getName()));
				}
			}
		}

		if (showSkillLevelUps && skillData != null) {
			int startLevel = Math.max(1, previousLevel + 1);
			for (int lvl = startLevel; lvl <= newLevel; lvl++) {
				for (SkillGuideEntry entry : skillData.getEntriesForLevel(skill, lvl)) {
					String msg = entry.getDisplayMessage();
					if (msg != null && !msg.isEmpty()) {
						skillMilestones.add(new MilestoneEntry(entry.getLevel(), msg));
					}
				}
			}
		}

		questMilestones.sort(Comparator.comparingInt(MilestoneEntry::level).reversed());
		skillMilestones.sort(Comparator.comparingInt(MilestoneEntry::level).reversed());

		int cap = maxMilestones <= 0 ? Integer.MAX_VALUE : maxMilestones;
		List<String> out = new ArrayList<>(Math.min(cap, questMilestones.size() + skillMilestones.size()));

		if (cap == Integer.MAX_VALUE) {
			for (MilestoneEntry e : questMilestones) out.add(e.message());
			for (MilestoneEntry e : skillMilestones) out.add(e.message());
			milestones = out.toArray(new String[0]);
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
				// Prefer filling skills first when both have remaining
				int addS = Math.min(remaining, sRemaining);
				sTake += addS;
				remaining -= addS;
				if (remaining > 0) {
					qTake += Math.min(remaining, qRemaining);
				}
			}
		}

		for (int i = 0; i < qTake; i++) out.add(questMilestones.get(i).message());
		for (int i = 0; i < sTake; i++) out.add(skillMilestones.get(i).message());

		milestones = out.toArray(new String[0]);
	}

	@Getter
	@Accessors(fluent = true)
	@RequiredArgsConstructor
	private static final class MilestoneEntry {
		private final int level;
		private final String message;
	}
}
