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

	private Map<Integer, Integer> enumCategoryGroups = new HashMap<>();


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
		enumCategoryGroups = loadIntegerEnum(1714);
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
				if (id < 300)
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

    public void end() {
		enumCategoryGroups.clear();
    }

	public Set<String> getAlphabeticalCategories(MapElementType type, boolean includeGrouping) {
		Set<String> categories = new LinkedHashSet<>();
		for (MapElementCategories element : MapElementCategories.values())
		{
			if (element.getType() == type)
			{
				if (includeGrouping)
				{
					categories.add(enumCategoryGroups.get(element.getCategoryOrNull()) + element.getDefaultName());
				}
				else
				{
					categories.add(element.getDefaultName());
				}

			}
		}
		return categories;
	}

    public int getEntryCount(MapElementType type) {
		int count = 0;
		for (MapElementCategories element : MapElementCategories.values())
		{
			if (element.getType() == type)
			{
				count++;
			}
		}
        return count;
    }

	public void updateIcon(String name)
	{
		for (MapElementCategories element : MapElementCategories.values())
		{
			if (element.getDefaultName().equals(name))
			{
				clientThread.invoke(() -> {
					for (Integer ob : element.getObjectIDs())
					{
						processMapElement(element, ob);
					}
				});
			}
		}
	}

    private void processMapElement(MapElementCategories element, Integer objectId) {
		ObjectComposition def = client.getObjectDefinition(objectId);

        MapElementSetting setting = getSetting(element.getDefaultName());

        boolean matchesZoom = setting.getScale() == null || setting.getScale() >= (float) client.getMinimapZoom();
        boolean shouldHide = setting.isDisabled() && matchesZoom;

		int mapID = -1; // set to hide
        if (!shouldHide) {
			mapID = element.getMapID();
        }

        if (element.getType() == MapElementType.MAP_FUNCTION) {
            def.setMapIconId(mapID);
        } else if (element.getType() == MapElementType.MAP_SCENERY) {
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
