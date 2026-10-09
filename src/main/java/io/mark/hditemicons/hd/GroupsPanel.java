package io.mark.hditemicons.hd;

import io.mark.hditemicons.CustomRotationStorage;
import io.mark.hditemicons.NamedItem;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.FlatTextField;
import net.runelite.client.ui.components.PluginErrorPanel;

final class GroupsPanel extends JPanel {
	private final CustomRotationStorage storage;
	private final Supplier<NamedItem> editing;
	private final Runnable onMembershipChanged;

	private final JPanel list = new JPanel();
	private final PluginErrorPanel noGroups = new PluginErrorPanel();
	@Nullable
	private String renaming;

	GroupsPanel(CustomRotationStorage storage, Supplier<NamedItem> editing, Runnable onMembershipChanged) {
		this.storage = storage;
		this.editing = editing;
		this.onMembershipChanged = onMembershipChanged;

		JLabel title = new JLabel("Groups");
		title.setForeground(Color.WHITE);
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.setBorder(new EmptyBorder(1, 0, 8, 0));
		header.add(title, BorderLayout.WEST);
		header.add(new IconLabelButton(EntryPanel.ADD_ICON, EntryPanel.ADD_HOVER_ICON, this::newGroup,
			"New group"), BorderLayout.EAST);

		noGroups.setContent("No groups yet",
			"Make one with +, then put items in it to give them all the same icon placement.");

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);

		setLayout(new BorderLayout());
		setOpaque(false);
		add(header, BorderLayout.NORTH);
		add(list, BorderLayout.CENTER);
		rebuild();
	}

	void rebuild() {
		List<String> names = storage.groupNames();
		list.removeAll();
		if (names.isEmpty()) {
			list.add(noGroups);
		} else {
			for (String name : names) {
				list.add(groupEntry(name));
				list.add(Box.createVerticalStrut(10));
			}
		}
		revalidate();
		repaint();
	}

	private JPanel groupEntry(String name) {
		FlatTextField nameField = new FlatTextField();
		nameField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		nameField.setEditable(false);
		nameField.setText(name);
		nameField.getTextField().setForeground(Color.WHITE);
		nameField.getTextField().setBorder(new EmptyBorder(0, 6, 0, 0));
		nameField.getTextField().setToolTipText("Click to rename this group");
		nameField.setPreferredSize(new Dimension(0, 24));
		attachRename(nameField, name);

		JPanel nameWrapper = new JPanel(new BorderLayout());
		nameWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		nameWrapper.setBorder(EntryPanel.NAME_BOTTOM_BORDER);
		nameWrapper.add(nameField, BorderLayout.CENTER);

		EntryPanel header = new EntryPanel(nameWrapper)
			.withButton(EntryPanel.EDIT_ICON, EntryPanel.EDIT_HOVER_ICON, () -> startRename(nameField), "Edit name")
			.withButton(EntryPanel.DELETE_ICON, EntryPanel.DELETE_HOVER_ICON, () -> deleteGroup(name),
				"Delete this group. The icons it set stay as they are.");

		JPanel entry = new JPanel(new BorderLayout());
		entry.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		entry.add(header, BorderLayout.NORTH);
		entry.add(body(name), BorderLayout.CENTER);

		if (name.equals(renaming)) {
			renaming = null;
			startRename(nameField);
		}
		return entry;
	}

	private JPanel body(String name) {
		int members = storage.membersOf(name).size();
		JLabel count = new JLabel(members + (members == 1 ? " item" : " items"));
		count.setFont(FontManager.getRunescapeSmallFont());
		count.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		body.setBorder(new EmptyBorder(8, 6, 8, 6));
		body.add(row(count));

		NamedItem item = editing.get();
		if (item == null)
			return body;

		boolean isMember = storage.membersOf(name).contains(item.id);
		JButton button = new JButton((isMember ? "Remove " : "Add ") + item.name);
		button.setToolTipText(isMember
			? "Stop editing " + item.name + " with the rest of this group"
			: "Put " + item.name + " in this group and give the rest its placement");
		button.addActionListener(e -> {
			if (isMember)
				storage.leaveGroup(item.id);
			else
				storage.addToGroup(name, item.id);
			if (!isMember)
				onMembershipChanged.run();
			rebuild();
		});
		body.add(Box.createVerticalStrut(6));
		body.add(row(button));
		return body;
	}

	private static JPanel row(JComponent content) {
		JPanel row = new JPanel(new BorderLayout()) {
			@Override
			public Dimension getMaximumSize() {
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		row.setOpaque(false);
		row.add(content, BorderLayout.CENTER);
		return row;
	}

	private void newGroup() {
		String name = storage.unusedGroupName("Group");
		storage.createGroup(name);
		renaming = name;
		rebuild();
	}

	private void deleteGroup(String name) {
		if (JOptionPane.showConfirmDialog(this, "Delete the group \"" + name + "\"?"
				+ " The icons it set stay as they are.", "Delete group", JOptionPane.OK_CANCEL_OPTION)
			!= JOptionPane.OK_OPTION)
			return;
		storage.deleteGroup(name);
		rebuild();
	}

	private void attachRename(FlatTextField field, String name) {
		field.getTextField().addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (!field.getTextField().isEditable())
					startRename(field);
			}
		});
		field.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER)
					finishRename(field, name, true);
				else if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
					finishRename(field, name, false);
			}
		});
		field.getTextField().addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) {
				finishRename(field, name, true);
			}
		});
	}

	private static void startRename(FlatTextField field) {
		field.setEditable(true);
		field.getTextField().requestFocus();
		field.getTextField().selectAll();
	}

	private void finishRename(FlatTextField field, String name, boolean keep) {
		if (!field.getTextField().isEditable())
			return;
		field.setEditable(false);

		String renamed = field.getText().trim();
		if (!keep || renamed.isEmpty() || renamed.equals(name) || storage.groupNames().contains(renamed)) {
			field.setText(name);
			return;
		}
		storage.renameGroup(name, renamed);
		rebuild();
	}
}
