package io.mark.remasteredslayerhelper.ui.renderers;

import io.mark.remasteredslayerhelper.data.QuestStateCache;
import io.mark.remasteredslayerhelper.domain.SlayerTask;
import io.mark.remasteredslayerhelper.util.SlayerTaskUnlockChecker;
import net.runelite.api.Client;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class SlayerTasksRenderer extends JPanel implements ListCellRenderer<SlayerTask> {
    private static final int ICON_SIZE = 32;
    private static final Color UNLOCKED_COLOR = ColorScheme.PROGRESS_COMPLETE_COLOR;
    private static final Color LOCKED_COLOR = ColorScheme.PROGRESS_ERROR_COLOR;
    private static final Color BACKGROUND_COLOR = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color SELECTED_BACKGROUND_COLOR = ColorScheme.DARK_GRAY_HOVER_COLOR;

    private final Client client;
    private final QuestStateCache questStateCache;
    private final Map<String, ImageIcon> iconCache = new HashMap<>();

    private final JLabel iconLabel = new JLabel();
    private final JLabel nameLabel = new JLabel();
    private final JLabel statusLabel = new JLabel();

    public SlayerTasksRenderer(Client client, QuestStateCache questStateCache) {
        this.client = client;
        this.questStateCache = questStateCache;

        setLayout(new BorderLayout(8, 0));
        setBorder(new EmptyBorder(6, 8, 6, 8));

        iconLabel.setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        nameLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

        textPanel.add(nameLabel);
        textPanel.add(statusLabel);

        add(iconLabel, BorderLayout.WEST);
        add(textPanel, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(
            JList<? extends SlayerTask> list,
            SlayerTask task,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {

        nameLabel.setText(task.getMonster());
        nameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        String lockReason = SlayerTaskUnlockChecker.getLockReason(client, questStateCache, task);
        boolean unlocked = lockReason == null;
        statusLabel.setText(unlocked ? "Unlocked" : "Locked (" + lockReason + ")");
        statusLabel.setForeground(unlocked ? UNLOCKED_COLOR : LOCKED_COLOR);

        iconLabel.setIcon(getIcon(task));

        setBackground(isSelected ? SELECTED_BACKGROUND_COLOR : BACKGROUND_COLOR);
        setOpaque(true);

        return this;
    }

    private ImageIcon getIcon(SlayerTask task) {
        String fileName = task.getMonsterFileName();
        return iconCache.computeIfAbsent(fileName, name -> {
            try {
                BufferedImage image = ImageUtil.loadImageResource(getClass(), name);
                return new ImageIcon(ImageUtil.resizeImage(image, ICON_SIZE, ICON_SIZE));
            } catch (IllegalArgumentException e) {
                return null;
            }
        });
    }
}
