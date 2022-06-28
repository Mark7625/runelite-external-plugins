package io.mark.hditemicons;

public enum IconCacheStorage {
	MEMORY("Memory only"),
	DISK("Disk");

	private final String label;

	IconCacheStorage(String label) {
		this.label = label;
	}

	@Override
	public String toString() {
		return label;
	}
}
