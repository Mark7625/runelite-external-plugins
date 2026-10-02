package io.mark.hditemicons.hd;

import io.mark.hditemicons.IconQuality;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Rendered icons kept on disk, in a folder per icon quality and brightness, and apart when custom rotations are off or
 * the interface is stretched.
 */
@Slf4j
class ItemIconCache {
	// Bump when icons are drawn differently, or keyed differently
	private static final int VERSION = 4;
	private static final int MAX_FOLDERS = 3;

	private final Filepath folder;
	private final int size;

	ItemIconCache(Filepath root, IconQuality quality, double brightness, boolean customRotations, boolean stretched, int size) {
		folder = root.joinSegment(String.format(Locale.ROOT, "v%d-%s-%.3f%s%s", VERSION, quality.name().toLowerCase(Locale.ROOT), brightness,
			customRotations ? "" : "-default-rotations", stretched ? "-stretched" : ""));
		this.size = size;
	}

	void markUsed() {
		try {
			// Creating a file updates the folder's modification time
			if (folder.isDirectory())
				folder.createTempFile("used", ".tmp").delete();
		} catch (IOException ex) {
			log.debug("Unable to mark item icons as used:", ex);
		}
	}

	/**
	 * The icon's pixels, no pixels if it couldn't be rendered, or null if it isn't kept.
	 */
	@Nullable
	int[] load(long key) {
		try {
			byte[] file;
			try (var in = folder.joinSegment(fileName(key)).openInputStream()) {
				file = in.readAllBytes();
			}
			if (file.length == 0)
				return new int[0];

			byte[] data;
			try (var in = new InflaterInputStream(new ByteArrayInputStream(file))) {
				data = in.readAllBytes();
			}
			if (data.length != size * Integer.BYTES)
				return null;
			int[] pixels = new int[size];
			ByteBuffer.wrap(data).asIntBuffer().get(pixels);
			return pixels;
		} catch (NoSuchFileException ex) {
			return null;
		} catch (IOException ex) {
			log.debug("Unable to load item icon {}:", fileName(key), ex);
			return null;
		}
	}

	/**
	 * Keeps the icon, or that it couldn't be rendered if there are no pixels.
	 */
	void save(long key, @Nullable int[] pixels) {
		try {
			byte[] file = new byte[0];
			if (pixels != null) {
				var buffer = ByteBuffer.allocate(pixels.length * Integer.BYTES);
				buffer.asIntBuffer().put(pixels);
				var out = new ByteArrayOutputStream();
				try (var deflater = new DeflaterOutputStream(out)) {
					deflater.write(buffer.array());
				}
				file = out.toByteArray();
			}

			// Moved into place, since other clients may be reading it
			folder.createDirectories();
			var temporary = folder.createTempFile(fileName(key), ".tmp");
			temporary.write(file);
			temporary.moveTo(folder.joinSegment(fileName(key)), REPLACE_EXISTING, ATOMIC_MOVE);
		} catch (IOException ex) {
			log.debug("Unable to keep item icon {}:", fileName(key), ex);
		}
	}

	void delete(long key) {
		try {
			folder.joinSegment(fileName(key)).deleteIfExists();
		} catch (IOException ex) {
			log.debug("Unable to delete item icon {}:", fileName(key), ex);
		}
	}

	/**
	 * Removes all but the most recently used folders, and the icons kept before there were folders.
	 */
	static void removeUnused(Filepath root) {
		if (!root.isDirectory())
			return;

		try {
			List<Filepath> entries;
			try (var list = root.walk(1)) {
				entries = list.filter(entry -> !entry.equals(root)).collect(Collectors.toList());
			}
			List<Filepath> folders = entries.stream()
				.filter(Filepath::isDirectory)
				.sorted(Comparator.comparing(ItemIconCache::lastModified).reversed())
				.collect(Collectors.toList());
			for (var unused : folders.subList(Math.min(MAX_FOLDERS, folders.size()), folders.size()))
				unused.deleteRecursively();
			for (var entry : entries)
				if (entry.isFile() && entry.getFileName().endsWith(".bin"))
					entry.delete();
		} catch (IOException ex) {
			log.debug("Unable to remove unused item icons:", ex);
		}
	}

	private static FileTime lastModified(Filepath folder) {
		try {
			return folder.getLastModifiedTime();
		} catch (IOException ex) {
			return FileTime.fromMillis(0);
		}
	}

	private static String fileName(long key) {
		return String.format("%016x", key);
	}
}
