package io.mark.globes;

import net.runelite.client.ui.FontManager;

import java.awt.Font;

public enum OverlayFontType {
	RUNESCAPE("RuneScape", FontManager.getRunescapeFont()),
	RUNESCAPE_SMALL("RuneScape Small", FontManager.getRunescapeSmallFont()),
	RUNESCAPE_BOLD("RuneScape Bold", FontManager.getRunescapeBoldFont()),
	DEFAULT("Default", FontManager.getDefaultFont()),
	DEFAULT_BOLD("Default Bold", FontManager.getDefaultBoldFont());

	private final String displayName;
	private final Font font;

	OverlayFontType(String displayName, Font font) {
		this.displayName = displayName;
		this.font = font;
	}

	public Font getFont() {
		return font;
	}

	@Override
	public String toString() {
		return displayName;
	}
}
