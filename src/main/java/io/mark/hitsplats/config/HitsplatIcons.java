package io.mark.hitsplats.config;

import io.mark.hitsplats.art.HitsplatStyle;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum HitsplatIcons
{
	ALL("All", null),
	STYLE_2011("2011", HitsplatStyle.STYLE_2011),
	STYLE_2010("2010", HitsplatStyle.STYLE_2010),
	STYLE_2002("2002", HitsplatStyle.STYLE_2002),
	OFF("Off", null);

	private final String displayName;
	private final HitsplatStyle style;

	public boolean shows(HitsplatStyle current)
	{
		if (this == ALL)
		{
			return true;
		}

		if (this == OFF)
		{
			return false;
		}

		return style == current;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
