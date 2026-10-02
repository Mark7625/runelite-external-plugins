package io.mark.hditemicons.hd;

import io.mark.hditemicons.CustomRotation;
import io.mark.hditemicons.CustomRotationStorage;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import net.runelite.client.util.ImageUtil;

import static io.mark.hditemicons.hd.ItemIconRasterizer.ICON_HEIGHT;
import static io.mark.hditemicons.hd.ItemIconRasterizer.ICON_WIDTH;

/**
 * A live property panel for an item's icon camera placement. Every change applies and saves
 * immediately, so there's no Save step - only per-field resets and a reset-everything.
 */
final class RotationEditorDialog extends JFrame {
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

	private static final ImageIcon RESET_ICON =
		new ImageIcon(ImageUtil.loadImageResource(RotationEditorDialog.class, "reset.png"));

	private static final int PREVIEW_SCALE = 6;
	private static final int COMMIT_THROTTLE_MS = 100;
	private static final double ANGLE_PER_DRAG_PIXEL = 4;
	private static final double RESIZE_PER_DRAG_PIXEL = 1;
	private static final double ZOOM_PER_WHEEL_NOTCH = 50;
	private static final int SPINNER_BOUND = 200_000;

	private final int itemId;
	private final ItemIconRasterizer.IconModel model;
	@Nullable
	private final int[] referencePixels;
	private final int[] palette;
	private final int supersample;
	private final CustomRotationStorage rotationStorage;
	private final IntConsumer onChanged;
	private final CustomRotation defaults;

	private final ExecutorService previewExecutor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "hd-item-icon-rotation-preview");
		thread.setDaemon(true);
		return thread;
	});
	private final Timer commitThrottle;
	private long previewGeneration;

	private Mode mode = Mode.ROTATE;
	private int dragX, dragY;

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

	RotationEditorDialog(String itemName, int itemId, CustomRotation initial, CustomRotation defaults,
						List<Preset> presets, ItemIconRasterizer.IconModel model, @Nullable int[] referencePixels,
						int[] palette, int supersample,
						CustomRotationStorage rotationStorage, IntConsumer onChanged) {
		super("Edit icon");
		this.itemId = itemId;
		this.model = model;
		this.referencePixels = referencePixels;
		this.palette = palette;
		this.supersample = supersample;
		this.rotationStorage = rotationStorage;
		this.onChanged = onChanged;
		this.defaults = defaults;

		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setResizable(false);
		setAlwaysOnTop(true);

		JLabel subtitle = new JLabel(itemName + " (" + itemId + ")", SwingConstants.CENTER);

		previewLabel = new JLabel();
		previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
		previewLabel.setOpaque(true);
		previewLabel.setBackground(Color.DARK_GRAY);
		previewLabel.setPreferredSize(new Dimension(ICON_WIDTH * PREVIEW_SCALE, ICON_HEIGHT * PREVIEW_SCALE));

		JRadioButton rotateButton = new JRadioButton("Rotate", true);
		JRadioButton panButton = new JRadioButton("Pan");
		JRadioButton scaleButton = new JRadioButton("Scale");
		ButtonGroup modeGroup = new ButtonGroup();
		modeGroup.add(rotateButton);
		modeGroup.add(panButton);
		modeGroup.add(scaleButton);
		rotateButton.addActionListener(e -> setMode(Mode.ROTATE));
		panButton.addActionListener(e -> setMode(Mode.PAN));
		scaleButton.addActionListener(e -> setMode(Mode.SCALE));
		JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
		modePanel.add(rotateButton);
		modePanel.add(panButton);
		modePanel.add(scaleButton);

		hintLabel = new JLabel("", SwingConstants.CENTER);
		hintLabel.setFont(hintLabel.getFont().deriveFont(hintLabel.getFont().getSize2D() - 1f));
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

		JPanel fields = new JPanel(new GridLayout(9, 1, 0, 4));
		fields.add(fieldRow("Zoom", zoomSpinner, () -> defaults.zoom2d));
		fields.add(fieldRow("Pan X", panXSpinner, () -> defaults.offsetX));
		fields.add(fieldRow("Pan Y", panYSpinner, () -> defaults.offsetY));
		fields.add(fieldRow("Angle X", angleXSpinner, () -> defaults.xan2d));
		fields.add(fieldRow("Angle Y", angleYSpinner, () -> defaults.yan2d));
		fields.add(fieldRow("Angle Z", angleZSpinner, () -> defaults.zan2d));
		fields.add(fieldRow("Resize X", resizeXSpinner, () -> defaults.resizeX));
		fields.add(fieldRow("Resize Y", resizeYSpinner, () -> defaults.resizeY));
		fields.add(fieldRow("Resize Z", resizeZSpinner, () -> defaults.resizeZ));

		Preset prompt = new Preset(presets.isEmpty() ? "No other saved icons" : "Pick a saved icon...", null);
		JComboBox<Preset> presetBox = new JComboBox<>();
		presetBox.addItem(prompt);
		for (Preset preset : presets)
			presetBox.addItem(preset);
		presetBox.setEnabled(!presets.isEmpty());
		presetBox.addActionListener(e -> {
			Preset picked = (Preset) presetBox.getSelectedItem();
			if (picked == null || picked.rotation == null)
				return;
			applyImmediately(() -> applyToSpinners(picked.rotation));
			// Back to the prompt, since this copies values rather than staying set to one
			presetBox.setSelectedItem(prompt);
		});

		JPanel presetRow = new JPanel(new BorderLayout(4, 0));
		JLabel presetLabel = new JLabel("Copy from");
		presetLabel.setPreferredSize(new Dimension(60, presetLabel.getPreferredSize().height));
		presetRow.add(presetLabel, BorderLayout.WEST);
		presetRow.add(presetBox, BorderLayout.CENTER);

		JButton resetAll = new JButton("Reset to default");
		resetAll.addActionListener(e -> applyImmediately(() -> applyToSpinners(defaults)));

		JPanel bottom = new JPanel(new BorderLayout(0, 8));
		bottom.add(presetRow, BorderLayout.NORTH);
		bottom.add(resetAll, BorderLayout.SOUTH);

		JPanel top = new JPanel(new BorderLayout(4, 4));
		top.add(subtitle, BorderLayout.NORTH);
		top.add(previewLabel, BorderLayout.CENTER);
		JPanel modeAndHint = new JPanel(new BorderLayout());
		modeAndHint.add(modePanel, BorderLayout.NORTH);
		modeAndHint.add(hintLabel, BorderLayout.SOUTH);
		top.add(modeAndHint, BorderLayout.SOUTH);

		JPanel content = new JPanel(new BorderLayout(8, 8));
		content.setBorder(new EmptyBorder(10, 10, 10, 10));
		content.add(top, BorderLayout.NORTH);
		content.add(fields, BorderLayout.CENTER);
		content.add(bottom, BorderLayout.SOUTH);
		setContentPane(content);

		pack();
		setLocationRelativeTo(null);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				commitThrottle.stop();
				previewExecutor.shutdownNow();
			}
		});

		commit();
		schedulePreview();
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
		return spinnerValue(zoomSpinner) / (double) (PREVIEW_SCALE * ItemIconRasterizer.PROJECTION_SCALE);
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
		JButton reset = new JButton(RESET_ICON);
		reset.setMargin(new Insets(0, 4, 0, 4));
		reset.setToolTipText("Reset " + text);
		reset.addActionListener(e -> applyImmediately(() -> spinner.setValue(defaultValue.getAsInt())));

		JPanel panel = new JPanel(new BorderLayout(4, 0));
		JLabel label = new JLabel(text);
		label.setPreferredSize(new Dimension(60, label.getPreferredSize().height));
		panel.add(label, BorderLayout.WEST);
		panel.add(spinner, BorderLayout.CENTER);
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
		// Having no override at all is what being at the defaults means: the icon goes back to
		// being fitted to the game's own, rather than placed from these values
		if (rotation.equals(defaults))
			rotationStorage.remove(itemId);
		else
			rotationStorage.put(itemId, rotation);
		onChanged.accept(itemId);
	}

	private void schedulePreview() {
		CustomRotation rotation = currentRotation();
		boolean atDefaults = rotation.equals(defaults);
		long generation = ++previewGeneration;
		previewExecutor.execute(() -> {
			// Skip stale work rather than letting a burst of changes queue up behind rendering
			if (generation != previewGeneration)
				return;
			ItemIconRasterizer rasterizer = new ItemIconRasterizer(model, rotation.xan2d, rotation.yan2d, rotation.zan2d,
				rotation.resizeX, rotation.resizeY, rotation.resizeZ, supersample);
			// At the defaults the icon itself is fitted to the game's own, so the preview is too,
			// or the two would disagree on what the default even looks like
			boolean fitted = atDefaults && referencePixels != null
				&& rasterizer.fitToReferenceSilhouette(referencePixels, palette);
			if (!fitted)
				rasterizer.placeExplicitly(rotation.zoom2d, rotation.offsetX, rotation.offsetY);
			// Unsharpened: this preview is magnified, not stretched by the interface
			int[] pixels = rasterizer.render(0, false, false, palette);
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

	private static Image scale(BufferedImage image) {
		return image.getScaledInstance(ICON_WIDTH * PREVIEW_SCALE, ICON_HEIGHT * PREVIEW_SCALE, Image.SCALE_FAST);
	}
}
