package io.mark.hditemicons.hd;

import java.io.IOException;
import org.junit.Test;

import static org.junit.Assert.*;

public class ObjMeshTest {
	@Test
	public void normalizesUnitsAndConvertsUpAxisAndUvs() throws Exception {
		ObjMesh mesh = ObjMesh.parse(TestPacks.TRIANGLE);
		assertArrayEquals(new float[]{-50, 50, 0}, mesh.x, .001f);
		assertArrayEquals(new float[]{50, 50, -50}, mesh.y, .001f);
		assertArrayEquals(new float[]{0, 1, 1, 1, .5f, 0}, mesh.uv[0], .001f);
	}

	@Test
	public void acceptsRelativeIndicesAndPlainGeometry() throws Exception {
		ObjMesh mesh = ObjMesh.parse("# triangle\nv 0 0 0\nv 1 0 0\nv 0 1 0\nf -3 -2 -1\n");
		assertArrayEquals(new int[]{0}, mesh.a);
		assertArrayEquals(new int[]{1}, mesh.b);
		assertArrayEquals(new int[]{2}, mesh.c);
		assertNull(mesh.uv[0]);
	}

	@Test
	public void rejectsNonFiniteCoordinatesBadIndicesAndNonTriangles() {
		for (String obj : new String[]{
			"v NaN 0 0", "v Infinity 0 0", "v 1000001 0 0",
			"v 0 0 0\nf 0 1 1", "v 0 0 0\nf 1 2 3", "v 0 0 0\nf 1 1 1 1",
			"v 0 0 0\nf 1 1 1", "v 0 0 0\nv 1 1 1\nv 2 2 2\nf 1 2 3"})
			assertThrows(IOException.class, () -> ObjMesh.parse(obj));
	}

	@Test
	public void boundsVertexCount() {
		String obj = "v 0 0 0\n".repeat(ObjMesh.MAX_VERTICES + 1);
		assertThrows(IOException.class, () -> ObjMesh.parse(obj));
	}
}
