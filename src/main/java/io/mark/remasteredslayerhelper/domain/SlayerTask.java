package io.mark.remasteredslayerhelper.domain;

import io.mark.remasteredslayerhelper.data.SlayerMaster;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

@Getter
public class SlayerTask {
    private final String monster;
    private final String[] locations;
    private final String[] attributes;
    private final String[] attackStyles;
    private final List<SlayerMaster> slayerMasters;
    private final String[] alternatives;
    private final int slayerLevel;
    private final Item[] itemsRequired;

    public SlayerTask(
            String monster,
            int slayerLevel,
            String[] locations,
            Item[] itemsRequired,
            String[] attributes,
            String[] attackStyles,
            String[] alternatives,
			List<SlayerMaster> slayerMasters) {
        this.monster = Objects.requireNonNull(monster, "monster cannot be null");
        this.slayerLevel = slayerLevel;
        this.locations = Objects.requireNonNull(locations, "locations cannot be null");
        this.itemsRequired = Objects.requireNonNull(itemsRequired, "items required cannot be null");
        this.attributes = Objects.requireNonNull(attributes, "attributes cannot be null");
        this.attackStyles = Objects.requireNonNull(attackStyles, "attack styles cannot be null");
        this.alternatives = Objects.requireNonNull(alternatives, "alternatives cannot be null");
        this.slayerMasters = slayerMasters;
    }

    public String getMonsterLowerCase() {
        return monster.toLowerCase();
    }

    public String getMonsterFileName() {
        String monsterImageName = monster.replace(" ", "_").concat(".png").toLowerCase();
        return String.format("/images/monsters/%s", monsterImageName);
    }

    public String[] getItemsRequiredNames() {
        String[] itemsRequiredNames = new String[itemsRequired.length];
        for (int i = 0; i < itemsRequired.length; i++) {
            itemsRequiredNames[i] = itemsRequired[i].getName();
        }
        return itemsRequiredNames;
    }

    public String[] getItemsRequiredIcons() {
        String[] itemsRequiredIcons = new String[itemsRequired.length];
        for (int i = 0; i < itemsRequired.length; i++) {
            itemsRequiredIcons[i] = itemsRequired[i].getIcon();
        }
        return itemsRequiredIcons;
    }

    @Override
    public String toString() {
        return this.getMonster(); // Replace with the actual property you want to display
    }
}
