package io.mark.globes;

public enum GlobeStyle {
	CLASSIC("Classic"),
	MODERN("Modern"),
	CUSTOM("Custom");

	private final String name;

	GlobeStyle(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return name;
	}
}
