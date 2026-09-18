package io.mark.remasteredslayerhelper;

public enum RepositoryLayout {
	NORMAL("Normal"),
	COMPACT("Compact");

	private final String displayName;

	RepositoryLayout(String displayName) {
		this.displayName = displayName;
	}

	@Override
	public String toString() {
		return displayName;
	}
}
