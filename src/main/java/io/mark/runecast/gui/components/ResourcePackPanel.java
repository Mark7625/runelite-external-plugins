/*
 * Copyright (c) 2025 Mark_ <https://github.com/Mark7625/>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.mark.runecast.gui.components;

import com.google.common.html.HtmlEscapers;
import com.google.inject.Inject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.SwingUtil;
import okhttp3.OkHttpClient;
import io.mark.runecast.gui.RuneCastSidebar;
import io.mark.runecast.resourcepacks.AbstractResourcePack;
import io.mark.runecast.resourcepacks.ResourcePackManager;
import io.mark.runecast.resourcepacks.data.Manifest;

@Slf4j
public class ResourcePackPanel extends JPanel {
    private static final ImageIcon FADE;
    private static final ImageIcon ARROW_UP;
    private static final ImageIcon ARROW_DOWN;
    private static final ImageIcon ARROW_UP_HOVER;
    private static final ImageIcon ARROW_DOWN_HOVER;

    static {
        FADE = new ImageIcon(ImageUtil.loadImageResource(RuneCastSidebar.class, "fade.png"));

        BufferedImage ARROW_UP_ICON = ImageUtil.loadImageResource(RuneCastSidebar.class, "arrow_up.png");
        BufferedImage ARROW_DOWN_ICON = ImageUtil.loadImageResource(RuneCastSidebar.class, "arrow_down.png");

        ARROW_UP = new ImageIcon(ARROW_UP_ICON);
        ARROW_DOWN = new ImageIcon(ARROW_DOWN_ICON);

        ARROW_UP_HOVER = new ImageIcon(ImageUtil.alphaOffset(ARROW_UP_ICON, -100));
        ARROW_DOWN_HOVER = new ImageIcon(ImageUtil.alphaOffset(ARROW_DOWN_ICON, -100));

    }

    @Inject
    private ResourcePackManager resourcePackManager;

    private final JPanel list;

    private final MessagePanel installHint = new MessagePanel(
            "Looking for more? ",
            "You can install additional resource packs<br>by clicking the button below."
    );

    ResourcePackPanel() {

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder());

        list = new JPanel();
        list.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 3));
        list.setLayout(new DynamicGridLayout(0, 1, 0, 5));
        list.setAlignmentX(Component.LEFT_ALIGNMENT);

        var scrollPane = new JScrollPane();
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setPreferredSize(new Dimension(0x7000, 0x7000));
        scrollPane.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                int visibleAmount = scrollPane.getVerticalScrollBar().getVisibleAmount();
                int maxSize = scrollPane.getVerticalScrollBar().getMaximum();
                boolean displayHint = visibleAmount == maxSize;
                if (displayHint) {
                    list.add(installHint);
                } else {
                    list.remove(installHint);
                }
            }
        });

        JPanel scrollContainer = new FixedWidthPanel();
        scrollContainer.setLayout(new BorderLayout());
        scrollContainer.add(list, BorderLayout.NORTH);
        scrollPane.setViewportView(scrollContainer);
        add(scrollPane);

        refreshPanel();
    }


    public void refreshPanel() {
        SwingUtilities.invokeLater(() -> {
            list.removeAll();

            var packs = resourcePackManager.getInstalledPacks();
            for (int i = 0; i < packs.size(); i++) {
                log.info("Adding installed pack: {}", packs.get(i).getPackName());
                list.add(createInstalledPackComponent(packs.get(i), i));
            }

            list.add(installHint);

            revalidate();
            repaint();
        });
    }

    public void movePack(int fromIndex, int toIndex) {
        Collections.swap(resourcePackManager.getInstalledPacks(), fromIndex, toIndex);
        refreshPanel();
    }

    public JPanel createInstalledPackComponent(AbstractResourcePack pack, int index) {
        JPanel panel = new JPanel();

        boolean isTop = index == 0;
        boolean isBottom = index == (resourcePackManager.getInstalledPacks().size() - 1);

        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setOpaque(true);
        panel.setLayout(null);
        panel.setBounds(0, 0, 221, 124);
        panel.setMinimumSize(new Dimension(221, 124));
        panel.setPreferredSize(new Dimension(221, 124));

        JButton moveDown = new JButton();
        moveDown.setText("");
        moveDown.setIcon(ARROW_DOWN);
        SwingUtil.removeButtonDecorations(moveDown);
        moveDown.setBounds(190, 5, 22, 22);
        panel.add(moveDown);
        moveDown.setEnabled(!isBottom);
        moveDown.addActionListener(ev -> movePack(index, index + 1));

        JButton moveUp = new JButton();
        moveUp.setText("");
        moveUp.setIcon(ARROW_UP);
        SwingUtil.removeButtonDecorations(moveUp);
        panel.add(moveUp);
        moveUp.setBounds(165, 5, 22, 22);
        moveUp.setEnabled(!isTop);
        moveUp.addActionListener(ev -> movePack(index, index - 1));

        Manifest manifest = pack.getManifest();
        JLabel author = new JLabel(manifest.getAuthor());
        author.setFont(FontManager.getRunescapeSmallFont());
        author.setToolTipText(manifest.getAuthor());
        author.setBounds(5, 105, 65, author.getPreferredSize().height);
        author.setForeground(Color.WHITE);
        panel.add(author);

        String descriptionText = manifest.getDescription();
        if (!descriptionText.startsWith("<html>")) {
            descriptionText = "<html>" + HtmlEscapers.htmlEscaper().escape(descriptionText) + "</html>";
        }
        JLabel description = new JLabel(descriptionText);
        description.setVerticalAlignment(JLabel.TOP);
        description.setToolTipText(descriptionText);
        description.setBounds(5, 30, 210, 70);
        description.setForeground(Color.WHITE);
        panel.add(description);

        JLabel packName = new JLabel(manifest.getDisplayName());
        packName.setFont(FontManager.getRunescapeBoldFont());
        packName.setToolTipText(manifest.getInternalName());
        packName.setBounds(5, 5, 105, 25);
        packName.setForeground(Color.WHITE);
        panel.add(packName);


        JLabel icon = new JLabel();
        icon.setHorizontalAlignment(JLabel.CENTER);

        JLabel blackBox = new JLabel();
        blackBox.setIcon(FADE);

        if (pack.hasPackImage()) {
            icon.setIcon(new ImageIcon(pack.getPackImage()));
            icon.setVisible(true);
            blackBox.setVisible(true);
        } else {
            icon.setVisible(false);
            blackBox.setVisible(false);
        }
        icon.setBounds(new Rectangle(new Point(0, 0), icon.getPreferredSize()));
        blackBox.setBounds(0, 0, 221, 124);

        panel.add(blackBox);
        panel.add(icon);

        return panel;
    }

}