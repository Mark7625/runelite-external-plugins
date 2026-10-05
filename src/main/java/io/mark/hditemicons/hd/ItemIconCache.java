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
	// Bump when icons are drawn differently
	private static final int VERSION = 6;
	private static final int MAX_FOLDERS = 3;
	private static final int HEADER_INTS = 2;
	private static final String REMEMBERED = "remembered";

	private final Filepath folder;
	private final int size;

	ItemIconCache(Filepath root, IconQuality quality, double brightness, boolean customRotations, boolean stretched,
				boolean animatedTextures, int size) {
		folder = root.joinSegment(String.format(Locale.ROOT, "v%d-%s-%.3f%s%s%s", VERSION, quality.name().toLowerCase(Locale.ROOT), brightness,
			customRotations ? "" : "-default-rotations", stretched ? "-stretched" : "", animatedTextures ? "" : "-static-textures"));
		this.size = size;
	}

	/**
	 * An icon as it was kept: one frame per phase of its animation, and the length of that
	 * animation in client cycles. A still icon is one frame with no period.
	 */
	static final class Kept {
		final int period;
		final int[][] frames;

		Kept(int period, int[][] frames) {
			this.period = period;
			this.frames = frames;
		}
	}

	/**
	 * The icons an account was shown last, most recently shown first.
	 */
	static final class Remembered {
		final long[] fingerprints;
		final int[] itemIds;

		Remembered(long[] fingerprints, int[] itemIds) {
			this.fingerprints = fingerprints;
			this.itemIds = itemIds;
		}
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
	 * The icon's frames, no frames if it couldn't be rendered, or null if it isn't kept.
	 */
	@Nullable
	Kept load(long key) {
		try {
			byte[] file;
			try (var in = folder.joinSegment(fileName(key)).openInputStream()) {
				file = in.readAllBytes();
			}
			if (file.length == 0)
				return new Kept(0, new int[0][]);

			byte[] data;
			try (var in = new InflaterInputStream(new ByteArrayInputStream(file))) {
				data = in.readAllBytes();
			}
			if (data.length < HEADER_INTS * Integer.BYTES)
				return null;
			var ints = ByteBuffer.wrap(data).asIntBuffer();
			int period = ints.get();
			int frameCount = ints.get();
			if (frameCount < 1 || data.length != (HEADER_INTS + (long) frameCount * size) * Integer.BYTES)
				return null;

			int[][] frames = new int[frameCount][];
			for (int frame = 0; frame < frameCount; frame++) {
				frames[frame] = new int[size];
				ints.get(frames[frame]);
			}
			return new Kept(period, frames);
		} catch (NoSuchFileException ex) {
			return null;
		} catch (IOException ex) {
			log.debug("Unable to load item icon {}:", fileName(key), ex);
			return null;
		}
	}

	/**
	 * Keeps the icon's frames, or that it couldn't be rendered if there are none.
	 */
	void save(long key, int period, @Nullable int[][] frames) {
		try {
			byte[] file = new byte[0];
			if (frames != null) {
				var buffer = ByteBuffer.allocate((HEADER_INTS + frames.length * size) * Integer.BYTES);
				var ints = buffer.asIntBuffer();
				ints.put(period);
				ints.put(frames.length);
				for (int[] frame : frames)
					ints.put(frame);
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

	Remembered loadRemembered(long account) {
		try (var in = folder.joinSegment(rememberedName(account)).openInputStream()) {
			var buffer = ByteBuffer.wrap(in.readAllBytes());
			int count = buffer.remaining() / (Long.BYTES + Integer.BYTES);
			long[] fingerprints = new long[count];
			int[] itemIds = new int[count];
			for (int i = 0; i < count; i++) {
				fingerprints[i] = buffer.getLong();
				itemIds[i] = buffer.getInt();
			}
			return new Remembered(fingerprints, itemIds);
		} catch (NoSuchFileException ex) {
			return new Remembered(new long[0], new int[0]);
		} catch (IOException ex) {
			log.debug("Unable to load the remembered item icons:", ex);
			return new Remembered(new long[0], new int[0]);
		}
	}

	void remember(long account, Remembered remembered) {
		try {
			var buffer = ByteBuffer.allocate(remembered.fingerprints.length * (Long.BYTES + Integer.BYTES));
			for (int i = 0; i < remembered.fingerprints.length; i++)
				buffer.putLong(remembered.fingerprints[i]).putInt(remembered.itemIds[i]);
			folder.createDirectories();
			var temporary = folder.createTempFile(rememberedName(account), ".tmp");
			temporary.write(buffer.array());
			temporary.moveTo(folder.joinSegment(rememberedName(account)), REPLACE_EXISTING, ATOMIC_MOVE);
		} catch (IOException ex) {
			log.debug("Unable to remember the item icons:", ex);
		}
	}

	// Per account, since each has its own bank
	private static String rememberedName(long account) {
		return REMEMBERED + "-" + Long.toHexString(account);
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
