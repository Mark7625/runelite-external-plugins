package io.mark.remasteredslayerhelper.ui.panels;

import io.mark.remasteredslayerhelper.data.SlayerMaster;
import io.mark.remasteredslayerhelper.data.SlayerTaskRepository;
import io.mark.remasteredslayerhelper.domain.SlayerTask;
import io.mark.remasteredslayerhelper.util.SlayerTasksFetcher;
import io.mark.remasteredslayerhelper.ui.renderers.SlayerTasksRenderer;
import io.mark.remasteredslayerhelper.ui.components.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

@Slf4j
public class SlayerPluginPanel extends PluginPanel {

    private final SlayerTasksFetcher slayerTasksFetcher;
    private final SearchBar searchBar;
    private final ItemManager itemManager;
    private DefaultListModel<SlayerTask> listModel = new DefaultListModel<>();
    private final String[] tabImageNamesWithExtensions = {
            "world_map.png", "inventory.png", "protect_from_all.png", "combat.png", "slayer_icon.png"
    };

    @Inject
    public SlayerPluginPanel(SlayerTaskRepository slayerTaskRepository, ItemManager itemManager) {
        this.slayerTasksFetcher = new SlayerTasksFetcher(slayerTaskRepository);
        this.itemManager = itemManager;
        searchBar = new SearchBar(this::filterList, this::clearFilter);
        createTaskListPanel(new ArrayList<>());
    }

    private JPanel createHeaderPanel(SlayerTask task) {
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 15);
        String monsterName = task.getMonster();
        String monsterFileName = task.getMonsterFileName();

        ImageIcon imageIcon;
        try {
            BufferedImage img = ImageUtil.loadImageResource(getClass(), monsterFileName);
            BufferedImage resizedImg = ImageUtil.resizeImage(img, img.getWidth() / 2, img.getHeight() / 2);

            imageIcon = new ImageIcon(resizedImg);
        } catch (NullPointerException e) {
            log.info(String.format("Couldn't find image with name... %s", monsterFileName), e);
            return new HeaderPanel(font, monsterName, Color.ORANGE, SwingConstants.CENTER).getHeaderPanel();
        }

        return new HeaderPanel(font, monsterName, Color.CYAN, imageIcon, SwingConstants.CENTER).getHeaderPanel();
    }

    private TabPanel createTabPanel(SlayerTask task) {
        TabPanel tabPanel = new TabPanel();

        List<ImageIcon> icons = new ArrayList<>();
        for (String imageNameWithExtension : tabImageNamesWithExtensions) {
            BufferedImage image = ImageUtil.loadImageResource(getClass(), String.format("/images/%s", imageNameWithExtension));
            ImageIcon imageIcon = new ImageIcon(image);
            icons.add(imageIcon);
        }

        Tab locationTab = new Tab(icons.get(0), task.getLocations(), "Map Location");
        Tab itemTab = new Tab(icons.get(1), task.getItemsRequired(), "Items Needed", itemManager);
        Tab attackStylesTab = new Tab(icons.get(2), task.getAttackStyles(), "Monster Attack Style");
        Tab attributesTab = new Tab(icons.get(3), task.getAttributes(), "Monsters Attributes");
        String[] masterNames = task.getSlayerMasters().stream().map(SlayerMaster::getDisplayName).toArray(String[]::new);
        Tab masterTab = new Tab(icons.get(4), masterNames, "Slayer Master");
        tabPanel.addTabs(new Tab[]{locationTab, itemTab, attackStylesTab, attributesTab, masterTab});

        return tabPanel;
    }

    public void createTaskListPanel(Collection<SlayerTask> tasks) {
        removeComponents(null);
        BufferedImage image = ImageUtil.loadImageResource(getClass(), String.format("/images/slayer_icon.png"));
        ImageIcon imageIcon = new ImageIcon(image);
        JLabel searchBarIcon = new JLabel();
        searchBarIcon.setVerticalAlignment(SwingConstants.CENTER);
        searchBarIcon.setHorizontalAlignment(SwingConstants.CENTER);
        searchBarIcon.setIcon(imageIcon);
        add(searchBarIcon);
        JLabel searchBarTitle = new JLabel("~ Slayer Helper ~");
        searchBarTitle.setForeground(Color.ORANGE);
        searchBarTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        searchBarTitle.setHorizontalAlignment(SwingConstants.CENTER);
        searchBarTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        JLabel searchBarHelperMsg = new JLabel("Search for a monster...");
        searchBarHelperMsg.setForeground(Color.WHITE);
        searchBarHelperMsg.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        add(searchBarTitle);
        add(searchBarHelperMsg);
        add(searchBar.getSearchBar());
        this.clearFilter();
        this.updateListModel(tasks);

        JList<SlayerTask> monsterNames = new JList<>(listModel);
        monsterNames.setCellRenderer(new SlayerTasksRenderer());
        monsterNames.setFocusable(true);
        monsterNames.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        monsterNames.setBackground(new Color(30, 30, 30));
        monsterNames.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                SlayerTask selectedTask = monsterNames.getSelectedValue();
                if (selectedTask != null) {
                    openTask(selectedTask);
                }
            }
        });

        add(monsterNames);
        revalidate();
        repaint();
    }

    private void openTask(SlayerTask task) {
        removeComponents(null);
        JButton backButton = new JButton("<- Back");
        backButton.setFocusPainted(false);
        backButton.setPreferredSize(new Dimension(50, 30));
        backButton.addActionListener(e -> {
            closeTask();
            remove(backButton);
        });
        add(backButton);
        add(createTabPanel(task).getTabbedPane());
        add(createHeaderPanel(task));
        backButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void closeTask() {
        // back to an empty list
        createTaskListPanel(new ArrayList<>());
    }

    private void removeComponents(@Nullable Component[] excludedComponents) {
        Component[] components = this.getComponents();

        for (Component component : components) {
            if (excludedComponents != null && Arrays.stream(excludedComponents).anyMatch(c -> c == component)) {
                continue;
            }
            remove(component);
        }

        revalidate();
        repaint();
    }

    public void filterList(String searchText) {
        if (!searchText.isEmpty()) {
            Collection<SlayerTask> tasks = slayerTasksFetcher.getSlayerTasksByFilter(searchText);
            updateListModel(tasks);
        } else {
            // Clear the list when the search text is empty
            listModel.clear();
        }
    }

    public void clearFilter() {
        searchBar.getSearchBar().setText(""); // Clear the search bar text
        updateListModel(slayerTasksFetcher.getAllSlayerTasks()); // Reset the list
    }

    // A helper method to update the list model
    public void updateListModel(Collection<SlayerTask> tasks) {
        listModel.clear();
        tasks.forEach(listModel::addElement);
    }
}
