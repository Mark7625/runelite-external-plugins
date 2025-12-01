package io.mark.f2p.overlay;

import io.mark.f2p.config.OverlayMode;

import java.awt.*;
import java.util.Objects;

public class CacheKey {
    private final int itemId;
    private final int quantity;
    private final OverlayMode mode;
    private final int colorHash;

    public CacheKey(int itemId, int quantity, OverlayMode mode, Color color) {
        this.itemId = itemId;
        this.quantity = quantity;
        this.mode = mode;
        this.colorHash = color != null ? color.hashCode() : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheKey cacheKey = (CacheKey) o;
        return itemId == cacheKey.itemId &&
                quantity == cacheKey.quantity &&
                colorHash == cacheKey.colorHash &&
                mode == cacheKey.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, quantity, mode, colorHash);
    }
}