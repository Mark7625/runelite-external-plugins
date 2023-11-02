package io.mark.minimap;

import net.runelite.client.config.*;

@ConfigGroup("enhancedminimap")
public interface EnhancedMinimapConfig extends Config
{
	String CONFIG_GROUP = "minimapIconManager";

	/*====== General settings ======*/

	@ConfigSection(
		name = "General",
		description = "General settings",
		position = 0
	)
	String generalSettings = "generalSettings";

	@ConfigItem(
			keyName = "hdminimap",
			name = "HD Minimap",
			description = "Adds the HD minimap from 2008.",
			position = 1,
			section = generalSettings
	)
	default boolean hdMinimap() {
		return true;
	}

	@ConfigItem(
			keyName = "scaleIcons",
			name = "Scale Down Minimap Icons",
			description = "Scale down minimap icons as you zoom out.",
			position = 2,
			section = generalSettings
	)
	default boolean scaleMinimapIcons() {
		return false;
	}

	@ConfigSection(
			name = "Hide Map Icons",
			description = "Hide Map Icons",
			position = 3,
			closedByDefault = true
	)
	String hideMapIcons = "minimapIconSettings";

	@ConfigItem(
			keyName = "hideicons",
			name = "Hide All icons",
			description = "Hides All Minimap Icons.",
			position = 4,
			section = hideMapIcons
	)
	default boolean hideAllMinimapIcons() {
		return false;
	}

	@ConfigItem(
			keyName = "hideicons",
			name = "Always Hide All icons",
			description = "Always Hide selected Icons ignoring scale",
			position = 4,
			section = hideMapIcons
	)
	default boolean alwaysHideIcons() {
		return false;
	}

	@Range(
		min = 8,
		max = 2
	)
	@ConfigItem(
		keyName = "scale",
		name = "Activated Scale",
		description = "Pick what scale amount you wish to hide the minimap icons",
		position = 6,
		section = hideMapIcons
	)
	default double effectScale() {
		return 8.0;
	}

	@ConfigItem(
			keyName = "agilitytraining",
			name = "Agility Training",
			description = "Agility Training Icon",
			position = 6,
			section = hideMapIcons
	)
	default boolean hideagilitytraining() {
		return false;
	}
	@ConfigItem(
			keyName = "amuletshop",
			name = "Amulet Shop",
			description = "Amulet Shop Icon",
			position = 7,
			section = hideMapIcons
	)
	default boolean hideamuletshop() {
		return false;
	}
	@ConfigItem(
			keyName = "anvil",
			name = "Anvil",
			description = "Anvil Icon",
			position = 8,
			section = hideMapIcons
	)
	default boolean hideanvil() {
		return false;
	}
	@ConfigItem(
			keyName = "apothecary",
			name = "Apothecary",
			description = "Apothecary Icon",
			position = 9,
			section = hideMapIcons
	)
	default boolean hideapothecary() {
		return false;
	}
	@ConfigItem(
			keyName = "archeryshop",
			name = "Archery Shop",
			description = "Archery Shop Icon",
			position = 10,
			section = hideMapIcons
	)
	default boolean hidearcheryshop() {
		return false;
	}
	@ConfigItem(
			keyName = "axeshop",
			name = "Axe Shop",
			description = "Axe Shop Icon",
			position = 11,
			section = hideMapIcons
	)
	default boolean hideaxeshop() {
		return false;
	}
	@ConfigItem(
			keyName = "banktutor",
			name = "Bank Tutor",
			description = "Bank Tutor Icon",
			position = 12,
			section = hideMapIcons
	)
	default boolean hidebanktutor() {
		return false;
	}
	@ConfigItem(
			keyName = "bountyhunterstore",
			name = "Bounty Hunter Store",
			description = "Bounty Hunter Store Icon",
			position = 13,
			section = hideMapIcons
	)
	default boolean hidebountyhunterstore() {
		return false;
	}
	@ConfigItem(
			keyName = "brewery",
			name = "Brewery",
			description = "Brewery Icon",
			position = 14,
			section = hideMapIcons
	)
	default boolean hidebrewery() {
		return false;
	}
	@ConfigItem(
			keyName = "candleshop",
			name = "Candle Shop",
			description = "Candle Shop Icon",
			position = 15,
			section = hideMapIcons
	)
	default boolean hidecandleshop() {
		return false;
	}
	@ConfigItem(
			keyName = "chainmailshop",
			name = "Chainmail Shop",
			description = "Chainmail Shop Icon",
			position = 16,
			section = hideMapIcons
	)
	default boolean hidechainmailshop() {
		return false;
	}
	@ConfigItem(
			keyName = "clothesshop",
			name = "Clothes Shop",
			description = "Clothes Shop Icon",
			position = 17,
			section = hideMapIcons
	)
	default boolean hideclothesshop() {
		return false;
	}
	@ConfigItem(
			keyName = "combattraining",
			name = "Combat Training",
			description = "Combat Training Icon",
			position = 18,
			section = hideMapIcons
	)
	default boolean hidecombattraining() {
		return false;
	}
	@ConfigItem(
			keyName = "combattutor",
			name = "Combat Tutor",
			description = "Combat Tutor Icon",
			position = 19,
			section = hideMapIcons
	)
	default boolean hidecombattutor() {
		return false;
	}
	@ConfigItem(
			keyName = "cookingrange",
			name = "Cooking Range",
			description = "Cooking Range Icon",
			position = 20,
			section = hideMapIcons
	)
	default boolean hidecookingrange() {
		return false;
	}
	@ConfigItem(
			keyName = "cookingtutor",
			name = "Cooking Tutor",
			description = "Cooking Tutor Icon",
			position = 21,
			section = hideMapIcons
	)
	default boolean hidecookingtutor() {
		return false;
	}
	@ConfigItem(
			keyName = "craftingshop",
			name = "Crafting Shop",
			description = "Crafting Shop Icon",
			position = 22,
			section = hideMapIcons
	)
	default boolean hidecraftingshop() {
		return false;
	}
	@ConfigItem(
			keyName = "craftingtutor",
			name = "Crafting Tutor",
			description = "Crafting Tutor Icon",
			position = 23,
			section = hideMapIcons
	)
	default boolean hidecraftingtutor() {
		return false;
	}
	@ConfigItem(
			keyName = "dangertutor",
			name = "Danger Tutor",
			description = "Danger Tutor Icon",
			position = 24,
			section = hideMapIcons
	)
	default boolean hidedangertutor() {
		return false;
	}
	@ConfigItem(
			keyName = "dairychurn",
			name = "Dairy Churn",
			description = "Dairy Churn Icon",
			position = 25,
			section = hideMapIcons
	)
	default boolean hidedairychurn() {
		return false;
	}
	@ConfigItem(
			keyName = "dairycow",
			name = "Dairy Cow",
			description = "Dairy Cow Icon",
			position = 26,
			section = hideMapIcons
	)
	default boolean hidedairycow() {
		return false;
	}
	@ConfigItem(
			keyName = "deadmantutor",
			name = "Deadman Tutor",
			description = "Deadman Tutor Icon",
			position = 27,
			section = hideMapIcons
	)
	default boolean hidedeadmantutor() {
		return false;
	}
	@ConfigItem(
			keyName = "dyetrader",
			name = "Dye Trader",
			description = "Dye Trader Icon",
			position = 28,
			section = hideMapIcons
	)
	default boolean hidedyetrader() {
		return false;
	}
	@ConfigItem(
			keyName = "estateagent",
			name = "Estate Agent",
			description = "Estate Agent Icon",
			position = 29,
			section = hideMapIcons
	)
	default boolean hideestateagent() {
		return false;
	}
	@ConfigItem(
			keyName = "farmingpatch",
			name = "Farming Patch",
			description = "Farming Patch Icon",
			position = 30,
			section = hideMapIcons
	)
	default boolean hidefarmingpatch() {
		return false;
	}
	@ConfigItem(
			keyName = "farmingshop",
			name = "Farming Shop",
			description = "Farming Shop Icon",
			position = 31,
			section = hideMapIcons
	)
	default boolean hidefarmingshop() {
		return false;
	}
	@ConfigItem(
			keyName = "fishingshop",
			name = "Fishing Shop",
			description = "Fishing Shop Icon",
			position = 32,
			section = hideMapIcons
	)
	default boolean hidefishingshop() {
		return false;
	}
	@ConfigItem(
			keyName = "fishingtutor",
			name = "Fishing Tutor",
			description = "Fishing Tutor Icon",
			position = 33,
			section = hideMapIcons
	)
	default boolean hidefishingtutor() {
		return false;
	}
	@ConfigItem(
			keyName = "foodshop",
			name = "Food Shop",
			description = "Food Shop Icon",
			position = 34,
			section = hideMapIcons
	)
	default boolean hidefoodshop() {
		return false;
	}
	@ConfigItem(
			keyName = "furtrader",
			name = "Fur Trader",
			description = "Fur Trader Icon",
			position = 35,
			section = hideMapIcons
	)
	default boolean hidefurtrader() {
		return false;
	}
	@ConfigItem(
			keyName = "gardensupplier",
			name = "Garden Supplier",
			description = "Garden Supplier Icon",
			position = 36,
			section = hideMapIcons
	)
	default boolean hidegardensupplier() {
		return false;
	}
	@ConfigItem(
			keyName = "gemshop",
			name = "Gem Shop",
			description = "Gem Shop Icon",
			position = 37,
			section = hideMapIcons
	)
	default boolean hidegemshop() {
		return false;
	}
	@ConfigItem(
			keyName = "generalstore",
			name = "General Store",
			description = "General Store Icon",
			position = 38,
			section = hideMapIcons
	)
	default boolean hidegeneralstore() {
		return false;
	}
	@ConfigItem(
			keyName = "grandexchange",
			name = "Grand Exchange",
			description = "Grand Exchange Icon",
			position = 39,
			section = hideMapIcons
	)
	default boolean hidegrandexchange() {
		return false;
	}
	@ConfigItem(
			keyName = "helmetshop",
			name = "Helmet Shop",
			description = "Helmet Shop Icon",
			position = 40,
			section = hideMapIcons
	)
	default boolean hidehelmetshop() {
		return false;
	}
	@ConfigItem(
			keyName = "herbalist",
			name = "Herbalist",
			description = "Herbalist Icon",
			position = 41,
			section = hideMapIcons
	)
	default boolean hideherbalist() {
		return false;
	}
	@ConfigItem(
			keyName = "holidayitemtrader",
			name = "Holiday Item Trader",
			description = "Holiday Item Trader Icon",
			position = 42,
			section = hideMapIcons
	)
	default boolean hideholidayitemtrader() {
		return false;
	}
	@ConfigItem(
			keyName = "huntershop",
			name = "Hunter Shop",
			description = "Hunter Shop Icon",
			position = 43,
			section = hideMapIcons
	)
	default boolean hidehuntershop() {
		return false;
	}
	@ConfigItem(
			keyName = "huntertraining",
			name = "Hunter Training",
			description = "Hunter Training Icon",
			position = 44,
			section = hideMapIcons
	)
	default boolean hidehuntertraining() {
		return false;
	}
	@ConfigItem(
			keyName = "huntertutor",
			name = "Hunter Tutor",
			description = "Hunter Tutor Icon",
			position = 45,
			section = hideMapIcons
	)
	default boolean hidehuntertutor() {
		return false;
	}
	@ConfigItem(
			keyName = "ironmantutor",
			name = "Ironman Tutor",
			description = "Ironman Tutor Icon",
			position = 46,
			section = hideMapIcons
	)
	default boolean hideironmantutor() {
		return false;
	}
	@ConfigItem(
			keyName = "jewelleryshop",
			name = "Jewellery Shop",
			description = "Jewellery Shop Icon",
			position = 47,
			section = hideMapIcons
	)
	default boolean hidejewelleryshop() {
		return false;
	}
	@ConfigItem(
			keyName = "junkchecker",
			name = "Junk Checker",
			description = "Junk Checker Icon",
			position = 48,
			section = hideMapIcons
	)
	default boolean hidejunkchecker() {
		return false;
	}
	@ConfigItem(
			keyName = "loom",
			name = "Loom",
			description = "Loom Icon",
			position = 49,
			section = hideMapIcons
	)
	default boolean hideloom() {
		return false;
	}
	@ConfigItem(
			keyName = "lumbridgeguide",
			name = "Lumbridge Guide",
			description = "Lumbridge Guide Icon",
			position = 50,
			section = hideMapIcons
	)
	default boolean hidelumbridgeguide() {
		return false;
	}
	@ConfigItem(
			keyName = "maceshop",
			name = "Mace Shop",
			description = "Mace Shop Icon",
			position = 51,
			section = hideMapIcons
	)
	default boolean hidemaceshop() {
		return false;
	}
	@ConfigItem(
			keyName = "magicshop",
			name = "Magic Shop",
			description = "Magic Shop Icon",
			position = 52,
			section = hideMapIcons
	)
	default boolean hidemagicshop() {
		return false;
	}
	@ConfigItem(
			keyName = "miningshop",
			name = "Mining Shop",
			description = "Mining Shop Icon",
			position = 53,
			section = hideMapIcons
	)
	default boolean hideminingshop() {
		return false;
	}
	@ConfigItem(
			keyName = "miningtutor",
			name = "Mining Tutor",
			description = "Mining Tutor Icon",
			position = 54,
			section = hideMapIcons
	)
	default boolean hideminingtutor() {
		return false;
	}
	@ConfigItem(
			keyName = "miningsite",
			name = "Mining Site",
			description = "Mining Site Icon",
			position = 55,
			section = hideMapIcons
	)
	default boolean hideminingsite() {
		return false;
	}
	@ConfigItem(
			keyName = "newspapertrader",
			name = "Newspaper Trader",
			description = "Newspaper Trader Icon",
			position = 56,
			section = hideMapIcons
	)
	default boolean hidenewspapertrader() {
		return false;
	}
	@ConfigItem(
			keyName = "petinsuranceshop",
			name = "Pet Insurance Shop",
			description = "Pet Insurance Shop Icon",
			position = 57,
			section = hideMapIcons
	)
	default boolean hidepetinsuranceshop() {
		return false;
	}
	@ConfigItem(
			keyName = "platebodyshop",
			name = "Platebody Shop",
			description = "Platebody Shop Icon",
			position = 58,
			section = hideMapIcons
	)
	default boolean hideplatebodyshop() {
		return false;
	}
	@ConfigItem(
			keyName = "platelegsshop",
			name = "Platelegs Shop",
			description = "Platelegs Shop Icon",
			position = 59,
			section = hideMapIcons
	)
	default boolean hideplatelegsshop() {
		return false;
	}
	@ConfigItem(
			keyName = "plateskirtshop",
			name = "Plateskirt Shop",
			description = "Plateskirt Shop Icon",
			position = 60,
			section = hideMapIcons
	)
	default boolean hideplateskirtshop() {
		return false;
	}
	@ConfigItem(
			keyName = "potterywheel",
			name = "Pottery Wheel",
			description = "Pottery Wheel Icon",
			position = 61,
			section = hideMapIcons
	)
	default boolean hidepotterywheel() {
		return false;
	}
	@ConfigItem(
			keyName = "prayertutor",
			name = "Prayer Tutor",
			description = "Prayer Tutor Icon",
			position = 62,
			section = hideMapIcons
	)
	default boolean hideprayertutor() {
		return false;
	}
	@ConfigItem(
			keyName = "pricingexpert",
			name = "Pricing Expert",
			description = "Pricing Expert Icon",
			position = 63,
			section = hideMapIcons
	)
	default boolean hidepricingexpert() {
		return false;
	}
	@ConfigItem(
			keyName = "pubbar",
			name = "Pub Bar",
			description = "Pub Bar Icon",
			position = 64,
			section = hideMapIcons
	)
	default boolean hidepubbar() {
		return false;
	}
	@ConfigItem(
			keyName = "raretrees",
			name = "Rare Trees",
			description = "Rare Trees Icon",
			position = 65,
			section = hideMapIcons
	)
	default boolean hideraretrees() {
		return false;
	}
	@ConfigItem(
			keyName = "ropetrader",
			name = "Rope Trader",
			description = "Rope Trader Icon",
			position = 66,
			section = hideMapIcons
	)
	default boolean hideropetrader() {
		return false;
	}
	@ConfigItem(
			keyName = "sandpit",
			name = "Sandpit",
			description = "Sandpit Icon",
			position = 67,
			section = hideMapIcons
	)
	default boolean hidesandpit() {
		return false;
	}
	@ConfigItem(
			keyName = "securitytutor",
			name = "Security Tutor",
			description = "Security Tutor Icon",
			position = 68,
			section = hideMapIcons
	)
	default boolean hidesecuritytutor() {
		return false;
	}
	@ConfigItem(
			keyName = "slayermaster",
			name = "Slayer Master",
			description = "Slayer Master Icon",
			position = 69,
			section = hideMapIcons
	)
	default boolean hideslayermaster() {
		return false;
	}
	@ConfigItem(
			keyName = "smithingtutor",
			name = "Smithing Tutor",
			description = "Smithing Tutor Icon",
			position = 70,
			section = hideMapIcons
	)
	default boolean hidesmithingtutor() {
		return false;
	}
	@ConfigItem(
			keyName = "spiceshop",
			name = "Spice Shop",
			description = "Spice Shop Icon",
			position = 71,
			section = hideMapIcons
	)
	default boolean hidespiceshop() {
		return false;
	}
	@ConfigItem(
			keyName = "spinningwheel",
			name = "Spinning Wheel",
			description = "Spinning Wheel Icon",
			position = 72,
			section = hideMapIcons
	)
	default boolean hidespinningwheel() {
		return false;
	}
	@ConfigItem(
			keyName = "staffshop",
			name = "Staff Shop",
			description = "Staff Shop Icon",
			position = 73,
			section = hideMapIcons
	)
	default boolean hidestaffshop() {
		return false;
	}
	@ConfigItem(
			keyName = "stonemasonshop",
			name = "Stonemason Shop",
			description = "Stonemason Shop Icon",
			position = 74,
			section = hideMapIcons
	)
	default boolean hidestonemasonshop() {
		return false;
	}
	@ConfigItem(
			keyName = "swordshop",
			name = "Sword Shop",
			description = "Sword Shop Icon",
			position = 75,
			section = hideMapIcons
	)
	default boolean hideswordshop() {
		return false;
	}
	@ConfigItem(
			keyName = "tannery",
			name = "Tannery",
			description = "Tannery Icon",
			position = 76,
			section = hideMapIcons
	)
	default boolean hidetannery() {
		return false;
	}
	@ConfigItem(
			keyName = "taxidermist",
			name = "Taxidermist",
			description = "Taxidermist Icon",
			position = 77,
			section = hideMapIcons
	)
	default boolean hidetaxidermist() {
		return false;
	}
	@ConfigItem(
			keyName = "teatrader",
			name = "Tea Trader",
			description = "Tea Trader Icon",
			position = 78,
			section = hideMapIcons
	)
	default boolean hideteatrader() {
		return false;
	}
	@ConfigItem(
			keyName = "training",
			name = "Training",
			description = "Training Icon",
			position = 79,
			section = hideMapIcons
	)
	default boolean hidetraining() {
		return false;
	}
	@ConfigItem(
			keyName = "vegetableshop",
			name = "Vegetable Shop",
			description = "Vegetable Shop Icon",
			position = 80,
			section = hideMapIcons
	)
	default boolean hidevegetableshop() {
		return false;
	}
	@ConfigItem(
			keyName = "watersource",
			name = "Water Source",
			description = "Water Source Icon",
			position = 81,
			section = hideMapIcons
	)
	default boolean hidewatersource() {
		return false;
	}
	@ConfigItem(
			keyName = "winetrader",
			name = "Wine Trader",
			description = "Wine Trader Icon",
			position = 82,
			section = hideMapIcons
	)
	default boolean hidewinetrader() {
		return false;
	}
	@ConfigItem(
			keyName = "windmill",
			name = "Windmill",
			description = "Windmill Icon",
			position = 83,
			section = hideMapIcons
	)
	default boolean hidewindmill() {
		return false;
	}
	@ConfigItem(
			keyName = "woodcuttingstump",
			name = "Woodcutting Stump",
			description = "Woodcutting Stump Icon",
			position = 84,
			section = hideMapIcons
	)
	default boolean hidewoodcuttingstump() {
		return false;
	}
	@ConfigItem(
			keyName = "woodcuttingtutor",
			name = "Woodcutting Tutor",
			description = "Woodcutting Tutor Icon",
			position = 85,
			section = hideMapIcons
	)
	default boolean hidewoodcuttingtutor() {
		return false;
	}


}
