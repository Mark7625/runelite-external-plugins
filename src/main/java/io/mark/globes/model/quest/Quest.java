package io.mark.globes.model.quest;

import lombok.Data;
import lombok.Getter;
import net.runelite.api.Skill;

import java.util.HashMap;
import java.util.Map;

@Getter
@Data
public class Quest {
	private final String name;
	private final int requiredCombatLevel;
	private final Map<Skill, Integer> skillRequirements;

	public Quest(String name, int requiredCombatLevel) {
		this.name = name;
		this.requiredCombatLevel = requiredCombatLevel;
		this.skillRequirements = new HashMap<>();
	}

	public void addSkillRequirement(Skill skill, int level) {
		skillRequirements.put(skill, level);
	}

	public boolean hasAllSkillRequirements(Map<Skill, Integer> playerLevels) {
		for (Map.Entry<Skill, Integer> requirement : skillRequirements.entrySet()) {
			int playerLevel = playerLevels.getOrDefault(requirement.getKey(), 0);
			if (playerLevel < requirement.getValue()) {
				return false;
			}
		}
		return true;
	}

}