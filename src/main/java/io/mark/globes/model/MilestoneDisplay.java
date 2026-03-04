package io.mark.globes.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MilestoneDisplay {
	public static final int QUEST_ICON_ID = -2;
	@Getter
	private final String message;
	@Getter
	private final int iconItemId;
}