package io.mark.remasteredslayerhelper.ui.renderers;

import io.mark.remasteredslayerhelper.domain.SlayerTask;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class SlayerTasksRenderer extends JLabel implements ListCellRenderer<SlayerTask> {
    @Override
    public Component getListCellRendererComponent(
            JList<? extends SlayerTask> list,
            SlayerTask value,
            int index,
            boolean isSelected,
            boolean cellHasFocus)
    {
        setText(value.getMonster());
        setVerticalAlignment(CENTER);
        setBorder(new EmptyBorder(10, 5, 10, 5));
        setForeground(Color.WHITE);
        return this;
    }
}