package io.mark.hdminimap.mapelement;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class MapElementEntry {
    private String category;
    private int mapId;
    private int categoryID;
}