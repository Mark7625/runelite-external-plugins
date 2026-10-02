package io.mark.hitsplats.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HealSplatMode
{
	OFF("Off", false, false),
	SPLAT("Splat only", true, false),
	SPLAT_AND_ITEM("Splat with item", true, true);

	private final String displayName;
	private final boolean enabled;
	private final boolean item;

	@Override
	public String toString()
	{
		return displayName;
	}
}
