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
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XpGlobesOverlay extends Overlay {

	private static final double MIN_ANIMATION_SPEED = 0.02;
	private static final double MAX_ANIMATION_SPEED = 0.15;
	private static final double ANIMATION_THRESHOLD = 0.001;
	private static final double ANIMATION_FRAME_TIME = 16.0;

	private static final int DEFAULT_ORB_WIDTH = 219;
	private static final int DEFAULT_ORB_HEIGHT = 210;
	private static final int MAX_ICON_SIZE = 154; // scaled from 40 for 219x210
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

	private static final long XP_DROP_DURATION_MS = 1800;
	private static final float XP_DROP_FADE_START_MS = 1200;
	private static final int XP_DROP_BASE_ICON_SIZE = 24;
	private static final int XP_DROP_ICON_SPACING = 2;

	private final Map<Skill, Integer> previousXp = new java.util.concurrent.ConcurrentHashMap<>();
	private final List<PendingXpGain> pendingGains = new ArrayList<>();
	private final List<XpDrop> activeDrops = new ArrayList<>();

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

	public void onStatChanged(Skill skill, int currentXp) {
		if (skill == Skill.OVERALL || !Constants.SKILL_ICONS.containsKey(skill)) {
			return;
		}
		Integer prev = previousXp.get(skill);
		if (prev != null && currentXp > prev) {
			synchronized (pendingGains) {
				pendingGains.add(new PendingXpGain(skill, currentXp - prev, Instant.now()));
			}
		}
		previousXp.put(skill, currentXp);
	}

	/** Clears previous XP tracking so we don't treat initial stat sync on login as gains. */
	public void initPreviousXp() {
		previousXp.clear();
	}

	/** Populates previous XP from client after login so first real gain is tracked. Call once client has stat data. */
	public void syncPreviousXpFromClient() {
		for (Skill skill : Skill.values()) {
			if (skill != Skill.OVERALL && Constants.SKILL_ICONS.containsKey(skill)) {
				previousXp.put(skill, client.getSkillExperience(skill));
			}
		}
	}

	public void clearXpDrops() {
		synchronized (pendingGains) {
			pendingGains.clear();
		}
		activeDrops.clear();
		previousXp.clear();
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
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
				RenderingHints.VALUE_RENDER_QUALITY);

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);

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
		if (!animateReposition) {
			globeXPositions.clear();
		} else {
			cleanupGlobePositions(xpGlobes);
		}

		int n = xpGlobes.size();
		int xpDropOffset = config.xpDropOffset();

		// Compute all orb X positions first (so XP drops use actual middle orb position)
		double[] orbPositions = new double[n];
		for (int i = 0; i < n; i++) {
			XpGlobe xpGlobe = xpGlobes.get(i);
			double targetX = PROGRESS_ARC_OFFSET + i * (globeWidth + orbSpacing);
			double currentX;
			if (!animateReposition) {
				currentX = targetX;
			} else {
				currentX = globeXPositions.getOrDefault(xpGlobe, targetX);
				double diffX = targetX - currentX;
				double step = Math.max(1.0, globeWidth * 0.2);
				if (Math.abs(diffX) <= step) {
					currentX = targetX;
				} else {
					currentX += Math.signum(diffX) * step;
				}
				globeXPositions.put(xpGlobe, currentX);
			}
			orbPositions[i] = currentX;
		}

		if (config.enableXpDrops()) {
			int overlayWidth = (n * (globeWidth + PROGRESS_ARC_OFFSET)) + (orbSpacing * (n - 1));
			int xpDropCenterX = overlayWidth / 2;
			int xpDropCenterY = baseY + globeHeight + xpDropOffset;
			processPendingXpDrops();
			long now = System.currentTimeMillis();
			activeDrops.removeIf(drop -> now - drop.spawnTime > XP_DROP_DURATION_MS);
			for (XpDrop drop : activeDrops) {
				renderXpDrop(graphics, drop, xpDropCenterX, xpDropCenterY, now);
			}
		}

		int index = 0;
		for (XpGlobe xpGlobe : xpGlobes) {
			double alpha = getFadeAlpha(xpGlobe);

			Composite oldComposite = graphics.getComposite();
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));

			int drawX = (int) Math.round(orbPositions[index]);

			graphics.drawImage(scaledGlobeImage, drawX, baseY, null);

			double scaleFactor = config.orbScale() / 100.0;
			// Scaled from 41, 8, 7 for 219x210 orb (was 57x56)
			int circleSize = (int) (157 * scaleFactor);
			int circleWidth = circleSize;
			int circleHeight = circleSize;
			int circleX = drawX + (int) (31 * scaleFactor);
			int circleY = baseY + (int) (26 * scaleFactor);

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

			if (backgroundCircle.contains(mouseX, mouseY)) {
				if (config.showPercentOnHover()) {
					graphics.setColor(DARK_OVERLAY_COLOR);
					graphics.fill(backgroundCircle);

					float baseFontSize = 60f;
					float scaledFontSize = baseFontSize * (float) scaleFactor;
					Font baseFont = config.overlayFont().getFont();
					Font scaledFont = baseFont.deriveFont(scaledFontSize);
					Font oldFont = graphics.getFont();
					graphics.setFont(scaledFont);

					FontMetrics fm = graphics.getFontMetrics();

					int percent = (int) Math.round(targetProgress * 100.0);
					percent = Math.max(0, Math.min(100, percent));

					String progressText = percent + "%";

					int textWidth = fm.stringWidth(progressText);

					int textX = circleX + (circleWidth - textWidth) / 2;
					int textY = circleY + (circleHeight - fm.getHeight()) / 2 + fm.getAscent();

					graphics.setColor(Color.WHITE);
					graphics.drawString(progressText, textX, textY);

					graphics.setFont(oldFont);
				}

				if (config.enableTooltips()) {
					int goalXp = xpTrackerService.getEndGoalXp(xpGlobe.getSkill());
					drawTooltip(xpGlobe, goalXp);
				}
			}

			graphics.setComposite(oldComposite);
			index++;
		}

		int markersLength = (xpGlobes.size() * (globeWidth + PROGRESS_ARC_OFFSET)) + (orbSpacing * (xpGlobes.size() - 1));
		int height = globeWidth + PROGRESS_ARC_OFFSET * 2;
		if (config.enableXpDrops()) {
			height += xpDropOffset + 80;
		}
		return new Dimension(markersLength, height);
	}

	private void processPendingXpDrops() {
		List<PendingXpGain> batch;
		synchronized (pendingGains) {
			if (pendingGains.isEmpty()) {
				return;
			}
			batch = new ArrayList<>(pendingGains);
			pendingGains.clear();
		}
		int totalXp = 0;
		List<Skill> skills = new ArrayList<>();
		for (PendingXpGain g : batch) {
			totalXp += g.xp;
			if (!skills.contains(g.skill)) {
				skills.add(g.skill);
			}
		}
		activeDrops.add(new XpDrop(totalXp, skills, System.currentTimeMillis()));
	}

	private void renderXpDrop(Graphics2D graphics, XpDrop drop, int centerX, int centerY, long now) {
		float elapsed = (now - drop.spawnTime) / 1000f;
		float offsetY = elapsed * config.xpDropSpeed();
		int y = (int) (centerY - offsetY);

		float alpha = 1f;
		if (elapsed * 1000 > XP_DROP_FADE_START_MS) {
			float fadeElapsed = (elapsed * 1000 - XP_DROP_FADE_START_MS) / 1000f;
			alpha = Math.max(0, 1f - fadeElapsed / ((XP_DROP_DURATION_MS - XP_DROP_FADE_START_MS) / 1000f));
		}

		float scale = config.xpDropScale() / 100f;
		int iconSize = (int) (XP_DROP_BASE_ICON_SIZE * scale);

		String xpText = "+" + QuantityFormatter.formatNumber(drop.totalXp) + " xp";
		boolean showIcons = config.xpDropShowIcons();

		Font baseFont = config.xpDropFont().getFont();
		float fontSize = Math.max(10f, baseFont.getSize2D() * scale);
		Font scaledFont = baseFont.deriveFont(fontSize);
		Font oldFont = graphics.getFont();
		graphics.setFont(scaledFont);
		FontMetrics fm = graphics.getFontMetrics();
		int textWidth = fm.stringWidth(xpText);
		int iconAreaWidth = showIcons && !drop.skills.isEmpty()
				? drop.skills.size() * (iconSize + XP_DROP_ICON_SPACING) - XP_DROP_ICON_SPACING
				: 0;
		int gap = showIcons && !drop.skills.isEmpty() ? 6 : 0;
		int totalWidth = textWidth + gap + iconAreaWidth;
		int rowHeight = Math.max(iconSize, fm.getHeight());

		int x = centerX - totalWidth / 2;
		int contentTop = y - fm.getAscent() - 2;

		Composite oldComposite = graphics.getComposite();
		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

		if (config.xpDropShowBackground()) {
			graphics.setColor(new Color(0, 0, 0, (int) (80 * alpha)));
			graphics.fillRoundRect(x - 4, contentTop, totalWidth + 8, rowHeight + 4, 4, 4);
		}

		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int drawX = x;
		if (showIcons && !drop.skills.isEmpty()) {
			int iconY = contentTop + (rowHeight - iconSize) / 2;
			for (Skill skill : drop.skills) {
				BufferedImage icon = getSkillImage(skill);
				if (icon != null) {
					graphics.drawImage(icon, drawX, iconY, iconSize, iconSize, null);
				}
				drawX += iconSize + XP_DROP_ICON_SPACING;
			}
			drawX += gap;
		}

		graphics.setColor(new Color(0, 0, 0, (int) (180 * alpha)));
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx != 0 || dy != 0) {
					graphics.drawString(xpText, drawX + dx, y + dy);
				}
			}
		}
		graphics.setColor(new Color(0, 0, 0, (int) (120 * alpha)));
		graphics.drawString(xpText, drawX + 1, y + 1);
		graphics.setColor(config.xpDropFontColor());
		graphics.drawString(xpText, drawX, y);

		graphics.setFont(oldFont);
		graphics.setComposite(oldComposite);
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
		int xpForCurrentLevel = Experience.getXpForLevel(currentLevel);

		int goalXp = xpTrackerService.getEndGoalXp(xpGlobe.getSkill());
		if (isGoalSet(xpGlobe.getSkill()) && goalXp > currentXp) {
			Integer goalStartVarp = Constants.SKILL_GOAL_START_VARP.get(xpGlobe.getSkill());
			long goalStartXp = goalStartVarp != null ? client.getVarpValue(goalStartVarp) : xpForCurrentLevel;
			if (goalStartXp <= 0) {
				goalStartXp = xpForCurrentLevel;
			}
			long range = goalXp - goalStartXp;
			if (range <= 0) {
				return 1.0;
			}
			return Math.min(1.0, Math.max(0.0, (double) (currentXp - goalStartXp) / range));
		}

		if (currentLevel >= Experience.MAX_REAL_LEVEL) {
			return 0.0;
		}

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

		double iconAreaSize = 113.0 * scaleFactor;
		double iconAreaX = x + 53.0 * scaleFactor;
		double iconAreaY = baseY + 48.0 * scaleFactor;

		int iconWidth = skillImage.getWidth();
		int iconHeight = skillImage.getHeight();

		int iconX = (int) Math.round(iconAreaX + (iconAreaSize - iconWidth) / 2.0);
		int iconY = (int) Math.round(iconAreaY + (iconAreaSize - iconHeight) / 2.0);

		graphics.drawImage(skillImage, iconX, iconY, null);
	}

	private BufferedImage getSkillImage(Skill skill) {
		Integer iconID = Constants.SKILL_ICONS.get(skill);
		if (iconID == null) return null;
		SpritePixels spriteIcon = client.getSpriteOverrides().get(iconID);
		if (spriteIcon != null) {
			return spriteIcon.toBufferedImage();
		}
		return spriteManager.getSprite(iconID, 0);
	}

	private BufferedImage getScaledSkillIcon(XpGlobe xpGlobe) {
		double scaleFactor = config.orbScale() / 100.0;
		int targetOrbSize = (int) (DEFAULT_ORB_WIDTH * scaleFactor);

		if (xpGlobe.getSkillIcon() != null && xpGlobe.getSize() == targetOrbSize) {
			return xpGlobe.getSkillIcon();
		}

		BufferedImage icon = getSkillImage(xpGlobe.getSkill());
		if (icon == null) {
			return null;
		}

		int iconWidth = icon.getWidth();
		int iconHeight = icon.getHeight();

		int maxIconSize = (int) (113 * scaleFactor);

		if (maxIconSize <= 0) {
			return null;
		}

		double widthScale = (double) maxIconSize / iconWidth;
		double heightScale = (double) maxIconSize / iconHeight;

		double scale = Math.min(widthScale, heightScale);

		int scaledWidth = (int) Math.round(iconWidth * scale);
		int scaledHeight = (int) Math.round(iconHeight * scale);

		BufferedImage scaledIcon =
				ImageUtil.resizeImage(icon, scaledWidth, scaledHeight, true);

		xpGlobe.setSkillIcon(scaledIcon);
		xpGlobe.setSize(targetOrbSize);

		return scaledIcon;
	}

	private void cleanupGlobePositions(List<XpGlobe> xpGlobes) {
		if (globeXPositions.isEmpty()) {
			return;
		}

		globeXPositions.keySet().removeIf(globe -> !xpGlobes.contains(globe));
	}

	private String formatTooltipNumber(long n) {
		return config.useCompactNumbers() ? QuantityFormatter.formatNumber(n) : new DecimalFormat("###,###,###").format(n);
	}

	private void drawTooltip(XpGlobe mouseOverSkill, int goalXp) {
		mouseOverSkill.setTime(Instant.now());

		String skillName = mouseOverSkill.getSkill().getName();
		int currentLevel = mouseOverSkill.getCurrentLevel();
		int currentXp = mouseOverSkill.getCurrentXp();
		boolean hasGoal = isGoalSet(mouseOverSkill.getSkill()) && goalXp > currentXp;

		final PanelComponent xpTooltip = (PanelComponent) this.xpTooltip.getComponent();
		xpTooltip.getChildren().clear();

		String headerRight = hasGoal ? Integer.toString(currentLevel) : currentLevel + "/" + currentLevel;
		xpTooltip.getChildren().add(LineComponent.builder()
				.left(skillName)
				.right(headerRight)
				.build());

		xpTooltip.getChildren().add(LineComponent.builder()
				.left("Current XP:")
				.leftColor(Color.ORANGE)
				.right(formatTooltipNumber(currentXp))
				.build());

		int displayLevel = currentLevel;
		if (currentLevel >= Experience.MAX_REAL_LEVEL && config.showVirtualLevel()) {
			displayLevel = Experience.getLevelForXp(currentXp);
		}

		long xpForNextLevel;
		long xpRemainder;
		if (hasGoal) {
			xpForNextLevel = goalXp;
			xpRemainder = goalXp - currentXp;
		} else if (displayLevel < Experience.MAX_VIRT_LEVEL) {
			xpForNextLevel = Experience.getXpForLevel(displayLevel + 1);
			xpRemainder = xpForNextLevel - currentXp;
		} else {
			xpForNextLevel = 0;
			xpRemainder = 0;
		}
		if (xpForNextLevel > 0) {
			xpTooltip.getChildren().add(LineComponent.builder()
					.left("Next Level:")
					.leftColor(Color.ORANGE)
					.right(formatTooltipNumber(xpForNextLevel))
					.build());

			if (hasGoal) {
				int targetLevel = Experience.getLevelForXp(goalXp);
				xpTooltip.getChildren().add(LineComponent.builder()
						.left("Target Level:")
						.leftColor(Color.ORANGE)
						.right(Integer.toString(targetLevel))
						.build());
			}

			xpTooltip.getChildren().add(LineComponent.builder()
					.left("Remainder:")
					.leftColor(Color.ORANGE)
					.right(formatTooltipNumber(xpRemainder))
					.build());
		}

		int xpHr = xpTrackerService.getXpHr(mouseOverSkill.getSkill());
		long xpUntilGoal = hasGoal ? goalXp - currentXp : 0;
		double hoursToGoal = (hasGoal && xpHr > 0) ? xpUntilGoal / (double) xpHr : 0;
		boolean under15Hours = hasGoal && xpHr > 0 && hoursToGoal < 15.0;

		if (under15Hours) {

			if (config.showXpHour()) {
				xpTooltip.getChildren().add(LineComponent.builder()
						.left("XP per hour:")
						.leftColor(Color.ORANGE)
						.right(formatTooltipNumber(xpHr))
						.build());
			}

			if (config.showActionsLeft()) {
				int actionsLeft = xpTrackerService.getActionsLeft(mouseOverSkill.getSkill());
				if (actionsLeft != Integer.MAX_VALUE) {
					xpTooltip.getChildren().add(LineComponent.builder()
							.left("Actions left:")
							.leftColor(Color.ORANGE)
							.right(formatTooltipNumber(actionsLeft))
							.build());
				}
			}

			if (config.showTimeTilGoal()) {
				String timeLeft = xpTrackerService.getTimeTilGoal(mouseOverSkill.getSkill());
				xpTooltip.getChildren().add(LineComponent.builder()
						.left("Time left:")
						.leftColor(Color.ORANGE)
						.right(timeLeft)
						.build());
			}
		}

		if (hasGoal && config.showGoalBar()) {
			LineComponent spacerTop = LineComponent.builder().left("").right("").build();
			spacerTop.setPreferredSize(new Dimension(0, 2));
			xpTooltip.getChildren().add(spacerTop);
			long goalStartXp = getGoalStartXp(mouseOverSkill.getSkill(), currentXp);
			xpTooltip.getChildren().add(getProgressBarComponent(goalXp, (int) goalStartXp, currentXp));
		}

		tooltipManager.add(this.xpTooltip);
	}

	private boolean isGoalSet(Skill skill) {
		Integer goalEndVarp = Constants.SKILL_GOAL_END_VARP.get(skill);
		return goalEndVarp != null && client.getVarpValue(goalEndVarp) != 0;
	}

	private long getGoalStartXp(Skill skill, int currentXp) {
		Integer goalStartVarp = Constants.SKILL_GOAL_START_VARP.get(skill);
		if (goalStartVarp == null) {
			return Experience.getXpForLevel(Experience.getLevelForXp(currentXp));
		}
		long goalStartXp = client.getVarpValue(goalStartVarp);
		if (goalStartXp <= 0) {
			return Experience.getXpForLevel(Experience.getLevelForXp(currentXp));
		}
		return goalStartXp;
	}

	private static ProgressBarComponent getProgressBarComponent(int goalXp, int goalStartXp, int currentXp) {
		long range = goalXp - goalStartXp;
		double progress = range > 0 ? Math.min(1.0, (double) (currentXp - goalStartXp) / range) : 1.0;

		ProgressBarComponent progressBar = new ProgressBarComponent();
		progressBar.setForegroundColor(Color.decode("#00B200"));
		progressBar.setBackgroundColor(Color.decode("#FF0000"));
		progressBar.setFontColor(Color.WHITE);
		progressBar.setMinimum(0);
		progressBar.setMaximum(100);
		progressBar.setValue(progress * 100);
		progressBar.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.PERCENTAGE);
		return progressBar;
	}

	private static class PendingXpGain {
		final Skill skill;
		final int xp;
		final Instant timestamp;

		PendingXpGain(Skill skill, int xp, Instant timestamp) {
			this.skill = skill;
			this.xp = xp;
			this.timestamp = timestamp;
		}
	}

	private static class XpDrop {
		final int totalXp;
		final List<Skill> skills;
		final long spawnTime;

		XpDrop(int totalXp, List<Skill> skills, long spawnTime) {
			this.totalXp = totalXp;
			this.skills = skills;
			this.spawnTime = spawnTime;
		}
	}

}
