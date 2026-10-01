package io.mark.hitsplats.combat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CombatStyle
{
	MELEE("meele"),
	RANGED("range"),
	MAGIC("magic"),
	CANNON("cannon"),
	DEFENCE("defence");

	private final String fileName;
}
