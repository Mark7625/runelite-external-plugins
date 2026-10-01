package io.mark.hitsplats.hit;

import io.mark.hitsplats.art.HitsplatSkin;
import io.mark.hitsplats.combat.CombatStyle;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;
import net.runelite.api.Actor;

@Singleton
public class HitsplatTracker
{
	private static final int MAX_SLOTS = 4;
	private static final int DURATION_CYCLES = 90;

	private final Map<Actor, TrackedHitsplat[]> tracked = new HashMap<>();

	public void add(Actor actor, int cycle, HitsplatSkin skin, CombatStyle combatStyle, String text, boolean block)
	{
		TrackedHitsplat[] slots = tracked.computeIfAbsent(actor, a -> new TrackedHitsplat[MAX_SLOTS]);

		int slotIndex = 0;
		for (int i = 0; i < MAX_SLOTS; i++)
		{
			if (slots[i] == null || cycle >= slots[i].getEndCycle())
			{
				slotIndex = i;
				break;
			}
		}

		slots[slotIndex] = new TrackedHitsplat(slotIndex, cycle, cycle + DURATION_CYCLES, skin, combatStyle, text, block);
	}

	public void remove(Actor actor)
	{
		tracked.remove(actor);
	}

	public void clear()
	{
		tracked.clear();
	}

	public Map<Actor, TrackedHitsplat[]> getTracked()
	{
		return tracked;
	}
}
