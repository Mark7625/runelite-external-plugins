package io.mark.hditemicons.hd;

import io.mark.hditemicons.CustomRotation;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.imageio.ImageIO;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static io.mark.hditemicons.hd.TestPacks.*;
import static org.junit.Assert.*;

public class ModelPackTest {
	@Test
	public void mapsExactIdsAndRoundTripsAssetsAndEditedCamera() throws Exception {
		ModelPack original = ModelPack.parse(pack(",\"color\":\"#65dce8\""), GSON);
		assertEquals(1, original.items.size());
		assertNotNull(original.items.get(ItemID.ABYSSAL_WHIP));
		assertNull(original.items.get(ItemID.COINS));
		CustomRotation edited = new CustomRotation(128, 256, 64, 1900, 3, -2, 128, 140, 100);
		byte[] exported = original.export(GSON, Map.of(ItemID.ABYSSAL_WHIP, edited));
		ModelPack imported = ModelPack.parse(exported, GSON);
		assertEquals(edited, imported.items.get(ItemID.ABYSSAL_WHIP).camera);
		assertArrayEquals(original.items.get(ItemID.ABYSSAL_WHIP).model.vertexX,
			imported.items.get(ItemID.ABYSSAL_WHIP).model.vertexX, 0);
		assertArrayEquals(original.items.get(ItemID.ABYSSAL_WHIP).model.customColors,
			imported.items.get(ItemID.ABYSSAL_WHIP).model.customColors);
	}

	@Test
	public void contentFingerprintSurvivesPersistenceAndChangesWithAssets() throws Exception {
		ModelPack original = ModelPack.parse(pack(""), GSON);
		assertEquals(original.fingerprint, ModelPack.parse(original.archive(), GSON).fingerprint);
		assertNotEquals(original.fingerprint, ModelPack.parse(pack(",\"color\":\"#ff0000\""), GSON).fingerprint);
		assertNotEquals(original.fingerprint,
			ModelPack.parse(pack(",\"camera\":{\"yaw\":128}"), GSON).fingerprint);
	}

	@Test
	public void rejectsMissingAssetsBadVersionsAndOutOfRangeCamera() throws Exception {
		assertThrows(IOException.class, () -> ModelPack.parse(archive(manifest(""), Map.of()), GSON));
		assertThrows(IOException.class, () -> ModelPack.parse(archive(manifest("").replace("\"formatVersion\":1", "\"formatVersion\":2"),
			Map.of("model.obj", TRIANGLE.getBytes(StandardCharsets.UTF_8))), GSON));
		for (String fields : new String[]{",\"camera\":{\"zoom\":0}", ",\"camera\":{\"pitch\":2048}",
			",\"camera\":{\"scaleX\":0}", ",\"camera\":{\"zoom\":2200.5}", ",\"camera\":{\"zoom\":4294969496}",
			",\"color\":\"red\"", ",\"scrollSpeed\":1"})
			assertThrows(IOException.class, () -> ModelPack.parse(pack(fields), GSON));
	}

	@Test
	public void rejectsArchiveAndManifestPathEscapes() throws Exception {
		for (String path : new String[]{"../model.obj", "/model.obj", "C:/model.obj", "models/../model.obj", "models\\model.obj"}) {
			assertThrows(IOException.class, () -> ModelPack.parse(archive(manifest(""), Map.of(path, new byte[0])), GSON));
		}
		assertThrows(IOException.class, () -> ModelPack.parse(archive(manifest("").replace("model.obj", "../model.obj"), Map.of()), GSON));
	}

	@Test
	public void rejectsOversizedExpandedFilesEvenWhenCompressedTiny() throws Exception {
		byte[] bomb = archive(manifest(""), Map.of("model.obj", new byte[4 * 1024 * 1024 + 1]));
		assertTrue(bomb.length < 10000);
		assertThrows(IOException.class, () -> ModelPack.parse(bomb, GSON));
		assertThrows(IOException.class, () -> ModelPack.parse(new byte[ModelPack.MAX_ARCHIVE_BYTES + 1], GSON));
	}

	@Test
	public void validatesTextureDimensionsAndUvPresence() throws Exception {
		byte[] rectangular = png(new BufferedImage(8, 16, BufferedImage.TYPE_INT_ARGB));
		assertThrows(IOException.class, () -> ModelPack.parse(textured(TRIANGLE, rectangular), GSON));
		byte[] square = png(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB));
		String noUv = TRIANGLE.replace("f 1/1 2/2 3/3", "f 1 2 3");
		assertThrows(IOException.class, () -> ModelPack.parse(textured(noUv, square), GSON));
		assertNotNull(ModelPack.parse(textured(TRIANGLE, square), GSON));
	}

	static byte[] textured(String mesh, byte[] png) throws IOException {
		return archive(manifest(",\"texture\":\"texture.png\",\"scrollDirection\":4,\"scrollSpeed\":1"),
			Map.of("model.obj", mesh.getBytes(StandardCharsets.UTF_8), "texture.png", png));
	}

	static byte[] png(BufferedImage image) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(image, "png", bytes);
		return bytes.toByteArray();
	}
}
