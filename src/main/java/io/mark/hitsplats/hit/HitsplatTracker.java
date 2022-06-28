package io.mark.hitsplats.hit;

import io.mark.hitsplats.art.HitsplatSkin;
import io.mark.hitsplats.combat.CombatStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import net.runelite.api.Actor;

@Singleton
public class HitsplatTracker
{
	private static final int MAX_SLOTS = 4;

	private final Map<Actor, List<TrackedHitsplat>> tracked = new HashMap<>();

	public void add(Actor actor, int cycle, int endCycle, HitsplatSkin skin, CombatStyle combatStyle, String text, boolean block)
	{
		List<TrackedHitsplat> splats = tracked.computeIfAbsent(actor, a -> new ArrayList<>(MAX_SLOTS));

		boolean full = splats.size() >= MAX_SLOTS;
		int cursor = 0;
		for (int i = 0; i < splats.size(); i++)
		{
			if (splats.get(i).getEndCycle() > cycle)
			{
				cursor = (i + 1) % MAX_SLOTS;
			}
			else
			{
				full = false;
			}
		}

		int slot = -1;
		if (full)
		{
			int earliest = Integer.MAX_VALUE;
			for (int i = 0; i < splats.size(); i++)
			{
				if (splats.get(i).getEndCycle() < earliest)
				{
					earliest = splats.get(i).getEndCycle();
					slot = i;
				}
			}
		}
		else
		{
			for (int attempt = 0; attempt < MAX_SLOTS; attempt++)
			{
				int index = cursor;
				cursor = (cursor + 1) % MAX_SLOTS;

				if (index >= splats.size())
				{
					splats.add(null);
					slot = splats.size() - 1;
					break;
				}

				if (splats.get(index).getEndCycle() <= cycle)
				{
					slot = index;
					break;
				}
			}
		}

		if (slot < 0)
		{
			return;
		}

		splats.set(slot, new TrackedHitsplat(slot, endCycle, skin, combatStyle, text, block));
	}

	public void remove(Actor actor)
	{
		tracked.remove(actor);
	}

	public void clear()
	{
		tracked.clear();
	}

	public Map<Actor, List<TrackedHitsplat>> getTracked()
	{
		return tracked;
	}
}
