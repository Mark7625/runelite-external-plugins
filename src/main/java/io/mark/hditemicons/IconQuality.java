package io.mark.hditemicons;

import lombok.Getter;

public enum IconQuality {
	LOW(2, "Low"),
	MEDIUM(4, "Medium"),
	HIGH(6, "High");

	@Getter
    private final int supersample;
	private final String label;

	IconQuality(int supersample, String label) {
		this.supersample = supersample;
		this.label = label;
	}

    @Override
	public String toString() {
		return label;
	}
}
