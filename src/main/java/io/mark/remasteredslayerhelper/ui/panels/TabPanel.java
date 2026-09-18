package io.mark.remasteredslayerhelper.ui.panels;

import io.mark.remasteredslayerhelper.ui.components.Tab;
import lombok.Getter;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class TabPanel {
    @Getter
    private final JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);

    public TabPanel() {
        // Fixed height avoids the tab content getting clipped when the JTabbedPane's
        // computed preferred size comes out too small for its selected tab's content.
        tabbedPane.setPreferredSize(new Dimension(210, 230));
        tabbedPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        tabbedPane.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        tabbedPane.setAlignmentX(Component.CENTER_ALIGNMENT);
    }

    private void addTab(Tab tab) {
        Objects.requireNonNull(tab, "tab cannot be null");

        JLabel label = new JLabel();
        label.setIcon(tab.getIcon());
        label.setCursor(new Cursor(Cursor.HAND_CURSOR));
        label.setBorder(BorderFactory.createEmptyBorder(4, 3, 4, 3));

        Component component = tabbedPane.add(tab.getContent());
        int index = tabbedPane.indexOfComponent(component);

        tabbedPane.setTabComponentAt(index, label);
    }

    public void addTabs(Tab[] tabs) {
        for (Tab tab : tabs) {
            addTab(tab);
        }
    }
}
