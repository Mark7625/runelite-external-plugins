package io.mark.remasteredslayerhelper.data;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import com.google.gson.GsonBuilder;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;
import net.runelite.http.api.RuneLiteAPI;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class SlayerTaskRepository {

	private static final String JSON_PATH = "/data/slayerTasks.json";

	private static final boolean PRINT_TASKS_TO_CONSOLE = false;

	@Inject
	private Client client;

	@Inject
	private SlayerMasterTaskRepository masterTaskRepository;

	@Getter
	private final Map<Integer, SlayerTaskInfo> tasks = new HashMap<>();


	public void load() {
		masterTaskRepository.load();

		Map<Integer, Skill> skillMap = new HashMap<>();
		var enum81 = client.getEnum(81);
		var enum680 = client.getEnum(680);

		for (int key : enum81.getIntVals()) {

			String skillName = enum680.getStringValue(key);

			if (skillName != null && !skillName.equalsIgnoreCase("SKILL")) {
				skillMap.put(enum81.getIntValue(key), Skill.valueOf(skillName.toUpperCase()));
			}
		}

		for (Integer dbTableRow : client.getDBTableRows(DBTableID.SlayerTask.ID)) {
			Integer id = (Integer) client.getDBTableField(dbTableRow, DBTableID.SlayerTask.COL_ID, 0)[0];
			String name = (String) client.getDBTableField(dbTableRow, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)[0];
			Integer combat = (Integer) client.getDBTableField(dbTableRow, DBTableID.SlayerTask.COL_MIN_COMLEVEL, 0)[0];

			Object[] stats = client.getDBTableField(dbTableRow, DBTableID.SlayerTask.COL_MIN_STAT_REQUIREMENT_ALL, 0);
			Object[] levels = client.getDBTableField(dbTableRow, DBTableID.SlayerTask.COL_MIN_STAT_REQUIREMENT_ALL, 1);

			Map<Skill, Integer> requirementsNeedsAll = new HashMap<>();
			for (int i = 0; i < stats.length && i < levels.length; i++) {
				Skill skill = skillMap.get((int) stats[i]);
				Integer level = (Integer) levels[i];
				if (skill == null || level == null) {
					continue;
				}
				requirementsNeedsAll.put(skill, level);
			}

			SlayerTaskInfo info = new SlayerTaskInfo(id, name, combat);
			info.setRequirementsNeedsAll(requirementsNeedsAll);
			info.setSlayerLevel(requirementsNeedsAll.getOrDefault(Skill.SLAYER, 1));
			tasks.put(id, info);
		}

		int syntheticId = -1;
		try (var stream = getClass().getResourceAsStream(JSON_PATH)) {
			if (stream != null) {
				try (Reader reader = new InputStreamReader(stream)) {
					SlayerTaskInfo[] jsonTasks = RuneLiteAPI.GSON.fromJson(reader, SlayerTaskInfo[].class);
					if (jsonTasks != null) {
						for (SlayerTaskInfo jsonInfo : jsonTasks) {
							if (jsonInfo.getId() != 0) {
								tasks.computeIfAbsent(jsonInfo.getId(), id -> new SlayerTaskInfo(id, "", 0)).copyFrom(jsonInfo);
							} else {
								jsonInfo.setId(syntheticId--);
								tasks.put(jsonInfo.getId(), jsonInfo);
							}
						}
					}
				}
			}
		} catch (IOException e) {
			log.error("Could not read slayerTasks.json", e);
		}

		for (Map.Entry<SlayerMaster, List<Integer>> e : masterTaskRepository.getMasters().entrySet()) {
			SlayerMaster master = e.getKey();
			for (Integer taskId : e.getValue()) {
				SlayerTaskInfo info = tasks.get(taskId);
				if (info != null) {
					Set<SlayerMaster> set = new LinkedHashSet<>(info.getSlayerMasters());
					set.add(master);
					info.setSlayerMasters(new ArrayList<>(set));
				}
			}
		}

		if (PRINT_TASKS_TO_CONSOLE) {
			Collection<SlayerTaskInfo> toPrint = getAllTasksWithJson();
			String json = new GsonBuilder().setPrettyPrinting().create().toJson(toPrint);
			System.out.println("--- Slayer tasks (with JSON data) ---\n" + json);
		}
	}

	public Collection<SlayerTaskInfo> getAllTasksWithJson() {
		return tasks.values().stream().filter(SlayerTaskInfo::hasJsonData).collect(Collectors.toList());
	}

	public SlayerTaskInfo get(int id) {
		return tasks.get(id);
	}

	public void clear() {
		tasks.clear();
		masterTaskRepository.clear();
	}

}
