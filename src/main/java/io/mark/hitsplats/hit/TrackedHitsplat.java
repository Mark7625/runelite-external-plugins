package io.mark.hitsplats.hit;

import io.mark.hitsplats.art.HitsplatSkin;
import io.mark.hitsplats.combat.CombatStyle;
import lombok.Getter;

@Getter
public class TrackedHitsplat
{
	private final int slot;
	private final int startCycle;
	private final int endCycle;
	private final HitsplatSkin skin;
	private final CombatStyle combatStyle;
	private final String text;
	private final boolean block;

	TrackedHitsplat(int slot, int startCycle, int endCycle, HitsplatSkin skin, CombatStyle combatStyle, String text, boolean block)
	{
		this.slot = slot;
		this.startCycle = startCycle;
		this.endCycle = endCycle;
		this.skin = skin;
		this.combatStyle = combatStyle;
		this.text = text;
		this.block = block;
	}
}
