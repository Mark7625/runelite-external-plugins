package io.mark.hitsplats.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HitsplatTint
{
		GAME_DEFAULT("Game default"),
		NEVER("Never"),
		ALWAYS("Always");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
