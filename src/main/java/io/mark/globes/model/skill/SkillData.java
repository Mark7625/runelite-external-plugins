package io.mark.globes.model.skill;

import io.mark.globes.util.Constants;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class SkillData {
	private static final int GUIDE_SCRIPT_ID = 661;
	private static final int GUIDE_SUB_SECTION_SCRIPT_ID = 660;

	private final Map<Skill, List<SkillGuideEntry>> skillToEntries = new HashMap<>();

	@Inject
	private ClientThread clientThread;

	@Inject
	private Client client;

	public void load() {
		clientThread.invoke(this::loadInternal);
	}

	private void loadInternal() {
		skillToEntries.clear();
		for (Skill skill : Constants.SKILL_BITS.keySet()) {
			skillToEntries.put(skill, new ArrayList<>());
		}

		Map<Skill, List<Integer>> skillCategories = new HashMap<>();
		for (Map.Entry<Skill, Integer> e : Constants.SKILL_BITS.entrySet()) {
			Skill skill = e.getKey();
			int bit = e.getValue();

			List<Integer> categories = new ArrayList<>();
			for (int catIndex = 1; catIndex < 30; catIndex++) {
				client.runScript(GUIDE_SUB_SECTION_SCRIPT_ID, bit, catIndex);
				int[] intStack = client.getIntStack();
				if (intStack != null && intStack.length > 0 && intStack[0] != -1) {
					categories.add(catIndex);
				}
			}
			skillCategories.put(skill, categories);
		}

		for (Map.Entry<Skill, Integer> e : Constants.SKILL_BITS.entrySet()) {
			Skill skill = e.getKey();
			int bit = e.getValue();
			List<Integer> categories = skillCategories.get(skill);
			if (categories == null) {
				continue;
			}

			List<SkillGuideEntry> entries = skillToEntries.get(skill);
			if (entries == null) {
				continue;
			}

			for (int category : categories) {
				for (int index = 0; index < 160; index++) {
					client.runScript(GUIDE_SCRIPT_ID, bit, category, index);
					int[] intStack = client.getIntStack();
					if (intStack == null || intStack.length < 2) {
						continue;
					}

					int level = intStack[0];
					if (level == -1) {
						continue;
					}

					int itemId = intStack[1];
					Object[] objectStack = client.getObjectStack();
					Object raw = (objectStack != null && objectStack.length > 0) ? objectStack[0] : null;
					String rawDescription = raw != null ? raw.toString() : "";
					boolean members = rawDescription.contains("Members:");

					entries.add(new SkillGuideEntry(skill, level, itemId, rawDescription, members));
				}
			}
		}
	}

	public List<SkillGuideEntry> getEntriesForSkill(Skill skill) {
		return skillToEntries.getOrDefault(skill, Collections.emptyList());
	}

	public List<SkillGuideEntry> getEntriesForLevel(Skill skill, int level) {
		List<SkillGuideEntry> out = new ArrayList<>();
		for (SkillGuideEntry e : getEntriesForSkill(skill)) {
			if (e.getLevel() == level) {
				out.add(e);
			}
		}
		return out;
	}
}