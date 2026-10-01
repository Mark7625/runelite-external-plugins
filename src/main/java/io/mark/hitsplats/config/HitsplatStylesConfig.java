package io.mark.hitsplats.config;

import io.mark.hitsplats.art.HitsplatStyle;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

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
		keyName = "styleIcons",
		name = "Combat style icons",
		description = "Which styles draw a melee, ranged, magic or cannon icon beside the splat. The style is worked out from the projectile that caused the hit, so it is a guess - hits with no projectile are treated as melee",
		position = 1
	)
	default HitsplatIcons styleIcons() {
		return HitsplatIcons.ALL;
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
		keyName = "hideBlockedDamage",
		name = "Hide blocked damage text",
		description = "Don't draw the 0 on blocked hits. Styles that use an icon for blocks, such as the 2011 shield, read better without it",
		position = 3
	)
	default boolean hideBlockedDamage() {
		return true;
	}

}
