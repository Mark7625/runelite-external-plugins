package io.mark.hitsplats.config;

import io.mark.hitsplats.art.HitsplatStyle;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HitsplatBlockArt
{
	DEFAULT("Default", null),
	STYLE_2010("2010", HitsplatStyle.STYLE_2010),
	STYLE_2011("2011", HitsplatStyle.STYLE_2011);

	private final String displayName;
	private final HitsplatStyle style;

	@Override
	public String toString()
	{
		return displayName;
	}
}
