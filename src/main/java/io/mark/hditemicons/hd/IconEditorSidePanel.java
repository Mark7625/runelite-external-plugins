package io.mark.hditemicons.hd;

import io.mark.hditemicons.CustomRotationStorage;
import io.mark.hditemicons.IconEditorHost;
import io.mark.hditemicons.NamedItem;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.ImageUtil;

@Singleton
public class IconEditorSidePanel {
	private static final int MAX_RESULTS = 25;
	private static final int ICON_WIDTH = 36;
	private static final int ICON_HEIGHT = 32;

	private final ClientToolbar clientToolbar;
	private final ItemManager itemManager;
	private final CustomRotationStorage storage;

	private volatile boolean attached;
	@Nullable
	private volatile IconEditorHost host;
	@Nullable
	private RotationEditorPanel editor;
	@Nullable
	private GroupsPanel groupsPanel;
	@Nullable
	private Holder holder;
	@Nullable
	private NavigationButton navigationButton;

	@Inject
	public IconEditorSidePanel(ClientToolbar clientToolbar, ItemManager itemManager, CustomRotationStorage storage) {
		this.clientToolbar = clientToolbar;
		this.itemManager = itemManager;
		this.storage = storage;
	}

	public void setHost(@Nullable IconEditorHost host) {
		this.host = host;
		if (host == null)
			SwingUtilities.invokeLater(() -> {
				if (holder != null)
					holder.forget();
			});
	}

	public void attach() {
		if (attached)
			return;
		attached = true;
		SwingUtilities.invokeLater(() -> {
			build();
			clientToolbar.addNavigation(navigationButton);
		});
	}

	public void detach() {
		if (!attached)
			return;
		attached = false;
		SwingUtilities.invokeLater(() -> {
			if (navigationButton == null)
				return;
			clientToolbar.removeNavigation(navigationButton);
			holder.forget();
		});
	}

	private void build() {
		if (holder != null)
			return;

		groupsPanel = new GroupsPanel(storage,
			() -> editor == null ? null : editor.editedItem(),
			() -> {
				if (editor != null)
					editor.applyToGroup();
			});
		holder = new Holder();
		navigationButton = NavigationButton.builder()
			.tooltip("HQ Item Icons")
			.icon(ImageUtil.loadImageResource(IconEditorSidePanel.class, "panel_icon.png"))
			.priority(7)
			.panel(holder)
			.build();
	}

	public boolean isAttached() {
		return attached;
	}

	private ShareButtons share() {
		return new ShareButtons(storage, () -> host, this::afterImport);
	}

	private void afterImport() {
		IconEditorHost currentHost = host;
		if (currentHost != null)
			currentHost.allIconsChanged();
		if (editor != null)
			editor.reloadFromStorage();
		if (groupsPanel != null)
			groupsPanel.rebuild();
	}

	void open(RotationEditorPanel next) {
		build();
		if (editor != null)
			editor.dispose();
		editor = next;
		next.setOnGroupsChanged(groupsPanel::rebuild);
		groupsPanel.rebuild();
		holder.show(next);
		clientToolbar.openPanel(navigationButton);
	}

	private final class Holder extends PluginPanel {
		private final JLabel title = new JLabel("Icon editor");
		private final IconTextField search = new IconTextField();
		private final PluginErrorPanel nothingToEdit = new PluginErrorPanel();
		private final JPanel results = new JPanel();
		private final JPanel content = new JPanel(new BorderLayout());
		private final JPanel display = new JPanel(new BorderLayout());
		private final MaterialTabGroup tabs = new MaterialTabGroup(display);
		private final MaterialTab iconTab;

		@Nullable
		private JComponent editorPanel;
		@Nullable
		private List<NamedItem> index;
		private boolean loadingIndex;

		Holder() {
			setLayout(new BorderLayout());
			setBorder(new EmptyBorder(10, 10, 10, 10));

			title.setForeground(Color.WHITE);

			search.setIcon(IconTextField.Icon.SEARCH);
			search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			search.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
			search.setPreferredSize(new Dimension(0, 24));
			search.setToolTipText("Search any item by name, whether or not you have one");
			search.getDocument().addDocumentListener(new DocumentListener() {
				@Override
				public void insertUpdate(DocumentEvent e) {
					searchChanged();
				}

				@Override
				public void removeUpdate(DocumentEvent e) {
					searchChanged();
				}

				@Override
				public void changedUpdate(DocumentEvent e) {
					searchChanged();
				}
			});
			search.addClearListener(this::searchChanged);

			results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
			results.setOpaque(false);
			content.setOpaque(false);
			nothingToEdit.setContent("Nothing to edit yet",
				"Search for an item above, or hold the edit hotkey and pick \"Edit icon rotation\""
					+ " on one in game.");

			JPanel header = new JPanel(new BorderLayout(0, 8));
			header.setOpaque(false);
			header.setBorder(new EmptyBorder(1, 0, 10, 0));
			header.add(title, BorderLayout.NORTH);
			header.add(search, BorderLayout.SOUTH);

			JPanel iconTabContent = new JPanel(new BorderLayout());
			iconTabContent.setOpaque(false);
			iconTabContent.add(header, BorderLayout.NORTH);
			iconTabContent.add(content, BorderLayout.CENTER);

			JPanel groupsTabContent = new JPanel(new BorderLayout());
			groupsTabContent.setOpaque(false);
			groupsTabContent.add(groupsPanel, BorderLayout.NORTH);

			iconTab = new MaterialTab("Icon", tabs, ShareButtons.below(iconTabContent, share()));
			tabs.addTab(iconTab);
			tabs.addTab(new MaterialTab("Groups", tabs, ShareButtons.below(groupsTabContent, share())));
			tabs.setBorder(new EmptyBorder(0, 0, 8, 0));
			tabs.select(iconTab);

			add(tabs, BorderLayout.NORTH);
			add(display, BorderLayout.CENTER);
			showEditorOrPrompt();
		}

		void show(JComponent next) {
			editorPanel = next;
			search.setText("");
			tabs.select(iconTab);
			showEditorOrPrompt();
		}

		void forget() {
			if (editor != null)
				editor.dispose();
			editor = null;
			editorPanel = null;
			index = null;
			search.setText("");
			showEditorOrPrompt();
		}

		private void searchChanged() {
			if (search.getText().trim().isEmpty()) {
				showEditorOrPrompt();
				return;
			}
			if (index == null) {
				loadIndex();
				return;
			}
			showResults();
		}

		private void loadIndex() {
			IconEditorHost currentHost = host;
			if (currentHost == null) {
				setContent(message("Turn the plugin on to search for items."));
				return;
			}
			setContent(message("Loading items..."));
			if (loadingIndex)
				return;
			loadingIndex = true;
			currentHost.itemIndex(loaded -> {
				loadingIndex = false;
				if (loaded.isEmpty()) {
					setContent(message("Item names aren't loaded yet."));
					return;
				}
				index = loaded;
				searchChanged();
			});
		}

		private void showEditorOrPrompt() {
			title.setVisible(editorPanel != null);
			title.setText("Icon editor");
			setContent(editorPanel != null ? editorPanel : nothingToEdit);
		}

		private void showResults() {
			List<NamedItem> matches = matches(search.getText().trim());
			results.removeAll();
			if (matches.isEmpty()) {
				results.add(message("No item matches that name."));
			} else {
				for (NamedItem item : matches) {
					results.add(resultRow(item));
					results.add(Box.createVerticalStrut(3));
				}
			}
			title.setVisible(false);
			tabs.select(iconTab);
			setContent(results);
		}

		private List<NamedItem> matches(String query) {
			String lower = query.toLowerCase(Locale.ROOT);
			List<NamedItem> found = new ArrayList<>();
			List<NamedItem> all = index;
			if (all == null)
				return found;
			for (NamedItem item : all)
				if (item.nameContains(lower))
					found.add(item);

			found.sort(Comparator
				.comparing((NamedItem item) -> !item.nameStartsWith(lower))
				.thenComparing(item -> item.name, String.CASE_INSENSITIVE_ORDER)
				.thenComparingInt(item -> item.id));
			return found.size() > MAX_RESULTS ? found.subList(0, MAX_RESULTS) : found;
		}

		private JPanel resultRow(NamedItem item) {
			JLabel icon = new JLabel();
			icon.setPreferredSize(new Dimension(ICON_WIDTH, ICON_HEIGHT));
			itemManager.getImage(item.id).addTo(icon);

			JLabel name = new JLabel(item.name);
			name.setForeground(Color.WHITE);
			JLabel id = new JLabel("Item " + item.id);
			id.setFont(FontManager.getRunescapeSmallFont());
			id.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			JPanel text = new JPanel(new BorderLayout());
			text.setOpaque(false);
			text.add(name, BorderLayout.NORTH);
			text.add(id, BorderLayout.SOUTH);

			JPanel row = new JPanel(new BorderLayout(6, 0)) {
				@Override
				public Dimension getMaximumSize() {
					return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
				}
			};
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			row.setBorder(new EmptyBorder(3, 4, 3, 4));
			row.setCursor(new Cursor(Cursor.HAND_CURSOR));
			row.setToolTipText("Edit this item's icon");
			row.add(icon, BorderLayout.WEST);
			row.add(text, BorderLayout.CENTER);
			row.addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					IconEditorHost currentHost = host;
					if (currentHost != null)
						currentHost.editItem(item.id);
				}

				@Override
				public void mouseEntered(MouseEvent e) {
					row.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
				}

				@Override
				public void mouseExited(MouseEvent e) {
					row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				}
			});
			return row;
		}

		private JComponent message(String text) {
			JLabel label = new JLabel(text);
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			label.setBorder(new EmptyBorder(8, 0, 0, 0));
			return label;
		}

		private void setContent(JComponent child) {
			content.removeAll();
			content.add(child, BorderLayout.NORTH);
			revalidate();
			repaint();
		}
	}
}
