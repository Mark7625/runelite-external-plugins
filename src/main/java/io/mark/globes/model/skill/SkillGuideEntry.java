package io.mark.globes.model.skill;

import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.client.util.Text;

@Getter
public class SkillGuideEntry {

	private final Skill skill;
	private final int level;
	private final int itemId;
	private final String rawDescription;
	private final boolean members;

	public SkillGuideEntry(Skill skill, int level, int itemId, String rawDescription, boolean members) {
		this.skill = skill;
		this.level = level;
		this.itemId = itemId;
		this.rawDescription = rawDescription;
		this.members = members;
	}

	public String getDisplayMessage() {
		if (rawDescription == null || rawDescription.isEmpty()) {
			return "";
		}
		String beforeBr = rawDescription;
		int brIndex = indexOfBr(rawDescription);
		if (brIndex >= 0) {
			beforeBr = rawDescription.substring(0, brIndex);
		}
		return Text.removeTags(beforeBr).trim();
	}

	private static int indexOfBr(String s) {
		int i = s.indexOf("<br>");
		if (i >= 0) return i;
		i = s.indexOf("<br/>");
		if (i >= 0) return i;
		i = s.indexOf("<br />");
		if (i >= 0) return i;
		return s.indexOf("<BR>");
	}
}
