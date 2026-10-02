package io.mark.hitsplats.heal;

import java.util.Arrays;
import javax.inject.Singleton;

@Singleton
public class HealTracker
{
	public static final int BELOW_POSITION = 4;

	private static final int SLOTS = 4;
	private static final int DISPLAY_CYCLES = 70;

	private final HealSplat[] positions = new HealSplat[SLOTS + 1];

	public void add(int cycle, int busySlots, int amount, int itemId)
	{
		int position = BELOW_POSITION;
		for (int slot = 0; slot < SLOTS; slot++)
		{
			HealSplat sitting = positions[slot];
			if ((busySlots & (1 << slot)) == 0 && (sitting == null || sitting.getEndCycle() <= cycle))
			{
				position = slot;
				break;
			}
		}

		positions[position] = new HealSplat(position, cycle + DISPLAY_CYCLES, String.valueOf(amount), itemId);
	}

	public void vacate(int slot)
	{
		if (slot < 0 || slot >= SLOTS)
		{
			return;
		}

		HealSplat sitting = positions[slot];
		if (sitting == null)
		{
			return;
		}

		positions[slot] = null;

		HealSplat below = positions[BELOW_POSITION];
		if (below != null && below.getEndCycle() > sitting.getEndCycle())
		{
			return;
		}

		sitting.setPosition(BELOW_POSITION);
		positions[BELOW_POSITION] = sitting;
	}

	public void prune(int cycle)
	{
		for (int i = 0; i < positions.length; i++)
		{
			HealSplat splat = positions[i];
			if (splat != null && splat.getEndCycle() <= cycle)
			{
				positions[i] = null;
			}
		}
	}

	public HealSplat[] getPositions()
	{
		return positions;
	}

	public boolean isEmpty()
	{
		for (HealSplat splat : positions)
		{
			if (splat != null)
			{
				return false;
			}
		}

		return true;
	}

	public void clear()
	{
		Arrays.fill(positions, null);
	}
}
