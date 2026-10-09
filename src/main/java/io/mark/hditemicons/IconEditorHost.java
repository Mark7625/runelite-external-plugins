package io.mark.hditemicons;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public interface IconEditorHost {
	void iconsChanged(Collection<Integer> itemIds);

	void allIconsChanged();

	void findSimilarItems(int itemId, Consumer<SimilarItems> onFound);

	void itemIndex(Consumer<List<NamedItem>> onReady);

	void editItem(int itemId);
}
