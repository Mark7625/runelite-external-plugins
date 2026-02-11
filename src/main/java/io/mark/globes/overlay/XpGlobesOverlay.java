package io.mark.globes.overlay;

import io.mark.globes.RemasteredXpGlobes;
import io.mark.globes.RemasteredXpGlobesConfig;
import io.mark.globes.model.XpGlobe;
import io.mark.globes.util.Constants;
import io.mark.globes.util.ImageCache;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.xptracker.XpTrackerService;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XpGlobesOverlay extends Overlay {

	private static final double MIN_ANIMATION_SPEED = 0.02;
	private static final double MAX_ANIMATION_SPEED = 0.15;
	private static final double ANIMATION_THRESHOLD = 0.001;
	private static final double ANIMATION_FRAME_TIME = 16.0;

	private static final int DEFAULT_ORB_WIDTH = 57;
	private static final int DEFAULT_ORB_HEIGHT = 56;
	private static final int MAX_ICON_SIZE = 40;
	private static final int PROGRESS_ARC_OFFSET = 0;
	private static final double ARC_START_ANGLE = 270.0;
	private static final double ARC_FULL_CIRCLE = 360.0;

	private final Client client;
	private final ImageCache globeImageCache;
	private final ImageCache arcImageCache;
	private int lastScale = -1;

	private final RemasteredXpGlobes plugin;
	private final RemasteredXpGlobesConfig config;
	private final XpTrackerService xpTrackerService;
	private final TooltipManager tooltipManager;
	private final SpriteManager spriteManager;
	private final Tooltip xpTooltip = new Tooltip(new PanelComponent());
	private final Map<XpGlobe, Double> globeXPositions = new HashMap<>();
	private static final Color DARK_OVERLAY_COLOR = new Color(0, 0, 0, 180);

	@Inject
	private XpGlobesOverlay(
			Client client,
			RemasteredXpGlobes plugin,
			RemasteredXpGlobesConfig config,
			XpTrackerService xpTrackerService,
			TooltipManager tooltipManager,
			SpriteManager spriteManager

	) {
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.xpTrackerService = xpTrackerService;
		this.tooltipManager = tooltipManager;
		this.spriteManager = spriteManager;
		setPosition(OverlayPosition.TOP_CENTER);

		this.globeImageCache = new ImageCache(RemasteredXpGlobes.class, "globe.png", DEFAULT_ORB_WIDTH, DEFAULT_ORB_HEIGHT);
		this.arcImageCache = new ImageCache(RemasteredXpGlobes.class, "arc.png", DEFAULT_ORB_WIDTH, DEFAULT_ORB_HEIGHT);
	}

	public void clearCache() {
		globeImageCache.clearCache();
		arcImageCache.clearCache();
		lastScale = -1;
	}

	private void clearSkillIconCacheIfScaleChanged() {
		int scale = config.orbScale();
		if (scale == lastScale) {
			return;
		}

		if (lastScale != -1) {
			for (XpGlobe xpGlobe : plugin.getXpGlobes()) {
				xpGlobe.setSkillIcon(null);
				xpGlobe.setSize(0);
			}
		}

		lastScale = scale;
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		if (config.disableXpGlobes()) {
			return null;
		}

		if (plugin.getCurrentLevelUp() != null) {
			return null;
		}

		clearSkillIconCacheIfScaleChanged();

		List<XpGlobe> xpGlobes = plugin.getXpGlobes();
		if (xpGlobes.isEmpty()) {
			return null;
		}

		int scale = config.orbScale();
		BufferedImage scaledGlobeImage = globeImageCache.getScaledImage(scale);
		BufferedImage scaledArcImage = arcImageCache.getScaledImage(scale);

		if (scaledGlobeImage == null || scaledArcImage == null) {
			return null;
		}

		int globeWidth = scaledGlobeImage.getWidth();
		int globeHeight = scaledGlobeImage.getHeight();
		int orbSpacing = config.orbSpacing();
		int baseY = PROGRESS_ARC_OFFSET + config.verticalOffset();

		java.awt.Rectangle bounds = getBounds();
		Point mouse = client.getMouseCanvasPosition();
		int mouseX = mouse.getX() - bounds.x;
		int mouseY = mouse.getY() - bounds.y;

		boolean animateReposition = config.animateOrbReposition();
		if (!animateReposition)
		{
			globeXPositions.clear();
		}
		else
		{
			cleanupGlobePositions(xpGlobes);
		}

		int index = 0;
		for (XpGlobe xpGlobe : xpGlobes) {
			double alpha = getFadeAlpha(xpGlobe);

			Composite oldComposite = graphics.getComposite();
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));

			double targetX = PROGRESS_ARC_OFFSET + index * (globeWidth + orbSpacing);
			double currentX;
			if (!animateReposition)
			{
				currentX = targetX;
			}
			else
			{
				currentX = globeXPositions.getOrDefault(xpGlobe, targetX);
				double diffX = targetX - currentX;

				double step = Math.max(1.0, globeWidth * 0.2);
				if (Math.abs(diffX) <= step)
				{
					currentX = targetX;
				}
				else
				{
					currentX += Math.signum(diffX) * step;
				}
				globeXPositions.put(xpGlobe, currentX);
			}
			int drawX = (int) Math.round(currentX);

			graphics.drawImage(scaledGlobeImage, drawX, baseY, null);

			double scaleFactor = config.orbScale() / 100.0;
			int circleWidth = (int) (41 * scaleFactor);
			int circleHeight = (int) (41 * scaleFactor);
			int circleX = drawX + (int) (8 * scaleFactor);
			int circleY = baseY + (int) (7 * scaleFactor);

			java.awt.geom.Ellipse2D backgroundCircle = new java.awt.geom.Ellipse2D.Double(
					circleX,
					circleY,
					circleWidth,
					circleHeight
			);

			double targetProgress = getXpProgress(xpGlobe);
			double animatedProgress = updateAnimatedProgress(xpGlobe, targetProgress);

			if (animatedProgress > 0) {
				drawProgressArc(graphics, scaledArcImage, drawX, globeWidth, globeHeight, animatedProgress);
			}

			drawSkillImage(graphics, xpGlobe, drawX, globeWidth, globeHeight);

			if (backgroundCircle.contains(mouseX, mouseY))
			{
				if (config.showPercentOnHover())
				{
					graphics.setColor(DARK_OVERLAY_COLOR);
					graphics.fill(backgroundCircle);

					int percent = (int) Math.round(targetProgress * 100.0);
					if (percent > 100)
					{
						percent = 100;
					}
					else if (percent < 0)
					{
						percent = 0;
					}

					String progressText = percent + "%";
					FontMetrics fm = graphics.getFontMetrics();
					int textWidth = fm.stringWidth(progressText);
					int textHeight = fm.getAscent();

					int textX = circleX + (circleWidth - textWidth) / 2;
					int textY = circleY + (circleHeight + textHeight) / 2 - 1;

					graphics.setColor(Color.WHITE);
					graphics.drawString(progressText, textX, textY);
				}

				if (config.enableTooltips())
				{
					int goalXp = xpTrackerService.getEndGoalXp(xpGlobe.getSkill());
					drawTooltip(xpGlobe, goalXp);
				}
			}

			graphics.setComposite(oldComposite);
			index++;
		}

		int markersLength = (xpGlobes.size() * (globeWidth + PROGRESS_ARC_OFFSET)) + (orbSpacing * (xpGlobes.size() - 1));
		return new Dimension(markersLength, globeWidth + PROGRESS_ARC_OFFSET * 2);
	}

	private double getFadeAlpha(XpGlobe xpGlobe) {
		double fadeOutDuration = config.fadeOutDuration();
		if (fadeOutDuration <= 0) {
			return 1.0;
		}

		Instant currentTime = Instant.now();
		long elapsedMillis = Duration.between(xpGlobe.getTime(), currentTime).toMillis();
		long durationMillis = config.xpOrbDuration() * 1000L;
		long fadeOutDurationMillis = (long) (fadeOutDuration * 1000);

		if (elapsedMillis >= durationMillis) {
			return 0.0;
		}

		long timeRemainingMillis = durationMillis - elapsedMillis;
		if (timeRemainingMillis <= fadeOutDurationMillis) {
			return Math.max(0.0, (double) timeRemainingMillis / fadeOutDurationMillis);
		}

		return 1.0;
	}

	private double getXpProgress(XpGlobe xpGlobe) {
		int currentLevel = xpGlobe.getCurrentLevel();
		int currentXp = xpGlobe.getCurrentXp();

		if (currentLevel >= Experience.MAX_REAL_LEVEL) {
			return 0.0;
		}

		int xpForCurrentLevel = Experience.getXpForLevel(currentLevel);
		int xpForNextLevel = Experience.getXpForLevel(currentLevel + 1);

		if (xpForNextLevel <= xpForCurrentLevel) {
			return 0.0;
		}

		int xpGained = currentXp - xpForCurrentLevel;
		int xpNeeded = xpForNextLevel - xpForCurrentLevel;

		return Math.min(1.0, Math.max(0.0, (double) xpGained / xpNeeded));
	}

	private double updateAnimatedProgress(XpGlobe xpGlobe, double targetProgress) {
		long currentTime = System.currentTimeMillis();
		long deltaTime = currentTime - xpGlobe.getLastUpdateTime();

		if (deltaTime <= 0) {
			return xpGlobe.getAnimatedProgress();
		}

		double currentProgress = xpGlobe.getAnimatedProgress();
		double difference = targetProgress - currentProgress;

		if (Math.abs(difference) < ANIMATION_THRESHOLD) {
			xpGlobe.setAnimatedProgress(targetProgress);
			return targetProgress;
		}

		double absDifference = Math.abs(difference);
		double speedFactor = MIN_ANIMATION_SPEED + (absDifference * (MAX_ANIMATION_SPEED - MIN_ANIMATION_SPEED));
		double change = difference * speedFactor * (deltaTime / ANIMATION_FRAME_TIME);
		double newProgress = currentProgress + change;

		if ((difference > 0 && newProgress > targetProgress) || (difference < 0 && newProgress < targetProgress)) {
			newProgress = targetProgress;
		}

		xpGlobe.setAnimatedProgress(newProgress);
		xpGlobe.setLastUpdateTime(currentTime);

		return newProgress;
	}

	private void drawProgressArc(Graphics2D graphics, BufferedImage arcImage, int x, int width, int height, double progress) {
		if (progress <= 0 || arcImage == null) {
			return;
		}

		Shape oldClip = graphics.getClip();
		double arcExtent = -ARC_FULL_CIRCLE * progress;

		int baseY = PROGRESS_ARC_OFFSET + config.verticalOffset();
		Arc2D.Double clipArc = new Arc2D.Double(x, baseY, width, height, ARC_START_ANGLE, arcExtent, Arc2D.PIE);

		graphics.setClip(clipArc);
		graphics.drawImage(arcImage, x, baseY, null);
		graphics.setClip(oldClip);
	}

	private void drawSkillImage(Graphics2D graphics, XpGlobe xpGlobe, int x, int orbWidth, int orbHeight) {
		BufferedImage skillImage = getScaledSkillIcon(xpGlobe);

		if (skillImage == null) {
			return;
		}

		double scaleFactor = config.orbScale() / 100.0;
		int baseY = PROGRESS_ARC_OFFSET + config.verticalOffset();

		double circleWidth = 41.0 * scaleFactor;
		double circleHeight = 41.0 * scaleFactor;
		double circleX = x + 8.0 * scaleFactor;
		double circleY = baseY + 7.0 * scaleFactor;

		int iconWidth = skillImage.getWidth();
		int iconHeight = skillImage.getHeight();

		int iconX = (int) Math.round(circleX + (circleWidth - iconWidth) / 2.0);
		int iconY = (int) Math.round(circleY + (circleHeight - iconHeight) / 2.0);

		graphics.drawImage(skillImage, iconX, iconY, null);
	}

	private BufferedImage getSkillImage(Skill skill) {
		int iconID = Constants.SKILL_ICONS.get(skill);
		SpritePixels spriteIcon = client.getSpriteOverrides().get(iconID);
		if (spriteIcon != null) {
			return spriteIcon.toBufferedImage();
		}
		return spriteManager.getSprite(iconID,0);
	}

	private BufferedImage getScaledSkillIcon(XpGlobe xpGlobe) {
		double scaleFactor = config.orbScale() / 100.0;
		int targetOrbSize = (int) (DEFAULT_ORB_WIDTH * scaleFactor);
		int maxIconSize = (int) (MAX_ICON_SIZE * scaleFactor);

		if (xpGlobe.getSkillIcon() != null && xpGlobe.getSize() == targetOrbSize) {
			return xpGlobe.getSkillIcon();
		}

		BufferedImage icon = getSkillImage(xpGlobe.getSkill());
		if (icon == null) {
			return null;
		}

		int iconWidth = icon.getWidth();
		int iconHeight = icon.getHeight();
		int maxDimension = Math.max(iconWidth, iconHeight);
		int scaledSize = (int) (maxDimension * scaleFactor);
		
		if (scaledSize > maxIconSize) {
			scaledSize = maxIconSize;
		}

		if (scaledSize <= 0) {
			return null;
		}

		double scale = (double) scaledSize / maxDimension;
		int scaledWidth = (int) (iconWidth * scale);
		int scaledHeight = (int) (iconHeight * scale);

		icon = ImageUtil.resizeImage(icon, scaledWidth, scaledHeight, true);
		xpGlobe.setSkillIcon(icon);
		xpGlobe.setSize(targetOrbSize);

		return icon;
	}

	private void cleanupGlobePositions(List<XpGlobe> xpGlobes)
	{
		if (globeXPositions.isEmpty()) {
			return;
		}

		globeXPositions.keySet().removeIf(globe -> !xpGlobes.contains(globe));
	}

	private void drawTooltip(XpGlobe mouseOverSkill, int goalXp)
	{
		// reset the timer on XpGlobe to prevent it from disappearing while hovered over it
		mouseOverSkill.setTime(Instant.now());

		String skillName = mouseOverSkill.getSkill().getName();
		String skillLevel = Integer.toString(mouseOverSkill.getCurrentLevel());

		DecimalFormat decimalFormat = new DecimalFormat("###,###,###");
		String skillCurrentXp = decimalFormat.format(mouseOverSkill.getCurrentXp());

		final PanelComponent xpTooltip = (PanelComponent) this.xpTooltip.getComponent();
		xpTooltip.getChildren().clear();

		xpTooltip.getChildren().add(LineComponent.builder()
				.left(skillName)
				.right(skillLevel)
				.build());

		xpTooltip.getChildren().add(LineComponent.builder()
				.left("Current XP:")
				.leftColor(Color.ORANGE)
				.right(skillCurrentXp)
				.build());

		if (goalXp > mouseOverSkill.getCurrentXp())
		{
			if (config.showActionsLeft())
			{
				int actionsLeft = xpTrackerService.getActionsLeft(mouseOverSkill.getSkill());
				if (actionsLeft != Integer.MAX_VALUE)
				{
					String actionsLeftString = decimalFormat.format(actionsLeft);
					xpTooltip.getChildren().add(LineComponent.builder()
							.left("Actions left:")
							.leftColor(Color.ORANGE)
							.right(actionsLeftString)
							.build());
				}
			}

			if (config.showXpLeft())
			{
				int xpLeft = goalXp - mouseOverSkill.getCurrentXp();
				String skillXpToLvl = decimalFormat.format(xpLeft);
				xpTooltip.getChildren().add(LineComponent.builder()
						.left("XP left:")
						.leftColor(Color.ORANGE)
						.right(skillXpToLvl)
						.build());
			}

			if (config.showXpHour())
			{
				int xpHr = xpTrackerService.getXpHr(mouseOverSkill.getSkill());
				if (xpHr != 0)
				{
					String xpHrString = decimalFormat.format(xpHr);
					xpTooltip.getChildren().add(LineComponent.builder()
							.left("XP per hour:")
							.leftColor(Color.ORANGE)
							.right(xpHrString)
							.build());
				}
			}

			if (config.showTimeTilGoal())
			{
				String timeLeft = xpTrackerService.getTimeTilGoal(mouseOverSkill.getSkill());
				xpTooltip.getChildren().add(LineComponent.builder()
						.left("Time left:")
						.leftColor(Color.ORANGE)
						.right(timeLeft)
						.build());
			}
		}

		tooltipManager.add(this.xpTooltip);
	}

}
