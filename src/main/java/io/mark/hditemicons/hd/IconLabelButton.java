/*
 * Copyright (c) 2021, geheur
 * All rights reserved.
 *
 * Ported from the Weapon Animation Replacer plugin (BSD 2-Clause), so that this plugin's panel
 * reads the same way as the rest of the client's.
 */
package io.mark.hditemicons.hd;

import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Icon;
import javax.swing.JLabel;

final class IconLabelButton extends JLabel {
	private final Icon icon;
	private final Icon iconMouseovered;
	private final Runnable onClick;

	IconLabelButton(Icon icon, Icon iconMouseovered, Runnable onClick, String tooltip) {
		this.icon = icon;
		this.iconMouseovered = iconMouseovered;
		this.onClick = onClick;
		setIcon(icon);
		setToolTipText(tooltip);
		setCursor(new Cursor(Cursor.HAND_CURSOR));

		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent mouseEvent) {
				IconLabelButton.this.onClick.run();
			}

			@Override
			public void mouseEntered(MouseEvent mouseEvent) {
				setIcon(iconMouseovered);
			}

			@Override
			public void mouseExited(MouseEvent mouseEvent) {
				setIcon(IconLabelButton.this.icon);
			}
		});
	}
}
