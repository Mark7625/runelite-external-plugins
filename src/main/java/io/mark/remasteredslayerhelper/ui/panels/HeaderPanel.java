package io.mark.remasteredslayerhelper.ui.panels;

import lombok.Getter;

import javax.swing.*;
import java.awt.*;

public class HeaderPanel {
    @Getter
    private final JPanel headerPanel = new JPanel();

    public HeaderPanel(Font font, String title, Color titleColor, ImageIcon imageIcon, int alignment) {
        JLabel titleLabel = new JLabel(title);
        JLabel imgIconLabel = new JLabel(imageIcon);

        titleLabel.setFont(font);
        titleLabel.setForeground(titleColor);
        titleLabel.setHorizontalAlignment(alignment);
        imgIconLabel.setHorizontalAlignment(alignment);

        GridBagConstraints gbc = new GridBagConstraints();

        headerPanel.setLayout(new GridBagLayout());

        gbc.gridy = 1;
        headerPanel.add(imgIconLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.ipady = 50;
        headerPanel.add(titleLabel, gbc);
    }

    public HeaderPanel(Font font, String title, Color titleColor, int alignment) {
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(font);
        titleLabel.setForeground(titleColor);
        titleLabel.setHorizontalAlignment(alignment);
        headerPanel.add(titleLabel);
    }
}
