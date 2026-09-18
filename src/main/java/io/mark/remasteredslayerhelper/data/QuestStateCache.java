package io.mark.remasteredslayerhelper.data;

import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;

import javax.inject.Singleton;
import java.util.EnumMap;
import java.util.Map;

/**
 * Quest.getState(Client) calls client.runScript() internally, which asserts it's running on
 * the client thread. UI code (Swing renderers, panels) runs on the EDT, so quest state for the
 * masters' unlock requirements is refreshed here on the client thread and read from a cached
 * snapshot everywhere else.
 */
@Singleton
public class QuestStateCache {

	private volatile Map<Quest, QuestState> states = new EnumMap<>(Quest.class);

	/**
	 * Must be called on the client thread.
	 */
	public void refresh(Client client) {
		Map<Quest, QuestState> updated = new EnumMap<>(Quest.class);
		for (SlayerMaster master : SlayerMaster.values()) {
			Quest quest = master.getRequiredQuest();
			if (quest != null) {
				updated.put(quest, quest.getState(client));
			}
		}
		states = updated;
	}

	/**
	 * Safe to call from any thread.
	 */
	public QuestState getState(Quest quest) {
		return states.getOrDefault(quest, QuestState.NOT_STARTED);
	}
}
