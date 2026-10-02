package io.mark.hitsplats.heal;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.plugins.itemstats.Effect;
import net.runelite.client.plugins.itemstats.ItemStatChangesService;
import net.runelite.client.plugins.itemstats.StatChange;
import net.runelite.client.plugins.itemstats.stats.Stats;

@Singleton
public class HealSource
{
	private static final int CLAIM_WINDOW_TICKS = 4;

	private final Client client;
	private final ItemStatChangesService itemStatChanges;

	private int itemId = -1;
	private int restored;
	private int clickedTick;

	@Inject
	private HealSource(Client client, ItemStatChangesService itemStatChanges)
	{
		this.client = client;
		this.itemStatChanges = itemStatChanges;
	}

	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!event.isItemOp())
		{
			return;
		}

		int clicked = event.getItemId();
		if (clicked <= 0)
		{
			return;
		}

		int restores = hitpointsRestored(clicked);
		if (restores <= 0)
		{
			return;
		}

		itemId = clicked;
		restored = restores;
		clickedTick = client.getTickCount();
	}

	public UsedItem claim()
	{
		if (itemId <= 0 || client.getTickCount() - clickedTick > CLAIM_WINDOW_TICKS)
		{
			return null;
		}

		UsedItem claimed = new UsedItem(itemId, restored);
		itemId = -1;
		return claimed;
	}

	public void clear()
	{
		itemId = -1;
	}

	private int hitpointsRestored(int id)
	{
		Effect effect = itemStatChanges.getItemStatChanges(id);
		if (effect == null)
		{
			return 0;
		}

		for (StatChange change : effect.calculate(client).getStatChanges())
		{
			if (change.getTheoretical() > 0 && Stats.HITPOINTS.getName().equals(change.getStat().getName()))
			{
				return change.getTheoretical();
			}
		}

		return 0;
	}

	@Getter
	@RequiredArgsConstructor
	public static class UsedItem
	{
		private final int itemId;
		private final int restored;
	}
}
