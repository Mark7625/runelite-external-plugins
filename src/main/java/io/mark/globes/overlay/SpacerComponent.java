package io.mark.globes.overlay;

import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

import java.awt.*;

public class SpacerComponent implements LayoutableRenderableEntity {
	private final int height;
	private final Rectangle bounds = new Rectangle();
	private Point preferredLocation = new Point();

	public SpacerComponent(int height) {
		this.height = height;
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		Dimension dim = new Dimension(0, height);
		bounds.setLocation(preferredLocation);
		bounds.setSize(dim);
		return dim;
	}

	@Override
	public Rectangle getBounds() {
		return bounds;
	}

	@Override
	public void setPreferredLocation(Point position) {
		this.preferredLocation = position;
	}

	@Override
	public void setPreferredSize(Dimension dimension) {
		// ignored
	}
}