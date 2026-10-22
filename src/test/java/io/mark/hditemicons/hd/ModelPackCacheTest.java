package io.mark.hditemicons.hd;

import io.mark.hditemicons.IconQuality;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.util.Filepath;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ModelPackCacheTest {
	@Test
	public void diskCacheNamespacesSeparatePacksAndOriginalIcons() {
		Filepath root = mock(Filepath.class);
		List<String> namespaces = new ArrayList<>();
		when(root.joinSegment(anyString())).thenAnswer(call -> {
			namespaces.add(call.getArgument(0));
			return mock(Filepath.class);
		});
		for (String pack : new String[]{"", "aaaa", "bbbb", "aaaa", ""})
			new ItemIconCache(root, IconQuality.NONE, .8, true, false, true, pack, 1440);
		assertNotEquals(namespaces.get(0), namespaces.get(1));
		assertNotEquals(namespaces.get(1), namespaces.get(2));
		assertEquals(namespaces.get(1), namespaces.get(3));
		assertEquals(namespaces.get(0), namespaces.get(4));
	}
}
