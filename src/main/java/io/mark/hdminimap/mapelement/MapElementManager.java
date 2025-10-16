/*
 * Copyright (c) 2025, Mark
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.mark.hdminimap.mapelement;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.mark.hdminimap.HDMinimapPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Tile;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

import static net.runelite.api.Constants.EXTENDED_SCENE_SIZE;
import static net.runelite.api.Constants.MAX_Z;

@Slf4j
@Singleton
public class MapElementManager {

    private final Map<MapElementType, List<MapElementEntry>> typeToEntriesMap = new HashMap<>();
    private final Map<MapElementType, Map<Integer, Integer>> typeToOriginalIdsMap = new HashMap<>();


    @Inject
    private Gson gson;

    @Inject
    private Client client;

    @Inject
    private ConfigManager configManager;

    public void start() {
        initializeTypeMaps();
        loadMapElements();
    }

    private void initializeTypeMaps() {
        for (MapElementType type : MapElementType.values()) {
            typeToEntriesMap.put(type, new ArrayList<>());
            typeToOriginalIdsMap.put(type, new HashMap<>());
        }
    }

    private void loadMapElements() {
        for (MapElementType elementType : MapElementType.values()) {
            try (InputStream inputStream = HDMinimapPlugin.class.getResourceAsStream(elementType.getFileName() + ".json")) {
                if (inputStream == null) {
                    log.error("Could not find mapFunctions.json resource");
                    return;
                }

                InputStreamReader reader = new InputStreamReader(inputStream);
                List<MapElementEntry> rawEntries = gson.fromJson(reader, new TypeToken<List<MapElementEntry>>(){}.getType());

                if (rawEntries == null) {
                    rawEntries = new ArrayList<>();
                }

                List<MapElementEntry> typeEntries = new ArrayList<>();

                for (MapElementEntry entry : rawEntries) {
                    MapElementEntry processedEntry = new MapElementEntry();
                    processedEntry.setCategory(normalizeCategoryName(entry.getCategory()));
                    processedEntry.setObjects(new ArrayList<>(entry.getObjects()));
                    typeEntries.add(processedEntry);
                }

                typeToEntriesMap.put(elementType, typeEntries);

                int totalObjects = rawEntries.stream()
                        .mapToInt(entry -> entry.getObjects().size())
                        .sum();

                log.info("Loaded {} map element entries with {} total object mappings for {} types",
                        rawEntries.size(), totalObjects, MapElementType.values().length);
            } catch (Exception e) {
                log.error("Failed to load map elements from JSON", e);
                initializeTypeMaps(); // Reset to empty maps
            }
        }
    }

    public void clear() {
        for (Map<Integer, Integer> originalIds : typeToOriginalIdsMap.values()) {
            originalIds.clear();
        }
        typeToEntriesMap.clear();
    }

    private String normalizeCategoryName(String categoryName) {
        if (categoryName == null) {
            return "unknown";
        }
        return categoryName.toLowerCase().replaceAll("[^a-z0-9]", "").trim();
    }

    public String getCategoryForObjectId(MapElementType type, int objectId) {
        List<MapElementEntry> entries = typeToEntriesMap.get(type);
        if (entries == null) {
            return "unknown";
        }

        return entries.stream()
                .filter(entry -> entry.getObjects().contains(objectId))
                .map(MapElementEntry::getCategory)
                .findFirst()
                .orElse("unknown");
    }

    public boolean hasCategoryForObjectId(MapElementType type, int objectId) {
        List<MapElementEntry> entries = typeToEntriesMap.get(type);
        if (entries == null) {
            log.debug("No entries found for type: {}", type);
            return false;
        }

        boolean found = entries.stream()
                .anyMatch(entry -> entry.getObjects().contains(objectId));

        if (!found) {
            log.debug("Object ID {} not found in {} entries for type {}",
                    objectId, entries.size(), type);
        }

        return found;
    }

    public Set<String> getAllCategories(MapElementType type) {
        List<MapElementEntry> entries = typeToEntriesMap.get(type);
        if (entries == null) {
            return new java.util.HashSet<>();
        }

        return entries.stream()
                .map(MapElementEntry::getCategory)
                .collect(Collectors.toSet());
    }

    /**
     * Updates map icons and functions by processing all tiles in the current scene.
     * - MAP_FUNCTION: Can only be on ground objects
     */
    public void updateIcons() {
        var tiles = client.getTopLevelWorldView().getScene().getExtendedTiles();

        for (int z = 0; z < MAX_Z; ++z) {
            for (int x = 0; x < EXTENDED_SCENE_SIZE; ++x) {
                for (int y = 0; y < EXTENDED_SCENE_SIZE; ++y) {
                    Tile tile = tiles[z][x][y];
                    if (tile != null) {
                        if (tile.getGroundObject() != null) {
                            int objectId = tile.getGroundObject().getId();
                            processMapElement(objectId, MapElementType.MAP_FUNCTION);
                            processMapElement(objectId, MapElementType.MAP_ICON);
                        }

                        for (GameObject object : tile.getGameObjects()) {
                            if (object != null) {
                                processMapElement(object.getId(), MapElementType.MAP_ICON);
                            }
                        }
                    }
                }
            }
        }
    }

    private void processMapElement(int objectId, MapElementType type) {
        if (!hasCategoryForObjectId(type, objectId)) {
            return;
        }

        ObjectComposition objectDef = client.getObjectDefinition(objectId);
        Map<Integer, Integer> originalIds = typeToOriginalIdsMap.get(type);

        if (!originalIds.containsKey(objectId)) {
            int originalId = type == MapElementType.MAP_FUNCTION ? objectDef.getMapIconId() : objectDef.getMapSceneId();
            if (originalId != -1) {
                originalIds.put(objectId, originalId);
            }
        }

        String categoryName = getCategoryForObjectId(type, objectId);
        if (Objects.equals(configManager.getConfiguration("hdminimap", categoryName), "true")) {
            if (type == MapElementType.MAP_FUNCTION) {
                objectDef.setMapIconId(-1);
            } else if (type == MapElementType.MAP_ICON) {
                //objectDef.setMapSceneId(-1);
            }
        }
    }


}