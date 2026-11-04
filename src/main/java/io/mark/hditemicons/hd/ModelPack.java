package io.mark.hditemicons.hd;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.mark.hditemicons.CustomRotation;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Immutable, validated assets. Parsing and export run on a worker; archives are never extracted. */
final class ModelPack {
	static final int MAX_ARCHIVE_BYTES = 16 * 1024 * 1024;
	private static final int MAX_EXPANDED_BYTES = 32 * 1024 * 1024;
	private static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
	final String name, author, fingerprint;
	final Map<Integer, Entry> items;
	private final Map<String, byte[]> files;
	private final byte[] archive;

	static final class Entry {
		final ItemIconRasterizer.IconModel model;
		final CustomRotation camera;

		Entry(ItemIconRasterizer.IconModel model, CustomRotation camera) {
			this.model = model;
			this.camera = camera;
		}
	}

	private ModelPack(String name, String author, String fingerprint, Map<Integer, Entry> items, Map<String, byte[]> files, byte[] archive) {
		this.name = name;
		this.author = author;
		this.fingerprint = fingerprint;
		this.items = Collections.unmodifiableMap(items);
		this.files = files;
		this.archive = archive;
	}

	byte[] archive() {
		return archive.clone();
	}

	static ModelPack read(InputStream source, Gson gson) throws IOException {
		return parse(readBounded(source, MAX_ARCHIVE_BYTES), gson);
	}

	static ModelPack parse(byte[] bytes, Gson gson) throws IOException {
		if (bytes.length > MAX_ARCHIVE_BYTES)
			throw new IOException("Model pack exceeds 16 MiB");
		Map<String, byte[]> files = new LinkedHashMap<>();
		int expanded = 0, count = 0;
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (++count > 512)
					throw new IOException("Model pack has too many files");
				if (entry.isDirectory())
					continue;
				String path = validPath(entry.getName());
				byte[] contents = readBounded(zip, Math.min(MAX_FILE_BYTES, MAX_EXPANDED_BYTES - expanded));
				expanded += contents.length;
				if (files.putIfAbsent(path, contents) != null)
					throw new IOException("Duplicate pack file: " + path);
			}
		}
		try {
			JsonObject manifest = gson.fromJson(text(files, "pack.json"), JsonObject.class);
			if (manifest == null || integer(manifest.get("formatVersion")) != 1)
				throw new IOException("Expected model pack formatVersion 1");
			String name = manifest.get("name").getAsString().trim();
			String author = manifest.has("author") ? manifest.get("author").getAsString().trim() : "";
			if (name.isEmpty() || name.length() > 100 || author.length() > 100)
				throw new IOException("Pack name/author must be at most 100 characters; name is required");
			JsonObject entries = manifest.getAsJsonObject("items");
			if (entries == null || entries.size() == 0 || entries.size() > 256)
				throw new IOException("A pack must map between 1 and 256 item IDs");
			Map<Integer, Entry> items = new LinkedHashMap<>();
			Map<String, ObjMesh> meshes = new HashMap<>();
			Map<String, int[]> textures = new HashMap<>();
			int triangles = 0, pixels = 0;
			for (Map.Entry<String, com.google.gson.JsonElement> mapping : entries.entrySet()) {
				int id = Integer.parseInt(mapping.getKey());
				if (id < 0 || id > 0xffff || !Integer.toString(id).equals(mapping.getKey()))
					throw new IOException("Item IDs must be canonical integers between 0 and 65535");
				JsonObject entry = mapping.getValue().getAsJsonObject();
				String meshPath = validPath(entry.get("model").getAsString());
				ObjMesh mesh = meshes.get(meshPath);
				if (mesh == null) {
					mesh = ObjMesh.parse(text(files, meshPath));
					meshes.put(meshPath, mesh);
				}
				triangles += mesh.a.length;
				if (triangles > 50_000)
					throw new IOException("Pack exceeds 50000 mapped triangles");
				int[] texture = null;
				if (entry.has("texture")) {
					String path = validPath(entry.get("texture").getAsString());
					texture = textures.get(path);
					if (texture == null) {
						texture = png(required(files, path));
						textures.put(path, texture);
					}
					// Each mapping owns its scrolling buffers, even when several share a PNG.
					pixels += texture.length;
					if (pixels > 4 * 1024 * 1024)
						throw new IOException("Pack exceeds four million mapped texture pixels");
					for (float[] uv : mesh.uv)
						if (uv == null)
							throw new IOException("Textured models require UVs on every face: " + meshPath);
				}
				String color = entry.has("color") ? entry.get("color").getAsString() : "#b0b0b0";
				if (!color.matches("#[0-9a-fA-F]{6}"))
					throw new IOException("color must be #RRGGBB");
				int direction = value(entry, "scrollDirection", 0);
				int speed = value(entry, "scrollSpeed", 0);
				if (direction < 0 || direction > 4 || speed < 0 || speed > 16 || (speed > 0 && (direction == 0 || texture == null)))
					throw new IOException("Scrolling requires a texture, direction 1-4 and speed 1-16");
				CustomRotation camera = camera(entry.getAsJsonObject("camera"));
				items.put(id, new Entry(ItemIconRasterizer.IconModel.custom(mesh,
					Integer.parseInt(color.substring(1), 16), texture, direction, speed), camera));
			}
			return new ModelPack(name, author, digest(bytes), items, files, bytes.clone());
		} catch (RuntimeException ex) {
			throw new IOException("Invalid pack.json: " + ex.getMessage(), ex);
		}
	}

	private static CustomRotation camera(JsonObject camera) throws IOException {
		int pitch = value(camera, "pitch", 0), yaw = value(camera, "yaw", 0), roll = value(camera, "roll", 0);
		int zoom = value(camera, "zoom", 2200), x = value(camera, "offsetX", 0), y = value(camera, "offsetY", 0);
		int sx = value(camera, "scaleX", 128), sy = value(camera, "scaleY", 128), sz = value(camera, "scaleZ", 128);
		if (pitch < 0 || pitch > 2047 || yaw < 0 || yaw > 2047 || roll < 0 || roll > 2047
			|| zoom < 256 || zoom > 20000 || Math.abs((long) x) > 1000 || Math.abs((long) y) > 1000
			|| sx < 1 || sx > 512 || sy < 1 || sy > 512 || sz < 1 || sz > 512)
			throw new IOException("Camera angles 0-2047, zoom 256-20000, offsets +/-1000, scales 1-512");
		return new CustomRotation(pitch, yaw, roll, zoom, x, y, sx, sy, sz);
	}

	private static int value(JsonObject object, String key, int fallback) {
		return object != null && object.has(key) ? integer(object.get(key)) : fallback;
	}

	private static int integer(com.google.gson.JsonElement value) {
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
			throw new IllegalArgumentException("Expected an integer number");
		return value.getAsBigDecimal().intValueExact();
	}

	byte[] export(Gson gson, Map<Integer, CustomRotation> overrides) throws IOException {
		JsonObject manifest = gson.fromJson(text(files, "pack.json"), JsonObject.class);
		JsonObject entries = manifest.getAsJsonObject("items");
		for (Integer id : items.keySet()) {
			CustomRotation r = overrides.get(id);
			if (r == null)
				continue;
			JsonObject camera = new JsonObject();
			camera.addProperty("pitch", r.xan2d); camera.addProperty("yaw", r.yan2d); camera.addProperty("roll", r.zan2d);
			camera.addProperty("zoom", r.zoom2d); camera.addProperty("offsetX", r.offsetX); camera.addProperty("offsetY", r.offsetY);
			camera.addProperty("scaleX", r.resizeX); camera.addProperty("scaleY", r.resizeY); camera.addProperty("scaleZ", r.resizeZ);
			entries.getAsJsonObject(id.toString()).add("camera", camera);
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (Map.Entry<String, byte[]> file : files.entrySet()) {
				zip.putNextEntry(new ZipEntry(file.getKey()));
				zip.write(file.getKey().equals("pack.json") ? gson.toJson(manifest).getBytes(StandardCharsets.UTF_8) : file.getValue());
				zip.closeEntry();
			}
		}
		byte[] result = bytes.toByteArray();
		// Exports must satisfy the same limits and camera constraints as imports.
		parse(result, gson);
		return result;
	}

	private static String validPath(String path) throws IOException {
		if (path.isEmpty() || path.length() > 200 || path.startsWith("/") || path.contains("\\") || path.contains(":"))
			throw new IOException("Expected a relative pack path: " + path);
		for (String part : path.split("/", -1))
			if (part.isEmpty() || part.equals(".") || part.equals(".."))
				throw new IOException("Invalid pack path: " + path);
		return path;
	}

	private static byte[] required(Map<String, byte[]> files, String path) throws IOException {
		byte[] bytes = files.get(validPath(path));
		if (bytes == null)
			throw new IOException("Missing pack file: " + path);
		return bytes;
	}

	private static String text(Map<String, byte[]> files, String path) throws IOException {
		return new String(required(files, path), StandardCharsets.UTF_8);
	}

	private static int[] png(byte[] bytes) throws IOException {
		try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
			java.util.Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
			ImageReader reader = readers.next();
			try {
				reader.setInput(input);
				int w = reader.getWidth(0), h = reader.getHeight(0);
				if (w != h || w < 8 || w > 1024 || (w & (w - 1)) != 0)
					throw new IOException("PNG textures must be square powers of two, 8-1024 pixels");
				BufferedImage image = reader.read(0);
				return image.getRGB(0, 0, w, h, null, 0, w);
			} finally {
				reader.dispose();
			}
		}
	}

	private static byte[] readBounded(InputStream input, int limit) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int n;
		while ((n = input.read(buffer)) != -1) {
			if (n > limit - bytes.size())
				throw new IOException("Model pack exceeds archive/file/expanded size limit");
			bytes.write(buffer, 0, n);
		}
		return bytes.toByteArray();
	}

	private static String digest(byte[] bytes) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
			StringBuilder hex = new StringBuilder();
			for (byte b : hash)
				hex.append(String.format("%02x", b & 255));
			return hex.toString();
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
