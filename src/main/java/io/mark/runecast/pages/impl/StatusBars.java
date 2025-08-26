package io.mark.runecast.pages.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.mark.runecast.SSEManager;
import io.mark.runecast.pages.Page;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.statusbars.StatusBarsConfig;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Singleton
public class StatusBars extends Page {


    private static final int MAX_RUN_ENERGY_VALUE = 100;
    private static final int MAX_SPECIAL_ATTACK_VALUE = 100;

    @Inject
    Client client;

    private SSEManager sseManager;

    private final Map<StatusBarsConfig.BarMode, BarRenderer> barRenderers = new EnumMap<>(StatusBarsConfig.BarMode.class);
    
    // Status effect fields
    private int poisonState = 0;
    private int diseaseState = 0;
    private int parasiteState = 0;
    private boolean hasActivePrayer = false;
    private boolean staminaActive = false;
    private boolean runActive = false;

    @Override
    public String getPageName() {
        return "status";
    }

    @Override
    public Map<String, Object> getPageData() {
        Map<String, Object> data = new HashMap<>();

        for (Map.Entry<StatusBarsConfig.BarMode, BarRenderer> entry : barRenderers.entrySet()) {
            StatusBarsConfig.BarMode barMode = entry.getKey();
            BarRenderer renderer = entry.getValue();
            
            switch (barMode) {
                case HITPOINTS:
                    data.put("hp", renderer.getCurrentValue());
                    data.put("maxHp", renderer.getMaxValue());
                    break;
                case PRAYER:
                    data.put("prayer", renderer.getCurrentValue());
                    data.put("maxPrayer", renderer.getMaxValue());
                    break;
                case RUN_ENERGY:
                    data.put("runEnergy", renderer.getCurrentValue());
                    data.put("maxRunEnergy", renderer.getMaxValue());
                    break;
                case SPECIAL_ATTACK:
                    data.put("specialAttack", renderer.getCurrentValue());
                    data.put("maxSpecialAttack", renderer.getMaxValue());
                    break;
            }
        }

        data.put("poisonState", poisonState);
        data.put("diseaseState", diseaseState);
        data.put("parasiteState", parasiteState);
        data.put("hasActivePrayer", hasActivePrayer);
        data.put("staminaActive", staminaActive);
        data.put("runActive", runActive);
        
        return data;
    }

    @Override
    public void onInit() {
        initRenderers();
    }

    @Override
    public void onUpdate() {
        refreshAllBars();
        updateStatusEffects();
    }

    public void init(SSEManager sseManager) {
        // This method is kept for backward compatibility but now delegates to onInit
        onInit();
        this.sseManager = sseManager;
    }

    private void initRenderers()
    {
        barRenderers.put(StatusBarsConfig.BarMode.HITPOINTS, new BarRenderer(
                () -> inLms() ? Experience.MAX_REAL_LEVEL : client.getRealSkillLevel(Skill.HITPOINTS),
                () -> client.getBoostedSkillLevel(Skill.HITPOINTS),
                () -> getRestoreValue(Skill.HITPOINTS.getName())
        ));
        barRenderers.put(StatusBarsConfig.BarMode.PRAYER, new BarRenderer(
                () -> inLms() ? Experience.MAX_REAL_LEVEL : client.getRealSkillLevel(Skill.PRAYER),
                () -> client.getBoostedSkillLevel(Skill.PRAYER),
                () -> getRestoreValue(Skill.PRAYER.getName())
        ));
        barRenderers.put(StatusBarsConfig.BarMode.RUN_ENERGY, new BarRenderer(
                () -> MAX_RUN_ENERGY_VALUE,
                () -> client.getEnergy() / 100,
                () -> getRestoreValue("Run Energy")
        ));
        barRenderers.put(StatusBarsConfig.BarMode.SPECIAL_ATTACK, new BarRenderer(
                () -> MAX_SPECIAL_ATTACK_VALUE,
                () -> client.getVarpValue(VarPlayerID.SA_ENERGY) / 10,
                () -> 0
        ));

    }

    private boolean inLms()
    {
        return client.getWidget(InterfaceID.BrOverlay.CONTENT) != null;
    }

    private int getRestoreValue(String skill)
    {
        final MenuEntry[] menu = client.getMenuEntries();
        final int menuSize = menu.length;
        if (menuSize == 0)
        {
            return 0;
        }

        final MenuEntry entry = menu[menuSize - 1];
        final Widget widget = entry.getWidget();
        int restoreValue = 0;

        if (widget != null && widget.getId() == InterfaceID.Inventory.ITEMS)
        {

        }

        return restoreValue;
    }

    public void onGameTick() {
        onUpdate();
    }

    private void refreshAllBars() {
        for (BarRenderer renderer : barRenderers.values()) {
            renderer.refreshSkills();
        }
    }
    
    private void updateStatusEffects() {
        // Update status effects
        poisonState = client.getVarpValue(VarPlayerID.POISON);
        diseaseState = client.getVarpValue(VarPlayerID.DISEASE);
        parasiteState = client.getVarbitValue(VarbitID.PARASITE);
        
        hasActivePrayer = false;
        for (Prayer pray : Prayer.values()) {
            if (client.isPrayerActive(pray)) {
                hasActivePrayer = true;
                break;
            }
        }

        runActive = client.getVarpValue(VarPlayerID.OPTION_RUN) == 1;
        staminaActive = client.getVarbitValue(VarbitID.STAMINA_ACTIVE) != 0;
    }


}

@RequiredArgsConstructor
class BarRenderer
{

    private final Supplier<Integer> maxValueSupplier;
    private final Supplier<Integer> currentValueSupplier;
    private final Supplier<Integer> healSupplier;
    @Getter
    private int maxValue;
    @Getter
    private int currentValue;

    public void refreshSkills()
    {
        maxValue = maxValueSupplier.get();
        currentValue = currentValueSupplier.get();
    }

    public int getHealValue() {
        return healSupplier.get();
    }

}
