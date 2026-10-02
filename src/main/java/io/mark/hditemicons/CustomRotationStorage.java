package io.mark.hditemicons;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Persists per-item {@link CustomRotation} overrides set from the rotation editor as JSON, so
 * they survive between sessions.
 */
@Slf4j
@Singleton
public class CustomRotationStorage {
	private static final String FILE_NAME = "custom-rotations.json";
	private static final Type MAP_TYPE = new TypeToken<Map<Integer, CustomRotation>>() {}.getType();

	// A drag commits every 100ms and each commit rewrites the whole file, so writes wait for
	// the edits to settle.
	private static final long SAVE_DEBOUNCE_MS = 500;

	private final Gson gson;
	private final Map<Integer, CustomRotation> rotations = new ConcurrentHashMap<>();

	@Nullable
	private Filepath directory;
	@Nullable
	private ScheduledExecutorService saveExecutor;
	@Nullable
	private ScheduledFuture<?> pendingSave;

	@Inject
	public CustomRotationStorage(Gson gson) {
		this.gson = gson;
	}

	public void load(Filepath directory) {
		this.directory = directory;
		saveExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "hd-item-icon-rotation-storage");
			thread.setDaemon(true);
			return thread;
		});

		Filepath file = directory.join(FILE_NAME);
		if (!file.isFile())
			return;
		try (BufferedReader reader = file.openBufferedReader()) {
			Map<Integer, CustomRotation> loaded = gson.fromJson(reader, MAP_TYPE);
			if (loaded != null)
				rotations.putAll(loaded);
		} catch (IOException | JsonSyntaxException e) {
			log.debug("Couldn't read custom icon rotations from {}, starting empty", file, e);
		}
	}

	public synchronized void reset() {
		ScheduledExecutorService executor = saveExecutor;
		Filepath currentDirectory = directory;

		boolean saveWasPending = pendingSave != null && pendingSave.cancel(false);
		pendingSave = null;
		if (executor != null) {
			// Snapshot before the clear below, or the flush writes an empty file
			if (saveWasPending && currentDirectory != null) {
				Map<Integer, CustomRotation> snapshot = Map.copyOf(rotations);
				executor.execute(() -> writeToDisk(currentDirectory, snapshot));
			}
			// shutdown(), not shutdownNow(), so that last write still runs - neither blocks
			executor.shutdown();
		}

		saveExecutor = null;
		directory = null;
		rotations.clear();
	}

	@Nullable
	public CustomRotation get(int itemId) {
		return rotations.get(itemId);
	}

	public Map<Integer, CustomRotation> all() {
		return Map.copyOf(rotations);
	}

	public void put(int itemId, CustomRotation rotation) {
		rotations.put(itemId, rotation);
		persist();
	}

	public void remove(int itemId) {
		rotations.remove(itemId);
		persist();
	}

	private synchronized void persist() {
		Filepath currentDirectory = directory;
		ScheduledExecutorService executor = saveExecutor;
		if (currentDirectory == null || executor == null)
			return;

		if (pendingSave != null)
			pendingSave.cancel(false);
		// Snapshotted when the write runs, so it carries the settled values, not the first of the burst
		pendingSave = executor.schedule(() -> writeToDisk(currentDirectory, Map.copyOf(rotations)),
			SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
	}

	private void writeToDisk(Filepath currentDirectory, Map<Integer, CustomRotation> snapshot) {
		try {
			if (!currentDirectory.isDirectory())
				currentDirectory.createDirectories();
			currentDirectory.join(FILE_NAME).write(gson.toJson(snapshot, MAP_TYPE));
		} catch (IOException e) {
			log.debug("Couldn't save custom icon rotations to {}", currentDirectory, e);
		}
	}
}
