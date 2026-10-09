package io.mark.hditemicons;

import java.util.List;
import java.util.Set;

public final class SimilarItems {
	public final List<NamedItem> items;
	public final Set<Integer> variants;

	public SimilarItems(List<NamedItem> items, Set<Integer> variants) {
		this.items = items;
		this.variants = variants;
	}
}
