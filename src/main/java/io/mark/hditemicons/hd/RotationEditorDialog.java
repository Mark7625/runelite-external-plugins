package io.mark.hditemicons.hd;

import io.mark.hditemicons.CustomRotationStorage;
import io.mark.hditemicons.IconEditorHost;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

final class RotationEditorDialog extends JFrame {
	private static final int PREVIEW_SCALE = 6;

	private final RotationEditorPanel editor;

	RotationEditorDialog(RotationEditorPanel.Session session, CustomRotationStorage storage, IconEditorHost host) {
		super("Edit icon");
		editor = new RotationEditorPanel(session, storage, host, PREVIEW_SCALE);
		GroupsPanel groups = new GroupsPanel(storage, editor::editedItem, editor::applyToGroup);
		editor.setOnGroupsChanged(groups::rebuild);

		Runnable afterImport = () -> {
			host.allIconsChanged();
			editor.reloadFromStorage();
			groups.rebuild();
		};

		JPanel editorContent = new JPanel(new BorderLayout());
		editorContent.setBackground(ColorScheme.DARK_GRAY_COLOR);
		editorContent.add(editor, BorderLayout.NORTH);
		JPanel groupsContent = new JPanel(new BorderLayout());
		groupsContent.setBackground(ColorScheme.DARK_GRAY_COLOR);
		groupsContent.add(groups, BorderLayout.NORTH);

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);
		MaterialTabGroup tabs = new MaterialTabGroup(display);
		MaterialTab iconTab = new MaterialTab("Icon", tabs,
			ShareButtons.below(editorContent, new ShareButtons(storage, () -> host, afterImport)));
		MaterialTab groupsTab = new MaterialTab("Groups", tabs,
			ShareButtons.below(groupsContent, new ShareButtons(storage, () -> host, afterImport)));
		tabs.addTab(iconTab);
		tabs.addTab(groupsTab);
		tabs.setBorder(new EmptyBorder(0, 0, 8, 0));
		iconTab.setOnSelectEvent(this::repack);
		groupsTab.setOnSelectEvent(this::repack);
		tabs.select(iconTab);

		JPanel content = new JPanel(new BorderLayout());
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(new EmptyBorder(10, 10, 10, 10));
		content.add(tabs, BorderLayout.NORTH);
		content.add(display, BorderLayout.CENTER);

		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setResizable(false);
		setAlwaysOnTop(true);
		setContentPane(content);
		pack();
		setLocationRelativeTo(null);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				editor.dispose();
			}
		});
	}

	private boolean repack() {
		SwingUtilities.invokeLater(this::pack);
		return true;
	}
}
