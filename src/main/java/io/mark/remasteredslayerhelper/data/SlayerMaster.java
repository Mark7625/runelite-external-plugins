package io.mark.remasteredslayerhelper.data;

import java.util.ArrayList;
import java.util.List;

public enum SlayerMaster {
	TURAEL(1, "Turael"),
	AYA(1, "Aya"),
	MAZCHNA(2, "Mazchna"),
	ACHTRYN(2, "Achtryn"),
	VANNAKA(3, "Vannaka"),
	CHAELDAR(4, "Chaeldar"),
	DURADEL(5, "Duradel"),
	NIEVE(6, "Nieve"),
	STEVE(6, "Steve"),
	KRYSTILIA(7, "Krystilia"),
	KONAR(8, "Konar quo Maten"),
	SPIRA(9, "Spria");

	private final int id;
	private final String displayName;

	SlayerMaster(int id, String displayName) {
		this.id = id;
		this.displayName = displayName;
	}

	public int getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	/**
	 * Lookup all master variants by ID
	 */
	public static List<SlayerMaster> fromId(int id) {
		List<SlayerMaster> result = new ArrayList<>();
		for (SlayerMaster master : values()) {
			if (master.id == id) {
				result.add(master);
			}
		}
		return result;
	}
}