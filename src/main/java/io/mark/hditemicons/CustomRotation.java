package io.mark.hditemicons;

import java.util.Objects;

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

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (!(other instanceof CustomRotation))
			return false;
		CustomRotation that = (CustomRotation) other;
		return xan2d == that.xan2d && yan2d == that.yan2d && zan2d == that.zan2d && zoom2d == that.zoom2d
			&& offsetX == that.offsetX && offsetY == that.offsetY
			&& resizeX == that.resizeX && resizeY == that.resizeY && resizeZ == that.resizeZ;
	}

	@Override
	public int hashCode() {
		return Objects.hash(xan2d, yan2d, zan2d, zoom2d, offsetX, offsetY, resizeX, resizeY, resizeZ);
	}
}
