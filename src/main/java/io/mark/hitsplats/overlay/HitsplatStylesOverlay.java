package io.mark.hitsplats.overlay;

import io.mark.hitsplats.art.HitsplatSkin;
import io.mark.hitsplats.art.HitsplatSprites;
import io.mark.hitsplats.art.HitsplatStyle;
import io.mark.hitsplats.art.StyleProperties;
import io.mark.hitsplats.HitsplatStylesPlugin;
import io.mark.hitsplats.combat.CombatStyle;
import io.mark.hitsplats.config.HitsplatIconSet;
import io.mark.hitsplats.config.HitsplatStylesConfig;
import io.mark.hitsplats.config.HitsplatTint;
import io.mark.hitsplats.heal.HealSplat;
import io.mark.hitsplats.heal.HealTracker;
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
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class HitsplatStylesOverlay extends Overlay
{
	private static final int[] SLOT_OFFSET = {0, 0, 0, -20, -15, -10, 15, -10, 15, 10, 0, 20, -15, 10, -30, -10};

	private static final int HEAL_BELOW_X = 0;
	private static final int HEAL_BELOW_Y = 24;

	private static final int ITEM_ICON_GAP = 4;

	private static final int DISPLAY_CYCLES = 70;
	private static final int NATIVE_SPLAT_TOP = -12;
	private static final int NATIVE_SPLAT_PAD = 1;
	private static final int DAMAGE_BASELINE = 17;
	private static final int HEAL_BASELINE = 1;
	private static final int HEAL_BASELINE_2010 = -1;
	private static int hitBaseline(HitsplatStyle style)
	{
		switch (style)
		{
			case STYLE_2002:
			case STYLE_2011:
				return 1;
			default:
				return 0;
		}
	}

	private static int scaled(int value, int sizePercent)
	{
		return value * sizePercent / 100;
	}



	private static final int FLOAT_DISTANCE = 12;

	private final Client client;
	private final HitsplatTracker tracker;
	private final HealTracker healTracker;
	private final HitsplatStylesConfig config;
	private final HitsplatSprites sprites;
	private final NpcHeights npcHeights;
	private final ItemManager itemManager;

	@Inject
	private HitsplatStylesOverlay(Client client, HitsplatTracker tracker, HealTracker healTracker, HitsplatStylesConfig config, HitsplatSprites sprites, NpcHeights npcHeights, ItemManager itemManager)
	{
		this.client = client;
		this.tracker = tracker;
		this.healTracker = healTracker;
		this.config = config;
		this.sprites = sprites;
		this.npcHeights = npcHeights;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.UNDER_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Map<Actor, List<TrackedHitsplat>> tracked = tracker.getTracked();
		boolean healSplats = !healTracker.isEmpty();
		if (tracked.isEmpty() && !healSplats)
		{
			return null;
		}

		HitsplatStyle style = config.style();
		HitsplatStyle blockStyle = style == HitsplatStyle.OSRS ? config.blockArt().getStyle() : null;
		Frame frame = new Frame(client.getGameCycle(), style, blockStyle, config.tint(),
			config.hideBlockedDamage(), config.hideBlockedIcon(), config.iconSet(), config.styleIconGap(),
			config.fadeOut() ? config.fadeLength() : 0, config.healSplatMode().isItem());

		Font font = graphics.getFont();
		Composite composite = graphics.getComposite();
		graphics.setFont(FontManager.getRunescapeSmallFont());
		frame.fontMetrics = graphics.getFontMetrics();

		for (Iterator<Map.Entry<Actor, List<TrackedHitsplat>>> it = tracked.entrySet().iterator(); it.hasNext(); )
		{
			Map.Entry<Actor, List<TrackedHitsplat>> entry = it.next();
			Actor actor = entry.getKey();
			List<TrackedHitsplat> splats = entry.getValue();

			boolean anyLeft = false;
			for (int i = 0; i < splats.size(); i++)
			{
				TrackedHitsplat splat = splats.get(i);
				if (splat == null || frame.cycle >= splat.getEndCycle() + frame.fadeLength)
				{
					continue;
				}

				anyLeft = true;
				if (!HitsplatStylesPlugin.DEBUG_SLOTS && splat.getSkin() != null && splat.getEndCycle() - DISPLAY_CYCLES <= frame.cycle)
				{
					renderHitsplat(graphics, actor, splat, frame);
				}
			}

			if (HitsplatStylesPlugin.DEBUG_SLOTS)
			{
				drawSlots(graphics, actor, splats);
			}

			if (!anyLeft)
			{
				it.remove();
			}
		}

		if (healSplats && !HitsplatStylesPlugin.DEBUG_SLOTS)
		{
			renderHealSplats(graphics, frame);
		}

		graphics.setFont(font);
		graphics.setComposite(composite);
		return null;
	}

	private void renderHealSplats(Graphics2D graphics, Frame frame)
	{
		healTracker.prune(frame.cycle - frame.fadeLength);

		HealSplat[] positions = healTracker.getPositions();
		Point anchor = null;

		for (HealSplat splat : positions)
		{
			if (splat == null)
			{
				continue;
			}

			if (anchor == null)
			{
				Player local = client.getLocalPlayer();
				anchor = local == null ? null : anchor(local);
				if (anchor == null)
				{
					return;
				}
			}

			renderHealSplat(graphics, anchor, splat, frame);
		}
	}

	private void renderHealSplat(Graphics2D graphics, Point anchor, HealSplat splat, Frame frame)
	{
		BufferedImage image = sprites.get(frame.style, HitsplatSkin.HEAL);
		if (image == null)
		{
			return;
		}

		int position = splat.getPosition();
		int offsetX;
		int offsetY;
		if (position == HealTracker.BELOW_POSITION)
		{
			offsetX = HEAL_BELOW_X;
			offsetY = HEAL_BELOW_Y;
		}
		else
		{
			offsetX = SLOT_OFFSET[position << 1];
			offsetY = SLOT_OFFSET[(position << 1) | 1];
		}

		int overrun = frame.cycle - splat.getEndCycle();

		int animationY = 0;
		int alpha = 255;
		if (overrun >= 0 && frame.fadeLength > 0)
		{
			animationY = -(FLOAT_DISTANCE * overrun / frame.fadeLength);
			alpha = ((frame.fadeLength - overrun) << 8) / frame.fadeLength;
		}

		alpha = Math.max(0, Math.min(255, alpha));

		StyleProperties properties = sprites.getProperties(frame.style);
		int size = properties.getSize();
		int splatX = anchor.getX() + offsetX - image.getWidth() / 2 + NATIVE_SPLAT_PAD;
		int top = anchor.getY() + offsetY + scaled(NATIVE_SPLAT_TOP, size) + animationY;
		int centerY = top + image.getHeight() / 2;

		graphics.setComposite(alpha >= 255
			? AlphaComposite.SrcOver
			: AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));

		BufferedImage splatShadow = frame.style.isShadowed() ? sprites.getShadow(frame.style, HitsplatSkin.HEAL) : null;
		if (splatShadow != null)
		{
			graphics.drawImage(splatShadow,
				splatX - (splatShadow.getWidth() - image.getWidth()) / 2,
				top - (splatShadow.getHeight() - image.getHeight()) / 2,
				null);
		}

		graphics.drawImage(image, splatX, top, null);

		if (frame.healItem && splat.getItemId() > 0)
		{
			BufferedImage item = itemManager.getImage(splat.getItemId());
			if (item != null && item.getWidth() > 0 && item.getHeight() > 0)
			{
				int height = image.getHeight();
				int width = Math.max(1, item.getWidth() * height / item.getHeight());
				graphics.drawImage(item, splatX - ITEM_ICON_GAP - width, centerY - height / 2, width, height, null);
			}
		}

		String text = splat.getText();
		int textX = splatX + (image.getWidth() - frame.fontMetrics.stringWidth(text)) / 2;
		int textY = top + scaled(DAMAGE_BASELINE, size) + properties.getTextOffsetY()
			+ (frame.style == HitsplatStyle.STYLE_2010 ? HEAL_BASELINE_2010 : HEAL_BASELINE);

		graphics.setColor(properties.getTextShadowColor());
		graphics.drawString(text, textX + 1, textY);
		graphics.setColor(properties.getTextColor());
		graphics.drawString(text, textX, textY);
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

		HitsplatStyle style = splat.isBlock() && frame.blockStyle != null ? frame.blockStyle : frame.style;

		BufferedImage image = sprites.get(style, skin);
		if (image == null)
		{
			return;
		}

		Point anchor = anchor(actor);
		if (anchor == null)
		{
			return;
		}

		String text = splat.getText();

		CombatStyle iconStyle = null;
		BufferedImage icon = null;
		if (!(frame.hideBlockedIcon && splat.isBlock()))
		{
			iconStyle = splat.getCombatStyle();
			icon = sprites.getIcon(frame.iconSet, iconStyle);
			if (icon == null)
			{
				iconStyle = CombatStyle.MELEE;
				icon = sprites.getIcon(frame.iconSet, iconStyle);
			}
		}

		int overrun = frame.cycle - splat.getEndCycle();

		int animationY = 0;
		int alpha = 255;
		if (overrun >= 0 && frame.fadeLength > 0)
		{
			animationY = -(FLOAT_DISTANCE * overrun / frame.fadeLength);
			alpha = ((frame.fadeLength - overrun) << 8) / frame.fadeLength;
		}

		alpha = Math.max(0, Math.min(255, alpha));

		StyleProperties properties = sprites.getProperties(style);
		int size = properties.getSize();
		int slot = Math.min(splat.getSlot() << 1, SLOT_OFFSET.length - 2);
		int splatX = anchor.getX() + SLOT_OFFSET[slot] - image.getWidth() / 2 + NATIVE_SPLAT_PAD;
		int top = anchor.getY() + SLOT_OFFSET[slot | 1] + scaled(NATIVE_SPLAT_TOP, size) + animationY;
		int centerY = top + image.getHeight() / 2;

		graphics.setComposite(alpha >= 255
			? AlphaComposite.SrcOver
			: AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));

		BufferedImage splatShadow = style.isShadowed() ? sprites.getShadow(style, skin) : null;
		if (splatShadow != null)
		{
			graphics.drawImage(splatShadow,
				splatX - (splatShadow.getWidth() - image.getWidth()) / 2,
				top - (splatShadow.getHeight() - image.getHeight()) / 2,
				null);
		}

		graphics.drawImage(image, splatX, top, null);

		if (icon != null)
		{
			int slotRight = splatX - frame.styleIconGap;
			int slotCenterX = slotRight - sprites.getIconSlotWidth(frame.iconSet) / 2;
			int iconX = slotCenterX - icon.getWidth() / 2;
			int iconY = centerY - icon.getHeight() / 2;

			BufferedImage shadow = sprites.getIconShadow(frame.iconSet, iconStyle);
			if (shadow != null)
			{
				graphics.drawImage(shadow,
					iconX - (shadow.getWidth() - icon.getWidth()) / 2,
					iconY - (shadow.getHeight() - icon.getHeight()) / 2,
					null);
			}

			graphics.drawImage(icon, iconX, iconY, null);
		}

		if (!(frame.hideBlockedDamage && splat.isBlock()))
		{
			int textX = splatX + (image.getWidth() - frame.fontMetrics.stringWidth(text)) / 2;
			int textY = top + scaled(DAMAGE_BASELINE, size) + properties.getTextOffsetY() + hitBaseline(style);

			graphics.setColor(properties.getTextShadowColor());
			graphics.drawString(text, textX + 1, textY);
			graphics.setColor(properties.getTextColor());
			graphics.drawString(text, textX, textY);
		}
	}

	private void drawSlots(Graphics2D graphics, Actor actor, List<TrackedHitsplat> splats)
	{
		Point anchor = anchor(actor);
		if (anchor == null)
		{
			return;
		}

		graphics.setComposite(AlphaComposite.SrcOver);
		graphics.setColor(Color.MAGENTA);
		graphics.fillRect(anchor.getX() - 1, anchor.getY() - 1, 3, 3);

		for (int i = 0; i < 4; i++)
		{
			int x = anchor.getX() + SLOT_OFFSET[i << 1] - 12 + NATIVE_SPLAT_PAD;
			int y = anchor.getY() + SLOT_OFFSET[(i << 1) | 1] + NATIVE_SPLAT_TOP;

			graphics.setColor(i < splats.size() && splats.get(i) != null ? Color.GREEN : Color.GRAY);
			graphics.drawRect(x, y, 24, 23);
			graphics.drawString(String.valueOf(i), x + 1, y + 10);
		}

		int rulerX = anchor.getX() + 40;
		int zero = anchor.getY() + NATIVE_SPLAT_TOP;
		for (int offset = -30; offset <= 30; offset += 5)
		{
			boolean labelled = offset % 10 == 0;
			graphics.setColor(offset == 0 ? Color.CYAN : Color.YELLOW);
			graphics.drawLine(rulerX, zero + offset, rulerX + (labelled ? 10 : 5), zero + offset);

			if (labelled)
			{
				graphics.drawString(String.valueOf(offset), rulerX + 13, zero + offset + 4);
			}
		}
	}

	private Point anchor(Actor actor)
	{
		LocalPoint localPoint = actor.getLocalLocation();
		if (localPoint == null)
		{
			return null;
		}

		WorldView worldView = client.getWorldView(localPoint.getWorldView());
		if (worldView == null)
		{
			return null;
		}

		int base = actor.getLogicalHeight();
		if (actor instanceof NPC)
		{
			NPCComposition composition = ((NPC) actor).getComposition();
			if (composition != null)
			{
				int configured = npcHeights.get(composition.getId());
				if (configured != -1)
				{
					base = configured;
				}
			}
		}

		int height = (base + actor.getAnimationHeightOffset()) / 2;
		int ground = groundHeight(localPoint, worldView, actor.getFootprintSize());
		return Perspective.localToCanvas(client, localPoint.getWorldView(),
			localPoint.getX(), localPoint.getY(), ground - height);
	}

	private int groundHeight(LocalPoint point, WorldView worldView, int footprint)
	{
		int plane = worldView.getPlane();
		if (footprint == 0)
		{
			return worldView.getTileHeight(point.getX(), point.getY(), plane);
		}

		int half = footprint / 2;
		int x = point.getX();
		int y = point.getY();

		int lowTileX = ((x - half) >> 7) + 1;
		int lowTileY = ((y - half) >> 7) + 1;
		int highTileX = (x + half) >> 7;
		int highTileY = (y + half) >> 7;

		int ground = Integer.MAX_VALUE;
		for (int tileX = lowTileX; tileX <= highTileX; tileX++)
		{
			for (int tileY = lowTileY; tileY <= highTileY; tileY++)
			{
				ground = Math.min(ground, heightAt(worldView, tileX << 7, tileY << 7, plane));
			}
		}

		ground = Math.min(ground, heightAt(worldView, x, y, plane));
		ground = Math.min(ground, heightAt(worldView, x - half, y - half, plane));
		ground = Math.min(ground, heightAt(worldView, x - half, y + half, plane));
		ground = Math.min(ground, heightAt(worldView, x + half, y - half, plane));
		return Math.min(ground, heightAt(worldView, x + half, y + half, plane));
	}

	private int heightAt(WorldView worldView, int x, int y, int plane)
	{
		return worldView.getTileHeight(x, y, plane);
	}

	private static final class Frame
	{
		private final int cycle;
		private final HitsplatStyle style;
		private final HitsplatStyle blockStyle;
		private final HitsplatTint tint;
		private final boolean hideBlockedDamage;
		private final boolean hideBlockedIcon;
		private final HitsplatIconSet iconSet;
		private final int styleIconGap;
		private final int fadeLength;
		private final boolean healItem;
		private FontMetrics fontMetrics;

		private Frame(int cycle, HitsplatStyle style, HitsplatStyle blockStyle, HitsplatTint tint, boolean hideBlockedDamage, boolean hideBlockedIcon, HitsplatIconSet iconSet, int styleIconGap, int fadeLength, boolean healItem)
		{
			this.cycle = cycle;
			this.style = style;
			this.blockStyle = blockStyle;
			this.tint = tint;
			this.hideBlockedDamage = hideBlockedDamage;
			this.hideBlockedIcon = hideBlockedIcon;
			this.iconSet = iconSet;
			this.styleIconGap = styleIconGap;
			this.fadeLength = fadeLength;
			this.healItem = healItem;
		}
	}
}
