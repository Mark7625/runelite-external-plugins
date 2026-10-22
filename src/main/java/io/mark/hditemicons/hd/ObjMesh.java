package io.mark.hditemicons.hd;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Small, deliberately bounded OBJ subset: positions, UVs and triangulated faces. */
final class ObjMesh {
	static final int MAX_VERTICES = 4096;
	static final int MAX_FACES = 8192;
	final float[] x, y, z;
	final int[] a, b, c;
	final float[][] uv;

	private ObjMesh(List<float[]> vertices, List<int[]> faces, List<float[]> coordinates) throws IOException {
		x = new float[vertices.size()];
		y = new float[x.length];
		z = new float[x.length];
		double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
		double maxX = -minX, maxY = -minX, maxZ = -minX;
		for (float[] v : vertices) {
			minX = Math.min(minX, v[0]); maxX = Math.max(maxX, v[0]);
			minY = Math.min(minY, v[1]); maxY = Math.max(maxY, v[1]);
			minZ = Math.min(minZ, v[2]); maxZ = Math.max(maxZ, v[2]);
		}
		double span = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
		if (!(span > 0))
			throw new IOException("OBJ has no extent");
		// A fixed 100-unit extent makes pack cameras independent of Blender's scene units.
		for (int i = 0; i < x.length; i++) {
			float[] v = vertices.get(i);
			x[i] = (float) ((v[0] - (minX + maxX) / 2) * 100 / span);
			y[i] = (float) (-(v[1] - (minY + maxY) / 2) * 100 / span);
			z[i] = (float) ((v[2] - (minZ + maxZ) / 2) * 100 / span);
		}
		a = new int[faces.size()]; b = new int[a.length]; c = new int[a.length];
		uv = new float[a.length][];
		boolean nondegenerate = false;
		for (int f = 0; f < a.length; f++) {
			int[] face = faces.get(f);
			a[f] = face[0]; b[f] = face[2]; c[f] = face[4];
			if (face[1] >= 0 && face[3] >= 0 && face[5] >= 0) {
				float[] u = coordinates.get(face[1]), v = coordinates.get(face[3]), w = coordinates.get(face[5]);
				uv[f] = new float[]{u[0], 1 - u[1], v[0], 1 - v[1], w[0], 1 - w[1]};
			}
			double ux = x[b[f]] - x[a[f]], uy = y[b[f]] - y[a[f]], uz = z[b[f]] - z[a[f]];
			double vx = x[c[f]] - x[a[f]], vy = y[c[f]] - y[a[f]], vz = z[c[f]] - z[a[f]];
			nondegenerate |= Math.abs(uy * vz - uz * vy) + Math.abs(uz * vx - ux * vz) + Math.abs(ux * vy - uy * vx) > 1e-8;
		}
		if (!nondegenerate)
			throw new IOException("OBJ has no nondegenerate triangles");
	}

	static ObjMesh parse(String text) throws IOException {
		List<float[]> vertices = new ArrayList<>(), coordinates = new ArrayList<>();
		List<int[]> faces = new ArrayList<>();
		int lineNumber = 0;
		for (String line : text.split("\\R")) {
			lineNumber++;
			line = line.split("#", 2)[0].trim();
			if (line.isEmpty())
				continue;
			String[] words = line.split("\\s+");
			try {
				switch (words[0]) {
					case "v":
						if (words.length != 4 || vertices.size() >= MAX_VERTICES)
							throw new IOException("Expected three coordinates; at most " + MAX_VERTICES + " vertices");
						vertices.add(new float[]{number(words[1]), number(words[2]), number(words[3])});
						break;
					case "vt":
						if (words.length < 3 || words.length > 4 || coordinates.size() >= MAX_VERTICES * 3)
							throw new IOException("Invalid or too many UV coordinates");
						coordinates.add(new float[]{number(words[1]), number(words[2])});
						break;
					case "f":
						if (words.length != 4 || faces.size() >= MAX_FACES)
							throw new IOException("Export triangulated faces; at most " + MAX_FACES + " triangles");
						int[] face = new int[6];
						for (int i = 0; i < 3; i++) {
							String[] corner = words[i + 1].split("/", -1);
							if (corner.length > 3)
								throw new IOException("Invalid face corner");
							face[i * 2] = index(corner[0], vertices.size());
							face[i * 2 + 1] = corner.length < 2 || corner[1].isEmpty() ? -1 : index(corner[1], coordinates.size());
						}
						faces.add(face);
						break;
					case "vn": case "o": case "g": case "s": case "usemtl": case "mtllib":
						// Geometry only: materials are declared in pack.json, never read from OBJ paths.
						break;
					default:
						throw new IOException("Unsupported OBJ directive: " + words[0]);
				}
			} catch (IllegalArgumentException | IOException ex) {
				throw new IOException("OBJ line " + lineNumber + ": " + ex.getMessage(), ex);
			}
		}
		if (vertices.isEmpty() || faces.isEmpty())
			throw new IOException("OBJ must contain vertices and triangulated faces");
		return new ObjMesh(vertices, faces, coordinates);
	}

	private static float number(String word) throws IOException {
		float value = Float.parseFloat(word);
		if (!Float.isFinite(value) || Math.abs(value) > 1_000_000)
			throw new IOException("Coordinates must be finite and within +/-1000000");
		return value;
	}

	private static int index(String word, int size) throws IOException {
		int index = Integer.parseInt(word);
		index = index < 0 ? size + index : index - 1;
		if (index < 0 || index >= size)
			throw new IOException("Face index is outside the declared coordinates");
		return index;
	}
}
