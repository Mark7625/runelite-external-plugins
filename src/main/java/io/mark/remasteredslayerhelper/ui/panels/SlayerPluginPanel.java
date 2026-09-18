package io.mark.remasteredslayerhelper.ui.panels;

import io.mark.remasteredslayerhelper.RepositoryLayout;
import io.mark.remasteredslayerhelper.SlayerHelperConfig;
import io.mark.remasteredslayerhelper.data.QuestStateCache;
import io.mark.remasteredslayerhelper.data.SlayerMaster;
import io.mark.remasteredslayerhelper.data.SlayerTaskRepository;
import io.mark.remasteredslayerhelper.domain.SlayerTask;
import io.mark.remasteredslayerhelper.util.SlayerTasksFetcher;
import io.mark.remasteredslayerhelper.ui.renderers.SlayerTasksRenderer;
import io.mark.remasteredslayerhelper.ui.components.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

@Slf4j
public class SlayerPluginPanel extends PluginPanel {

    private static final String LOGIN_MESSAGE = "Login to activate";
    private static final String SELECT_TASK_MESSAGE = "<html><center>Select a task to<br>view details</center></html>";
    // Compact mode's split pane: 40% task list, 60% task detail
    private static final double SPLIT_LIST_RATIO = 0.4;
    private boolean loggedIn = false;
    private RepositoryLayout repositoryLayout = RepositoryLayout.NORMAL;

    private final SlayerTasksFetcher slayerTasksFetcher;
    private final SearchBar searchBar;
    private final ItemManager itemManager;
    private final DefaultListModel<SlayerTask> listModel = new DefaultListModel<>();
    private final String[] tabImageNamesWithExtensions = {
            "world_map.png", "inventory.png", "protect_from_all.png", "combat.png", "slayer_icon.png"
    };

    private static final String CURRENT_TASK_CARD = "current";
    private static final String TASK_REPOSITORY_CARD = "repository";
    private static final String LIST_CARD = "list";
    private static final String DETAIL_CARD = "detail";

    // MaterialTabGroup requires a display panel to manage, but it only ever anchors
    // the selected tab's content at BorderLayout.NORTH (sized to its preferred height
    // rather than stretched). We don't want that, so this panel is never added to the
    // visible component tree - actual content is swapped via our own stretched cardPanel.
    private final JPanel unusedTabGroupDisplay = new JPanel();
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);
    private final JPanel currentTaskPanel = new JPanel(new BorderLayout());
    private final JPanel taskRepositoryPanel = new JPanel(new BorderLayout());
    private final CardLayout repositoryCardLayout = new CardLayout();
    private final JPanel repositoryCardPanel = new JPanel(repositoryCardLayout);
    private final JPanel taskListView = new JPanel(new BorderLayout(0, 8));
    private final JPanel taskDetailView = new JPanel(new BorderLayout());
    private MaterialTabGroup tabGroup;
    private MaterialTab currentTaskTab;

    @Inject
    public SlayerPluginPanel(SlayerTaskRepository slayerTaskRepository, ItemManager itemManager, Client client, QuestStateCache questStateCache, SlayerHelperConfig config) {
        // PluginPanel's default (wrap=true) constructor nests this panel inside its own
        // internal BorderLayout.NORTH slot, which caps it to its preferred height instead
        // of stretching to fill the sidebar. Opt out so we can manage our own full-height
        // layout, matching how RuneLite's own full-height panels (e.g. GrandExchangePanel,
        // TimeTrackingPanel) do it.
        super(false);
        this.repositoryLayout = config.repositoryLayout();
        this.slayerTasksFetcher = new SlayerTasksFetcher(slayerTaskRepository);
        this.itemManager = itemManager;
        searchBar = new SearchBar(this::filterList, this::clearFilter);

        setLayout(new BorderLayout());

        tabGroup = new MaterialTabGroup(unusedTabGroupDisplay);
        currentTaskTab = new MaterialTab("Current Task", tabGroup, new JPanel());
        currentTaskTab.setOnSelectEvent(() -> {
            cardLayout.show(cardPanel, CURRENT_TASK_CARD);
            return true;
        });
        MaterialTab repositoryTab = new MaterialTab("Task Repository", tabGroup, new JPanel());
        repositoryTab.setOnSelectEvent(() -> {
            cardLayout.show(cardPanel, TASK_REPOSITORY_CARD);
            return true;
        });
        tabGroup.addTab(currentTaskTab);
        tabGroup.addTab(repositoryTab);

        buildTaskRepositoryPanel(client, questStateCache);

        cardPanel.add(currentTaskPanel, CURRENT_TASK_CARD);
        cardPanel.add(taskRepositoryPanel, TASK_REPOSITORY_CARD);

        add(tabGroup, BorderLayout.NORTH);
        add(cardPanel, BorderLayout.CENTER);

        tabGroup.select(repositoryTab);

        updateLoginState(false);
    }

    public void onTasksLoaded() {
        SwingUtilities.invokeLater(() -> {
            updateLoginState(true);
            clearFilter();
        });
    }

    public void onLoggedOut() {
        SwingUtilities.invokeLater(() -> updateLoginState(false));
    }

    public void setRepositoryLayout(RepositoryLayout layout) {
        SwingUtilities.invokeLater(() -> {
            repositoryLayout = layout;
            if (loggedIn) {
                showRepositoryContent();
            }
        });
    }

    private void updateLoginState(boolean loggedIn) {
        this.loggedIn = loggedIn;
        if (loggedIn) {
            showRepositoryContent();
            showEmptyCurrentTaskState();
        } else {
            showLoginGate(taskRepositoryPanel);
            showLoginGate(currentTaskPanel);
        }
    }

    private void showLoginGate(JPanel panel) {
        panel.removeAll();
        JLabel label = new JLabel(LOGIN_MESSAGE);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(label, BorderLayout.CENTER);
        panel.revalidate();
        panel.repaint();
    }

    private void showRepositoryContent() {
        taskRepositoryPanel.removeAll();

        if (repositoryLayout == RepositoryLayout.COMPACT) {
            showEmptyTaskDetailState();

            JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, taskListView, taskDetailView);
            splitPane.setResizeWeight(SPLIT_LIST_RATIO);
            splitPane.setContinuousLayout(true);
            splitPane.setBorder(BorderFactory.createEmptyBorder());
            splitPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            // setDividerLocation(double) needs the split pane to already have a real size to
            // compute against - isShowing()/invokeLater still isn't reliably late enough. Wait
            // for the first real (non-zero) resize instead, then detach so we don't fight the
            // user's own manual drags afterward.
            splitPane.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    if (splitPane.getHeight() > 0) {
                        splitPane.setDividerLocation(SPLIT_LIST_RATIO);
                        splitPane.removeComponentListener(this);
                    }
                }
            });
            taskRepositoryPanel.add(splitPane, BorderLayout.CENTER);
        } else {
            taskRepositoryPanel.add(repositoryCardPanel, BorderLayout.CENTER);
            repositoryCardLayout.show(repositoryCardPanel, LIST_CARD);
        }

        taskRepositoryPanel.revalidate();
        taskRepositoryPanel.repaint();
    }

    private void buildTaskRepositoryPanel(Client client, QuestStateCache questStateCache) {
        taskListView.setBorder(new EmptyBorder(10, 0, 0, 0));
        taskListView.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        taskDetailView.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        taskListView.add(searchBar.getSearchBar(), BorderLayout.NORTH);

        JList<SlayerTask> taskList = new JList<>(listModel);
        taskList.setCellRenderer(new SlayerTasksRenderer(client, questStateCache));
        taskList.setFocusable(true);
        taskList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        taskList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        taskList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                SlayerTask selectedTask = taskList.getSelectedValue();
                if (selectedTask != null) {
                    openTask(selectedTask);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(taskList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        taskListView.add(scrollPane, BorderLayout.CENTER);

        repositoryCardPanel.add(taskListView, LIST_CARD);
        repositoryCardPanel.add(taskDetailView, DETAIL_CARD);
    }

    private JLabel createMonsterIconLabel(SlayerTask task) {
        String monsterFileName = task.getMonsterFileName();
        JLabel iconLabel = new JLabel();
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
        try {
            BufferedImage img = ImageUtil.loadImageResource(getClass(), monsterFileName);
            iconLabel.setIcon(new ImageIcon(img));
        } catch (IllegalArgumentException | NullPointerException e) {
            log.info(String.format("Couldn't find image with name... %s", monsterFileName), e);
        }
        return iconLabel;
    }

    private JLabel createMonsterNameLabel(SlayerTask task) {
        JLabel nameLabel = new JLabel(task.getMonster());
        nameLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        nameLabel.setForeground(ColorScheme.BRAND_ORANGE);
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        nameLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        return nameLabel;
    }

    private static final int TAB_ICON_SIZE = 14;

    private TabPanel createTabPanel(SlayerTask task) {
        TabPanel tabPanel = new TabPanel();

        List<ImageIcon> icons = new ArrayList<>();
        for (String imageNameWithExtension : tabImageNamesWithExtensions) {
            BufferedImage image = ImageUtil.loadImageResource(getClass(), String.format("/images/%s", imageNameWithExtension));
            BufferedImage resized = ImageUtil.resizeImage(image, TAB_ICON_SIZE, TAB_ICON_SIZE);
            ImageIcon imageIcon = new ImageIcon(resized);
            icons.add(imageIcon);
        }

        Tab locationTab = new Tab(icons.get(0), task.getLocations(), "Map Location");
        Tab itemTab = new Tab(icons.get(1), task.getItemsRequired(), "Items Needed", itemManager);
        Tab attackStylesTab = new Tab(icons.get(2), task.getAttackStyles(), "Monster Attack Style");
        Tab attributesTab = new Tab(icons.get(3), task.getAttributes(), "Monsters Attributes");
        String[] masterNames = task.getSlayerMasters().stream().map(SlayerMaster::getDisplayName).toArray(String[]::new);
        Tab masterTab = new Tab(icons.get(4), masterNames, "Slayer Master");
        Tab[] tabs = {locationTab, itemTab, attackStylesTab, attributesTab, masterTab};

        // Put the monster name at the top of every tab's content so it always shows
        // directly below the tab strip, regardless of which tab is selected.
        for (Tab tab : tabs) {
            tab.getContent().add(createMonsterNameLabel(task), 0);
        }

        tabPanel.addTabs(tabs);

        return tabPanel;
    }

    private void showEmptyCurrentTaskState() {
        currentTaskPanel.removeAll();

        JLabel emptyLabel = new JLabel("<html><center>Select a task from<br>Task Repository to view details</center></html>");
        emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        currentTaskPanel.add(emptyLabel, BorderLayout.CENTER);

        currentTaskPanel.revalidate();
        currentTaskPanel.repaint();
    }

    private void showEmptyTaskDetailState() {
        taskDetailView.removeAll();

        JLabel emptyLabel = new JLabel(SELECT_TASK_MESSAGE);
        emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        taskDetailView.add(emptyLabel, BorderLayout.CENTER);

        taskDetailView.revalidate();
        taskDetailView.repaint();
    }

    private void openTask(SlayerTask task) {
        taskDetailView.removeAll();
        taskDetailView.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        boolean compact = repositoryLayout == RepositoryLayout.COMPACT;

        JLabel iconLabel = createMonsterIconLabel(task);
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTabbedPane tabbedPane = createTabPanel(task).getTabbedPane();
        tabbedPane.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Tabs (with the monster name at the top of each tab's content) pinned at the top,
        // icon + back button pinned flush to the bottom, so the detail view always fills
        // the full panel height without a dead gap. In compact mode the list stays visible
        // alongside the detail view, so there's nothing to go "back" to.
        JPanel headerBox = new JPanel();
        headerBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        headerBox.setLayout(new BoxLayout(headerBox, BoxLayout.Y_AXIS));
        headerBox.add(tabbedPane);

        JPanel footerBox = new JPanel();
        footerBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        footerBox.setLayout(new BoxLayout(footerBox, BoxLayout.Y_AXIS));
        footerBox.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        footerBox.add(iconLabel);

        if (!compact) {
            JButton backButton = new JButton("<- Back");
            backButton.setFocusPainted(false);
            backButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            backButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            backButton.addActionListener(e -> repositoryCardLayout.show(repositoryCardPanel, LIST_CARD));
            footerBox.add(backButton);
        }

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        content.add(headerBox, BorderLayout.NORTH);
        content.add(footerBox, BorderLayout.SOUTH);

        taskDetailView.add(content, BorderLayout.CENTER);
        taskDetailView.revalidate();
        taskDetailView.repaint();

        if (!compact) {
            repositoryCardLayout.show(repositoryCardPanel, DETAIL_CARD);
        }
    }

    public void filterList(String searchText) {
        if (!searchText.isEmpty()) {
            Collection<SlayerTask> tasks = slayerTasksFetcher.getSlayerTasksByFilter(searchText);
            updateListModel(tasks);
        } else {
            updateListModel(slayerTasksFetcher.getAllSlayerTasks());
        }
    }

    public void clearFilter() {
        searchBar.getSearchBar().setText("");
        updateListModel(slayerTasksFetcher.getAllSlayerTasks());
    }

    public void updateListModel(Collection<SlayerTask> tasks) {
        listModel.clear();
        tasks.forEach(listModel::addElement);
    }
}
