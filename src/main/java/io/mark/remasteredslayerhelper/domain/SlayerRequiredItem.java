package io.mark.remasteredslayerhelper.domain;

import net.runelite.api.ItemID;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum SlayerRequiredItem {
	NOSE_PEG(ItemID.NOSE_PEG, "Nose peg"),
	EARMUFFS(ItemID.EARMUFFS, "Earmuffs"),
	SLAYER_HELMET(ItemID.SLAYER_HELMET, "Slayer helmet"),
	MIRROR_SHIELD(ItemID.MIRROR_SHIELD, "Mirror shield"),
	VS_SHIELD(ItemID.VS_SHIELD, "V's shield"),
	FACEMASK(ItemID.FACEMASK, "Facemask"),
	ROCK_HAMMER(ItemID.ROCK_HAMMER, "Rock hammer"),
	ROCK_THROWNHAMMER(ItemID.ROCK_THROWNHAMMER, "Rock thrownhammer"),
	SPINY_HELMET(ItemID.SPINY_HELMET, "Spiny helmet"),
	WITCHWOOD_ICON(ItemID.WITCHWOOD_ICON, "Witchwood icon"),
	BULLSEYE_LANTERN(ItemID.BULLSEYE_LANTERN, "Bullseye lantern (lit)"),
	LIT_BUG_LANTERN(ItemID.LIT_BUG_LANTERN, "Lit bug lantern"),
	BOOTS_OF_STONE(ItemID.BOOTS_OF_STONE, "Boots of stone"),
	BOOTS_OF_BRIMSTONE(ItemID.BOOTS_OF_BRIMSTONE, "Boots of brimstone"),
	GRANITE_BOOTS(ItemID.GRANITE_BOOTS, "Granite boots"),
	INSULATED_BOOTS(ItemID.INSULATED_BOOTS, "Insulated boots"),
	ELEMENTAL_SHIELD(ItemID.ELEMENTAL_SHIELD, "Elemental shield"),
	MIND_SHIELD(ItemID.MIND_SHIELD, "Mind shield"),
	ANCIENT_WYVERN_SHIELD(ItemID.ANCIENT_WYVERN_SHIELD, "Ancient wyvern shield"),
	DRAGONFIRE_SHIELD(ItemID.DRAGONFIRE_SHIELD, "Dragonfire shield"),
	ANTIDRAGON_SHIELD(ItemID.ANTIDRAGON_SHIELD, "Anti-dragon shield"),
	SLAYER_GLOVES(ItemID.SLAYER_GLOVES, "Slayer gloves");

	private final int itemId;
	private final String displayName;

	SlayerRequiredItem(int itemId, String displayName) {
		this.itemId = itemId;
		this.displayName = displayName;
	}

	public int getItemId() {
		return itemId;
	}

	public String getDisplayName() {
		return displayName;
	}

	private static final Map<String, SlayerRequiredItem> BY_DISPLAY_NAME = Arrays.stream(values())
		.collect(Collectors.toMap(s -> s.displayName.toLowerCase().trim(), Function.identity(), (a, b) -> a));

	public static SlayerRequiredItem fromDisplayName(String displayName) {
		if (displayName == null || displayName.isBlank()) {
			return null;
		}
		return BY_DISPLAY_NAME.get(displayName.toLowerCase().trim());
	}
}
