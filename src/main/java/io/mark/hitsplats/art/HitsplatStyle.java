package io.mark.hitsplats.art;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HitsplatStyle
{
	OSRS("osrs", "OSRS", 0, true),
	STYLE_2002("2002", "2002", -1, true),
	STYLE_2010("2010", "2010", 0, true),
	STYLE_2011("2011", "2011", -2, false);

	private final String directory;
	private final String displayName;
	private final int textOffsetY;
	private final boolean shadowed;

	@Override
	public String toString()
	{
		return displayName;
	}
}
