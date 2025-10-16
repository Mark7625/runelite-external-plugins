package io.mark.hdminimap;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/**
 * Configuration interface for the HD Minimap plugin
 */
@ConfigGroup(HDMinimapConfig.CONFIG_GROUP)
public interface HDMinimapConfig extends Config {

    String CONFIG_GROUP = "hdminimap";

    @ConfigSection(
            name = "Disable Map Icons",
            description = "Disable Map Icons",
            position = 2
    )
    String mapIconSettings = "mapIcons";


    @ConfigItem(
            keyName = "agilityshortcut",
            name = "Agility shortcut",
            description = "Toggle map icons for agility shortcut objects.",
            position = 0,
            section = mapIconSettings
    )
    default boolean agilityshortcut()
    {
        return false;
    }

    @ConfigItem(
            keyName = "agilitytraining",
            name = "Agility training",
            description = "Toggle map icons for agility training objects.",
            position = 1,
            section = mapIconSettings
    )
    default boolean agilitytraining()
    {
        return false;
    }

    @ConfigItem(
            keyName = "altar",
            name = "Altar",
            description = "Toggle map icons for altar objects.",
            position = 2,
            section = mapIconSettings
    )
    default boolean altar()
    {
        return false;
    }

    @ConfigItem(
            keyName = "anvil",
            name = "Anvil",
            description = "Toggle map icons for anvil objects.",
            position = 3,
            section = mapIconSettings
    )
    default boolean anvil()
    {
        return false;
    }

    @ConfigItem(
            keyName = "apothecary",
            name = "Apothecary",
            description = "Toggle map icons for apothecary objects.",
            position = 4,
            section = mapIconSettings
    )
    default boolean apothecary()
    {
        return false;
    }

    @ConfigItem(
            keyName = "bank",
            name = "Bank",
            description = "Toggle map icons for bank objects.",
            position = 5,
            section = mapIconSettings
    )
    default boolean bank()
    {
        return false;
    }

    @ConfigItem(
            keyName = "birdhousesite",
            name = "Bird house site",
            description = "Toggle map icons for bird house site objects.",
            position = 6,
            section = mapIconSettings
    )
    default boolean birdhousesite()
    {
        return false;
    }

    @ConfigItem(
            keyName = "bountyhuntertrader",
            name = "Bounty Hunter trader",
            description = "Toggle map icons for bounty hunter trader objects.",
            position = 7,
            section = mapIconSettings
    )
    default boolean bountyhuntertrader()
    {
        return false;
    }

    @ConfigItem(
            keyName = "brewery",
            name = "Brewery",
            description = "Toggle map icons for brewery objects.",
            position = 8,
            section = mapIconSettings
    )
    default boolean brewery()
    {
        return false;
    }

    @ConfigItem(
            keyName = "clanhub",
            name = "Clan Hub",
            description = "Toggle map icons for clan hub objects.",
            position = 9,
            section = mapIconSettings
    )
    default boolean clanhub()
    {
        return false;
    }

    @ConfigItem(
            keyName = "combatachievements",
            name = "Combat Achievements",
            description = "Toggle map icons for combat achievements objects.",
            position = 10,
            section = mapIconSettings
    )
    default boolean combatachievements()
    {
        return false;
    }

    @ConfigItem(
            keyName = "combattraining",
            name = "Combat training",
            description = "Toggle map icons for combat training objects.",
            position = 11,
            section = mapIconSettings
    )
    default boolean combattraining()
    {
        return false;
    }

    @ConfigItem(
            keyName = "cookingrange",
            name = "Cooking range",
            description = "Toggle map icons for cooking range objects.",
            position = 12,
            section = mapIconSettings
    )
    default boolean cookingrange()
    {
        return false;
    }

    @ConfigItem(
            keyName = "dairychurn",
            name = "Dairy churn",
            description = "Toggle map icons for dairy churn objects.",
            position = 13,
            section = mapIconSettings
    )
    default boolean dairychurn()
    {
        return false;
    }

    @ConfigItem(
            keyName = "dairycow",
            name = "Dairy cow",
            description = "Toggle map icons for dairy cow objects.",
            position = 14,
            section = mapIconSettings
    )
    default boolean dairycow()
    {
        return false;
    }

    @ConfigItem(
            keyName = "deathsoffice",
            name = "Death's Office",
            description = "Toggle map icons for death's office objects.",
            position = 15,
            section = mapIconSettings
    )
    default boolean deathsoffice()
    {
        return false;
    }

    @ConfigItem(
            keyName = "distractiondiversion",
            name = "Distraction & Diversion",
            description = "Toggle map icons for distraction & diversion objects.",
            position = 16,
            section = mapIconSettings
    )
    default boolean distractiondiversion()
    {
        return false;
    }

    @ConfigItem(
            keyName = "dungeon",
            name = "Dungeon",
            description = "Toggle map icons for dungeon objects.",
            position = 17,
            section = mapIconSettings
    )
    default boolean dungeon()
    {
        return false;
    }

    @ConfigItem(
            keyName = "dyetrader",
            name = "Dye trader",
            description = "Toggle map icons for dye trader objects.",
            position = 18,
            section = mapIconSettings
    )
    default boolean dyetrader()
    {
        return false;
    }

    @ConfigItem(
            keyName = "estateagent",
            name = "Estate Agent",
            description = "Toggle map icons for estate agent objects.",
            position = 19,
            section = mapIconSettings
    )
    default boolean estateagent()
    {
        return false;
    }

    @ConfigItem(
            keyName = "fairyrings",
            name = "Fairy Rings",
            description = "Toggle map icons for fairy rings objects.",
            position = 20,
            section = mapIconSettings
    )
    default boolean fairyrings()
    {
        return false;
    }

    @ConfigItem(
            keyName = "farmingpatch",
            name = "Farming patch",
            description = "Toggle map icons for farming patch objects.",
            position = 21,
            section = mapIconSettings
    )
    default boolean farmingpatch()
    {
        return false;
    }

    @ConfigItem(
            keyName = "fishingspot",
            name = "Fishing spot",
            description = "Toggle map icons for fishing spot objects.",
            position = 22,
            section = mapIconSettings
    )
    default boolean fishingspot()
    {
        return false;
    }

    @ConfigItem(
            keyName = "furtrader",
            name = "Fur trader",
            description = "Toggle map icons for fur trader objects.",
            position = 23,
            section = mapIconSettings
    )
    default boolean furtrader()
    {
        return false;
    }

    @ConfigItem(
            keyName = "furnace",
            name = "Furnace",
            description = "Toggle map icons for furnace objects.",
            position = 24,
            section = mapIconSettings
    )
    default boolean furnace()
    {
        return false;
    }

    @ConfigItem(
            keyName = "gardensupplier",
            name = "Garden supplier",
            description = "Toggle map icons for garden supplier objects.",
            position = 25,
            section = mapIconSettings
    )
    default boolean gardensupplier()
    {
        return false;
    }

    @ConfigItem(
            keyName = "grandexchange",
            name = "Grand Exchange",
            description = "Toggle map icons for grand exchange objects.",
            position = 26,
            section = mapIconSettings
    )
    default boolean grandexchange()
    {
        return false;
    }

    @ConfigItem(
            keyName = "grindstone",
            name = "Grindstone",
            description = "Toggle map icons for grindstone objects.",
            position = 27,
            section = mapIconSettings
    )
    default boolean grindstone()
    {
        return false;
    }

    @ConfigItem(
            keyName = "hairdresser",
            name = "Hairdresser",
            description = "Toggle map icons for hairdresser objects.",
            position = 28,
            section = mapIconSettings
    )
    default boolean hairdresser()
    {
        return false;
    }

    @ConfigItem(
            keyName = "herbalist",
            name = "Herbalist",
            description = "Toggle map icons for herbalist objects.",
            position = 29,
            section = mapIconSettings
    )
    default boolean herbalist()
    {
        return false;
    }

    @ConfigItem(
            keyName = "holidayevent",
            name = "Holiday event",
            description = "Toggle map icons for holiday event objects.",
            position = 30,
            section = mapIconSettings
    )
    default boolean holidayevent()
    {
        return false;
    }

    @ConfigItem(
            keyName = "holidayitemtrader",
            name = "Holiday item trader",
            description = "Toggle map icons for holiday item trader objects.",
            position = 31,
            section = mapIconSettings
    )
    default boolean holidayitemtrader()
    {
        return false;
    }

    @ConfigItem(
            keyName = "houseportal",
            name = "House portal",
            description = "Toggle map icons for house portal objects.",
            position = 32,
            section = mapIconSettings
    )
    default boolean houseportal()
    {
        return false;
    }

    @ConfigItem(
            keyName = "huntertraining",
            name = "Hunter training",
            description = "Toggle map icons for hunter training objects.",
            position = 33,
            section = mapIconSettings
    )
    default boolean huntertraining()
    {
        return false;
    }

    @ConfigItem(
            keyName = "junkchecker",
            name = "Junk checker",
            description = "Toggle map icons for junk checker objects.",
            position = 34,
            section = mapIconSettings
    )
    default boolean junkchecker()
    {
        return false;
    }

    @ConfigItem(
            keyName = "loom",
            name = "Loom",
            description = "Toggle map icons for loom objects.",
            position = 35,
            section = mapIconSettings
    )
    default boolean loom()
    {
        return false;
    }

    @ConfigItem(
            keyName = "makeovermage",
            name = "Makeover Mage",
            description = "Toggle map icons for makeover mage objects.",
            position = 36,
            section = mapIconSettings
    )
    default boolean makeovermage()
    {
        return false;
    }

    @ConfigItem(
            keyName = "minigame",
            name = "Minigame",
            description = "Toggle map icons for minigame objects.",
            position = 37,
            section = mapIconSettings
    )
    default boolean minigame()
    {
        return false;
    }

    @ConfigItem(
            keyName = "miningsite",
            name = "Mining site",
            description = "Toggle map icons for mining site objects.",
            position = 38,
            section = mapIconSettings
    )
    default boolean miningsite()
    {
        return false;
    }

    @ConfigItem(
            keyName = "newspapertrader",
            name = "Newspaper trader",
            description = "Toggle map icons for newspaper trader objects.",
            position = 39,
            section = mapIconSettings
    )
    default boolean newspapertrader()
    {
        return false;
    }

    @ConfigItem(
            keyName = "polishingwheel",
            name = "Polishing wheel",
            description = "Toggle map icons for polishing wheel objects.",
            position = 40,
            section = mapIconSettings
    )
    default boolean polishingwheel()
    {
        return false;
    }

    @ConfigItem(
            keyName = "pollbooth",
            name = "Poll booth",
            description = "Toggle map icons for poll booth objects.",
            position = 41,
            section = mapIconSettings
    )
    default boolean pollbooth()
    {
        return false;
    }

    @ConfigItem(
            keyName = "potterywheel",
            name = "Pottery wheel",
            description = "Toggle map icons for pottery wheel objects.",
            position = 42,
            section = mapIconSettings
    )
    default boolean potterywheel()
    {
        return false;
    }

    @ConfigItem(
            keyName = "pricingexpert",
            name = "Pricing expert",
            description = "Toggle map icons for pricing expert objects.",
            position = 43,
            section = mapIconSettings
    )
    default boolean pricingexpert()
    {
        return false;
    }

    @ConfigItem(
            keyName = "pub",
            name = "Pub",
            description = "Toggle map icons for pub objects.",
            position = 44,
            section = mapIconSettings
    )
    default boolean pub()
    {
        return false;
    }

    @ConfigItem(
            keyName = "quest",
            name = "Quest",
            description = "Toggle map icons for quest objects.",
            position = 45,
            section = mapIconSettings
    )
    default boolean quest()
    {
        return false;
    }

    @ConfigItem(
            keyName = "raid",
            name = "Raid",
            description = "Toggle map icons for raid objects.",
            position = 46,
            section = mapIconSettings
    )
    default boolean raid()
    {
        return false;
    }

    @ConfigItem(
            keyName = "raretrees",
            name = "Rare trees",
            description = "Toggle map icons for rare trees objects.",
            position = 47,
            section = mapIconSettings
    )
    default boolean raretrees()
    {
        return false;
    }

    @ConfigItem(
            keyName = "ropetrader",
            name = "Rope trader",
            description = "Toggle map icons for rope trader objects.",
            position = 48,
            section = mapIconSettings
    )
    default boolean ropetrader()
    {
        return false;
    }

    @ConfigItem(
            keyName = "sandpit",
            name = "Sandpit",
            description = "Toggle map icons for sandpit objects.",
            position = 49,
            section = mapIconSettings
    )
    default boolean sandpit()
    {
        return false;
    }

    @ConfigItem(
            keyName = "sawmill",
            name = "Sawmill",
            description = "Toggle map icons for sawmill objects.",
            position = 50,
            section = mapIconSettings
    )
    default boolean sawmill()
    {
        return false;
    }

    @ConfigItem(
            keyName = "silktrader",
            name = "Silk trader",
            description = "Toggle map icons for silk trader objects.",
            position = 51,
            section = mapIconSettings
    )
    default boolean silktrader()
    {
        return false;
    }

    @ConfigItem(
            keyName = "singingbowl",
            name = "Singing bowl",
            description = "Toggle map icons for singing bowl objects.",
            position = 52,
            section = mapIconSettings
    )
    default boolean singingbowl()
    {
        return false;
    }

    @ConfigItem(
            keyName = "slayermaster",
            name = "Slayer Master",
            description = "Toggle map icons for slayer master objects.",
            position = 53,
            section = mapIconSettings
    )
    default boolean slayermaster()
    {
        return false;
    }

    @ConfigItem(
            keyName = "spinningwheel",
            name = "Spinning wheel",
            description = "Toggle map icons for spinning wheel objects.",
            position = 54,
            section = mapIconSettings
    )
    default boolean spinningwheel()
    {
        return false;
    }

    @ConfigItem(
            keyName = "stagnantwatersource",
            name = "Stagnant water source",
            description = "Toggle map icons for stagnant water source objects.",
            position = 55,
            section = mapIconSettings
    )
    default boolean stagnantwatersource()
    {
        return false;
    }

    @ConfigItem(
            keyName = "stonemason",
            name = "Stonemason",
            description = "Toggle map icons for stonemason objects.",
            position = 56,
            section = mapIconSettings
    )
    default boolean stonemason()
    {
        return false;
    }

    @ConfigItem(
            keyName = "tannery",
            name = "Tannery",
            description = "Toggle map icons for tannery objects.",
            position = 57,
            section = mapIconSettings
    )
    default boolean tannery()
    {
        return false;
    }

    @ConfigItem(
            keyName = "taskmaster",
            name = "Task Master",
            description = "Toggle map icons for task master objects.",
            position = 58,
            section = mapIconSettings
    )
    default boolean taskmaster()
    {
        return false;
    }

    @ConfigItem(
            keyName = "taxidermist",
            name = "Taxidermist",
            description = "Toggle map icons for taxidermist objects.",
            position = 59,
            section = mapIconSettings
    )
    default boolean taxidermist()
    {
        return false;
    }

    @ConfigItem(
            keyName = "teatrader",
            name = "Tea trader",
            description = "Toggle map icons for tea trader objects.",
            position = 60,
            section = mapIconSettings
    )
    default boolean teatrader()
    {
        return false;
    }

    @ConfigItem(
            keyName = "thievingactivity",
            name = "Thieving activity",
            description = "Toggle map icons for thieving activity objects.",
            position = 61,
            section = mapIconSettings
    )
    default boolean thievingactivity()
    {
        return false;
    }

    @ConfigItem(
            keyName = "transportation",
            name = "Transportation",
            description = "Toggle map icons for transportation objects.",
            position = 62,
            section = mapIconSettings
    )
    default boolean transportation()
    {
        return false;
    }

    @ConfigItem(
            keyName = "triphammer",
            name = "Trip Hammer",
            description = "Toggle map icons for trip hammer objects.",
            position = 63,
            section = mapIconSettings
    )
    default boolean triphammer()
    {
        return false;
    }

    @ConfigItem(
            keyName = "tutors",
            name = "Tutors",
            description = "Toggle map icons for tutors objects.",
            position = 64,
            section = mapIconSettings
    )
    default boolean tutors()
    {
        return false;
    }

    @ConfigItem(
            keyName = "valetotemsite",
            name = "Vale totem site",
            description = "Toggle map icons for vale totem site objects.",
            position = 65,
            section = mapIconSettings
    )
    default boolean valetotemsite()
    {
        return false;
    }

    @ConfigItem(
            keyName = "watersource",
            name = "Water source",
            description = "Toggle map icons for water source objects.",
            position = 66,
            section = mapIconSettings
    )
    default boolean watersource()
    {
        return false;
    }

    @ConfigItem(
            keyName = "windmill",
            name = "Windmill",
            description = "Toggle map icons for windmill objects.",
            position = 67,
            section = mapIconSettings
    )
    default boolean windmill()
    {
        return false;
    }

    @ConfigItem(
            keyName = "winetrader",
            name = "Wine trader",
            description = "Toggle map icons for wine trader objects.",
            position = 68,
            section = mapIconSettings
    )
    default boolean winetrader()
    {
        return false;
    }

    @ConfigItem(
            keyName = "woodcuttingstump",
            name = "Woodcutting stump",
            description = "Toggle map icons for woodcutting stump objects.",
            position = 69,
            section = mapIconSettings
    )
    default boolean woodcuttingstump()
    {
        return false;
    }

    @ConfigItem(
            keyName = "amuletshop",
            name = "Amulet shop",
            description = "Toggle map icons for amulet shop objects.",
            position = 70,
            section = mapIconSettings
    )
    default boolean amuletshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "archeryshop",
            name = "Archery shop",
            description = "Toggle map icons for archery shop objects.",
            position = 71,
            section = mapIconSettings
    )
    default boolean archeryshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "axeshop",
            name = "Axe shop",
            description = "Toggle map icons for axe shop objects.",
            position = 72,
            section = mapIconSettings
    )
    default boolean axeshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "candleshop",
            name = "Candle shop",
            description = "Toggle map icons for candle shop objects.",
            position = 73,
            section = mapIconSettings
    )
    default boolean candleshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "chainmailshop",
            name = "Chainmail shop",
            description = "Toggle map icons for chainmail shop objects.",
            position = 74,
            section = mapIconSettings
    )
    default boolean chainmailshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "clothesshop",
            name = "Clothes shop",
            description = "Toggle map icons for clothes shop objects.",
            position = 75,
            section = mapIconSettings
    )
    default boolean clothesshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "craftingshop",
            name = "Crafting shop",
            description = "Toggle map icons for crafting shop objects.",
            position = 76,
            section = mapIconSettings
    )
    default boolean craftingshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "farmingshop",
            name = "Farming shop",
            description = "Toggle map icons for farming shop objects.",
            position = 77,
            section = mapIconSettings
    )
    default boolean farmingshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "fishingshop",
            name = "Fishing shop",
            description = "Toggle map icons for fishing shop objects.",
            position = 78,
            section = mapIconSettings
    )
    default boolean fishingshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "foodshop",
            name = "Food shop",
            description = "Toggle map icons for food shop objects.",
            position = 79,
            section = mapIconSettings
    )
    default boolean foodshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "forestryshop",
            name = "Forestry shop",
            description = "Toggle map icons for forestry shop objects.",
            position = 80,
            section = mapIconSettings
    )
    default boolean forestryshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "gemshop",
            name = "Gem shop",
            description = "Toggle map icons for gem shop objects.",
            position = 81,
            section = mapIconSettings
    )
    default boolean gemshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "generalstore",
            name = "General store",
            description = "Toggle map icons for general store objects.",
            position = 82,
            section = mapIconSettings
    )
    default boolean generalstore()
    {
        return false;
    }

    @ConfigItem(
            keyName = "helmetshop",
            name = "Helmet shop",
            description = "Toggle map icons for helmet shop objects.",
            position = 83,
            section = mapIconSettings
    )
    default boolean helmetshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "huntershop",
            name = "Hunter shop",
            description = "Toggle map icons for hunter shop objects.",
            position = 84,
            section = mapIconSettings
    )
    default boolean huntershop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "jewelleryshop",
            name = "Jewellery shop",
            description = "Toggle map icons for jewellery shop objects.",
            position = 85,
            section = mapIconSettings
    )
    default boolean jewelleryshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "maceshop",
            name = "Mace shop",
            description = "Toggle map icons for mace shop objects.",
            position = 86,
            section = mapIconSettings
    )
    default boolean maceshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "magicshop",
            name = "Magic shop",
            description = "Toggle map icons for magic shop objects.",
            position = 87,
            section = mapIconSettings
    )
    default boolean magicshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "miningshop",
            name = "Mining shop",
            description = "Toggle map icons for mining shop objects.",
            position = 88,
            section = mapIconSettings
    )
    default boolean miningshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "petshop",
            name = "Pet shop",
            description = "Toggle map icons for pet shop objects.",
            position = 89,
            section = mapIconSettings
    )
    default boolean petshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "platebodyshop",
            name = "Platebody shop",
            description = "Toggle map icons for platebody shop objects.",
            position = 90,
            section = mapIconSettings
    )
    default boolean platebodyshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "platelegsshop",
            name = "Platelegs shop",
            description = "Toggle map icons for platelegs shop objects.",
            position = 91,
            section = mapIconSettings
    )
    default boolean platelegsshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "plateskirtshop",
            name = "Plateskirt shop",
            description = "Toggle map icons for plateskirt shop objects.",
            position = 92,
            section = mapIconSettings
    )
    default boolean plateskirtshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "scimitarshop",
            name = "Scimitar shop",
            description = "Toggle map icons for scimitar shop objects.",
            position = 93,
            section = mapIconSettings
    )
    default boolean scimitarshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "shieldshop",
            name = "Shield shop",
            description = "Toggle map icons for shield shop objects.",
            position = 94,
            section = mapIconSettings
    )
    default boolean shieldshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "silvershop",
            name = "Silver shop",
            description = "Toggle map icons for silver shop objects.",
            position = 95,
            section = mapIconSettings
    )
    default boolean silvershop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "speedrunningshop",
            name = "Speedrunning shop",
            description = "Toggle map icons for speedrunning shop objects.",
            position = 96,
            section = mapIconSettings
    )
    default boolean speedrunningshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "spiceshop",
            name = "Spice shop",
            description = "Toggle map icons for spice shop objects.",
            position = 97,
            section = mapIconSettings
    )
    default boolean spiceshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "staffshop",
            name = "Staff shop",
            description = "Toggle map icons for staff shop objects.",
            position = 98,
            section = mapIconSettings
    )
    default boolean staffshop()
    {
        return false;
    }

    @ConfigItem(
            keyName = "swordshop",
            name = "Sword shop",
            description = "Toggle map icons for sword shop objects.",
            position = 99,
            section = mapIconSettings
    )
    default boolean swordshop()
    {
        return false;
    }

}