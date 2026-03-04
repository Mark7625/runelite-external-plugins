package io.mark.globes;

public enum RequirementDisplayMode {
	ALL_MET("All met"),
	PARTIAL_MET("Some Met");

	private final String displayName;

	RequirementDisplayMode(String displayName) {
		this.displayName = displayName;
	}

	@Override
	public String toString() {
		return displayName;
	}
}
