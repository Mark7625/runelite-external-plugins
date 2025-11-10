package io.mark.hdminimap.mapelement;

import com.google.gson.Gson;
import io.mark.hdminimap.utils.ImageUtils;
import java.awt.image.BufferedImage;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.ObjectComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

import net.runelite.client.game.SpriteManager;

@Slf4j
@Singleton
public class MapElementManager {
    public static final String CONFIG_GROUP = "hdminimap-json";

	private static final int MAXIMUM_OBJECT_ID_IN_GAME = 100000;
	private static final int MAXIMUM_SCENERY_SPRITE = 265;

	private static final String TRANSPORTATION_NAME = "Transportation";
	private static final int TRANSPORTATION_MAP_GROUP = 58;

	private Map<Integer, String> enumCategoryNames = new HashMap<>();
	private Map<Integer, Integer> enumCategorySprites = new HashMap<>();
	private Map<Integer, Integer> enumCategoryGroups = new HashMap<>();

    private final Map<MapElementType, List<MapElementEntry>> typeToEntriesMap = new HashMap<>();
	private final Map<String, List<Integer>> objLookup = new HashMap<>();


	@Inject
	private Client client;
    @Inject
    private Gson gson;
    @Inject
    private ConfigManager configManager;
	@Inject
	private SpriteManager spriteManager;
	@Inject
	private ClientThread clientThread;

    public void start(Client client) {
        this.client = client;

		for (MapElementType type : MapElementType.values()) {
            typeToEntriesMap.put(type, new ArrayList<>());
        }

		enumCategorySprites = loadIntegerEnum(1712);
		enumCategoryNames = loadStringEnum(1713);
		enumCategoryGroups = loadIntegerEnum(1714);
		loadMapElements();
    }

	public Set<String> getKeyset()
	{
		return objLookup.keySet();
	}

	private Map<Integer, Integer> loadIntegerEnum(int enumID)
	{
		int[] keys = client.getEnum(enumID).getKeys();
		int[] values = client.getEnum(enumID).getIntVals();
		Map<Integer, Integer> loadedEnum = new HashMap<>();

		for (int i = 0; i < Math.min(keys.length, values.length); i++)
		{
			loadedEnum.put(keys[i], values[i]);
		}
		return loadedEnum;
	}

	private Map<Integer, String> loadStringEnum(int enumID)
	{
		int[] keys = client.getEnum(enumID).getKeys();
		String[] values = client.getEnum(enumID).getStringVals();
		Map<Integer, String> loadedEnum = new HashMap<>();

		for (int i = 0; i < Math.min(keys.length, values.length); i++)
		{
			String value = values[i];
			if (value != null && !value.isBlank())
			{
				loadedEnum.put(keys[i], value);
			}
		}
		return loadedEnum;
	}

    private void loadMapElements() {
        for (MapElementType type : MapElementType.values()) {
            List<MapElementEntry> entries = loadEntriesForType(type);
            typeToEntriesMap.put(type, entries);
            log.info("Loaded {} entries for {}", entries.size(), type);
        }
    }

    private List<MapElementEntry> loadEntriesForType(MapElementType type) {
		List<MapElementEntry> entries = new ArrayList<>();
		Map<String, List<Integer>> entryNamesAndMapIDs = new HashMap<>();
		Map<Integer, List<Integer>> entryMapIDsAndObjectIDs = new HashMap<>();
		Map<String, String> nameAndAlphabeticalPrefix = new HashMap<>();
		Map<String, String> nameAndGroupingPrefix = new HashMap<>();

		String unknownSpritePrefix = (type == MapElementType.MAP_FUNCTION) ? "Icon" : "Scenery";
		int unknownSpriteNumber = 0;
		String alphabeticalPrefix;
		String groupPrefix;

		objectIterationLoop:
		for (int objectID = 0; objectID < MAXIMUM_OBJECT_ID_IN_GAME; objectID++)
		{
			alphabeticalPrefix = "";
			groupPrefix = "";
			ObjectComposition def = client.getObjectDefinition(objectID);
			if (def == null)
			{
				continue;
			}

			int mapID = (type == MapElementType.MAP_FUNCTION) ? def.getMapIconId() : def.getMapSceneId();
			if (mapID == -1)
			{
				continue;
			}

			// Firstly check if the mapID is already registered, in which case add the objectID to the entry
			for (List<Integer> namedMapIDs : entryNamesAndMapIDs.values())
			{
				if (namedMapIDs.contains(mapID))
				{
					entryMapIDsAndObjectIDs.get(namedMapIDs.get(0)).add(objectID);
					continue objectIterationLoop;
				}
			}

			// Secondly, check if the image is already used, in which case add the mapID and objectID to the entry
			BufferedImage mapSprite = getImage(type, mapID);

			for (List<Integer> entryMapID : entryNamesAndMapIDs.values())
			{
				BufferedImage entrySprite = getImage(type, entryMapID.get(0));
				if (compareImages(mapSprite, entrySprite))
				{
					// Check for the Transportation Icon as this is also used for Fairy Rings
					if (entryMapID.get(0) == TRANSPORTATION_MAP_GROUP && mapID != TRANSPORTATION_MAP_GROUP)
					{
						continue;
					}

					entryMapID.add(mapID);
					entryMapIDsAndObjectIDs.get(entryMapID.get(0)).add(objectID);
					continue objectIterationLoop;
				}
			}

			// Thirdly, check the inbuilt category icons to see if there is a known name for the icon, or apply the
			// default name
			String name = null;
			for (Map.Entry<Integer, Integer> categorySpriteEnum : enumCategorySprites.entrySet())
			{
				BufferedImage enumSprite = spriteManager.getSprite(categorySpriteEnum.getValue(),0);
				if (enumSprite == null)
				{
					log.debug("Could not find enum Sprite for category {}", categorySpriteEnum.getKey());
					break;
				}

				if (compareImages(mapSprite, enumSprite))
				{
					if (enumCategoryNames.containsKey(categorySpriteEnum.getKey()))
					{
						name = enumCategoryNames.get(categorySpriteEnum.getKey());
						groupPrefix = enumCategoryGroups.get(categorySpriteEnum.getKey()).toString();
						if (name.equals(TRANSPORTATION_NAME) && mapID != TRANSPORTATION_MAP_GROUP)
						{
							name = "Transportation (Fairy Rings)";
							groupPrefix = "5";
						}
						break;
					}
				}
			}
			if (name == null)
			{
				name = unknownSpritePrefix + unknownSpriteNumber;
				unknownSpriteNumber += 1;
				alphabeticalPrefix = "z_";
				groupPrefix = "5";
			}

			if (!name.equals("Map link")) // Map links aren't removable
			{
				List<Integer> newMapIDList = new ArrayList<>();
				newMapIDList.add(mapID);
				entryNamesAndMapIDs.put(name, newMapIDList);
				List<Integer> newObjectIDList = new ArrayList<>();
				newObjectIDList.add(objectID);
				entryMapIDsAndObjectIDs.put(mapID, newObjectIDList);

				nameAndAlphabeticalPrefix.put(name,alphabeticalPrefix);
				nameAndGroupingPrefix.put(name,groupPrefix);
			}
		}

		// Add all the entries - needs to be done at the end to collate ObjectIDs
		for (Map.Entry<String, List<Integer>> entry : entryNamesAndMapIDs.entrySet())
		{
			String name = entry.getKey();
			List<Integer> cumulativeObjectIDs = new ArrayList<>();
			for (Integer mapID : entry.getValue())
			{
				if (entryMapIDsAndObjectIDs.containsKey(mapID))
				{
					cumulativeObjectIDs.addAll(entryMapIDsAndObjectIDs.get(mapID));
				}
			}
			entries.add(new MapElementEntry(name, nameAndAlphabeticalPrefix.get(name), nameAndGroupingPrefix.get(name), entry.getValue().get(0), cumulativeObjectIDs));
			objLookup.put(name, cumulativeObjectIDs);
		}

		return entries;
    }

	public BufferedImage getImage(MapElementType type, Integer id)
	{
		BufferedImage image = new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
		if (type == MapElementType.MAP_FUNCTION)
		{
			image = client.getMapElementConfig(id).getMapIcon(false).toBufferedImage();
		}
		else if (type == MapElementType.MAP_SCENERY)
		{
			try
			{
				if (id < MAXIMUM_SCENERY_SPRITE)
				{
					IndexedSprite im = client.getMapScene()[id];
					if (im != null)
					{
						image = ImageUtils.toBufferedImage(client.getMapScene()[id]);
					}
				}
			}
			catch (Exception e)
			{
				log.debug("Error loading scenery ID: {}",id);
			}
		}
		return image;
	}

	public static boolean compareImages(BufferedImage imgA, BufferedImage imgB)
	{
		if (imgA == null || imgB == null)
		{
			return false;
		}
		if (imgA.getWidth() != imgB.getWidth() || imgA.getHeight() != imgB.getHeight())
		{
			return false;
		}
		int width = imgA.getWidth();
		int height = imgA.getHeight();

		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				if (imgA.getRGB(x, y) != imgB.getRGB(x, y))
				{
					return false;
				}
			}
		}
		return true;
	}

    public void end() {
        typeToEntriesMap.clear();
		enumCategoryNames.clear();
		enumCategorySprites.clear();
		enumCategoryGroups.clear();
    }

    public List<MapElementEntry> getAll(MapElementType type) {
        return typeToEntriesMap.getOrDefault(type, Collections.emptyList());
    }

    public Set<String> getAllCategories(MapElementType type) {
        List<MapElementEntry> entries = typeToEntriesMap.getOrDefault(type, Collections.emptyList());
        Set<String> categories = new LinkedHashSet<>();
        for (MapElementEntry entry : entries) {
            categories.add(entry.getName());
        }
        return categories;
    }

	public Set<String> getAllCategoriesAlphabetical(MapElementType type) {
		List<MapElementEntry> entries = typeToEntriesMap.getOrDefault(type, Collections.emptyList());
		Set<String> categories = new LinkedHashSet<>();
		for (MapElementEntry entry : entries) {
			categories.add(entry.getNameSortPrefix() + entry.getName());
		}
		return categories;
	}

	public Set<String> getAllCategoriesGrouping(MapElementType type) {
		List<MapElementEntry> entries = typeToEntriesMap.getOrDefault(type, Collections.emptyList());
		Set<String> categories = new LinkedHashSet<>();
		for (MapElementEntry entry : entries) {
			categories.add(entry.getGroupSortPrefix() + entry.getNameSortPrefix() + entry.getName());
		}
		return categories;
	}

    public int getEntryCount(MapElementType type) {
        List<MapElementEntry> entries = typeToEntriesMap.getOrDefault(type, Collections.emptyList());
        return entries.size();
    }

	public void updateIcon(String name)
	{
		clientThread.invoke(() -> {
			MapElementType type = getTypeFromNamedEntry(name);
			for (Integer ob : objLookup.get(name))
			{
				processMapElement(name, ob, type);
			}
		});
	}

	private MapElementType getTypeFromNamedEntry(String name)
	{
		MapElementType type2 = MapElementType.MAP_FUNCTION;
		for (MapElementType type : MapElementType.values())
		{
			for (MapElementEntry entry : typeToEntriesMap.getOrDefault(type, Collections.emptyList()))
			{
				if (entry.getName().equals(name))
				{
					return type;
				}
			}
		}
		return type2;
	}

	private int getMapIDFromNamedEntry(String name)
	{
		int mapID = -1;
		for (MapElementType type : MapElementType.values())
		{
			for (MapElementEntry entry : typeToEntriesMap.getOrDefault(type, Collections.emptyList()))
			{
				if (entry.getName().equals(name))
				{
					return entry.getMapID();
				}
			}
		}
		return mapID;
	}

    private void processMapElement(String name, Integer objectId, MapElementType type) {
		ObjectComposition def = client.getObjectDefinition(objectId);

        MapElementSetting setting = getSetting(name);

        boolean matchesZoom = setting.getScale() == null || setting.getScale() >= (float) client.getMinimapZoom();
        boolean shouldHide = setting.isDisabled() && matchesZoom;

		int mapID = -1; // set to hide
        if (!shouldHide) {
			mapID = getMapIDFromNamedEntry(name);
        }

        if (type == MapElementType.MAP_FUNCTION) {
            def.setMapIconId(mapID);
        } else if (type == MapElementType.MAP_SCENERY) {
            def.setMapSceneId(mapID);
        }
    }

    public MapElementSetting getSetting(String category) {
        String json = configManager.getConfiguration(CONFIG_GROUP, category);
        if (json == null) {
            return MapElementSetting.defaults();
        }

        MapElementSetting setting = gson.fromJson(json, MapElementSetting.class);
        return setting != null ? setting : MapElementSetting.defaults();
    }

    public void saveSetting(String category, MapElementSetting setting) {
        configManager.setConfiguration(CONFIG_GROUP, category, gson.toJson(setting));
    }

    public void setDisabled(String category, boolean disabled) {
        MapElementSetting current = getSetting(category);
        saveSetting(category, new MapElementSetting(disabled, current.getScale()));
    }

    public void setScale(String category, Float scale) {
        MapElementSetting current = getSetting(category);
        saveSetting(category, new MapElementSetting(current.isDisabled(), scale));
    }
}
