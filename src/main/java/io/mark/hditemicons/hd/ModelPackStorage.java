package io.mark.hditemicons.hd;

import com.google.gson.Gson;
import io.mark.hditemicons.CustomRotation;
import io.mark.hditemicons.CustomRotationStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Filepath;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/** Owns one active pack. Disk/validation is serialized off-thread; publication is client-thread only. */
@Slf4j
@Singleton
public class ModelPackStorage {
	private static final String SAVED_PACK = "custom-model-pack.zip";
	private final Gson gson;
	private final ClientThread clientThread;
	private final CustomRotationStorage rotations;
	private volatile long session;
	@Nullable private volatile ExecutorService worker;
	@Nullable private volatile ModelPack pack;
	private volatile String status = "No model pack loaded";
	private Runnable listener = () -> {};

	@Inject
	public ModelPackStorage(Gson gson, ClientThread clientThread, CustomRotationStorage rotations) {
		this.gson = gson;
		this.clientThread = clientThread;
		this.rotations = rotations;
	}

	public void setListener(Runnable listener) {
		this.listener = listener;
	}

	public String status() {
		return status;
	}

	boolean hasPack() {
		return pack != null;
	}

	@Nullable
	ModelPack.Entry get(int itemId) {
		ModelPack current = pack;
		return current == null ? null : current.items.get(itemId);
	}

	String fingerprint() {
		ModelPack current = pack;
		return current == null ? "" : current.fingerprint;
	}

	private Filepath directory;
	private Runnable afterChange;

	void start(Filepath directory, Runnable afterChange) {
		this.directory = directory;
		this.afterChange = afterChange;
		long started = ++session;
		worker = Executors.newSingleThreadExecutor(r -> {
			Thread thread = new Thread(r, "hq-item-model-packs");
			thread.setDaemon(true);
			return thread;
		});
		setStatus("Loading model pack...");
		submit(() -> {
			Filepath saved = directory.joinSegment(SAVED_PACK);
			ModelPack loaded = null;
			if (saved.isFile())
				try (InputStream input = saved.openInputStream()) {
					loaded = ModelPack.read(input, gson);
				}
			publish(started, loaded);
		});
	}

	void stop() {
		session++;
		ExecutorService executor = worker;
		worker = null;
		if (executor != null)
			executor.shutdownNow();
		pack = null;
		setStatus("Turn HQ Item Icons on to load model packs");
	}

	void importPack(Filepath source) {
		long started = session;
		Filepath targetDirectory = directory;
		submit(() -> {
			ModelPack imported;
			try (InputStream input = source.openInputStream()) {
				imported = ModelPack.read(input, gson);
			}
			if (started != session)
				return;
			// Preserve the previous file until all assets validate and the replacement is written.
			atomicWrite(targetDirectory, targetDirectory.joinSegment(SAVED_PACK), imported.archive());
			publish(started, imported);
		});
	}

	void exportPack(Filepath target) {
		ModelPack current = pack;
		if (current == null) {
			setStatus("Import a model pack before exporting");
			return;
		}
		Map<Integer, CustomRotation> overrides = rotations.all();
		submit(() -> {
			// Chooser filepaths are rooted at the selected file: write only that chosen file.
			target.write(current.export(gson, overrides));
			setStatus("Exported " + current.name + " to " + target.getFileName());
		});
	}

	void removePack() {
		long started = session;
		Filepath targetDirectory = directory;
		submit(() -> {
			targetDirectory.joinSegment(SAVED_PACK).deleteIfExists();
			publish(started, null);
		});
	}

	private void publish(long started, @Nullable ModelPack next) {
		clientThread.invoke(() -> {
			if (started != session || worker == null)
				return;
			pack = next;
			afterChange.run();
			setStatus(next == null ? "No model pack loaded" : next.name + " — " + next.items.size() + " item models"
				+ (next.author.isEmpty() ? "" : " by " + next.author));
		});
	}

	private void submit(IoTask task) {
		ExecutorService executor = worker;
		long started = session;
		if (executor == null) {
			setStatus("Turn HQ Item Icons on to load model packs");
			return;
		}
		try {
			executor.execute(() -> {
				if (started != session)
					return;
				try {
					task.run();
				} catch (IOException | RuntimeException ex) {
					log.debug("Model pack operation failed", ex);
					if (started == session)
						setStatus("Model pack error: " + ex.getMessage() + (pack == null ? "" : ". Previous pack retained."));
				}
			});
		} catch (RejectedExecutionException ex) {
			log.debug("Model pack worker stopped", ex);
		}
	}

	private void setStatus(String message) {
		status = message;
		SwingUtilities.invokeLater(() -> listener.run());
	}

	private static void atomicWrite(Filepath directory, Filepath target, byte[] bytes) throws IOException {
		directory.createDirectories();
		Filepath temporary = directory.createTempFile("model-pack-", ".tmp");
		try {
			temporary.write(bytes);
			try {
				temporary.moveTo(target, REPLACE_EXISTING, ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ex) {
				temporary.moveTo(target, REPLACE_EXISTING);
			}
		} finally {
			temporary.deleteIfExists();
		}
	}

	private interface IoTask {
		void run() throws IOException;
	}
}
