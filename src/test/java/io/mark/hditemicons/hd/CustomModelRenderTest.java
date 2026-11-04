package io.mark.hditemicons.hd;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import net.runelite.api.Model;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static io.mark.hditemicons.hd.TestPacks.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CustomModelRenderTest {
	private static final int[] PALETTE = ItemIconRasterizer.buildPalette(.8);

	@Test
	public void shippedExampleParsesAndRendersWithItsPackCamera() throws Exception {
		String manifest;
		byte[] mesh;
		try (InputStream input = getClass().getResourceAsStream("/model-pack/pack.json")) {
			manifest = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		try (InputStream input = getClass().getResourceAsStream("/model-pack/models/crystal.obj")) {
			mesh = input.readAllBytes();
		}
		ModelPack pack = ModelPack.parse(archive(manifest, Map.of("models/crystal.obj", mesh)), GSON);
		ModelPack.Entry entry = pack.items.get(ItemID.ABYSSAL_WHIP);
		ItemIconRasterizer renderer = new ItemIconRasterizer(entry.model,
			entry.camera.xan2d, entry.camera.yan2d, entry.camera.zan2d, 2);
		renderer.placeExplicitly(entry.camera.zoom2d, entry.camera.offsetX, entry.camera.offsetY);
		assertTrue(Arrays.stream(renderer.render(2, true, false, PALETTE)).filter(p -> p != 0).count() > 100);
	}

	@Test
	public void customGeometryActuallyRendersWithTransparentBackgroundAndMaterialColour() throws Exception {
		ModelPack pack = ModelPack.parse(pack(",\"color\":\"#ff0000\""), GSON);
		ItemIconRasterizer renderer = renderer(pack.items.get(ItemID.ABYSSAL_WHIP).model);
		int[] pixels = renderer.render(0, false, false, PALETTE);
		long filled = Arrays.stream(pixels).filter(p -> p != 0).count();
		assertTrue(filled > 100 && filled < pixels.length);
		assertEquals(0, pixels[0]);
		assertTrue(Arrays.stream(pixels).anyMatch(p -> (p >>> 24) > 0 && (p & 0xff0000) != 0));
		assertTrue(Arrays.stream(pixels).allMatch(p -> (p & 0x00ffff) == 0));
	}

	@Test
	public void texturedUvRenderingAnimatesAndLoops() throws Exception {
		BufferedImage texture = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 8; y++)
			for (int x = 0; x < 8; x++)
				texture.setRGB(x, y, x < 4 ? 0xffff0000 : 0xff0000ff);
		ModelPack pack = ModelPack.parse(ModelPackTest.textured(TRIANGLE, ModelPackTest.png(texture)), GSON);
		ItemIconRasterizer.IconModel model = pack.items.get(ItemID.ABYSSAL_WHIP).model;
		assertEquals(8, model.animationPeriod);
		ItemIconRasterizer renderer = renderer(model);
		renderer.animateTo(0);
		int[] first = renderer.render(0, false, false, PALETTE);
		assertTrue(Arrays.stream(first).anyMatch(p -> (p & 0xff0000) != 0));
		assertTrue(Arrays.stream(first).anyMatch(p -> (p & 0x0000ff) != 0));
		renderer.animateTo(1);
		assertFalse(Arrays.equals(first, renderer.render(0, false, false, PALETTE)));
		renderer.animateTo(8);
		assertArrayEquals(first, renderer.render(0, false, false, PALETTE));
	}

	@Test
	public void transparentTextureProducesNoSilhouetteOrOutline() throws Exception {
		BufferedImage transparent = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
		ModelPack pack = ModelPack.parse(ModelPackTest.textured(TRIANGLE, ModelPackTest.png(transparent)), GSON);
		ItemIconRasterizer renderer = renderer(pack.items.get(ItemID.ABYSSAL_WHIP).model);
		assertTrue(Arrays.stream(renderer.render(2, true, false, PALETTE)).allMatch(p -> p == 0));
	}

	@Test
	public void nativeModelCaptureStillUsesOriginalRasterizerPath() {
		Model model = mock(Model.class);
		when(model.getVerticesCount()).thenReturn(3);
		when(model.getFaceCount()).thenReturn(1);
		when(model.getVerticesX()).thenReturn(new float[]{-50, 50, 0});
		when(model.getVerticesY()).thenReturn(new float[]{50, 50, -50});
		when(model.getVerticesZ()).thenReturn(new float[]{0, 0, 0});
		when(model.getFaceIndices1()).thenReturn(new int[]{0});
		when(model.getFaceIndices2()).thenReturn(new int[]{1});
		when(model.getFaceIndices3()).thenReturn(new int[]{2});
		when(model.getFaceColors1()).thenReturn(new int[]{12000});
		when(model.getFaceColors2()).thenReturn(new int[]{12000});
		when(model.getFaceColors3()).thenReturn(new int[]{-1});
		ItemIconRasterizer.IconModel captured = ItemIconRasterizer.IconModel.capture(model, null);
		assertNull(captured.customColors);
		assertNull(captured.customUv);
		assertTrue(Arrays.stream(renderer(captured).render(0, false, false, PALETTE)).anyMatch(p -> p != 0));
	}

	private static ItemIconRasterizer renderer(ItemIconRasterizer.IconModel model) {
		ItemIconRasterizer renderer = new ItemIconRasterizer(model, 0, 0, 0, 2);
		renderer.placeExplicitly(2200, 0, 0);
		return renderer;
	}
}
