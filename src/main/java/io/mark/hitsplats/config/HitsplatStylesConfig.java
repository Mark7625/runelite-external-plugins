package io.mark.hitsplats.config;

import io.mark.hitsplats.art.HitsplatStyle;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(HitsplatStylesConfig.GROUP)
public interface HitsplatStylesConfig extends Config {
	String GROUP = "hitsplat-styles";
	String KEY_STYLE = "style";

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
		keyName = "tint",
		name = "Tint other people's hits",
		description = "The game draws hits the local player had no part in with darker art. Set to Never to give everyone the bright art",
		position = 1
	)
	default HitsplatTint tint() {
		return HitsplatTint.GAME_DEFAULT;
	}

	@ConfigItem(
		keyName = "blockArt",
		name = "OSRS block splat",
		description = "Which shield to draw for blocked hits while the OSRS style is selected. Has no effect on the other styles, which have shields of their own",
		position = 2
	)
	default HitsplatBlockArt blockArt() {
		return HitsplatBlockArt.DEFAULT;
	}

	@ConfigItem(
		keyName = "hideBlockedDamage",
		name = "Hide blocked damage text",
		description = "Don't draw the 0 on blocked hits. Styles that use an icon for blocks, such as the 2011 shield, read better without it",
		position = 3
	)
	default boolean hideBlockedDamage() {
		return true;
	}

	@ConfigItem(
		keyName = "hideBlockedIcon",
		name = "Hide icon on blocked hits",
		description = "Don't draw a combat style icon beside hits that landed for nothing",
		position = 4
	)
	default boolean hideBlockedIcon() {
		return true;
	}

	@ConfigItem(
		keyName = "iconSet",
		name = "Style icons",
		description = "Which set of combat style icons to draw beside the splat",
		position = 5
	)
	default HitsplatIconSet iconSet() {
		return HitsplatIconSet.MODERN;
	}

	@ConfigItem(
		keyName = "shadows",
		name = "Drop shadows",
		description = "Draw a soft shadow behind the splats and their combat style icons",
		position = 6
	)
	default boolean shadows() {
		return true;
	}

	@ConfigItem(
		keyName = "fadeOut",
		name = "Fade out",
		description = "Float the splat up and fade it away once the game's own hitsplat has expired, instead of cutting it off the way the game does",
		position = 7
	)
	default boolean fadeOut() {
		return true;
	}

	@Range(min = 1, max = 200)
	@ConfigItem(
		keyName = "fadeLength",
		name = "Fade length",
		description = "How many client cycles the splat lingers for after the game's own hitsplat expires. 30 cycles is one game tick",
		position = 8
	)
	default int fadeLength() {
		return 25;
	}

}
