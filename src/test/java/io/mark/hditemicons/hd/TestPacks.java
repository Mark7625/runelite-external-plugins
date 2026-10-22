package io.mark.hditemicons.hd;

import com.google.gson.Gson;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.runelite.api.gameval.ItemID;
import net.runelite.http.api.RuneLiteAPI;

final class TestPacks {
	static final Gson GSON = RuneLiteAPI.GSON;
	static final String TRIANGLE = "v -1 -1 0\nv 1 -1 0\nv 0 1 0\n"
		+ "vt 0 0\nvt 1 0\nvt 0.5 1\nf 1/1 2/2 3/3\n";

	static String manifest(String entryFields) {
		return "{\"formatVersion\":1,\"name\":\"Test models\",\"items\":{\"" + ItemID.ABYSSAL_WHIP
			+ "\":{\"model\":\"model.obj\"" + entryFields + "}}}";
	}

	static byte[] pack(String entryFields) throws IOException {
		return archive(manifest(entryFields), Map.of("model.obj", TRIANGLE.getBytes(StandardCharsets.UTF_8)));
	}

	static byte[] archive(String manifest, Map<String, byte[]> files) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("pack.json"));
			zip.write(manifest.getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			for (Map.Entry<String, byte[]> file : files.entrySet()) {
				zip.putNextEntry(new ZipEntry(file.getKey()));
				zip.write(file.getValue());
				zip.closeEntry();
			}
		}
		return bytes.toByteArray();
	}
}
