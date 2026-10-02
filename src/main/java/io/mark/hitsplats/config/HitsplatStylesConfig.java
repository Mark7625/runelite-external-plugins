package io.mark.hitsplats.config;

import io.mark.hitsplats.art.HitsplatStyle;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(HitsplatStylesConfig.GROUP)
public interface HitsplatStylesConfig extends Config {
	String GROUP = "hitsplat-styles";
	String KEY_STYLE = "style";
	String KEY_SHADOW_WIDTH = "shadowWidth";
	String KEY_RESOURCE_PACKS = "resourcePacks";

	@ConfigSection(
		name = "Blocked hits",
		description = "How hits that landed for nothing are drawn",
		position = 3
	)
	String blockedSection = "blockedSection";

	@ConfigSection(
		name = "Combat style icons",
		description = "The icon drawn beside each splat showing what the hit came from",
		position = 7
	)
	String iconSection = "iconSection";

	@ConfigSection(
		name = "Shadow and fade",
		description = "Drop shadows, and how the splat leaves the screen",
		position = 10
	)
	String effectsSection = "effectsSection";

	@ConfigSection(
		name = "Heal splats",
		description = "A splat of your own when you heal, which the game draws no hitsplat for",
		position = 14
	)
	String healSection = "healSection";

	@ConfigItem(
		keyName = KEY_STYLE,
		name = "Style",
		description = "Which set of hitsplat images to draw",
		position = 0
	)
	default HitsplatStyle style() {
		return HitsplatStyle.STYLE_2010;
	}

	@ConfigItem(
		keyName = KEY_RESOURCE_PACKS,
		name = "Use resource packs",
		description = "Use any PNGs you have put in the plugin's folder in place of the built-in art",
		position = 1
	)
	default boolean resourcePacks() {
		return true;
	}

	@ConfigItem(
		keyName = "tint",
		name = "Tint other people's hits",
		description = "The game draws hits the local player had no part in with darker art. Set to Never to give everyone the bright art",
		position = 2
	)
	default HitsplatTint tint() {
		return HitsplatTint.GAME_DEFAULT;
	}

	@ConfigItem(
		keyName = "blockArt",
		name = "OSRS block splat",
		description = "Which shield to draw for blocked hits while the OSRS style is selected. Has no effect on the other styles, which have shields of their own",
		position = 4,
		section = blockedSection
	)
	default HitsplatBlockArt blockArt() {
		return HitsplatBlockArt.DEFAULT;
	}

	@ConfigItem(
		keyName = "hideBlockedDamage",
		name = "Hide damage text",
		description = "Don't draw the 0 on blocked hits. Styles that use an icon for blocks, such as the 2011 shield, read better without it",
		position = 5,
		section = blockedSection
	)
	default boolean hideBlockedDamage() {
		return true;
	}

	@ConfigItem(
		keyName = "hideBlockedIcon",
		name = "Hide style icon",
		description = "Don't draw a combat style icon beside hits that landed for nothing",
		position = 6,
		section = blockedSection
	)
	default boolean hideBlockedIcon() {
		return true;
	}

	@ConfigItem(
		keyName = "iconSet",
		name = "Icon set",
		description = "Which set of combat style icons to draw beside the splat",
		position = 8,
		section = iconSection
	)
	default HitsplatIconSet iconSet() {
		return HitsplatIconSet.MODERN;
	}

	@Range(min = -6, max = 12)
	@ConfigItem(
		keyName = "styleIconGap",
		name = "Gap from splat",
		description = "How many pixels sit between the combat style icon and the splat. Lower tucks it in closer, negative overlaps the splat",
		position = 9,
		section = iconSection
	)
	default int styleIconGap() {
		return 4;
	}

	@Range(min = 0, max = 6)
	@ConfigItem(
		keyName = KEY_SHADOW_WIDTH,
		name = "Shadow width",
		description = "How far the soft shadow behind the splats and their combat style icons spreads out. 0 draws no shadow",
		position = 11,
		section = effectsSection
	)
	default int shadowWidth() {
		return 2;
	}

	@ConfigItem(
		keyName = "fadeOut",
		name = "Fade out",
		description = "Float the splat up and fade it away once the game's own hitsplat has expired, instead of cutting it off the way the game does",
		position = 12,
		section = effectsSection
	)
	default boolean fadeOut() {
		return true;
	}

	@Range(min = 1, max = 200)
	@ConfigItem(
		keyName = "fadeLength",
		name = "Fade length",
		description = "How many client cycles the splat lingers for after the game's own hitsplat expires. 30 cycles is one game tick",
		position = 13,
		section = effectsSection
	)
	default int fadeLength() {
		return 25;
	}

	@ConfigItem(
		keyName = "healSplatMode",
		name = "Show heal splats",
		description = "Show a splat on yourself when you heal, and whether to show the food or potion that did it",
		position = 15,
		section = healSection
	)
	default HealSplatMode healSplatMode() {
		return HealSplatMode.SPLAT_AND_ITEM;
	}

	@ConfigItem(
		keyName = "healOverheal",
		name = "Show overheal",
		description = "Show what the food or potion heals for in full, instead of the amount your health bar had room for",
		position = 16,
		section = healSection
	)
	default boolean healOverheal() {
		return false;
	}

}
