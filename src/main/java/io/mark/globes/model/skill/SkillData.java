package io.mark.globes.model.skill;

import net.runelite.api.Client;
import net.runelite.api.EnumID;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class SkillData {

	private final List<SkillFeature> features = new ArrayList<>();

	@Inject
	private ClientThread clientThread;

	@Inject
	private Client client;

	public void load() {
		clientThread.invoke(this::loadInternal);
	}

	private void loadInternal() {
		features.clear();

		Map<Integer, Skill> skillMap = new HashMap<>();

		var enum81 = client.getEnum(81);
		var enum680 = client.getEnum(680);

		for (int key : enum81.getIntVals()) {

			String skillName = enum680.getStringValue(key);

			if (skillName != null && !skillName.equalsIgnoreCase("SKILL")) {
				skillMap.put(enum81.getIntValue(key), Skill.valueOf(skillName.toUpperCase()));
			}
		}

		for (Integer dbTableRow : client.getDBTableRows(DBTableID.SkillFeatures.ID)) {
			String unlockDesc = (String) client.getDBTableField(dbTableRow, DBTableID.SkillFeatures.COL_TEXT, 0)[0];
			boolean members = (Integer) client.getDBTableField(dbTableRow, DBTableID.SkillFeatures.COL_MEMBERSONLY, 0)[0] == 1;
			Integer iconId = (Integer) client.getDBTableField(dbTableRow, DBTableID.SkillFeatures.COL_ICON, 0)[0];

			int itemId = (iconId != null && iconId >= 0) ? iconId : -1;

			Object[] stats = client.getDBTableField(dbTableRow, DBTableID.SkillFeatures.COL_SKILL, 0);
			Object[] levels = client.getDBTableField(dbTableRow, DBTableID.SkillFeatures.COL_SKILL, 1);

			if (stats == null || levels == null) {
				continue;
			}

			Map<Skill, Integer> requirements = new HashMap<>();
			for (int i = 0; i < stats.length && i < levels.length; i++) {
				Skill skill = skillMap.get((int) stats[i]);
				Integer level = (Integer) levels[i];
				if (skill == null || level == null) {
					continue;
				}
				requirements.put(skill, level);
			}
			if (!requirements.isEmpty()) {
				features.add(new SkillFeature(unlockDesc != null ? unlockDesc : "", members, itemId, requirements));
			}
		}
	}

	/**
	 * Returns skill features for the given skill/level.
	 *
	 * @param requireAllLevels when true, only include features where the player meets every level requirement;
	 *                        when false, include when the player meets at least one requirement.
	 */
	public List<SkillFeature> getEntriesForLevel(Skill skill, int level, Map<Skill, Integer> playerLevels, boolean requireAllLevels) {
		List<SkillFeature> out = new ArrayList<>();
		for (SkillFeature feature : features) {
			Integer requiredLevel = feature.getRequirements().get(skill);
			if (requiredLevel == null || requiredLevel != level) {
				continue;
			}
			boolean include;
			if (requireAllLevels) {
				include = true;
				for (Map.Entry<Skill, Integer> req : feature.getRequirements().entrySet()) {
					if (playerLevels.getOrDefault(req.getKey(), 0) < req.getValue()) {
						include = false;
						break;
					}
				}
			} else {
				include = false;
				for (Map.Entry<Skill, Integer> req : feature.getRequirements().entrySet()) {
					if (playerLevels.getOrDefault(req.getKey(), 0) >= req.getValue()) {
						include = true;
						break;
					}
				}
			}
			if (include) {
				out.add(feature);
			}
		}
		return out;
	}
}