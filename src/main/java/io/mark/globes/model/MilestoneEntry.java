package io.mark.globes.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MilestoneEntry {
	@Getter
	private final int level;
	@Getter
	private final String message;
	@Getter
	private final int iconItemId;
}
