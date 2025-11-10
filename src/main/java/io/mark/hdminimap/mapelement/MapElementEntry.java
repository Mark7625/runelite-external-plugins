package io.mark.hdminimap.mapelement;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class MapElementEntry {
    private String name;
	private String nameSortPrefix;
	private String groupSortPrefix;
	private int mapID;
	private List<Integer> objectIDs;
}