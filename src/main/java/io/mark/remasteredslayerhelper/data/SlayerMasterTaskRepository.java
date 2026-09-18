package io.mark.remasteredslayerhelper.data;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class SlayerMasterTaskRepository {

	@Inject
	private Client client;

	@Getter
	private final Map<SlayerMaster, List<Integer>> masters = new HashMap<>();

	public void load() {
		masters.clear();

		Map<Integer, Integer> rowToTaskId = new HashMap<>();
		for (Integer dbTableRow : client.getDBTableRows(DBTableID.SlayerTask.ID)) {
			Integer taskId = (Integer) client.getDBTableField(dbTableRow, DBTableID.SlayerTask.COL_ID, 0)[0];
			rowToTaskId.put(dbTableRow, taskId);
		}

		for (Integer dbTableRow : client.getDBTableRows(DBTableID.SlayerMasterTask.ID)) {
			Integer masterId = (Integer) client.getDBTableField(dbTableRow, DBTableID.SlayerMasterTask.COL_MASTER_ID, 0)[0];
			Integer taskRow = (Integer) client.getDBTableField(dbTableRow, DBTableID.SlayerMasterTask.COL_TASK, 0)[0];
			Integer taskId = rowToTaskId.get(taskRow);
			if (taskId == null) {
				continue;
			}
			for (SlayerMaster master : SlayerMaster.fromId(masterId)) {
				masters.computeIfAbsent(master, k -> new ArrayList<>()).add(taskId);
			}
		}
	}

	public void clear() {
		masters.clear();
	}

}
