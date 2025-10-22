package io.mark.hdminimap.mapelement;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.mark.hdminimap.HDMinimapPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Tile;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.*;

import static net.runelite.api.Constants.EXTENDED_SCENE_SIZE;
import static net.runelite.api.Constants.MAX_Z;

@Slf4j
@Singleton
public class MapElementManager {
    private static final Type LIST_TYPE = new TypeToken<List<MapElementEntry>>() {}.getType();
    public static final String CONFIG_GROUP = "hdminimap-json";

    private final Map<MapElementType, List<MapElementEntry>> typeToEntriesMap = new EnumMap<>(MapElementType.class);
    private final Map<Integer, String> enumMapIconNames = new HashMap<>();

    @Inject
    private Gson gson;
    @Inject
    private Client client;
    @Inject
    private ConfigManager configManager;
    @Inject
    private ClientThread clientThread;

    public void start() {
        for (MapElementType type : MapElementType.values()) {
            typeToEntriesMap.put(type, new ArrayList<>());
        }
        loadMapElements();
    }

    private void loadMapElements() {
        loadMapIconNames();

        for (MapElementType type : MapElementType.values()) {
            List<MapElementEntry> entries = loadEntriesForType(type);
            typeToEntriesMap.put(type, entries);
            log.info("Loaded {} entries for {}", entries.size(), type);
        }

        List<MapElementEntry> mapIcons = new ArrayList<>();
        for (int i = 0; i < client.getMapScene().length; i++) {
            mapIcons.add(new MapElementEntry("Icon" + i, i,-1));
        }
        typeToEntriesMap.put(MapElementType.MAP_ICON, mapIcons);

    }

    private List<MapElementEntry> loadEntriesForType(MapElementType type) {
        String fileName = type.getFileName() + ".json";

        try (InputStream in = HDMinimapPlugin.class.getResourceAsStream(fileName)) {
            if (in == null) {
                log.warn("Missing resource for type {}: {}", type, fileName);
                return Collections.emptyList();
            }

            try (InputStreamReader reader = new InputStreamReader(in)) {
                List<MapElementEntry> entries = gson.fromJson(reader, LIST_TYPE);
                if (entries == null) {
                    return Collections.emptyList();
                }

                if (type == MapElementType.MAP_FUNCTION) {
                    for (MapElementEntry entry : entries) {
                        if (entry.getCategory() == null) {
                            String fallback = enumMapIconNames.get(entry.getCategoryID());
                            if (fallback != null) {
                                entry.setCategory(fallback);
                            }
                        }
                    }
                }

                return entries;
            }
        } catch (Exception e) {
            log.error("Failed to load map elements for {}", type, e);
            return Collections.emptyList();
        }
    }

    private void loadMapIconNames() {
        int[] ids = client.getEnum(1713).getKeys();
        String[] names = client.getEnum(1713).getStringVals();

        for (int i = 0; i < Math.min(ids.length, names.length); i++) {
            String name = names[i];
            if (name != null && !name.isBlank()) {
                enumMapIconNames.put(ids[i], name);
            }
        }
    }

    public void clear() {
        typeToEntriesMap.clear();
    }

    public String getCategoryForMapAreaId(MapElementType type, int objectId) {
        List<MapElementEntry> entries = typeToEntriesMap.getOrDefault(type, Collections.emptyList());
        for (MapElementEntry entry : entries) {
            if (entry.getMapId() == objectId) {
                return entry.getCategory();
            }
        }
        return "unknown";
    }

    public List<MapElementEntry> getAll(MapElementType type) {
        return typeToEntriesMap.getOrDefault(type, Collections.emptyList());
    }

    public Set<String> getAllCategories(MapElementType type) {
        List<MapElementEntry> entries = typeToEntriesMap.getOrDefault(type, Collections.emptyList());
        Set<String> categories = new LinkedHashSet<>();
        for (MapElementEntry entry : entries) {
            categories.add(entry.getCategory());
        }
        return categories;
    }

    public int getEntryCount(MapElementType type) {
        List<MapElementEntry> entries = typeToEntriesMap.getOrDefault(type, Collections.emptyList());
        return entries.size();
    }

    public void updateIcons() {
        currentAreaCategories.clear();
        
        Tile[][][] tiles = client.getTopLevelWorldView().getScene().getExtendedTiles();

        for (int z = 0; z < MAX_Z; z++) {
            for (int x = 0; x < EXTENDED_SCENE_SIZE; x++) {
                for (int y = 0; y < EXTENDED_SCENE_SIZE; y++) {
                    Tile tile = tiles[z][x][y];
                    if (tile == null) {
                        continue;
                    }

                    if (tile.getGroundObject() != null) {
                        int id = tile.getGroundObject().getId();
                        processMapElement(id, MapElementType.MAP_FUNCTION);
                        processMapElement(id, MapElementType.MAP_ICON);
                    }

                    GameObject[] gameObjects = tile.getGameObjects();
                    if (gameObjects != null) {
                        for (GameObject obj : gameObjects) {
                            if (obj != null) {
                                processMapElement(obj.getId(), MapElementType.MAP_ICON);
                            }
                        }
                    }
                }
            }
        }
    }

    private void processMapElement(int objectId, MapElementType type) {
        ObjectComposition def = client.getObjectDefinition(objectId);
        int mapId = (type == MapElementType.MAP_FUNCTION) ? def.getMapIconId() : def.getMapSceneId();

        if (mapId == -1) {
            return;
        }

        String category = getCategoryForMapAreaId(type, mapId);
        currentAreaCategories.add(category);
        
        MapElementSetting setting = getSetting(category);

        boolean matchesZoom = setting.getScale() == null || setting.getScale() >= (float) client.getMinimapZoom();
        boolean shouldHide = setting.isDisabled() && matchesZoom;

        if (!shouldHide) {
            return;
        }

        if (type == MapElementType.MAP_FUNCTION) {
            def.setMapIconId(-1);
        } else if (type == MapElementType.MAP_ICON) {
            def.setMapSceneId(-1);
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

    public final Set<String> currentAreaCategories = new HashSet<>();

    public Set<String> getCurrentAreaCategories() {
        return new HashSet<>(currentAreaCategories);
    }

    public boolean isCategoryInCurrentArea(String category) {
        return currentAreaCategories.contains(category);
    }

}
