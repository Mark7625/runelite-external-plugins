package io.mark.remasteredslayerhelper.domain;

import java.util.Objects;

public class Item {
    private final String name;
    private final String icon;
    private final Integer itemId;

    public Item(String name, String icon) {
        this(name, icon, null);
    }

    public Item(String name, String icon, Integer itemId) {
        this.name = Objects.requireNonNull(name, "item name cannot be null");
        this.icon = icon;
        this.itemId = itemId;
    }

    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }

    public Integer getItemId() {
        return itemId;
    }
}