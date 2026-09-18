package io.mark.remasteredslayerhelper.data;

import com.google.gson.annotations.SerializedName;
import io.mark.remasteredslayerhelper.domain.Item;
import io.mark.remasteredslayerhelper.domain.SlayerRequiredItem;
import io.mark.remasteredslayerhelper.domain.SlayerTask;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Combined slayer task data from game DB and slayerTasks.json (keyed by task id).
 * Deserialized directly from JSON (taskid, locations, itemsRequired, etc.); DB fields merged in repository.
 */
@Getter
@Setter
public class SlayerTaskInfo {
	@SerializedName("taskid")
	private int id;
	private String name;
	private int combatLevel;
	private Map<Skill, Integer> requirementsNeedsAll;

	private int slayerLevel;
	private List<String> locations = new ArrayList<>();
	private List<String> itemsRequired = new ArrayList<>();
	private List<String> attributes = new ArrayList<>();
	private List<String> attackStyles = new ArrayList<>();
	private List<String> alternatives = new ArrayList<>();
	private List<SlayerMaster> slayerMasters = new ArrayList<>();

	public SlayerTaskInfo(int id, String name, int combatLevel) {
		this.id = id;
		this.name = name;
		this.combatLevel = combatLevel;
	}

	public void copyFrom(SlayerTaskInfo other) {
		setLocations(copyList(other.getLocations()));
		setItemsRequired(copyList(other.getItemsRequired()));
		setAttributes(copyList(other.getAttributes()));
		setAttackStyles(copyList(other.getAttackStyles()));
		setAlternatives(copyList(other.getAlternatives()));
	}

	private static List<String> copyList(List<String> list) {
		return list != null && !list.isEmpty() ? new ArrayList<>(list) : new ArrayList<>();
	}

	public SlayerTask toSlayerTask() {
		if (name == null || name.isEmpty()) {
			return null;
		}
		Item[] items = itemsRequired.stream()
			.map(itemName -> {
				SlayerRequiredItem req = SlayerRequiredItem.fromDisplayName(itemName);
				Integer id = req != null ? req.getItemId() : null;
				return new Item(itemName, null, id);
			})
			.toArray(Item[]::new);
		return new SlayerTask(
			name,
			slayerLevel,
			toArray(locations),
			items,
			toArray(attributes),
			toArray(attackStyles),
			toArray(alternatives),
			slayerMasters
		);
	}

	private static String[] toArray(List<String> list) {
		if (list == null) return new String[0];
		return list.toArray(new String[0]);
	}

	public boolean hasJsonData() {
		return locations != null && !locations.isEmpty();
	}
}
