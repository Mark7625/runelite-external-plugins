package io.mark.hitsplats.heal;

import lombok.Getter;

@Getter
public class HealSplat
{
	private final int endCycle;
	private final String text;
	private final int itemId;

	private int position;

	HealSplat(int position, int endCycle, String text, int itemId)
	{
		this.position = position;
		this.endCycle = endCycle;
		this.text = text;
		this.itemId = itemId;
	}

	void setPosition(int position)
	{
		this.position = position;
	}
}
