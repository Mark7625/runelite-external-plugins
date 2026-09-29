package io.mark.hditemicons;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * The bundled item_render_sheet.csv (see tools/item-render-sheet-generator). RuneLite's API
 * exposes an item's rotation angles but none of these fields, so they come from the cache dump.
 */
@Slf4j
@Singleton
public class ItemRenderSheet {
	public static final int DEFAULT_ZOOM_2D = 2000;
	public static final int DEFAULT_RESIZE = 128;

	public static final class Placement {
		public final int zoom2d;
		public final int offsetX;
		public final int offsetY;
		public final int resizeX;
		public final int resizeY;
		public final int resizeZ;

		Placement(int zoom2d, int offsetX, int offsetY, int resizeX, int resizeY, int resizeZ) {
			this.zoom2d = zoom2d;
			this.offsetX = offsetX;
			this.offsetY = offsetY;
			this.resizeX = resizeX;
			this.resizeY = resizeY;
			this.resizeZ = resizeZ;
		}
	}

	private static final Placement DEFAULT_PLACEMENT =
		new Placement(DEFAULT_ZOOM_2D, 0, 0, DEFAULT_RESIZE, DEFAULT_RESIZE, DEFAULT_RESIZE);

	private final Map<Integer, Placement> placements = load();

	public Placement get(int itemId) {
		return placements.getOrDefault(itemId, DEFAULT_PLACEMENT);
	}

	private static Map<Integer, Placement> load() {
		Map<Integer, Placement> result = new HashMap<>();
		try (InputStream in = ItemRenderSheet.class.getResourceAsStream("item_render_sheet.csv")) {
			if (in == null) {
				log.warn("item_render_sheet.csv is missing from resources");
				return result;
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isEmpty() || line.charAt(0) == '#' || line.startsWith("id,"))
					continue;
				String[] parts = line.split(",", -1);
				int itemId = Integer.parseInt(parts[0]);
				result.put(itemId, new Placement(
					parseOr(parts[1], DEFAULT_ZOOM_2D),
					parseOr(parts[2], 0),
					parseOr(parts[3], 0),
					parseOr(parts[4], DEFAULT_RESIZE),
					parseOr(parts[5], DEFAULT_RESIZE),
					parseOr(parts[6], DEFAULT_RESIZE)));
			}
		} catch (IOException e) {
			log.warn("Couldn't read item_render_sheet.csv", e);
		}
		return result;
	}

	private static int parseOr(String value, int defaultValue) {
		return value.isEmpty() ? defaultValue : Integer.parseInt(value);
	}
}
