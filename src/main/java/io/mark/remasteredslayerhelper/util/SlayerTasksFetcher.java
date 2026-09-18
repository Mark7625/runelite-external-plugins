package io.mark.remasteredslayerhelper.util;

import io.mark.remasteredslayerhelper.data.SlayerTaskRepository;
import io.mark.remasteredslayerhelper.domain.SlayerTask;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class SlayerTasksFetcher {

	private final SlayerTaskRepository slayerTaskRepository;

	public SlayerTasksFetcher(SlayerTaskRepository slayerTaskRepository) {
		this.slayerTaskRepository = slayerTaskRepository;
	}

	public Collection<SlayerTask> getAllSlayerTasks() {
		return slayerTaskRepository.getAllTasksWithJson().stream()
			.map(info -> info.toSlayerTask())
			.filter(Objects::nonNull)
			.collect(Collectors.toCollection(ArrayList::new));
	}

	public Collection<SlayerTask> getSlayerTasksByFilter(String searchText) {
		String lower = searchText.toLowerCase();
		return slayerTaskRepository.getAllTasksWithJson().stream()
			.map(info -> info.toSlayerTask())
			.filter(Objects::nonNull)
			.filter(task -> task.getMonsterLowerCase().contains(lower))
			.collect(Collectors.toCollection(ArrayList::new));
	}
}
