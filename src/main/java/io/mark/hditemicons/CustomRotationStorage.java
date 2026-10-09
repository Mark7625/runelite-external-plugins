package io.mark.hditemicons;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
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
	private static final String EDITOR_FILE_NAME = "icon-editor.json";
	private static final Type MAP_TYPE = new TypeToken<Map<Integer, CustomRotation>>() {}.getType();

	// A drag commits every 100ms and each commit rewrites the whole file, so writes wait for
	// the edits to settle.
	private static final long SAVE_DEBOUNCE_MS = 500;

	private final Gson gson;
	private final Map<Integer, CustomRotation> rotations = new ConcurrentHashMap<>();
	private final Map<String, Set<Integer>> groups = new ConcurrentHashMap<>();
	private final Map<String, CustomRotation> presets = new ConcurrentHashMap<>();

	@Nullable
	private Filepath directory;
	@Nullable
	private ScheduledExecutorService saveExecutor;
	@Nullable
	private ScheduledFuture<?> pendingSave;
	@Nullable
	private ScheduledFuture<?> pendingEditorSave;

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
		if (file.isFile()) {
			try (BufferedReader reader = file.openBufferedReader()) {
				Map<Integer, CustomRotation> loaded = gson.fromJson(reader, MAP_TYPE);
				if (loaded != null)
					rotations.putAll(loaded);
			} catch (IOException | JsonSyntaxException e) {
				log.debug("Couldn't read custom icon rotations from {}, starting empty", file, e);
			}
		}

		Filepath editorFile = directory.join(EDITOR_FILE_NAME);
		if (!editorFile.isFile())
			return;
		try (BufferedReader reader = editorFile.openBufferedReader()) {
			take(gson.fromJson(reader, IconEditorData.class));
		} catch (IOException | JsonSyntaxException e) {
			log.debug("Couldn't read icon groups and presets from {}, starting empty", editorFile, e);
		}
	}

	public synchronized void reset() {
		ScheduledExecutorService executor = saveExecutor;
		Filepath currentDirectory = directory;

		boolean saveWasPending = pendingSave != null && pendingSave.cancel(false);
		boolean editorSaveWasPending = pendingEditorSave != null && pendingEditorSave.cancel(false);
		pendingSave = null;
		pendingEditorSave = null;
		if (executor != null) {
			// Snapshot before the clear below, or the flush writes an empty file
			if (saveWasPending && currentDirectory != null) {
				Map<Integer, CustomRotation> snapshot = Map.copyOf(rotations);
				executor.execute(() -> writeToDisk(currentDirectory, snapshot));
			}
			if (editorSaveWasPending && currentDirectory != null) {
				IconEditorData snapshot = editorData();
				executor.execute(() -> writeEditorDataToDisk(currentDirectory, snapshot));
			}
			// shutdown(), not shutdownNow(), so that last write still runs - neither blocks
			executor.shutdown();
		}

		saveExecutor = null;
		directory = null;
		rotations.clear();
		groups.clear();
		presets.clear();
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

	public void putAll(Iterable<Integer> itemIds, @Nullable CustomRotation rotation) {
		for (int itemId : itemIds) {
			if (rotation == null)
				rotations.remove(itemId);
			else
				rotations.put(itemId, rotation);
		}
		persist();
	}

	public List<String> groupNames() {
		List<String> names = new ArrayList<>(groups.keySet());
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	public Set<Integer> groupMembers(int itemId) {
		String name = groupOf(itemId);
		return name == null ? Set.of(itemId) : membersOf(name);
	}

	public Set<Integer> membersOf(String groupName) {
		Set<Integer> members = groups.get(groupName);
		return members == null ? Set.of() : snapshot(members);
	}

	@Nullable
	public String groupOf(int itemId) {
		for (Map.Entry<String, Set<Integer>> group : groups.entrySet())
			if (group.getValue().contains(itemId))
				return group.getKey();
		return null;
	}

	public void createGroup(String name) {
		groups.computeIfAbsent(name, key -> newMemberSet());
		persistEditorData();
	}

	public void renameGroup(String from, String to) {
		Set<Integer> members = groups.remove(from);
		if (members == null)
			return;
		groups.put(to, members);
		persistEditorData();
	}

	public String unusedGroupName(String prefix) {
		for (int suffix = 1; ; suffix++) {
			String name = prefix + " " + suffix;
			if (!groups.containsKey(name))
				return name;
		}
	}

	public void deleteGroup(String name) {
		groups.remove(name);
		persistEditorData();
	}

	public void addToGroup(String name, int itemId) {
		for (Set<Integer> members : groups.values())
			members.remove(itemId);
		groups.computeIfAbsent(name, key -> newMemberSet()).add(itemId);
		persistEditorData();
	}

	public void leaveGroup(int itemId) {
		for (Set<Integer> members : groups.values())
			members.remove(itemId);
		persistEditorData();
	}

	public List<String> presetNames() {
		List<String> names = new ArrayList<>(presets.keySet());
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	@Nullable
	public CustomRotation preset(String name) {
		return presets.get(name);
	}

	public void putPreset(String name, CustomRotation rotation) {
		presets.put(name, rotation);
		persistEditorData();
	}

	public void removePreset(String name) {
		presets.remove(name);
		persistEditorData();
	}

	public String exportToJson() {
		return gson.toJson(new IconEditorData(Map.copyOf(rotations), copyOfGroups(), Map.copyOf(presets)));
	}

	@Nullable
	public ImportPreview readImport(String json) {
		IconEditorData data;
		try {
			data = gson.fromJson(json, IconEditorData.class);
		} catch (JsonSyntaxException e) {
			log.debug("Couldn't read an icon editor export", e);
			return null;
		}
		if (data == null)
			return null;

		clean(data);
		ImportPreview preview = new ImportPreview(data);
		if (data.rotations != null)
			for (Integer itemId : data.rotations.keySet())
				if (rotations.containsKey(itemId))
					preview.rotationsReplaced.add(itemId);
		if (data.groups != null)
			for (String name : data.groups.keySet())
				if (groups.containsKey(name))
					preview.groupsReplaced.add(name);
		if (data.presets != null)
			for (String name : data.presets.keySet())
				if (presets.containsKey(name))
					preview.presetsReplaced.add(name);
		return preview;
	}

	public ImportCounts applyImport(IconEditorData chosen) {
		ImportCounts counts = take(chosen);
		if (counts.rotations > 0)
			persist();
		if (counts.groups > 0 || counts.presets > 0)
			persistEditorData();
		return counts;
	}

	private ImportCounts take(@Nullable IconEditorData data) {
		ImportCounts counts = new ImportCounts();
		if (data == null)
			return counts;

		clean(data);
		if (data.rotations != null) {
			rotations.putAll(data.rotations);
			counts.rotations = data.rotations.size();
		}
		if (data.groups != null) {
			for (Map.Entry<String, Set<Integer>> group : data.groups.entrySet()) {
				Set<Integer> members = newMemberSet();
				members.addAll(group.getValue());
				groups.put(group.getKey(), members);
				counts.groups++;
			}
		}
		if (data.presets != null) {
			presets.putAll(data.presets);
			counts.presets = data.presets.size();
		}
		return counts;
	}

	private static void clean(IconEditorData data) {
		if (data.rotations != null)
			data.rotations.values().removeIf(Objects::isNull);
		if (data.groups != null)
			data.groups.values().removeIf(Objects::isNull);
		if (data.presets != null)
			data.presets.values().removeIf(Objects::isNull);
	}

	public static final class ImportCounts {
		public int rotations;
		public int groups;
		public int presets;
	}

	public static final class ImportPreview {
		public final IconEditorData data;
		public final Set<Integer> rotationsReplaced = new HashSet<>();
		public final Set<String> groupsReplaced = new HashSet<>();
		public final Set<String> presetsReplaced = new HashSet<>();

		ImportPreview(IconEditorData data) {
			this.data = data;
		}

		public boolean isEmpty() {
			return count(data.rotations) == 0 && count(data.groups) == 0 && count(data.presets) == 0;
		}

		private static int count(@Nullable Map<?, ?> map) {
			return map == null ? 0 : map.size();
		}
	}

	public void offDisk(Runnable task) {
		ScheduledExecutorService executor = saveExecutor;
		if (executor != null)
			executor.execute(task);
	}

	private static Set<Integer> newMemberSet() {
		return Collections.synchronizedSet(new LinkedHashSet<>());
	}

	private static Set<Integer> snapshot(Set<Integer> members) {
		synchronized (members) {
			return Set.copyOf(members);
		}
	}

	private Map<String, Set<Integer>> copyOfGroups() {
		Map<String, Set<Integer>> copy = new TreeMap<>(Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER));
		for (Map.Entry<String, Set<Integer>> group : groups.entrySet())
			copy.put(group.getKey(), snapshot(group.getValue()));
		return copy;
	}

	private IconEditorData editorData() {
		return new IconEditorData(null, copyOfGroups(), Map.copyOf(presets));
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

	private synchronized void persistEditorData() {
		Filepath currentDirectory = directory;
		ScheduledExecutorService executor = saveExecutor;
		if (currentDirectory == null || executor == null)
			return;

		if (pendingEditorSave != null)
			pendingEditorSave.cancel(false);
		pendingEditorSave = executor.schedule(() -> writeEditorDataToDisk(currentDirectory, editorData()),
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

	private void writeEditorDataToDisk(Filepath currentDirectory, IconEditorData snapshot) {
		try {
			if (!currentDirectory.isDirectory())
				currentDirectory.createDirectories();
			currentDirectory.join(EDITOR_FILE_NAME).write(gson.toJson(snapshot));
		} catch (IOException e) {
			log.debug("Couldn't save icon groups and presets to {}", currentDirectory, e);
		}
	}
}
