package io.mark.hitsplats.art;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import net.runelite.api.HitsplatID;
import org.junit.Test;

public class HitsplatSkinTest
{
	@Test
	public void meAndOtherPickBrightAndTintedArt()
	{
		assertEquals(HitsplatSkin.DAMAGE, HitsplatSkin.forType(HitsplatID.DAMAGE_ME, true));
		assertEquals(HitsplatSkin.DAMAGE_TINTED, HitsplatSkin.forType(HitsplatID.DAMAGE_OTHER, true));
		assertEquals(HitsplatSkin.BLOCK, HitsplatSkin.forType(HitsplatID.BLOCK_ME, false));
		assertEquals(HitsplatSkin.BLOCK_TINTED, HitsplatSkin.forType(HitsplatID.BLOCK_OTHER, true));
	}

	@Test
	public void typesWithoutAnOtherVariantFallBackToWhoTheHitIsOn()
	{
		assertEquals(HitsplatSkin.POISON, HitsplatSkin.forType(HitsplatID.POISON, true));
		assertEquals(HitsplatSkin.POISON_TINTED, HitsplatSkin.forType(HitsplatID.POISON, false));
		assertEquals(HitsplatSkin.VENOM, HitsplatSkin.forType(HitsplatID.VENOM, true));
		assertEquals(HitsplatSkin.VENOM_TINTED, HitsplatSkin.forType(HitsplatID.VENOM, false));
	}

	@Test
	public void onlyReplacedTypesAreSupported()
	{
		assertTrue(HitsplatSkin.isSupported(HitsplatID.DAMAGE_ME));
		assertTrue(HitsplatSkin.isSupported(HitsplatID.VENOM));

		assertFalse(HitsplatSkin.isSupported(HitsplatID.BURN));
		assertFalse(HitsplatSkin.isSupported(HitsplatID.DOOM));
		assertFalse(HitsplatSkin.isSupported(HitsplatID.HEAL));
	}

	@Test
	public void blocksAreTheTypesThatLandForNothing()
	{
		assertTrue(HitsplatSkin.isBlock(HitsplatID.BLOCK_ME));
		assertTrue(HitsplatSkin.isBlock(HitsplatID.BLOCK_OTHER));
		assertFalse(HitsplatSkin.isBlock(HitsplatID.DAMAGE_ME));
	}

	@Test
	public void tintingIsReversible()
	{
		for (HitsplatSkin skin : HitsplatSkin.values())
		{
			assertEquals(skin.untinted(), skin.tinted().untinted());
			assertEquals(skin.tinted(), skin.untinted().tinted());
		}
	}

	@Test
	public void everySkinHasAFileToLookFor()
	{
		for (HitsplatSkin skin : HitsplatSkin.values())
		{
			assertNotEquals(skin + " has no file names", 0, skin.getFileNames().length);
		}
	}
}
