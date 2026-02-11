package io.mark.globes;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Units;

/**
 * Configuration interface for the HD Minimap plugin
 */
@ConfigGroup(RemasteredXpGlobesConfig.CONFIG_GROUP)
public interface RemasteredXpGlobesConfig extends Config {

    String CONFIG_GROUP = "remasteredxpglobes";

	@ConfigSection(
		name = "Global",
		description = "General settings.",
		position = 0
	)
	String globalSection = "global";

	@ConfigSection(
		name = "Progress orbs",
		description = "XP progress orb settings.",
		closedByDefault = true,
		position = 1
	)
	String progressOrbsSection = "progressOrbs";

	@ConfigSection(
		name = "Level up orbs",
		description = "Level-up orb settings.",
		closedByDefault = true,
		position = 2
	)
	String levelUpOrbsSection = "levelUpOrbs";

	@ConfigSection(
			name = "Progress Tooltips",
			description = "Progress Orb Tooltips.",
			closedByDefault = true,
			position = 3
	)
	String xpTooltipSection = "xpOrbsTooltips";

	@ConfigItem(
			keyName = "hideMaxed",
			name = "Hide maxed skills",
			description = "Stop globes from showing up for level 99 skills.",
			section = globalSection,
			position = 1
	)
	default boolean hideMaxed() {
		return false;
	}

	@ConfigItem(
			keyName = "showVirtualLevel",
			name = "Show virtual level",
			description = "Shows virtual level if over 99 in a skill and 'Hide maxed skills' is not checked.",
			section = globalSection,
			position = 2
	)
	default boolean showVirtualLevel() {
		return false;
	}

	@ConfigItem(
			keyName = "disableXpGlobes",
			name = "Disable XP globes",
			description = "Completely disables the XP globes overlay.",
			section = globalSection,
			position = 3
	)
	default boolean disableXpGlobes()
	{
		return false;
	}

	@ConfigItem(
			keyName = "disableLevelUpPopups",
			name = "Disable level up popups",
			description = "Completely disables the level up popup overlay.",
			section = globalSection,
			position = 4
	)
	default boolean disableLevelUpPopups()
	{
		return false;
	}

	@ConfigItem(
			keyName = "verticalOffset",
			name = "Vertical offset",
			description = "Offset all globes down from the top of the screen.",
			section = globalSection,
			position = 5
	)
	@Units(Units.PIXELS)
	default int verticalOffset()
	{
		return 0;
	}

	@ConfigItem(
			keyName = "Orb duration",
			name = "Duration of orbs",
			description = "Change the duration the XP orbs are visible.",
			section = progressOrbsSection,
			position = 22
	)
	@Units(Units.SECONDS)
	default int xpOrbDuration()
	{
		return 8;
	}

	@ConfigItem(
			keyName = "showPercentOnHover",
			name = "Show % On Hover",
			description = "Darken XP orb on hover and show hover information.",
			section = progressOrbsSection,
			position = 21
	)
	default boolean showPercentOnHover()
	{
		return true;
	}

	@ConfigItem(
			keyName = "animateOrbReposition",
			name = "Animate orb reposition",
			description = "Slide XP globes into new positions when globes are added/removed.",
			section = progressOrbsSection,
			position = 27
	)
	default boolean animateOrbReposition()
	{
		return false;
	}

	@ConfigItem(
			keyName = "orbScale",
			name = "Orb scale",
			description = "Scale the XP orbs size (default 100% = 57x56).",
			section = progressOrbsSection,
			position = 23
	)
	@Units(Units.PERCENT)
	default int orbScale()
	{
		return 100;
	}

	@ConfigItem(
			keyName = "orbSpacing",
			name = "Orb spacing",
			description = "Spacing between orbs in pixels.",
			section = progressOrbsSection,
			position = 24
	)
	@Units(Units.PIXELS)
	default int orbSpacing()
	{
		return 5;
	}

	@ConfigItem(
			keyName = "fadeOutDuration",
			name = "Fade out duration",
			description = "Duration of fade out animation in seconds. Set to 0 to disable fade.",
			section = progressOrbsSection,
			position = 25
	)
	@Units(Units.SECONDS)
	default double fadeOutDuration()
	{
		return 0.5;
	}

	@ConfigItem(
			keyName = "maximumGlobes",
			name = "Maximum globes",
			description = "Maximum number of globes shown at once.",
			section = progressOrbsSection,
			position = 26
	)
	default int maximumGlobes()
	{
		return 5;
	}

	@ConfigItem(
			keyName = "levelUpDuration",
			name = "Level up duration",
			description = "Duration level up globe is visible in seconds.",
			section = levelUpOrbsSection,
			position = 27
	)
	@Units(Units.SECONDS)
	default int levelUpDuration()
	{
		return 6;
	}

	@ConfigItem(
			keyName = "levelUpFadeDuration",
			name = "Level up fade duration",
			description = "Time to fade out in seconds. Set to 0 to disable fade.",
			section = levelUpOrbsSection,
			position = 28
	)
	@Units(Units.SECONDS)
	default double levelUpFadeDuration()
	{
		return 0.5;
	}

	@ConfigItem(
			keyName = "levelUpScale",
			name = "Level up scale",
			description = "Scale the level up globe size (default 100% = 144x98).",
			section = levelUpOrbsSection,
			position = 29
	)
	@Units(Units.PERCENT)
	default int levelUpScale()
	{
		return 100;
	}

	@ConfigItem(
			keyName = "showMilestones",
			name = "Show milestones",
			description = "Show milestone messages below level-up globe.",
			section = levelUpOrbsSection,
			position = 30
	)
	default boolean showMilestones()
	{
		return true;
	}

	@ConfigItem(
			keyName = "maxMilestones",
			name = "Maximum milestones",
			description = "Maximum number of milestone messages to show.",
			section = levelUpOrbsSection,
			position = 31
	)
	default int maxMilestones()
	{
		return 10;
	}

	@ConfigItem(
			keyName = "showQuestRequirementsMet",
			name = "Quest requirements met",
			description = "Show when this level meets one of the requirements for a quest.",
			section = levelUpOrbsSection,
			position = 32
	)
	default boolean showQuestRequirementsMet()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showAllQuestRequirementsMet",
			name = "All quest requirements met",
			description = "Show when you now have all the levels required for a quest.",
			section = levelUpOrbsSection,
			position = 33
	)
	default boolean showAllQuestRequirementsMet()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showSkillLevelUps",
			name = "Skill level ups",
			description = "Show skill guide unlocks for this level (e.g. new items, activities).",
			section = levelUpOrbsSection,
			position = 34
	)
	default boolean showSkillLevelUps()
	{
		return true;
	}

	@ConfigItem(
			keyName = "enableTooltips",
			name = "Enable tooltips",
			description = "Configures whether or not to show tooltips.",
			position = 0,
			section = xpTooltipSection
	)
	default boolean enableTooltips()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showXpLeft",
			name = "Show XP left",
			description = "Shows XP left inside the globe tooltip box.",
			position = 1,
			section = xpTooltipSection
	)
	default boolean showXpLeft()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showActionsLeft",
			name = "Show actions left",
			description = "Shows the number of actions left inside the globe tooltip box.",
			position = 2,
			section = xpTooltipSection
	)
	default boolean showActionsLeft()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showXpHour",
			name = "Show XP/hr",
			description = "Shows XP per hour inside the globe tooltip box.",
			position = 3,
			section = xpTooltipSection
	)
	default boolean showXpHour()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showTimeTilGoal",
			name = "Show time til goal",
			description = "Shows the amount of time until goal level in the globe tooltip box.",
			position = 4,
			section = xpTooltipSection
	)
	default boolean showTimeTilGoal()
	{
		return true;
	}

}