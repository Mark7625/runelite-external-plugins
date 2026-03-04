package io.mark.globes;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
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

	@ConfigSection(
			name = "XP drop popups",
			description = "Floating XP popup that appears from center of screen.",
			closedByDefault = true,
			position = 4
	)
	String xpDropsSection = "xpDrops";

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
			keyName = "overlayFont",
			name = "Overlay font",
			description = "Font used for XP popups, level up text, and orb percent on hover.",
			section = globalSection,
			position = 5
	)
	default OverlayFontType overlayFont()
	{
		return OverlayFontType.RUNESCAPE;
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
			keyName = "enableXpDrops",
			name = "Enable XP drop popups",
			description = "Shows the floating XP popup below the middle orb when you gain experience.",
			section = xpDropsSection,
			position = 0
	)
	default boolean enableXpDrops()
	{
		return true;
	}

	@ConfigItem(
			keyName = "xpDropSpeed",
			name = "XP drop speed",
			description = "Speed at which the XP popup moves up (pixels per second).",
			section = xpDropsSection,
			position = 1
	)
	@Range(min = 20, max = 150)
	default int xpDropSpeed()
	{
		return 80;
	}

	@ConfigItem(
			keyName = "xpDropShowIcons",
			name = "Show skill icons",
			description = "Show skill icons next to the XP amount.",
			section = xpDropsSection,
			position = 2
	)
	default boolean xpDropShowIcons()
	{
		return false;
	}

	@ConfigItem(
			keyName = "xpDropFontColor",
			name = "Font color",
			description = "Color of the XP text.",
			section = xpDropsSection,
			position = 3
	)
	default Color xpDropFontColor()
	{
		return Color.decode("#E4A63E");
	}

	@ConfigItem(
			keyName = "xpDropScale",
			name = "Scale",
			description = "Scale of the XP popup.",
			section = xpDropsSection,
			position = 4
	)
	@Range(min = 50, max = 150)
	@Units(Units.PERCENT)
	default int xpDropScale()
	{
		return 140;
	}

	@ConfigItem(
			keyName = "xpDropOffset",
			name = "Offset below orb",
			description = "Pixels below the middle orb where the XP drop appears.",
			section = xpDropsSection,
			position = 5
	)
	@Range(min = 0, max = 200)
	@Units(Units.PIXELS)
	default int xpDropOffset()
	{
		return 100;
	}

	@ConfigItem(
			keyName = "xpDropShowBackground",
			name = "Show background box",
			description = "Draws a dark box behind the XP drop text for better visibility.",
			section = xpDropsSection,
			position = 6
	)
	default boolean xpDropShowBackground()
	{
		return false;
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
			keyName = "orbScale1",
			name = "Orb scale",
			description = "Scale the XP orbs size. (default = 27)",
			section = progressOrbsSection,
			position = 23
	)
	@Units(Units.PERCENT)
	default int orbScale()
	{
		return 27;
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
			description = "Scale the level up globe size.",
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
			keyName = "questRequirementMode",
			name = "Quest unlocks",
			description = "Only when all met: show when you have all quest requirements. When some met: show when this level meets at least one requirement for a quest.",
			section = levelUpOrbsSection,
			position = 32
	)
	default RequirementDisplayMode questRequirementMode()
	{
		return RequirementDisplayMode.PARTIAL_MET;
	}

	@ConfigItem(
			keyName = "skillUnlockRequirementMode",
			name = "Skill unlocks",
			description = "Only when all met: show an unlock when you meet every level requirement. When some met: show when you meet at least one requirement.",
			section = levelUpOrbsSection,
			position = 33
	)
	default RequirementDisplayMode skillUnlockRequirementMode()
	{
		return RequirementDisplayMode.ALL_MET;
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
			keyName = "showMilestoneIcons",
			name = "Show milestone icons",
			description = "Show Milestone icons.",
			section = levelUpOrbsSection,
			position = 35
	)
	default boolean showMilestoneIcons()
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
			name = "Show until goal",
			description = "Shows XP remaining until goal in the globe tooltip.",
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

	@ConfigItem(
			keyName = "showGoalInfo",
			name = "Show goal XP",
			description = "Shows Goal XP and Until goal when a goal is set.",
			position = 5,
			section = xpTooltipSection
	)
	default boolean showGoalInfo()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showGoalBar",
			name = "Show goal progress bar",
			description = "Shows a progress bar for goal completion.",
			position = 6,
			section = xpTooltipSection
	)
	default boolean showGoalBar()
	{
		return true;
	}

	@ConfigItem(
			keyName = "useCompactNumbers",
			name = "Compact number format",
			description = "Use compact format for numbers (e.g. 1.2M instead of 1,200,000).",
			position = 7,
			section = xpTooltipSection
	)
	default boolean useCompactNumbers()
	{
		return false;
	}

}