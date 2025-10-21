package io.mark.hdminimap.mapelement;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MapElementSetting {
    private boolean disabled;
    private Float scale;

    public static MapElementSetting defaults() {
        return new MapElementSetting(false, null);
    }
}


