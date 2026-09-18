package io.mark.remasteredslayerhelper.ui.components;

import io.mark.remasteredslayerhelper.domain.Item;
import io.mark.remasteredslayerhelper.util.WikiUtil;
import lombok.Getter;
import net.runelite.client.game.ItemManager;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Objects;

public class Tab {
    @Getter
    private final ImageIcon icon;
    @Getter
    private final JPanel content;

    public Tab(ImageIcon icon, String[] content, String type) {
        this(icon, content, type, null);
    }

    public Tab(ImageIcon icon, Item[] items, String type, ItemManager itemManager) {
        Objects.requireNonNull(icon, "icon cannot be null");
        Objects.requireNonNull(items, "items cannot be null");
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(new Color(30, 30, 30));
        JLabel typeLabel = new JLabel(type);
        typeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        typeLabel.setForeground(Color.ORANGE);
        typeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        typeLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        contentPanel.add(typeLabel);
        for (Item item : items) {
            String name = item.getName();
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            row.setBackground(new Color(30, 30, 30));
            if (itemManager != null && item.getItemId() != null) {
                ImageIcon itemIcon = new ImageIcon(itemManager.getImage(item.getItemId(), 0, true));
                row.add(new JLabel(itemIcon));
            }
            Container wikiButton = WikiUtil.createLinkButton(name, WikiUtil.getWikiUrl(type, name));
            row.add(wikiButton);
            contentPanel.add(row);
        }
        this.icon = icon;
        this.content = contentPanel;
    }

    private Tab(ImageIcon icon, String[] content, String type, Void unused) {
        Arrays.sort(content, (a, b) -> Integer.compare(b.length(), a.length()));
        Objects.requireNonNull(icon, "icon cannot be null");
        Objects.requireNonNull(content, "content cannot be null");
        JPanel contentPanel = new JPanel();
        contentPanel.setBackground(new Color(30, 30, 30));
        JLabel typeLabel = new JLabel(type);
        typeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        typeLabel.setForeground(Color.ORANGE);
        typeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        typeLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        contentPanel.add(typeLabel);
        for (String s : content) {
            JLabel label = new JLabel(s);
            label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            label.setForeground(Color.WHITE);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            Container wikiButton = WikiUtil.createLinkButton(s, WikiUtil.getWikiUrl(type, s));
            contentPanel.add(wikiButton);
        }
        this.icon = icon;
        this.content = contentPanel;
    }

    private String convertStringsToLineSeparatedString(String[] strings) {
        return String.join(System.lineSeparator(), strings);
    }
}
