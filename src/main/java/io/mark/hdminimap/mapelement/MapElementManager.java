package io.mark.hdminimap.mapelement;

import com.google.gson.Gson;
import io.mark.hdminimap.utils.ImageUtils;
import java.awt.image.BufferedImage;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.ObjectComposition;
import net.runelite.api.worldmap.MapElementConfig;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

import net.runelite.client.game.SpriteManager;

@Slf4j
@Singleton
public class MapElementManager {
    public static final String CONFIG_GROUP = "hdminimap-json";

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
    }

	public BufferedImage getImage(MapElementType type, Integer id)
	{
		BufferedImage image = new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
		if (type == MapElementType.MAP_FUNCTION)
		{
			MapElementConfig mapElementConfig = client.getMapElementConfig(id);
			if (mapElementConfig.getMapIcon(false) == null) {
				return null;
			}
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

	public Set<String> getAlphabeticalCategories(MapElementType type, boolean includeGrouping)
	{
		List<MapElementCategories> elements = new ArrayList<>();

		for (MapElementCategories element : MapElementCategories.values())
		{
			if (element.getType() == type)
			{
				elements.add(element);
			}
		}

		if (includeGrouping)
		{
			elements.sort(
					Comparator
							.comparing(MapElementCategories::getGrouping)
							.thenComparing(
									MapElementCategories::getDefaultName,
									String.CASE_INSENSITIVE_ORDER
							)
			);
		}
		else
		{
			elements.sort(
					Comparator.comparing(
							MapElementCategories::getDefaultName,
							String.CASE_INSENSITIVE_ORDER
					)
			);
		}

		Set<String> categories = new LinkedHashSet<>();

		for (MapElementCategories element : elements)
		{
			categories.add(element.getDefaultName());
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

    public void updateAllIcons() {
        for (MapElementCategories element : MapElementCategories.values())
        {
            MapElementSetting setting = getSetting(element.getDefaultName());
            if (setting.isDisabled()) {
                updateIcon(element);
            }
        }
    }

	public void updateIcon(String name)
	{
		updateIcon(MapElementCategories.getByDefaultName(name));
	}

	public void updateIcon(@Nullable MapElementCategories element)
	{
		if (element != null)
		{
			clientThread.invoke(() -> {
				for (Integer ob : element.getObjectIDs())
				{
					processMapElement(element, ob);
				}
			});
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
