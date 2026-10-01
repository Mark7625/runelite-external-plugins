package io.mark.hitsplats.art;

import net.runelite.api.HitsplatID;

public enum HitsplatSkin
{
	DAMAGE("damage_normal"),
	DAMAGE_TINTED("damage_tinted", "damage_normal"),
	DAMAGE_MAX("damage_max", "damage_normal"),
	BLOCK("block_normal"),
	BLOCK_TINTED("block_tinted", "block_normal"),
	POISON("poison_normal"),
	POISON_TINTED("poison_tinted", "poison_normal"),
	VENOM("venom_normal"),
	VENOM_TINTED("venom_tinted", "venom_normal"),
	DISEASE("disease_normal"),
	DISEASE_TINTED("disease_tinted", "disease_normal"),
	HEAL("heal_normal", "poison_normal"),
	HEAL_TINTED("heal_tinted", "heal_normal", "poison_tinted", "poison_normal"),
	CYAN("damage_cyan_normal", "damage_normal"),
	CYAN_TINTED("damage_cyan_tinted", "damage_cyan_normal", "damage_tinted", "damage_normal"),
	CYAN_MAX("damage_cyan_max", "damage_max", "damage_cyan_normal", "damage_normal"),
	ORANGE("damage_orange_normal", "damage_normal"),
	ORANGE_TINTED("damage_orange_tinted", "damage_orange_normal", "damage_tinted", "damage_normal"),
	ORANGE_MAX("damage_orange_max", "damage_max", "damage_orange_normal", "damage_normal"),
	YELLOW("damage_yellow_normal", "damage_normal"),
	YELLOW_TINTED("damage_yellow_tinted", "damage_yellow_normal", "damage_tinted", "damage_normal"),
	YELLOW_MAX("damage_yellow_max", "damage_max", "damage_yellow_normal", "damage_normal"),
	WHITE("damage_white_normal", "damage_normal"),
	WHITE_TINTED("damage_white_tinted", "damage_white_normal", "damage_tinted", "damage_normal"),
	WHITE_MAX("damage_white_max", "damage_max", "damage_white_normal", "damage_normal"),
	POISE("damage_poise_normal", "damage_normal"),
	POISE_TINTED("damage_poise_tinted", "damage_poise_normal", "damage_tinted", "damage_normal"),
	POISE_MAX("damage_poise_max", "damage_max", "damage_poise_normal", "damage_normal"),
	PRAYER_DRAIN("prayer_drain_normal", "damage_normal"),
	BLEED("bleed_normal", "damage_normal"),
	BURN("burn_normal", "poison_normal", "damage_normal"),
	CORRUPTION("corruption_normal", "damage_normal"),
	DOOM("doom_normal", "damage_normal"),
	SANITY_DRAIN("sanity_drain_normal", "damage_normal"),
	SANITY_RESTORE("sanity_restore_normal", "heal_normal", "poison_normal");

	private final String[] fileNames;

	HitsplatSkin(String... fileNames)
	{
		this.fileNames = fileNames;
	}

	public String[] getFileNames()
	{
		return fileNames;
	}

	public HitsplatSkin untinted()
	{
		switch (this)
		{
			case DAMAGE_TINTED:
				return DAMAGE;
			case BLOCK_TINTED:
				return BLOCK;
			case POISON_TINTED:
				return POISON;
			case VENOM_TINTED:
				return VENOM;
			case DISEASE_TINTED:
				return DISEASE;
			case HEAL_TINTED:
				return HEAL;
			case CYAN_TINTED:
				return CYAN;
			case ORANGE_TINTED:
				return ORANGE;
			case YELLOW_TINTED:
				return YELLOW;
			case WHITE_TINTED:
				return WHITE;
			case POISE_TINTED:
				return POISE;
			default:
				return this;
		}
	}

	public HitsplatSkin tinted()
	{
		switch (this)
		{
			case DAMAGE:
				return DAMAGE_TINTED;
			case BLOCK:
				return BLOCK_TINTED;
			case POISON:
				return POISON_TINTED;
			case VENOM:
				return VENOM_TINTED;
			case DISEASE:
				return DISEASE_TINTED;
			case HEAL:
				return HEAL_TINTED;
			case CYAN:
				return CYAN_TINTED;
			case ORANGE:
				return ORANGE_TINTED;
			case YELLOW:
				return YELLOW_TINTED;
			case WHITE:
				return WHITE_TINTED;
			case POISE:
				return POISE_TINTED;
			default:
				return this;
		}
	}

	public static boolean isBlock(int hitsplatType)
	{
		return hitsplatType == HitsplatID.BLOCK_ME
			|| hitsplatType == HitsplatID.BLOCK_OTHER
			|| hitsplatType == HitsplatID.DISEASE_BLOCKED;
	}

	public static boolean isSupported(int hitsplatType)
	{
		switch (hitsplatType)
		{
			case HitsplatID.DAMAGE_ME:
			case HitsplatID.DAMAGE_OTHER:
			case HitsplatID.DAMAGE_MAX_ME:
			case HitsplatID.BLOCK_ME:
			case HitsplatID.BLOCK_OTHER:
			case HitsplatID.POISON:
			case HitsplatID.VENOM:
			case HitsplatID.DISEASE:
			case HitsplatID.DISEASE_BLOCKED:
				return true;
			default:
				return false;
		}
	}

	public static HitsplatSkin forType(int hitsplatType, boolean onLocalPlayer)
	{
		switch (hitsplatType)
		{
			case HitsplatID.DAMAGE_ME:
				return DAMAGE;
			case HitsplatID.DAMAGE_MAX_ME:
				return DAMAGE_MAX;
			case HitsplatID.BLOCK_ME:
				return BLOCK;
			case HitsplatID.BLOCK_OTHER:
				return BLOCK_TINTED;
			case HitsplatID.DAMAGE_ME_CYAN:
				return CYAN;
			case HitsplatID.DAMAGE_OTHER_CYAN:
				return CYAN_TINTED;
			case HitsplatID.DAMAGE_MAX_ME_CYAN:
				return CYAN_MAX;
			case HitsplatID.CYAN_UP:
			case HitsplatID.CYAN_DOWN:
				return onLocalPlayer ? CYAN : CYAN_TINTED;
			case HitsplatID.DAMAGE_ME_ORANGE:
				return ORANGE;
			case HitsplatID.DAMAGE_OTHER_ORANGE:
				return ORANGE_TINTED;
			case HitsplatID.DAMAGE_MAX_ME_ORANGE:
				return ORANGE_MAX;
			case HitsplatID.DAMAGE_ME_YELLOW:
				return YELLOW;
			case HitsplatID.DAMAGE_OTHER_YELLOW:
				return YELLOW_TINTED;
			case HitsplatID.DAMAGE_MAX_ME_YELLOW:
				return YELLOW_MAX;
			case HitsplatID.DAMAGE_ME_WHITE:
				return WHITE;
			case HitsplatID.DAMAGE_OTHER_WHITE:
				return WHITE_TINTED;
			case HitsplatID.DAMAGE_MAX_ME_WHITE:
				return WHITE_MAX;
			case HitsplatID.DAMAGE_ME_POISE:
				return POISE;
			case HitsplatID.DAMAGE_OTHER_POISE:
				return POISE_TINTED;
			case HitsplatID.DAMAGE_MAX_ME_POISE:
				return POISE_MAX;
			case HitsplatID.POISON:
				return onLocalPlayer ? POISON : POISON_TINTED;
			case HitsplatID.VENOM:
				return onLocalPlayer ? VENOM : VENOM_TINTED;
			case HitsplatID.DISEASE:
				return onLocalPlayer ? DISEASE : DISEASE_TINTED;
			case HitsplatID.DISEASE_BLOCKED:
				return onLocalPlayer ? BLOCK : BLOCK_TINTED;
			case HitsplatID.HEAL:
				return onLocalPlayer ? HEAL : HEAL_TINTED;
			case HitsplatID.PRAYER_DRAIN:
				return PRAYER_DRAIN;
			case HitsplatID.BLEED:
				return BLEED;
			case HitsplatID.BURN:
				return BURN;
			case HitsplatID.CORRUPTION:
				return CORRUPTION;
			case HitsplatID.DOOM:
				return DOOM;
			case HitsplatID.SANITY_DRAIN:
				return SANITY_DRAIN;
			case HitsplatID.SANITY_RESTORE:
				return SANITY_RESTORE;
			case HitsplatID.DAMAGE_OTHER:
			default:
				return DAMAGE_TINTED;
		}
	}
}
