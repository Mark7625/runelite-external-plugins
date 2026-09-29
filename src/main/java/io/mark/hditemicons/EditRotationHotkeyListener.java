package io.mark.hditemicons;

import javax.inject.Inject;
import net.runelite.client.util.HotkeyListener;

/** Tracks whether the hotkey is held, since the menu entry has no key event to match against. */
class EditRotationHotkeyListener extends HotkeyListener {
	private volatile boolean pressed;

	@Inject
	EditRotationHotkeyListener(HdItemIconsConfig config) {
		super(config::editRotationHotkey);
	}

	@Override
	public void hotkeyPressed() {
		pressed = true;
	}

	@Override
	public void hotkeyReleased() {
		pressed = false;
	}

	boolean isPressed() {
		return pressed;
	}
}
