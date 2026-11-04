package io.mark.hditemicons.hd;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import net.runelite.client.util.Filepath;

final class ModelPackPanel extends JPanel {
	ModelPackPanel(ModelPackStorage storage) {
		setLayout(new BorderLayout(0, 10));
		setOpaque(false);
		setBorder(new EmptyBorder(8, 0, 0, 0));
		JTextArea status = new JTextArea();
		status.setEditable(false);
		status.setOpaque(false);
		status.setLineWrap(true);
		status.setWrapStyleWord(true);
		status.setRows(6);
		status.setText(storage.status());

		JButton importPack = new JButton("Import model pack");
		importPack.setToolTipText("Replace the active model pack with a ZIP containing pack.json, OBJ models and PNG textures");
		importPack.addActionListener(e -> {
			List<Filepath> selected = new Filepath.Chooser().setIsOpen().setAcceptsFiles()
				.setDialogTitle("Import item-icon model pack")
				.addExtensionFilter("Model pack ZIP", "zip").showDialog(this);
			if (selected != null && !selected.isEmpty())
				storage.importPack(selected.get(0));
		});
		JButton exportPack = new JButton("Export model pack");
		exportPack.setToolTipText("Share the active model assets, including your saved camera settings for these items");
		exportPack.addActionListener(e -> {
			List<Filepath> selected = new Filepath.Chooser().setIsSave().setAcceptsFiles()
				.setDialogTitle("Export item-icon model pack").setFileName("hq-model-pack.zip")
				.addExtensionFilter("Model pack ZIP", "zip").setDefaultExtension("zip").showDialog(this);
			if (selected != null && !selected.isEmpty())
				storage.exportPack(selected.get(0));
		});
		JButton removePack = new JButton("Remove model pack");
		removePack.addActionListener(e -> storage.removePack());
		Runnable refresh = () -> {
			status.setText(storage.status());
			exportPack.setEnabled(storage.hasPack());
			removePack.setEnabled(storage.hasPack());
		};
		storage.setListener(refresh);
		refresh.run();
		JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 6));
		buttons.setOpaque(false);
		buttons.add(importPack);
		buttons.add(exportPack);
		buttons.add(removePack);
		add(status, BorderLayout.NORTH);
		add(buttons, BorderLayout.CENTER);
	}
}
