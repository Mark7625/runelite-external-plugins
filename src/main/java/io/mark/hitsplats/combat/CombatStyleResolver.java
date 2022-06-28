package io.mark.hitsplats.combat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Projectile;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.kit.KitType;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

@Singleton
public class CombatStyleResolver
{
	private static final int MATCH_WINDOW_CYCLES = 40;
	private static final int PRUNE_AFTER_CYCLES = 30;

	private static final CombatStyle UNATTRIBUTED_PROJECTILE_STYLE = CombatStyle.MAGIC;

	private final ItemManager itemManager;
	private final CombatStyleTables tables;
	private final List<InFlight> inFlight = new ArrayList<>();

	@Inject
	private CombatStyleResolver(ItemManager itemManager, CombatStyleTables tables)
	{
		this.itemManager = itemManager;
		this.tables = tables;
	}

	public void onProjectileMoved(Projectile projectile)
	{
		for (InFlight seen : inFlight)
		{
			if (seen.projectile == projectile && seen.startCycle == projectile.getStartCycle())
			{
				return;
			}
		}

		inFlight.add(new InFlight(projectile, projectile.getStartCycle(), projectile.getEndCycle(),
			projectile.getTargetActor(), styleOf(projectile)));
	}

	public CombatStyle resolve(Actor target, int cycle)
	{
		prune(cycle);

		CombatStyle style = CombatStyle.MELEE;
		int closest = Integer.MAX_VALUE;

		for (InFlight seen : inFlight)
		{
			if (seen.target != target)
			{
				continue;
			}

			int delta = Math.abs(cycle - seen.endCycle);
			if (delta <= MATCH_WINDOW_CYCLES && delta < closest)
			{
				closest = delta;
				style = seen.style;
			}
		}

		return style;
	}

	public void clear()
	{
		inFlight.clear();
	}

	private void prune(int cycle)
	{
		for (Iterator<InFlight> it = inFlight.iterator(); it.hasNext(); )
		{
			if (cycle - it.next().endCycle > PRUNE_AFTER_CYCLES)
			{
				it.remove();
			}
		}
	}

	private CombatStyle styleOf(Projectile projectile)
	{
		if (isCannonball(projectile.getId()))
		{
			return CombatStyle.CANNON;
		}

		CombatStyle projectileStyle = tables.forProjectile(projectile.getId());
		if (projectileStyle != null)
		{
			return projectileStyle;
		}

		Actor source = projectile.getSourceActor();
		if (source == null)
		{
			return UNATTRIBUTED_PROJECTILE_STYLE;
		}

		if (source instanceof Player)
		{
			CombatStyle weaponStyle = weaponStyle((Player) source);
			if (weaponStyle == CombatStyle.RANGED)
			{
				return CombatStyle.RANGED;
			}

			if (weaponStyle == CombatStyle.MAGIC)
			{
				return CombatStyle.MAGIC;
			}
		}

		CombatStyle animationStyle = tables.forAnimation(source.getAnimation());
		if (animationStyle == CombatStyle.RANGED || animationStyle == CombatStyle.MAGIC)
		{
			return animationStyle;
		}

		return UNATTRIBUTED_PROJECTILE_STYLE;
	}

	private CombatStyle weaponStyle(Player player)
	{
		PlayerComposition composition = player.getPlayerComposition();
		if (composition == null)
		{
			return null;
		}

		int weaponId = composition.getEquipmentId(KitType.WEAPON);
		if (weaponId <= 0)
		{
			return null;
		}

		ItemStats stats = itemManager.getItemStats(weaponId);
		if (stats == null || stats.getEquipment() == null)
		{
			return null;
		}

		ItemEquipmentStats equipment = stats.getEquipment();
		int melee = Math.max(equipment.getAstab(), Math.max(equipment.getAslash(), equipment.getAcrush()));

		if (equipment.getArange() > melee && equipment.getArange() >= equipment.getAmagic())
		{
			return CombatStyle.RANGED;
		}

		if (equipment.getAmagic() > melee)
		{
			return CombatStyle.MAGIC;
		}

		return CombatStyle.MELEE;
	}

	private static boolean isCannonball(int projectileId)
	{
		return projectileId == SpotanimID.CANNONBALL_TRAVEL
			|| projectileId == SpotanimID.CANNONBALL_TRAVEL_GRANITE
			|| projectileId == SpotanimID.CANNONBALL_TRAVEL_LEAGUE
			|| projectileId == SpotanimID.CANNONBALL_TRAVEL_GRANITE_LEAGUE;
	}

	private static class InFlight
	{
		private final Projectile projectile;
		private final int startCycle;
		private final int endCycle;
		private final Actor target;
		private final CombatStyle style;

		private InFlight(Projectile projectile, int startCycle, int endCycle, Actor target, CombatStyle style)
		{
			this.projectile = projectile;
			this.startCycle = startCycle;
			this.endCycle = endCycle;
			this.target = target;
			this.style = style;
		}
	}
}
