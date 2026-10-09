package io.mark.hditemicons;

import java.util.Locale;

public final class NamedItem {
	public final int id;
	public final String name;
	private final String searchName;

	public NamedItem(int id, String name) {
		this.id = id;
		this.name = name;
		this.searchName = name.toLowerCase(Locale.ROOT);
	}

	public boolean nameContains(String lowerCaseQuery) {
		return searchName.contains(lowerCaseQuery);
	}

	public boolean nameStartsWith(String lowerCaseQuery) {
		return searchName.startsWith(lowerCaseQuery);
	}

	@Override
	public String toString() {
		return name + " (" + id + ")";
	}
}
