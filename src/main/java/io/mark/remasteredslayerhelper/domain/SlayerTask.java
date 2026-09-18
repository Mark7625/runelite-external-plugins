package io.mark.remasteredslayerhelper.domain;

import io.mark.remasteredslayerhelper.data.SlayerMaster;
import lombok.Getter;
import net.runelite.api.Skill;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private final int combatLevel;
    private final Item[] itemsRequired;
    private final Map<Skill, Integer> requirementsNeedsAll;
    private final Map<Skill, Integer> requirementsNeedsAny;

    public SlayerTask(
            String monster,
            int slayerLevel,
            int combatLevel,
            String[] locations,
            Item[] itemsRequired,
            String[] attributes,
            String[] attackStyles,
            String[] alternatives,
			List<SlayerMaster> slayerMasters,
			Map<Skill, Integer> requirementsNeedsAll,
			Map<Skill, Integer> requirementsNeedsAny) {
        this.monster = Objects.requireNonNull(monster, "monster cannot be null");
        this.slayerLevel = slayerLevel;
        this.combatLevel = combatLevel;
        this.locations = Objects.requireNonNull(locations, "locations cannot be null");
        this.itemsRequired = Objects.requireNonNull(itemsRequired, "items required cannot be null");
        this.attributes = Objects.requireNonNull(attributes, "attributes cannot be null");
        this.attackStyles = Objects.requireNonNull(attackStyles, "attack styles cannot be null");
        this.alternatives = Objects.requireNonNull(alternatives, "alternatives cannot be null");
        this.slayerMasters = slayerMasters;
        this.requirementsNeedsAll = requirementsNeedsAll != null ? requirementsNeedsAll : Collections.emptyMap();
        this.requirementsNeedsAny = requirementsNeedsAny != null ? requirementsNeedsAny : Collections.emptyMap();
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
