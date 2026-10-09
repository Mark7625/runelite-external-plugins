package io.mark.hditemicons.hd;

import io.mark.hditemicons.CustomRotation;
import io.mark.hditemicons.CustomRotationStorage;
import io.mark.hditemicons.IconEditorHost;
import io.mark.hditemicons.NamedItem;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.FlatTextField;
import net.runelite.client.util.ImageUtil;

import static io.mark.hditemicons.hd.ItemIconRasterizer.ICON_HEIGHT;
import static io.mark.hditemicons.hd.ItemIconRasterizer.ICON_WIDTH;

/**
 * A live property panel for an item's icon camera placement. Every change applies and saves
 * immediately, so there's no Save step - only per-field resets and a reset-everything.
 */
final class RotationEditorPanel extends JPanel {
	private enum Mode { ROTATE, PAN, SCALE }

	/**
	 * One entry of the saved-icon dropdown, whose values can be copied onto the item being
	 * edited. The prompt entry carries no rotation.
	 */
	static final class Preset {
		private final String label;
		@Nullable
		private final CustomRotation rotation;

		Preset(String label, @Nullable CustomRotation rotation) {
			this.label = label;
			this.rotation = rotation;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	static final class Session {
		final int itemId;
		final String itemName;
		final CustomRotation initial;
		final CustomRotation defaults;
		final List<Preset> savedItems;
		final ItemIconRasterizer.IconModel model;
		@Nullable
		final int[] referencePixels;
		final int[] palette;
		final int supersample;

		Session(int itemId, String itemName, CustomRotation initial, CustomRotation defaults, List<Preset> savedItems,
				ItemIconRasterizer.IconModel model, @Nullable int[] referencePixels, int[] palette, int supersample) {
			this.itemId = itemId;
			this.itemName = itemName;
			this.initial = initial;
			this.defaults = defaults;
			this.savedItems = savedItems;
			this.model = model;
			this.referencePixels = referencePixels;
			this.palette = palette;
			this.supersample = supersample;
		}
	}

	private static final BufferedImage RESET_IMAGE =
		ImageUtil.loadImageResource(RotationEditorPanel.class, "reset.png");
	private static final ImageIcon RESET_ICON = new ImageIcon(RESET_IMAGE);
	private static final ImageIcon RESET_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(RESET_IMAGE, -50));

	private static final int COMMIT_THROTTLE_MS = 100;
	private static final double ANGLE_PER_DRAG_PIXEL = 4;
	private static final double RESIZE_PER_DRAG_PIXEL = 1;
	private static final double ZOOM_PER_WHEEL_NOTCH = 50;
	private static final int SPINNER_BOUND = 200_000;

	private final Session session;
	private final CustomRotationStorage storage;
	private final IconEditorHost host;
	private final int previewScale;

	private final ExecutorService previewExecutor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "hd-item-icon-rotation-preview");
		thread.setDaemon(true);
		return thread;
	});
	private final Timer commitThrottle;
	private volatile long previewGeneration;

	private Mode mode = Mode.ROTATE;
	private int dragX, dragY;
	private boolean settingCopyFrom;
	private Runnable onGroupsChanged = () -> {};

	private final JSpinner zoomSpinner;
	private final JSpinner panXSpinner;
	private final JSpinner panYSpinner;
	private final JSpinner angleXSpinner;
	private final JSpinner angleYSpinner;
	private final JSpinner angleZSpinner;
	private final JSpinner resizeXSpinner;
	private final JSpinner resizeYSpinner;
	private final JSpinner resizeZSpinner;
	private final JLabel previewLabel;
	private final JLabel hintLabel;
	private final JComboBox<Preset> copyFromBox;
	private final JPanel itemPanel;
	private final JPanel itemBody;

	RotationEditorPanel(Session session, CustomRotationStorage storage, IconEditorHost host, int previewScale) {
		this.session = session;
		this.storage = storage;
		this.host = host;
		this.previewScale = previewScale;

		CustomRotation initial = session.initial;
		previewLabel = new JLabel();
		previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
		previewLabel.setOpaque(true);
		previewLabel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		previewLabel.setPreferredSize(new Dimension(ICON_WIDTH * previewScale, ICON_HEIGHT * previewScale));

		JRadioButton rotateButton = new JRadioButton("Rotate", true);
		JRadioButton panButton = new JRadioButton("Pan");
		JRadioButton scaleButton = new JRadioButton("Scale");
		ButtonGroup modeGroup = new ButtonGroup();
		JPanel modePanel = new JPanel(new GridLayout(1, 3, 2, 0));
		modePanel.setOpaque(false);
		for (JRadioButton button : new JRadioButton[]{rotateButton, panButton, scaleButton}) {
			button.setOpaque(false);
			button.setFont(FontManager.getRunescapeSmallFont());
			modeGroup.add(button);
			modePanel.add(button);
		}
		rotateButton.addActionListener(e -> setMode(Mode.ROTATE));
		panButton.addActionListener(e -> setMode(Mode.PAN));
		scaleButton.addActionListener(e -> setMode(Mode.SCALE));

		hintLabel = new JLabel("", SwingConstants.CENTER);
		hintLabel.setFont(FontManager.getRunescapeSmallFont());
		hintLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		updateHint();

		zoomSpinner = newSpinner(initial.zoom2d);
		panXSpinner = newSpinner(initial.offsetX);
		panYSpinner = newSpinner(initial.offsetY);
		angleXSpinner = newSpinner(initial.xan2d);
		angleYSpinner = newSpinner(initial.yan2d);
		angleZSpinner = newSpinner(initial.zan2d);
		resizeXSpinner = newSpinner(initial.resizeX);
		resizeYSpinner = newSpinner(initial.resizeY);
		resizeZSpinner = newSpinner(initial.resizeZ);

		// A throttle, not a debounce, so the live icon keeps pace with a drag instead of
		// freezing until it ends. The preview isn't gated by it at all.
		commitThrottle = new Timer(COMMIT_THROTTLE_MS, e -> commit());
		commitThrottle.setRepeats(false);
		ChangeListener changed = e -> onValueChanged();
		for (JSpinner spinner : new JSpinner[]{zoomSpinner, panXSpinner, panYSpinner,
			angleXSpinner, angleYSpinner, angleZSpinner, resizeXSpinner, resizeYSpinner, resizeZSpinner})
			spinner.addChangeListener(changed);

		attachDragControls();

		CustomRotation defaults = session.defaults;
		JPanel fields = new JPanel(new GridLayout(9, 1, 0, 4));
		fields.setOpaque(false);
		fields.add(fieldRow("Zoom", zoomSpinner, () -> defaults.zoom2d));
		fields.add(fieldRow("Pan X", panXSpinner, () -> defaults.offsetX));
		fields.add(fieldRow("Pan Y", panYSpinner, () -> defaults.offsetY));
		fields.add(fieldRow("Angle X", angleXSpinner, () -> defaults.xan2d));
		fields.add(fieldRow("Angle Y", angleYSpinner, () -> defaults.yan2d));
		fields.add(fieldRow("Angle Z", angleZSpinner, () -> defaults.zan2d));
		fields.add(fieldRow("Resize X", resizeXSpinner, () -> defaults.resizeX));
		fields.add(fieldRow("Resize Y", resizeYSpinner, () -> defaults.resizeY));
		fields.add(fieldRow("Resize Z", resizeZSpinner, () -> defaults.resizeZ));

		copyFromBox = new JComboBox<>();
		fillCopyFrom();
		copyFromBox.addActionListener(e -> {
			if (settingCopyFrom)
				return;
			Preset picked = (Preset) copyFromBox.getSelectedItem();
			if (picked == null || picked.rotation == null)
				return;
			applyImmediately(() -> applyToSpinners(picked.rotation));
		});

		itemBody = new JPanel();
		itemBody.setLayout(new BoxLayout(itemBody, BoxLayout.Y_AXIS));
		itemBody.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		itemBody.setBorder(new EmptyBorder(8, 6, 8, 6));

		JPanel modeAndHint = new JPanel(new BorderLayout());
		modeAndHint.setOpaque(false);
		modeAndHint.add(modePanel, BorderLayout.NORTH);
		modeAndHint.add(hintLabel, BorderLayout.SOUTH);

		itemBody.add(new Row(previewLabel));
		itemBody.add(Box.createVerticalStrut(4));
		itemBody.add(new Row(modeAndHint));
		itemBody.add(Box.createVerticalStrut(8));
		itemBody.add(new Row(fields));
		itemBody.add(Box.createVerticalStrut(8));
		itemBody.add(new Row(labelled("Copy from", copyFromBox)));
		itemBody.add(Box.createVerticalStrut(8));

		JButton resetAll = new JButton("Reset to default");
		resetAll.setToolTipText("Put this icon back to the game's own placement");
		resetAll.addActionListener(e -> resetToDefaults());
		itemBody.add(new Row(resetAll));

		itemPanel = new JPanel(new BorderLayout());
		itemPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(new Row(itemPanel));

		buildItem();
		commit();
		schedulePreview();
	}

	void setOnGroupsChanged(Runnable onGroupsChanged) {
		this.onGroupsChanged = onGroupsChanged;
	}

	void applyToGroup() {
		applyImmediately(() -> {});
	}

	NamedItem editedItem() {
		return new NamedItem(session.itemId, session.itemName);
	}

	void reloadFromStorage() {
		fillCopyFrom();
		CustomRotation imported = storage.get(session.itemId);
		if (imported != null)
			applyImmediately(() -> applyToSpinners(imported));
	}

	private void buildItem() {
		FlatTextField title = new FlatTextField();
		title.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		title.setEditable(false);
		title.setText(session.itemName);
		title.getTextField().setForeground(Color.WHITE);
		title.getTextField().setBorder(new EmptyBorder(0, 6, 0, 0));
		title.getTextField().setToolTipText(session.itemName + " (" + session.itemId + ")");
		title.setPreferredSize(new Dimension(0, 24));

		JPanel titleWrapper = new JPanel(new BorderLayout());
		titleWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		titleWrapper.setBorder(EntryPanel.NAME_BOTTOM_BORDER);
		titleWrapper.add(title, BorderLayout.CENTER);

		EntryPanel header = new EntryPanel(titleWrapper)
			.withButton(EntryPanel.GEAR_ICON, EntryPanel.GEAR_HOVER_ICON, this::showItemMenu, "More");

		itemPanel.add(header, BorderLayout.NORTH);
		itemPanel.add(itemBody, BorderLayout.CENTER);
	}

	private void showItemMenu() {
		JPopupMenu menu = new JPopupMenu();
		menu.setBorder(new EmptyBorder(5, 5, 5, 5));
		addMenuItem(menu, "Apply to similar items...", this::applyToSimilarItems);
		menu.addSeparator();
		addMenuItem(menu, "Save as preset...", this::saveAsPreset);
		addMenuItem(menu, "Delete preset...", this::deletePreset);

		Point location = MouseInfo.getPointerInfo().getLocation();
		SwingUtilities.convertPointFromScreen(location, this);
		menu.show(this, location.x, location.y);
	}

	private static void addMenuItem(JPopupMenu menu, String name, Runnable onClick) {
		JMenuItem item = new JMenuItem(name);
		item.addActionListener(e -> onClick.run());
		menu.add(item);
	}

	private void resetToDefaults() {
		if (JOptionPane.showConfirmDialog(this, "Put this icon back to the game's own placement?",
			"Reset icon", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
			return;
		applyImmediately(() -> applyToSpinners(session.defaults));
	}

	void dispose() {
		commitThrottle.stop();
		previewExecutor.shutdownNow();
	}

	private static final class Row extends JPanel {
		Row(JComponent child) {
			super(new BorderLayout());
			setOpaque(false);
			setAlignmentX(Component.LEFT_ALIGNMENT);
			add(child, BorderLayout.CENTER);
		}

		@Override
		public Dimension getMaximumSize() {
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}

	private static JPanel labelled(String text, JComponent field) {
		JPanel panel = new JPanel(new BorderLayout(4, 0));
		panel.setOpaque(false);
		JLabel label = new JLabel(text);
		label.setPreferredSize(new Dimension(56, label.getPreferredSize().height));
		panel.add(label, BorderLayout.WEST);
		panel.add(field, BorderLayout.CENTER);
		return panel;
	}

	private void setMode(Mode newMode) {
		mode = newMode;
		updateHint();
	}

	private void updateHint() {
		switch (mode) {
			case ROTATE:
				hintLabel.setText("Drag rotate · Right-drag roll · Wheel zoom");
				break;
			case PAN:
				hintLabel.setText("Drag to pan · Wheel zoom");
				break;
			case SCALE:
				hintLabel.setText("Drag to resize · Wheel zoom");
				break;
		}
	}

	private void attachDragControls() {
		// Screen coordinates, since a drag routinely leaves the small preview label and
		// component-relative ones stop tracking properly once outside its bounds
		previewLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				dragX = e.getXOnScreen();
				dragY = e.getYOnScreen();
			}
		});
		previewLabel.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseDragged(MouseEvent e) {
				int dx = e.getXOnScreen() - dragX;
				int dy = e.getYOnScreen() - dragY;
				dragX = e.getXOnScreen();
				dragY = e.getYOnScreen();
				if (SwingUtilities.isRightMouseButton(e)) {
					nudge(angleZSpinner, dx * ANGLE_PER_DRAG_PIXEL);
					return;
				}
				switch (mode) {
					case ROTATE:
						nudge(angleYSpinner, dx * ANGLE_PER_DRAG_PIXEL);
						nudge(angleXSpinner, dy * ANGLE_PER_DRAG_PIXEL);
						break;
					case PAN: {
						double panSensitivity = panUnitsPerDragPixel();
						nudge(panXSpinner, dx * panSensitivity);
						nudge(panYSpinner, dy * panSensitivity);
						break;
					}
					case SCALE:
						nudge(resizeXSpinner, dx * RESIZE_PER_DRAG_PIXEL);
						nudge(resizeYSpinner, -dy * RESIZE_PER_DRAG_PIXEL);
						break;
				}
			}
		});
		previewLabel.addMouseWheelListener(e -> nudge(zoomSpinner, e.getWheelRotation() * ZOOM_PER_WHEEL_NOTCH));
	}

	private void nudge(JSpinner spinner, double delta) {
		if (delta == 0)
			return;
		spinner.setValue(spinnerValue(spinner) + (int) Math.round(delta));
	}

	/**
	 * Inverts the rasterizer's perspective projection so a dragged pixel moves the icon by a
	 * pixel. A fixed rate can't work, since the projection divides by the current zoom.
	 */
	private double panUnitsPerDragPixel() {
		return spinnerValue(zoomSpinner) / (double) (previewScale * ItemIconRasterizer.PROJECTION_SCALE);
	}

	private static JSpinner newSpinner(int value) {
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, -SPINNER_BOUND, SPINNER_BOUND, 1));
		spinner.setEditor(new JSpinner.NumberEditor(spinner, "#,##0"));
		return spinner;
	}

	private static int spinnerValue(JSpinner spinner) {
		return ((Number) spinner.getValue()).intValue();
	}

	private JPanel fieldRow(String text, JSpinner spinner, IntSupplier defaultValue) {
		IconLabelButton reset = new IconLabelButton(RESET_ICON, RESET_HOVER_ICON,
			() -> applyImmediately(() -> spinner.setValue(defaultValue.getAsInt())), "Reset " + text);
		reset.setBorder(new EmptyBorder(0, 4, 0, 2));

		JPanel panel = labelled(text, spinner);
		panel.add(reset, BorderLayout.EAST);
		return panel;
	}

	private void applyToSpinners(CustomRotation rotation) {
		zoomSpinner.setValue(rotation.zoom2d);
		panXSpinner.setValue(rotation.offsetX);
		panYSpinner.setValue(rotation.offsetY);
		angleXSpinner.setValue(rotation.xan2d);
		angleYSpinner.setValue(rotation.yan2d);
		angleZSpinner.setValue(rotation.zan2d);
		resizeXSpinner.setValue(rotation.resizeX);
		resizeYSpinner.setValue(rotation.resizeY);
		resizeZSpinner.setValue(rotation.resizeZ);
	}

	private CustomRotation currentRotation() {
		return new CustomRotation(spinnerValue(angleXSpinner), spinnerValue(angleYSpinner), spinnerValue(angleZSpinner),
			spinnerValue(zoomSpinner), spinnerValue(panXSpinner), spinnerValue(panYSpinner),
			spinnerValue(resizeXSpinner), spinnerValue(resizeYSpinner), spinnerValue(resizeZSpinner));
	}

	/**
	 * A spinner changed, from typing, a drag or the wheel. The preview re-renders right away;
	 * the commit is throttled separately, being the expensive half.
	 */
	private void onValueChanged() {
		showWhatIsCopied();
		schedulePreview();
		scheduleCommit();
	}

	/** Commits a change immediately, bypassing the throttle - for buttons, which shouldn't lag. */
	private void applyImmediately(Runnable change) {
		change.run();
		commitThrottle.stop();
		commit();
	}

	private void scheduleCommit() {
		// start() on a running Timer is a no-op, not a restart, which is what makes this a
		// throttle rather than a debounce
		if (!commitThrottle.isRunning())
			commitThrottle.start();
	}

	private void commit() {
		CustomRotation rotation = currentRotation();
		Set<Integer> targets = storage.groupMembers(session.itemId);
		// Having no override at all is what being at the defaults means: the icon goes back to
		// being fitted to the game's own, rather than placed from these values
		storage.putAll(targets, rotation.equals(session.defaults) ? null : rotation);
		host.iconsChanged(targets);
	}

	private void fillCopyFrom() {
		List<String> presetNames = storage.presetNames();
		boolean anything = !presetNames.isEmpty() || !session.savedItems.isEmpty();
		copyFromBox.removeAllItems();
		copyFromBox.addItem(new Preset(anything ? "Pick a preset or item..." : "Nothing saved yet", null));
		for (String name : presetNames)
			copyFromBox.addItem(new Preset(name, storage.preset(name)));
		for (Preset saved : session.savedItems)
			copyFromBox.addItem(saved);
		copyFromBox.setEnabled(anything);
		showWhatIsCopied();
	}

	private void showWhatIsCopied() {
		CustomRotation current = currentRotation();
		Preset target = copyFromBox.getItemAt(0);
		for (int i = 1; i < copyFromBox.getItemCount(); i++) {
			Preset entry = copyFromBox.getItemAt(i);
			if (current.equals(entry.rotation)) {
				target = entry;
				break;
			}
		}
		if (target == copyFromBox.getSelectedItem())
			return;

		settingCopyFrom = true;
		copyFromBox.setSelectedItem(target);
		settingCopyFrom = false;
	}

	private void saveAsPreset() {
		String name = JOptionPane.showInputDialog(this, "Name for this preset", session.itemName);
		if (name == null)
			return;
		name = name.trim();
		if (name.isEmpty())
			return;
		if (storage.preset(name) != null
			&& JOptionPane.showConfirmDialog(this, "Replace the preset named \"" + name + "\"?",
			"Preset exists", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
			return;

		storage.putPreset(name, currentRotation());
		fillCopyFrom();
	}

	private void deletePreset() {
		List<String> names = storage.presetNames();
		if (names.isEmpty()) {
			JOptionPane.showMessageDialog(this, "You haven't saved any presets yet.");
			return;
		}
		Object picked = JOptionPane.showInputDialog(this, "Preset to delete", "Delete preset",
			JOptionPane.QUESTION_MESSAGE, null, names.toArray(), names.get(0));
		if (picked == null)
			return;
		storage.removePreset(picked.toString());
		fillCopyFrom();
	}

	private void applyToSimilarItems() {
		CustomRotation rotation = currentRotation();
		host.findSimilarItems(session.itemId, found -> {
			List<NamedItem> others = new ArrayList<>();
			for (NamedItem item : found.items)
				if (item.id != session.itemId)
					others.add(item);
			if (others.isEmpty()) {
				JOptionPane.showMessageDialog(this, "Nothing else reads as a version of this item.");
				return;
			}

			List<NamedItem> picked = chooseItems("Apply to similar items",
				"<html>Ticked are the game's own versions of this item.<br>"
					+ "The rest just read as the same kind of thing.</html>", others, found.variants);
			if (picked.isEmpty())
				return;

			Set<Integer> ids = new LinkedHashSet<>();
			for (NamedItem item : picked)
				ids.add(item.id);
			storage.putAll(ids, rotation.equals(session.defaults) ? null : rotation);
			host.iconsChanged(ids);

			String group = storage.groupOf(session.itemId);
			if (group != null)
				for (int id : ids)
					storage.addToGroup(group, id);
			onGroupsChanged.run();

			JOptionPane.showMessageDialog(this, "Applied to " + ids.size() + (ids.size() == 1 ? " item" : " items")
				+ (group == null ? "." : ", and added them to \"" + group + "\"."));
		});
	}

	private List<NamedItem> chooseItems(String title, String message, List<NamedItem> items, Set<Integer> ticked) {
		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		List<JCheckBox> boxes = new ArrayList<>();
		for (NamedItem item : items) {
			JCheckBox box = new JCheckBox(item.toString(), ticked.contains(item.id));
			boxes.add(box);
			list.add(box);
		}

		JScrollPane scroll = new JScrollPane(list);
		scroll.setPreferredSize(new Dimension(280, Math.min(360, 40 + items.size() * 24)));
		JPanel content = new JPanel(new BorderLayout(0, 6));
		content.add(new JLabel(message), BorderLayout.NORTH);
		content.add(scroll, BorderLayout.CENTER);

		int answer = JOptionPane.showConfirmDialog(this, content, title,
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		List<NamedItem> picked = new ArrayList<>();
		if (answer != JOptionPane.OK_OPTION)
			return picked;
		for (int i = 0; i < items.size(); i++)
			if (boxes.get(i).isSelected())
				picked.add(items.get(i));
		return picked;
	}

	private void schedulePreview() {
		CustomRotation rotation = currentRotation();
		boolean atDefaults = rotation.equals(session.defaults);
		long generation = ++previewGeneration;
		previewExecutor.execute(() -> {
			// Skip stale work rather than letting a burst of changes queue up behind rendering
			if (generation != previewGeneration)
				return;
			ItemIconRasterizer rasterizer = new ItemIconRasterizer(session.model, rotation.xan2d, rotation.yan2d,
				rotation.zan2d, rotation.resizeX, rotation.resizeY, rotation.resizeZ, session.supersample);
			// At the defaults the icon itself is fitted to the game's own, so the preview is too,
			// or the two would disagree on what the default even looks like
			boolean fitted = atDefaults && session.referencePixels != null
				&& rasterizer.fitToReferenceSilhouette(session.referencePixels, session.palette);
			if (!fitted)
				rasterizer.placeExplicitly(rotation.zoom2d, rotation.offsetX, rotation.offsetY);
			// Unsharpened: this preview is magnified, not stretched by the interface
			int[] pixels = rasterizer.render(0, false, false, session.palette);
			SwingUtilities.invokeLater(() -> {
				if (generation != previewGeneration)
					return;
				previewLabel.setIcon(new ImageIcon(scale(toImage(pixels))));
			});
		});
	}

	private static BufferedImage toImage(int[] pixels) {
		BufferedImage image = new BufferedImage(ICON_WIDTH, ICON_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, ICON_WIDTH, ICON_HEIGHT, pixels, 0, ICON_WIDTH);
		return image;
	}

	private Image scale(BufferedImage image) {
		return image.getScaledInstance(ICON_WIDTH * previewScale, ICON_HEIGHT * previewScale, Image.SCALE_FAST);
	}
}
