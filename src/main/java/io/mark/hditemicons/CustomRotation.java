package io.mark.hditemicons;

/**
 * An override for an item's icon camera placement, named after the item definition fields it
 * stands in for. Angles are in Jau (0-2047 per turn), resize is out of 128.
 */
public final class CustomRotation {
	public final int xan2d;
	public final int yan2d;
	public final int zan2d;
	public final int zoom2d;
	public final int offsetX;
	public final int offsetY;
	public final int resizeX;
	public final int resizeY;
	public final int resizeZ;

	public CustomRotation(int xan2d, int yan2d, int zan2d, int zoom2d, int offsetX, int offsetY,
						int resizeX, int resizeY, int resizeZ) {
		this.xan2d = xan2d;
		this.yan2d = yan2d;
		this.zan2d = zan2d;
		this.zoom2d = zoom2d;
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.resizeX = resizeX;
		this.resizeY = resizeY;
		this.resizeZ = resizeZ;
	}
}
