package io.mark.globes.model.quest;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public final class QuestUnlockResult {
	private final List<Quest> newlyUnlockedQuests = new ArrayList<>();
	private final List<Quest> fullyUnlockedQuests = new ArrayList<>();
}