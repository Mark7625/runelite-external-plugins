package io.mark.hdminimap.ui;

import io.mark.hdminimap.HDMinimapPlugin;
import io.mark.hdminimap.mapelement.MapElementEntry;
import io.mark.hdminimap.mapelement.MapElementManager;
import io.mark.hdminimap.mapelement.MapElementType;
import io.mark.hdminimap.utils.ImageUtils;
import net.runelite.api.Client;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class MapIconViewer extends JPanel
{

    private final JButton[] buttons;
    private final MapElementType mapElementType;
    private final MapElementManager mapElementManager;

    private static final int BUTTON_SIZE = 27;
    private static final boolean SHOW_SORT_DROPDOWN = false;

    private final JPanel buttonPanel;
    private final JPanel buttonsFlowPanel;
    private final JPanel inlineConfigPanel;
    private final JLabel selectedLabel;
    private final JComboBox<String> scaleComboBox;

    private static final Float[] ALLOWED_SCALES = new Float[]{
            null,
            2.0f, 2.25f, 2.5f, 2.75f,
            3.0f, 3.25f, 3.5f, 3.75f,
            4.0f, 4.25f, 4.5f, 4.75f,
            5.0f, 5.25f, 5.5f,
            6.0f, 6.5f, 6.75f,
            7.75f, 8.0f
    };

    private MapElementEntry currentEntry;

    @Inject
    public MapIconViewer(MapElementManager mapElementManager, Client client, MapElementType mapElementType)
    {

        this.mapElementManager = mapElementManager;
        this.mapElementType = mapElementType;

        int totalButtons = mapElementManager.getEntryCount(mapElementType);
        this.buttons = new JButton[totalButtons];

        setLayout(new BorderLayout(5, 5));
        add(createHeader(), BorderLayout.NORTH);

        buttonPanel = new JPanel(new BorderLayout());
        buttonsFlowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));

        int index = 0;
        for (MapElementEntry entry : mapElementManager.getAll(mapElementType))
        {
            BufferedImage original = null;

            if (mapElementType == MapElementType.MAP_FUNCTION) {
                if (Objects.equals(entry.getCategory(), "Fairy Rings")) {
                    original = ImageUtil.loadImageResource(HDMinimapPlugin.class, "ring.png");
                } else {
                    original = client.getMapElementConfig(entry.getMapId()).getMapIcon(false).toBufferedImage();
                }
            } else {
                int mapId = entry.getMapId();
                if (mapId <= 200) {
                    original = ImageUtils.toBufferedImage(client.getMapScene()[mapId]);
                }
            }

            if (original != null) {
                JButton btn = createButton(entry, original);
                buttonsFlowPanel.add(btn);
                buttons[index++] = btn;
            }
        }

        inlineConfigPanel = new JPanel(new BorderLayout());
        inlineConfigPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        inlineConfigPanel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        inlineConfigPanel.setVisible(false);

        selectedLabel = new JLabel("Selected: None");
        selectedLabel.setForeground(Color.WHITE);
        selectedLabel.setHorizontalAlignment(SwingConstants.CENTER);
        selectedLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));

        JPanel scalePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        scalePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scalePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        scalePanel.add(new JLabel("Scale:"));

        scaleComboBox = new JComboBox<>();
        for (Float f : ALLOWED_SCALES)
        {
            scaleComboBox.addItem(f == null ? "Any" : f.toString());
        }

        scaleComboBox.addActionListener(e -> {
            if (currentEntry != null)
            {
                String selected = (String) scaleComboBox.getSelectedItem();
                Float scale = "Any".equals(selected) ? null : Float.parseFloat(selected);
                mapElementManager.setScale(currentEntry.getCategory(), scale);
            }
        });

        scalePanel.add(scaleComboBox);

        inlineConfigPanel.add(selectedLabel, BorderLayout.NORTH);
        inlineConfigPanel.add(scalePanel, BorderLayout.SOUTH);

        buttonPanel.add(buttonsFlowPanel, BorderLayout.CENTER);
        buttonPanel.add(inlineConfigPanel, BorderLayout.SOUTH);
        add(buttonPanel, BorderLayout.CENTER);
    }

    private JPanel createHeader()
    {
        JPanel headerPanel = new JPanel(new BorderLayout(5, 5));
        headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5));

        JPanel toolbar = new JPanel();
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.X_AXIS));
        toolbar.setBackground(ColorScheme.DARK_GRAY_COLOR);

        int elementHeight = 28;

        IconTextField searchBar = new IconTextField();
        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setPreferredSize(new Dimension(300, elementHeight));  // expanded
        searchBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, elementHeight));
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        toolbar.add(searchBar);
        toolbar.add(Box.createRigidArea(new Dimension(5, 0)));

        JComboBox<String> sortDropdown = getStringJComboBox(elementHeight);

        if (SHOW_SORT_DROPDOWN) {
            toolbar.add(sortDropdown);
        }

        searchBar.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                filterAndSortButtons(searchBar.getText(), (String) sortDropdown.getSelectedItem());
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });
        searchBar.addClearListener(() -> filterAndSortButtons("", (String) sortDropdown.getSelectedItem()));
        sortDropdown.addActionListener(e -> filterAndSortButtons(searchBar.getText(), (String) sortDropdown.getSelectedItem()));

        headerPanel.add(toolbar, BorderLayout.NORTH);

        if (mapElementType != MapElementType.MAP_ICON) {
            PluginErrorPanel errorPanel = new PluginErrorPanel();
            errorPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
            errorPanel.setContent("Info", "Right-click a map function button to adjust its scale. Function will only be hidde if their current scale is equal to or above the selected value");

            headerPanel.add(errorPanel, BorderLayout.SOUTH);
        }

        return headerPanel;
    }

    private static JComboBox<String> getStringJComboBox(int elementHeight) {
        String[] sortOptions = new String[]{"Alphabetical"};
        JComboBox<String> sortDropdown = new JComboBox<>(sortOptions);

        FontMetrics fm = sortDropdown.getFontMetrics(sortDropdown.getFont());
        int maxWidth = 0;
        for (String item : sortOptions)
        {
            int width = fm.stringWidth(item);
            if (width > maxWidth) maxWidth = width;
        }
        maxWidth += 40; // extra padding for arrow + margin
        sortDropdown.setPreferredSize(new Dimension(maxWidth, elementHeight));
        sortDropdown.setMaximumSize(new Dimension(maxWidth, elementHeight));
        return sortDropdown;
    }

    private void filterAndSortButtons(String query, String sortOption)
    {
        query = query.toLowerCase();

        Map<String, JButton> categoryButtonMap = new HashMap<>();
        List<String> categories = new ArrayList<>(mapElementManager.getAllCategories(mapElementType));
        for (int i = 0; i < buttons.length; i++)
        {
            categoryButtonMap.put(categories.get(i), buttons[i]);
        }

        List<String> filtered = new ArrayList<>();
        for (String cat : categories)
        {
            if (cat.toLowerCase().contains(query))
            {
                filtered.add(cat);
            }
        }

        if ("Alphabetical".equals(sortOption))
        {
            filtered.sort(String::compareToIgnoreCase);
        }

        buttonsFlowPanel.removeAll();
        for (String cat : filtered)
        {
            JButton btn = categoryButtonMap.get(cat);
            buttonsFlowPanel.add(btn);
        }

        buttonPanel.revalidate();
        buttonPanel.repaint();
    }

    private JButton createButton(MapElementEntry entry, BufferedImage image)
    {
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(BUTTON_SIZE, BUTTON_SIZE));
        button.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setIcon(new ImageIcon(image));

        button.setToolTipText(entry.getCategory());

        boolean isDisabled = mapElementManager.getSetting(entry.getCategory()).isDisabled();
        button.setBackground(isDisabled ? new Color(255, 0, 0, 120) : ColorScheme.DARKER_GRAY_COLOR);

        button.addActionListener(e -> {
            boolean active = !isActive(button);
            button.setBackground(active ? new Color(255, 0, 0, 120) : ColorScheme.DARKER_GRAY_COLOR);
            boolean newState = !mapElementManager.getSetting(entry.getCategory()).isDisabled();
            mapElementManager.setDisabled(entry.getCategory(), newState);

            hideInlineConfig();
        });

        button.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                if (SwingUtilities.isRightMouseButton(e) && isActive(button))
                {
                    showInlineConfig(entry);
                }
            }
        });

        return button;
    }



    private boolean isActive(JButton button)
    {
        return button.getBackground().equals(new Color(255, 0, 0, 120));
    }

    private void showInlineConfig(MapElementEntry entry)
    {
        if (mapElementType == MapElementType.MAP_ICON) {
            return;
        }
        currentEntry = entry;
        selectedLabel.setText(entry.getCategory() + ": ");

        Float currentScale = mapElementManager.getSetting(entry.getCategory()).getScale();
        if (currentScale == null)
        {
            scaleComboBox.setSelectedIndex(0);
        }
        else
        {
            for (int i = 0; i < ALLOWED_SCALES.length; i++)
            {
                if (ALLOWED_SCALES[i] != null && ALLOWED_SCALES[i].equals(currentScale))
                {
                    scaleComboBox.setSelectedIndex(i);
                    break;
                }
            }
        }

        inlineConfigPanel.setVisible(true);
        revalidate();
        repaint();
    }

    private void hideInlineConfig()
    {
        currentEntry = null;
        selectedLabel.setText("Selected: None");
        inlineConfigPanel.setVisible(false);
        revalidate();
        repaint();
    }
}
