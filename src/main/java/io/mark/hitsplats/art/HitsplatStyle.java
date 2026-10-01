package io.mark.hitsplats.art;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HitsplatStyle
{
	STYLE_2002("2002", "2002", 0),
	STYLE_2010("2010", "2010", 0),
	STYLE_2011("2011", "2011", 2);

	private final String directory;
	private final String displayName;
	private final int textOffsetY;

	@Override
	public String toString()
	{
		return displayName;
	}
}
