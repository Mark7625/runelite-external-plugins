package io.mark.hdminimap.ui;

import io.mark.hdminimap.mapelement.MapElementManager;
import io.mark.hdminimap.mapelement.MapElementType;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class MinimapPanel extends PluginPanel {

    @Inject
    private MinimapPanel(MapElementManager mapElementManager)
    {
        super(false);

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel display = new JPanel();
        MaterialTabGroup tabGroup = new MaterialTabGroup(display);

        MaterialTab mapFunctionTab = new MaterialTab("Map Functions", tabGroup, new MapIconViewer(mapElementManager, MapElementType.MAP_FUNCTION));
        MaterialTab mapSceneTab = new MaterialTab("Map Scenery", tabGroup, new MapIconViewer(mapElementManager, MapElementType.MAP_SCENERY));

        tabGroup.setBorder(new EmptyBorder(5, 0, 0, 0));
        tabGroup.addTab(mapFunctionTab);
        tabGroup.addTab(mapSceneTab);
        tabGroup.select(mapFunctionTab);

        add(tabGroup, BorderLayout.NORTH);
        add(display, BorderLayout.CENTER);
    }
}