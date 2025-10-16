package io.mark.hdminimap.mapelement;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MapElementEntry {
    private String category;
    private List<Integer> objects;
}