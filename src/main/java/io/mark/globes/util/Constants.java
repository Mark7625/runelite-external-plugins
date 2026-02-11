package io.mark.globes.util;

import com.google.common.collect.ImmutableMap;
import net.runelite.api.Skill;
import net.runelite.api.gameval.SpriteID;

import java.util.Map;

public final class Constants {


	public static final Map<Skill, Integer> SKILL_BITS = ImmutableMap.<Skill, Integer>builder()
			.put(Skill.ATTACK, 1)
			.put(Skill.STRENGTH, 2)
			.put(Skill.RANGED, 3)
			.put(Skill.MAGIC, 4)
			.put(Skill.DEFENCE, 5)
			.put(Skill.HITPOINTS, 6)
			.put(Skill.PRAYER, 7)
			.put(Skill.AGILITY, 8)
			.put(Skill.HERBLORE, 9)
			.put(Skill.THIEVING, 10)
			.put(Skill.CRAFTING, 11)
			.put(Skill.RUNECRAFT, 12)
			.put(Skill.MINING, 13)
			.put(Skill.SMITHING, 14)
			.put(Skill.FISHING, 15)
			.put(Skill.COOKING, 16)
			.put(Skill.FIREMAKING, 17)
			.put(Skill.WOODCUTTING, 18)
			.put(Skill.FLETCHING, 19)
			.put(Skill.SLAYER, 20)
			.put(Skill.FARMING, 21)
			.put(Skill.CONSTRUCTION, 22)
			.put(Skill.HUNTER, 23)
			.put(Skill.SAILING, 24)
	.build();

	public static final Map<Skill, Integer> SKILL_ICONS = ImmutableMap.<Skill, Integer>builder()
			.put(Skill.ATTACK, SpriteID.Staticons.ATTACK)
			.put(Skill.STRENGTH, SpriteID.Staticons.STRENGTH)
			.put(Skill.RANGED, SpriteID.Staticons.RANGED)
			.put(Skill.MAGIC, SpriteID.Staticons.MAGIC)
			.put(Skill.DEFENCE, SpriteID.Staticons.DEFENCE)
			.put(Skill.HITPOINTS, SpriteID.Staticons.HITPOINTS)
			.put(Skill.PRAYER, SpriteID.Staticons.PRAYER)
			.put(Skill.AGILITY, SpriteID.Staticons.AGILITY)
			.put(Skill.HERBLORE, SpriteID.Staticons.HERBLORE)
			.put(Skill.THIEVING, SpriteID.Staticons.THIEVING)
			.put(Skill.CRAFTING, SpriteID.Staticons.CRAFTING)
			.put(Skill.RUNECRAFT, SpriteID.Staticons2.RUNECRAFT)
			.put(Skill.MINING, SpriteID.Staticons.MINING)
			.put(Skill.SMITHING, SpriteID.Staticons.SMITHING)
			.put(Skill.FISHING, SpriteID.Staticons.FISHING)
			.put(Skill.COOKING, SpriteID.Staticons.COOKING)
			.put(Skill.FIREMAKING, SpriteID.Staticons.FIREMAKING)
			.put(Skill.WOODCUTTING, SpriteID.Staticons.WOODCUTTING)
			.put(Skill.FLETCHING, SpriteID.Staticons.FLETCHING)
			.put(Skill.SLAYER, SpriteID.Staticons2.SLAYER)
			.put(Skill.FARMING, SpriteID.Staticons2.FARMING)
			.put(Skill.CONSTRUCTION, SpriteID.Staticons2.CONSTRUCTION)
			.put(Skill.HUNTER, SpriteID.Staticons2.HUNTER)
			.put(Skill.SAILING, SpriteID.Staticons2.SAILING)
	.build();
}