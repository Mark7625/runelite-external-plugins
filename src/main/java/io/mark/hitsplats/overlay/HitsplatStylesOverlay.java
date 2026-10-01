package io.mark.hitsplats.overlay;

import io.mark.hitsplats.art.HitsplatSkin;
import io.mark.hitsplats.art.HitsplatSprites;
import io.mark.hitsplats.art.HitsplatStyle;
import io.mark.hitsplats.config.HitsplatStylesConfig;
import io.mark.hitsplats.config.HitsplatTint;
import io.mark.hitsplats.hit.HitsplatTracker;
import io.mark.hitsplats.hit.TrackedHitsplat;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class HitsplatStylesOverlay extends Overlay
{
	private static final int[] SLOT_OFFSET_X = {0, 0, -15, 15};
	private static final int[] SLOT_OFFSET_Y = {0, -20, -10, -10};
	private static final int FADE_CYCLES = 25;
	private static final int RISE_PIXELS = 12;
	private static final int ICON_OVERLAP = 2;

	private final Client client;
	private final HitsplatTracker tracker;
	private final HitsplatStylesConfig config;
	private final HitsplatSprites sprites;

	@Inject
	private HitsplatStylesOverlay(Client client, HitsplatTracker tracker, HitsplatStylesConfig config, HitsplatSprites sprites)
	{
		this.client = client;
		this.tracker = tracker;
		this.config = config;
		this.sprites = sprites;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.UNDER_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Map<Actor, TrackedHitsplat[]> tracked = tracker.getTracked();
		if (tracked.isEmpty())
		{
			return null;
		}

		HitsplatStyle style = config.style();
		Frame frame = new Frame(client.getGameCycle(), config.tint(), config.styleIcons().shows(style),
			config.hideBlockedDamage(), style.getTextOffsetY());

		Font font = graphics.getFont();
		Composite composite = graphics.getComposite();
		graphics.setFont(FontManager.getRunescapeSmallFont());
		frame.fontMetrics = graphics.getFontMetrics();

		for (Iterator<Map.Entry<Actor, TrackedHitsplat[]>> it = tracked.entrySet().iterator(); it.hasNext(); )
		{
			Map.Entry<Actor, TrackedHitsplat[]> entry = it.next();
			Actor actor = entry.getKey();
			TrackedHitsplat[] slots = entry.getValue();

			boolean anyLeft = false;
			for (int i = 0; i < slots.length; i++)
			{
				TrackedHitsplat splat = slots[i];
				if (splat == null)
				{
					continue;
				}

				if (frame.cycle >= splat.getEndCycle())
				{
					slots[i] = null;
					continue;
				}

				anyLeft = true;
				if (splat.getSkin() != null)
				{
					renderHitsplat(graphics, actor, splat, frame);
				}
			}

			if (!anyLeft)
			{
				it.remove();
			}
		}

		graphics.setFont(font);
		graphics.setComposite(composite);
		return null;
	}

	private void renderHitsplat(Graphics2D graphics, Actor actor, TrackedHitsplat splat, Frame frame)
	{
		HitsplatSkin skin = splat.getSkin();
		switch (frame.tint)
		{
			case NEVER:
				skin = skin.untinted();
				break;
			case ALWAYS:
				skin = skin.tinted();
				break;
			default:
				break;
		}

		BufferedImage image = sprites.get(skin);
		if (image == null)
		{
			return;
		}

		String text = splat.getText();
		Point anchor = actor.getCanvasTextLocation(graphics, text, actor.getLogicalHeight() / 2);
		if (anchor == null)
		{
			return;
		}

		int elapsed = frame.cycle - splat.getStartCycle();
		int duration = splat.getEndCycle() - splat.getStartCycle();
		int fadeElapsed = elapsed - (duration - FADE_CYCLES);

		int alpha = 255;
		int rise = 0;
		if (fadeElapsed > 0)
		{
			int clamped = Math.min(fadeElapsed, FADE_CYCLES);
			alpha = Math.max(0, 255 - 255 * clamped / FADE_CYCLES);
			rise = RISE_PIXELS * clamped / FADE_CYCLES;
		}

		int centerX = anchor.getX() + SLOT_OFFSET_X[splat.getSlot()];
		int centerY = anchor.getY() + SLOT_OFFSET_Y[splat.getSlot()] - rise;

		graphics.setComposite(alpha == 255
			? AlphaComposite.SrcOver
			: AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));

		graphics.drawImage(image, centerX - image.getWidth() / 2, centerY - image.getHeight() / 2, null);

		if (frame.showIcons)
		{
			BufferedImage icon = sprites.getIcon(splat.getCombatStyle());
			if (icon != null)
			{
				int iconX = centerX - image.getWidth() / 2 - icon.getWidth() + ICON_OVERLAP;
				graphics.drawImage(icon, iconX, centerY - icon.getHeight() / 2, null);
			}
		}

		if (!(frame.hideBlockedDamage && splat.isBlock()))
		{
			int textX = centerX - frame.fontMetrics.stringWidth(text) / 2;
			int textY = centerY + frame.fontMetrics.getAscent() / 2 + frame.textOffsetY;

			graphics.setColor(Color.BLACK);
			graphics.drawString(text, textX + 1, textY + 1);
			graphics.setColor(Color.WHITE);
			graphics.drawString(text, textX, textY);
		}
	}

	private static final class Frame
	{
		private final int cycle;
		private final HitsplatTint tint;
		private final boolean showIcons;
		private final boolean hideBlockedDamage;
		private final int textOffsetY;
		private FontMetrics fontMetrics;

		private Frame(int cycle, HitsplatTint tint, boolean showIcons, boolean hideBlockedDamage, int textOffsetY)
		{
			this.cycle = cycle;
			this.tint = tint;
			this.showIcons = showIcons;
			this.hideBlockedDamage = hideBlockedDamage;
			this.textOffsetY = textOffsetY;
		}
	}
}
