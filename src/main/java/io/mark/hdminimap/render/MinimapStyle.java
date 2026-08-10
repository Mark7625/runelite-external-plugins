package io.mark.hdminimap.render;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MinimapStyle {
    DEFAULT("OSRS"),
    HD("Shaded (HD)"),
    HD117("117 Style");
    HD("Shaded (HD)");

    private final String displayName;

    @Override
    public String toString() {
        return displayName;
    }
}