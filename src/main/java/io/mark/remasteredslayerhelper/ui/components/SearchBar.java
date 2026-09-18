package io.mark.remasteredslayerhelper.ui.components;

import lombok.Getter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

@Getter
public class SearchBar {
    private final IconTextField searchBar;

    public interface OnKeyTypedHandler {
        void run(String text);
    }

    public interface OnClearHandler {
        void run();
    }

    public SearchBar(OnKeyTypedHandler onKeyTypedHandler, OnClearHandler onClearHandler, IconTextField searchBar) {
        this.searchBar = searchBar;
        initialize(onKeyTypedHandler, onClearHandler);
    }

    public SearchBar(OnKeyTypedHandler onKeyTypedHandler, OnClearHandler onClearHandler) {
        this(onKeyTypedHandler, onClearHandler, new IconTextField());
    }

    private void initialize(OnKeyTypedHandler onKeyTypedHandler, OnClearHandler onClearHandler) {
        searchBar.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                onKeyTypedHandler.run(searchBar.getText());
            }
        });

        searchBar.addClearListener(() -> {
            onClearHandler.run();
            searchBar.setIcon(IconTextField.Icon.SEARCH);
            searchBar.setEditable(true);
        });

        setSearchBarStyle();
    }

    private void setSearchBarStyle() {
        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchBar.setMinimumSize(new Dimension(0, 30));
    }
}
