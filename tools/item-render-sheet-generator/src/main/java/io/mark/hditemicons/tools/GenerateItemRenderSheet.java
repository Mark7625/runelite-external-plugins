package io.mark.hditemicons.tools;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.runelite.cache.ItemManager;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.fs.Store;
import net.runelite.cache.fs.flat.FlatStorage;

/**
 * Dumps every item's icon camera placement from a game cache, in the same shape as the
 * render-placement columns of jhughes/glamourer_data's v1/item_render_sheet.csv. RuneLite's
 * plugin API exposes an item's rotation angles but none of these fields.
 */
public final class GenerateItemRenderSheet {
	private static final String HEADER = "id,zoom2d,xOffset2d,yOffset2d,resizeX,resizeY,resizeZ";

	// What the client treats as unset; all-default rows are omitted entirely
	private static final int DEFAULT_ZOOM_2D = 2000;
	private static final int DEFAULT_RESIZE = 128;

	public static void main(String[] args) throws IOException {
		if (args.length != 3) {
			System.err.println("Usage: GenerateItemRenderSheet <cache dir> <output csv> <cache version>");
			System.exit(1);
			return;
		}

		File cacheDir = new File(args[0]);
		File outFile = new File(args[1]);
		String cacheVersion = args[2];

		try (Store store = openStore(cacheDir)) {
			store.load();
			ItemManager itemManager = new ItemManager(store);
			itemManager.load();

			List<ItemDefinition> items = new ArrayList<>(itemManager.getItems());
			items.sort(Comparator.comparingInt(item -> item.id));

			File parent = outFile.getParentFile();
			if (parent != null)
				Files.createDirectories(parent.toPath());

			int written = 0;
			// Explicit \n, not println()'s platform separator, to keep output OS-independent
			try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outFile.toPath(), StandardCharsets.UTF_8))) {
				writer.print("# This file is generated; do not edit manually.\n");
				writer.print("# cache: " + cacheVersion + "\n");
				writer.print(HEADER + "\n");
				for (ItemDefinition item : items) {
					if (isDefault(item))
						continue;
					writer.print(item.id
						+ "," + emptyIf(item.zoom2d, DEFAULT_ZOOM_2D)
						+ "," + emptyIf(item.xOffset2d, 0)
						+ "," + emptyIf(item.yOffset2d, 0)
						+ "," + emptyIf(item.resizeX, DEFAULT_RESIZE)
						+ "," + emptyIf(item.resizeY, DEFAULT_RESIZE)
						+ "," + emptyIf(item.resizeZ, DEFAULT_RESIZE)
						+ "\n");
					written++;
				}
			}
			System.out.println("Wrote render params for " + written + " items to " + outFile.getAbsolutePath());
		}
	}

	private static Store openStore(File cacheDir) throws IOException {
		File[] flat = cacheDir.listFiles((dir, name) -> name.endsWith(".flatcache"));
		return flat != null && flat.length > 0 ? new Store(new FlatStorage(cacheDir)) : new Store(cacheDir);
	}

	private static boolean isDefault(ItemDefinition item) {
		return item.zoom2d == DEFAULT_ZOOM_2D
			&& item.xOffset2d == 0
			&& item.yOffset2d == 0
			&& item.resizeX == DEFAULT_RESIZE
			&& item.resizeY == DEFAULT_RESIZE
			&& item.resizeZ == DEFAULT_RESIZE;
	}

	private static String emptyIf(int value, int defaultValue) {
		return value == defaultValue ? "" : Integer.toString(value);
	}
}
