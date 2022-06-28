package io.mark.hitsplats.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HitsplatIconSet
{
	MODERN("Modern", "modern"),
	OSRS("OSRS", "osrs");

	private final String displayName;
	private final String directory;

	@Override
	public String toString()
	{
		return displayName;
	}
}
