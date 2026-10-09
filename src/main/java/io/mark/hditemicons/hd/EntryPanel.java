/*
 * Copyright (c) 2021, geheur
 * All rights reserved.
 *
 * Ported from the Weapon Animation Replacer plugin (BSD 2-Clause), so that this plugin's panel
 * reads the same way as the rest of the client's.
 */
package io.mark.hditemicons.hd;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.BorderFactory;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

final class EntryPanel extends JPanel {
	static final Border NAME_BOTTOM_BORDER = new CompoundBorder(
		BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
		BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR));

	static final ImageIcon ADD_ICON;
	static final ImageIcon ADD_HOVER_ICON;
	static final ImageIcon DELETE_ICON;
	static final ImageIcon DELETE_HOVER_ICON;
	static final ImageIcon EDIT_ICON;
	static final ImageIcon EDIT_HOVER_ICON;
	static final ImageIcon GEAR_ICON;
	static final ImageIcon GEAR_HOVER_ICON;

	static {
		BufferedImage addImage = ImageUtil.loadImageResource(EntryPanel.class, "add_icon.png");
		ADD_ICON = new ImageIcon(addImage);
		ADD_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(addImage, 0.53f));

		BufferedImage deleteImage = ImageUtil.loadImageResource(EntryPanel.class, "delete.png");
		DELETE_ICON = new ImageIcon(deleteImage);
		DELETE_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(deleteImage, -50));

		BufferedImage editImage = ImageUtil.loadImageResource(EntryPanel.class, "edit.png");
		EDIT_ICON = new ImageIcon(editImage);
		EDIT_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(editImage, -150));

		// Drawn at 24px, which towers over the other header buttons, so it's brought down to theirs
		BufferedImage gearImage = ImageUtil.resizeImage(
			ImageUtil.loadImageResource(EntryPanel.class, "gear_icon.png"), 14, 14);
		GEAR_ICON = new ImageIcon(gearImage);
		GEAR_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(gearImage, 0.53f));
	}

	private final JPanel buttons = new JPanel();

	EntryPanel(JComponent title) {
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);

		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
		buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		buttons.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));

		add(title, BorderLayout.CENTER);
		add(buttons, BorderLayout.EAST);
	}

	EntryPanel withButton(ImageIcon icon, ImageIcon hover, Runnable onClick, String tooltip) {
		buttons.add(new IconLabelButton(icon, hover, onClick, tooltip));
		return this;
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}
}
