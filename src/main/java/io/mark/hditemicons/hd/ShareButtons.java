package io.mark.hditemicons.hd;

import io.mark.hditemicons.CustomRotation;
import io.mark.hditemicons.CustomRotationStorage;
import io.mark.hditemicons.IconEditorData;
import io.mark.hditemicons.IconEditorHost;
import io.mark.hditemicons.NamedItem;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.Filepath;

final class ShareButtons extends JPanel {
	private static final String EXPORT_FILE_NAME = "hq-item-icons.json";
	private static final int LIST_WIDTH = 340;
	private static final int LIST_HEIGHT = 420;

	private final CustomRotationStorage storage;
	private final Supplier<IconEditorHost> host;
	private final Runnable afterImport;

	ShareButtons(CustomRotationStorage storage, Supplier<IconEditorHost> host, Runnable afterImport) {
		this.storage = storage;
		this.host = host;
		this.afterImport = afterImport;

		JButton importButton = new JButton("Import");
		importButton.setToolTipText("Merge icon settings shared from a file into yours");
		importButton.addActionListener(e -> importFromFile());
		JButton exportButton = new JButton("Export");
		exportButton.setToolTipText("Write every saved icon, group and preset to a file");
		exportButton.addActionListener(e -> exportToFile());

		setLayout(new GridLayout(1, 2, 4, 0));
		setOpaque(false);
		setBorder(new EmptyBorder(10, 0, 0, 0));
		add(importButton);
		add(exportButton);
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	private void exportToFile() {
		List<Filepath> chosen = new Filepath.Chooser()
			.setIsSave()
			.setAcceptsFiles()
			.setDialogTitle("Export icon settings")
			.addExtensionFilter("JSON files", "json")
			.setDefaultExtension("json")
			.setFileName(EXPORT_FILE_NAME)
			.showDialog(this);
		if (chosen == null || chosen.isEmpty())
			return;

		Filepath target = chosen.get(0);
		String json = storage.exportToJson();
		storage.offDisk(() -> {
			String message;
			try {
				target.write(json);
				message = "Saved your icon settings to " + target.getFileName() + ".";
			} catch (IOException e) {
				message = "Couldn't write " + target.getFileName() + ": " + e.getMessage();
			}
			report(message);
		});
	}

	private void importFromFile() {
		List<Filepath> chosen = new Filepath.Chooser()
			.setIsOpen()
			.setAcceptsFiles()
			.setDialogTitle("Import icon settings")
			.addExtensionFilter("JSON files", "json")
			.showDialog(this);
		if (chosen == null || chosen.isEmpty())
			return;

		Filepath source = chosen.get(0);
		storage.offDisk(() -> {
			String json;
			try (InputStream in = source.openInputStream()) {
				json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				report("Couldn't read " + source.getFileName() + ": " + e.getMessage());
				return;
			}

			CustomRotationStorage.ImportPreview preview = storage.readImport(json);
			if (preview == null) {
				report(source.getFileName() + " isn't an icon settings file.");
				return;
			}
			if (preview.isEmpty()) {
				report(source.getFileName() + " has nothing saved in it.");
				return;
			}
			SwingUtilities.invokeLater(() -> nameItemsThenConfirm(preview));
		});
	}

	private void nameItemsThenConfirm(CustomRotationStorage.ImportPreview preview) {
		IconEditorHost currentHost = host.get();
		if (currentHost == null || preview.data.rotations == null || preview.data.rotations.isEmpty()) {
			confirmImport(preview, Map.of());
			return;
		}
		currentHost.itemIndex(index -> {
			Map<Integer, String> names = new HashMap<>();
			for (NamedItem item : index)
				if (preview.data.rotations.containsKey(item.id))
					names.put(item.id, item.name);
			confirmImport(preview, names);
		});
	}

	private void confirmImport(CustomRotationStorage.ImportPreview preview, Map<Integer, String> names) {
		Map<Integer, JCheckBox> icons = new LinkedHashMap<>();
		Map<String, JCheckBox> groups = new LinkedHashMap<>();
		Map<String, JCheckBox> presets = new LinkedHashMap<>();

		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		list.setBorder(new EmptyBorder(6, 8, 6, 8));

		if (preview.data.rotations != null && !preview.data.rotations.isEmpty()) {
			Map<String, Integer> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			for (Integer itemId : preview.data.rotations.keySet())
				byName.put(label(names.get(itemId), itemId), itemId);
			addSection(list, "Icons", byName.size());
			for (Map.Entry<String, Integer> entry : byName.entrySet())
				icons.put(entry.getValue(),
					addEntry(list, entry.getKey(), preview.rotationsReplaced.contains(entry.getValue())));
		}
		if (preview.data.groups != null && !preview.data.groups.isEmpty()) {
			addSection(list, "Groups", preview.data.groups.size());
			for (String name : sorted(preview.data.groups.keySet())) {
				int members = preview.data.groups.get(name).size();
				groups.put(name, addEntry(list, name + " (" + members + (members == 1 ? " item)" : " items)"),
					preview.groupsReplaced.contains(name)));
			}
		}
		if (preview.data.presets != null && !preview.data.presets.isEmpty()) {
			addSection(list, "Presets", preview.data.presets.size());
			for (String name : sorted(preview.data.presets.keySet()))
				presets.put(name, addEntry(list, name, preview.presetsReplaced.contains(name)));
		}

		List<JCheckBox> all = new ArrayList<>();
		all.addAll(icons.values());
		all.addAll(groups.values());
		all.addAll(presets.values());

		JScrollPane scroll = new JScrollPane(list,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setPreferredSize(new Dimension(LIST_WIDTH, Math.min(LIST_HEIGHT, 60 + all.size() * 24)));

		JButton tickAll = new JButton("Tick all");
		tickAll.addActionListener(e -> all.forEach(box -> box.setSelected(true)));
		JButton untickAll = new JButton("Untick all");
		untickAll.addActionListener(e -> all.forEach(box -> box.setSelected(false)));
		JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
		buttons.setOpaque(false);
		buttons.add(tickAll);
		buttons.add(untickAll);

		JPanel content = new JPanel(new BorderLayout(0, 6));
		content.add(new JLabel("Take from this file:"), BorderLayout.NORTH);
		content.add(scroll, BorderLayout.CENTER);
		content.add(buttons, BorderLayout.SOUTH);

		int answer = JOptionPane.showConfirmDialog(this, content, "Import icon settings",
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (answer != JOptionPane.OK_OPTION)
			return;

		IconEditorData chosen = pick(preview, icons, groups, presets);
		if (chosen == null)
			return;

		CustomRotationStorage.ImportCounts counts = storage.applyImport(chosen);
		afterImport.run();
		JOptionPane.showMessageDialog(this, "Imported " + counts.rotations + " icons, "
			+ counts.groups + " groups and " + counts.presets + " presets.");
	}

	@Nullable
	private static IconEditorData pick(CustomRotationStorage.ImportPreview preview, Map<Integer, JCheckBox> icons,
										Map<String, JCheckBox> groups, Map<String, JCheckBox> presets) {
		Map<Integer, CustomRotation> takenRotations = new LinkedHashMap<>();
		for (Map.Entry<Integer, JCheckBox> entry : icons.entrySet())
			if (entry.getValue().isSelected())
				takenRotations.put(entry.getKey(), preview.data.rotations.get(entry.getKey()));

		Map<String, Set<Integer>> takenGroups = new LinkedHashMap<>();
		for (Map.Entry<String, JCheckBox> entry : groups.entrySet())
			if (entry.getValue().isSelected())
				takenGroups.put(entry.getKey(), preview.data.groups.get(entry.getKey()));

		Map<String, CustomRotation> takenPresets = new LinkedHashMap<>();
		for (Map.Entry<String, JCheckBox> entry : presets.entrySet())
			if (entry.getValue().isSelected())
				takenPresets.put(entry.getKey(), preview.data.presets.get(entry.getKey()));

		if (takenRotations.isEmpty() && takenGroups.isEmpty() && takenPresets.isEmpty())
			return null;
		return new IconEditorData(takenRotations, takenGroups, takenPresets);
	}

	private static List<String> sorted(Set<String> names) {
		List<String> list = new ArrayList<>(names);
		list.sort(String.CASE_INSENSITIVE_ORDER);
		return list;
	}

	private static String label(@Nullable String name, int itemId) {
		return (name == null ? "Item" : name) + " (" + itemId + ")";
	}

	private static void addSection(JPanel list, String title, int count) {
		if (list.getComponentCount() > 0)
			list.add(Box.createVerticalStrut(8));
		JLabel header = new JLabel(title + " (" + count + ")");
		header.setFont(FontManager.getRunescapeSmallFont());
		header.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		list.add(header);
	}

	private static JCheckBox addEntry(JPanel list, String label, boolean replacesExisting) {
		JCheckBox box = new JCheckBox(replacesExisting ? label + " - replaces yours" : label, true);
		box.setAlignmentX(Component.LEFT_ALIGNMENT);
		box.setOpaque(false);
		if (replacesExisting)
			box.setForeground(ColorScheme.BRAND_ORANGE);
		list.add(box);
		return box;
	}

	private void report(String message) {
		SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, message));
	}

	static JPanel below(JPanel content, ShareButtons share) {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setOpaque(false);
		panel.add(content, BorderLayout.CENTER);
		panel.add(share, BorderLayout.SOUTH);
		return panel;
	}
}
