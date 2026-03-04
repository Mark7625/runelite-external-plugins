package io.mark.globes.model.skill;

import lombok.Value;
import net.runelite.api.Skill;

import java.util.Map;

@Value
public class SkillFeature {

	String unlockDesc;
	boolean members;
	int itemId;
	Map<Skill, Integer> requirements;

	public String getDisplayMessage() {
		return unlockDesc != null ? unlockDesc : "";
	}
}
