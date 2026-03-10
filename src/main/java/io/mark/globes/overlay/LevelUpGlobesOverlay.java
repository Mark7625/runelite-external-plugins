package io.mark.globes.overlay;

import io.mark.globes.GlobeStyle;
import io.mark.globes.RemasteredXpGlobes;
import io.mark.globes.RemasteredXpGlobesConfig;
import io.mark.globes.model.LevelUpGlobe;
import io.mark.globes.OverlayFontType;
import io.mark.globes.model.MilestoneDisplay;
import io.mark.globes.util.Constants;
import io.mark.globes.util.ImageCache;
import net.runelite.api.Client;

import java.io.File;
import net.runelite.api.Skill;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.time.Duration;
import java.time.Instant;

public class LevelUpGlobesOverlay extends Overlay {

	private static final int FRAME_COUNT = 6;
	private static final long LEVEL_UP_FRAME_DURATION_MS = 100;
	private static final long MILESTONE_START_DELAY_MILLIS = 3000;
	private static final long MILESTONE_DISPLAY_MILLIS = 3000;
	private static final long ICON_DARKEN_AND_NUMBERS_START_MILLIS = 3000;
	private static final long MILESTONE_FADE_IN_MILLIS = 300;
	private static final long MILESTONE_SLIDE_START_MILLIS = 2000;
	private static final long ORB_FADE_MILLIS = 2000;
	private static final int MILESTONE_OFFSET_Y = 5;

	private static BufferedImage baseMilestoneLeft;
	private static BufferedImage baseMilestoneMiddle;
	private static BufferedImage baseMilestoneRight;
	private BufferedImage scaledMilestoneLeft;
	private BufferedImage scaledMilestoneMiddle;
	private BufferedImage scaledMilestoneRight;
	private int cachedMilestoneScale = -1;

	private int currentMilestoneIndex;
	private Instant currentMilestoneStartTime;
	private LevelUpGlobe lastLevelUpGlobe;

	private final ImageCache silverLevelUpImageCache;
	private final ImageCache goldLevelUpImageCache;
	private final ImageCache numberImageCache;
	private final RemasteredXpGlobes plugin;
	private final RemasteredXpGlobesConfig config;
	private final Client client;
	private final SpriteManager spriteManager;
	private final ItemManager itemManager;

	private static final int MILESTONE_ICON_SIZE = 18;
	private static final int MILESTONE_QUEST_ICON_SIZE = 15;
	private static final int MILESTONE_ICON_TEXT_GAP = 8;
	private static final int MILESTONE_ICON_CONTENT_PADDING = 28;
	private static final float MILESTONE_DARK_LUMINANCE_THRESHOLD = 0.36f;
	private static final float MILESTONE_BRIGHTEN_SCALE = 1.2f;
	private static final float MILESTONE_BRIGHTEN_OFFSET = 32f;

	private BufferedImage cachedQuestIcon;

	@Inject
	public LevelUpGlobesOverlay(RemasteredXpGlobes plugin, RemasteredXpGlobesConfig config, Client client, SpriteManager spriteManager, ItemManager itemManager) {
		this.plugin = plugin;
		this.config = config;
		this.client = client;
		this.spriteManager = spriteManager;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.TOP_CENTER);
		this.silverLevelUpImageCache = new ImageCache(RemasteredXpGlobes.class,
				() -> (config.globeStyle() == GlobeStyle.MODERN ? "globes_modern/" : "") + "level_up_silver/level_up_",
				FRAME_COUNT, 144, 98,
				() -> config.globeStyle() == GlobeStyle.CUSTOM ? config.customSpritesPath() : "");
		this.goldLevelUpImageCache = new ImageCache(RemasteredXpGlobes.class,
				() -> (config.globeStyle() == GlobeStyle.MODERN ? "globes_modern/" : "") + "level_up_gold/level_up_",
				FRAME_COUNT, 144, 98,
				() -> config.globeStyle() == GlobeStyle.CUSTOM ? config.customSpritesPath() : "");
		this.numberImageCache = new ImageCache(RemasteredXpGlobes.class,
				() -> (config.globeStyle() == GlobeStyle.MODERN ? "globes_modern/" : "") + "numbers/",
				10, 33, 48,
				() -> config.globeStyle() == GlobeStyle.CUSTOM ? config.customSpritesPath() : "");
		loadMilestoneImages();
	}

	public void clearCache()
	{
		silverLevelUpImageCache.clearCache();
		goldLevelUpImageCache.clearCache();
		numberImageCache.clearCache();
		cachedQuestIcon = null;

		baseMilestoneLeft = null;
		baseMilestoneMiddle = null;
		baseMilestoneRight = null;

		scaledMilestoneLeft = null;
		scaledMilestoneMiddle = null;
		scaledMilestoneRight = null;
		cachedMilestoneScale = -1;

		currentMilestoneIndex = 0;
		currentMilestoneStartTime = null;
		lastLevelUpGlobe = null;
	}

	private static BufferedImage safeLoadMilestonePart(String path) {
		try {
			return ImageUtil.loadImageResource(RemasteredXpGlobes.class, path);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private void loadMilestoneImages() {
		if (baseMilestoneLeft != null) {
			return;
		}
		String classicLeft = "level_up_silver/left.png";
		String classicMiddle = "level_up_silver/middle.png";
		String classicRight = "level_up_silver/right.png";
		BufferedImage internalLeft;
		BufferedImage internalMiddle;
		BufferedImage internalRight;
		if (config.globeStyle() == GlobeStyle.MODERN) {
			String modernBase = "/io/mark/globes_modern/level_up_silver/";
			internalLeft = safeLoadMilestonePart(modernBase + "left.png");
			if (internalLeft == null) internalLeft = safeLoadMilestonePart(classicLeft);
			internalMiddle = safeLoadMilestonePart(modernBase + "middle.png");
			if (internalMiddle == null) internalMiddle = safeLoadMilestonePart(classicMiddle);
			internalRight = safeLoadMilestonePart(modernBase + "right.png");
			if (internalRight == null) internalRight = safeLoadMilestonePart(classicRight);
		} else {
			internalLeft = safeLoadMilestonePart(classicLeft);
			internalMiddle = safeLoadMilestonePart(classicMiddle);
			internalRight = safeLoadMilestonePart(classicRight);
		}
		if (internalLeft == null || internalMiddle == null || internalRight == null) {
			baseMilestoneLeft = internalLeft;
			baseMilestoneMiddle = internalMiddle;
			baseMilestoneRight = internalRight;
			return;
		}
		baseMilestoneLeft = internalLeft;
		baseMilestoneMiddle = internalMiddle;
		baseMilestoneRight = internalRight;
		if (config.globeStyle() != GlobeStyle.CUSTOM) {
			return;
		}
		try {
			String customPath = config.customSpritesPath();
			if (customPath != null && !customPath.trim().isEmpty()) {
				File dir = new File(customPath.trim());
				int wLeft = internalLeft.getWidth(), hLeft = internalLeft.getHeight();
				int wMid = internalMiddle.getWidth(), hMid = internalMiddle.getHeight();
				int wRight = internalRight.getWidth(), hRight = internalRight.getHeight();
				BufferedImage customLeft = ImageCache.loadImageFromFile(new File(dir, "level_up_silver/left.png"), wLeft, hLeft);
				BufferedImage customMid = ImageCache.loadImageFromFile(new File(dir, "level_up_silver/middle.png"), wMid, hMid);
				BufferedImage customRight = ImageCache.loadImageFromFile(new File(dir, "level_up_silver/right.png"), wRight, hRight);
				if (customLeft != null) baseMilestoneLeft = customLeft;
				if (customMid != null) baseMilestoneMiddle = customMid;
				if (customRight != null) baseMilestoneRight = customRight;
			}
		} catch (Throwable ignored) {
		}
	}

	private void updateScaledMilestoneImages(int scale) {
		if (baseMilestoneLeft == null) {
			loadMilestoneImages();
		}
		if (baseMilestoneLeft == null || (scaledMilestoneLeft != null && cachedMilestoneScale == scale)) return;
		double s = scale / 100.0;
		scaledMilestoneLeft = ImageUtil.resizeImage(baseMilestoneLeft, (int)(baseMilestoneLeft.getWidth() * s), (int)(baseMilestoneLeft.getHeight() * s), true);
		scaledMilestoneMiddle = ImageUtil.resizeImage(baseMilestoneMiddle, (int)(baseMilestoneMiddle.getWidth() * s), (int)(baseMilestoneMiddle.getHeight() * s), true);
		scaledMilestoneRight = ImageUtil.resizeImage(baseMilestoneRight, (int)(baseMilestoneRight.getWidth() * s), (int)(baseMilestoneRight.getHeight() * s), true);
		cachedMilestoneScale = scale;
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		if (config.disableLevelUpPopups()) {
			lastLevelUpGlobe = null;
			currentMilestoneIndex = 0;
			currentMilestoneStartTime = null;
			return null;
		}

		LevelUpGlobe currentLevelUp = plugin.getCurrentLevelUp();
		if (currentLevelUp == null) {
			lastLevelUpGlobe = null;
			currentMilestoneIndex = 0;
			currentMilestoneStartTime = null;
			return null;
		}

		long elapsedMillis = Duration.between(currentLevelUp.getTime(), Instant.now()).toMillis();
		int maxMilestones = 0;
		if (config.showMilestones() && currentLevelUp.getMilestones() != null) {
			maxMilestones = Math.min(config.maxMilestones(), currentLevelUp.getMilestones().size());
		}
		
		long fadeStartMillis;
		long fadeDurationMillis;
		if (maxMilestones == 0) {
			fadeStartMillis = 5000;
			fadeDurationMillis = 1000;
		} else {
			long milestoneFinishMillis = MILESTONE_START_DELAY_MILLIS + maxMilestones * MILESTONE_DISPLAY_MILLIS;
			fadeStartMillis = milestoneFinishMillis;
			fadeDurationMillis = ORB_FADE_MILLIS;
		}

		float alpha = 1.0f;
		if (elapsedMillis >= fadeStartMillis) {
			if (elapsedMillis >= fadeStartMillis + fadeDurationMillis) {
				alpha = 0.0f;
			} else {
				alpha = 1.0f - (float) (elapsedMillis - fadeStartMillis) / fadeDurationMillis;
			}
		}

		long cycleMillis = FRAME_COUNT * LEVEL_UP_FRAME_DURATION_MS;
		long timeInCycle = elapsedMillis % cycleMillis;
		int frameIndex = (int) (timeInCycle / LEVEL_UP_FRAME_DURATION_MS) % FRAME_COUNT;
		float frameBlend = (timeInCycle % LEVEL_UP_FRAME_DURATION_MS) / (float) LEVEL_UP_FRAME_DURATION_MS;

		int scale = config.levelUpScale();
		ImageCache orbCache = currentLevelUp.getNewLevel() >= 99
			? goldLevelUpImageCache
			: silverLevelUpImageCache;

		BufferedImage image = orbCache.getScaledImage(frameIndex, scale);
		if (image == null) {
			return null;
		}

		int width = image.getWidth();
		int height = image.getHeight();
		int x = -(width / 2);
		int y = config.verticalOffset();

		double scaleFactor = scale / 100.0;
		int innerWidth = (int) (72 * scaleFactor);
		int innerHeight = (int) (72 * scaleFactor);
		int innerX = x + (int) (36 * scaleFactor);
		int innerY = y + (int) (19 * scaleFactor);

		if (config.showMilestones()) {
			updateScaledMilestoneImages(scale);
			updateMilestoneState(currentLevelUp);
			drawMilestoneMessage(graphics, currentLevelUp, x, y, width, height, scale);
		}

		Composite old = graphics.getComposite();
		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		graphics.drawImage(image, x, y, null);
		if (frameBlend > 0.001f) {
			int nextIndex = (frameIndex + 1) % FRAME_COUNT;
			BufferedImage nextImage = orbCache.getScaledImage(nextIndex, scale);
			if (nextImage != null) {
				graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * frameBlend));
				graphics.drawImage(nextImage, x, y, null);
			}
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		}
		drawInnerGlobe(graphics, currentLevelUp, innerX, innerY, innerWidth, innerHeight, alpha, elapsedMillis, scale);
		graphics.setComposite(old);

		return null;
	}

	private void updateMilestoneState(LevelUpGlobe levelUp) {
		if (levelUp.getMilestones() == null || levelUp.getMilestones().isEmpty()) return;
		if (lastLevelUpGlobe != levelUp) {
			currentMilestoneIndex = 0;
			currentMilestoneStartTime = null;
			lastLevelUpGlobe = levelUp;
		}
		long elapsed = Duration.between(levelUp.getTime(), Instant.now()).toMillis();
		if (elapsed < MILESTONE_START_DELAY_MILLIS) return;
		if (currentMilestoneStartTime == null) {
			currentMilestoneStartTime = Instant.ofEpochMilli(levelUp.getTime().toEpochMilli() + MILESTONE_START_DELAY_MILLIS);
			return;
		}
		int max = Math.min(config.maxMilestones(), levelUp.getMilestones().size());
		if (currentMilestoneIndex >= max) return;
		long milestoneElapsed = Duration.between(currentMilestoneStartTime, Instant.now()).toMillis();
		if (milestoneElapsed >= MILESTONE_DISPLAY_MILLIS) {
			currentMilestoneIndex++;
			currentMilestoneStartTime = Instant.now();
		}
	}

	private void drawMilestoneMessage(Graphics2D graphics, LevelUpGlobe levelUp, int orbX, int orbY, int orbWidth, int orbHeight, int scalePercent) {
		if (levelUp.getMilestones() == null || levelUp.getMilestones().isEmpty()) return;
		int max = Math.min(config.maxMilestones(), levelUp.getMilestones().size());
		if (currentMilestoneIndex >= max || scaledMilestoneLeft == null || scaledMilestoneMiddle == null || scaledMilestoneRight == null) return;

		MilestoneDisplay milestone = levelUp.getMilestones().get(currentMilestoneIndex);
		String message = milestone.getMessage();
		if (message == null || message.isEmpty()) return;
		message = stripAfterBr(message);
		if (message.isEmpty()) return;

		boolean showIcon = config.showMilestoneIcons() && milestone.getIconItemId() != -1;
		int iconSize = (milestone.getIconItemId() == MilestoneDisplay.QUEST_ICON_ID) ? MILESTONE_QUEST_ICON_SIZE : MILESTONE_ICON_SIZE;
		BufferedImage iconImage = null;
		if (showIcon) {
			if (milestone.getIconItemId() == MilestoneDisplay.QUEST_ICON_ID) {
				iconImage = getQuestIcon();
			} else {
				iconImage = itemManager.getImage(milestone.getIconItemId());
			}
		}
		float fontSize = (float) (config.overlayFont().getFont().getSize2D() * Math.max(0.5, scalePercent / 100.0));
		Font font = config.overlayFont().getFont().deriveFont(fontSize);
		FontMetrics fm = graphics.getFontMetrics(font);
		int textWidth = fm.stringWidth(message);
		int textHeight = fm.getHeight();
		int horizontalPadding = showIcon ? MILESTONE_ICON_CONTENT_PADDING : 20;
		int contentWidth = horizontalPadding + (showIcon ? (iconSize + MILESTONE_ICON_TEXT_GAP) : 0) + textWidth + horizontalPadding;

		int leftW = scaledMilestoneLeft.getWidth();
		int middleW = scaledMilestoneMiddle.getWidth();
		int rightW = scaledMilestoneRight.getWidth();
		int middleNeeded = contentWidth - leftW - rightW;
		int middleCount = middleW > 0 ? Math.max(1, (int) Math.ceil((double) middleNeeded / middleW)) : 1;
		int bgWidth = leftW + middleW * middleCount + rightW;
		int bgHeight = scaledMilestoneLeft.getHeight();

		double scaleFactor = scalePercent / 100.0;
		int baseY = orbY + orbHeight + (int) (MILESTONE_OFFSET_Y * scaleFactor);
		int milestoneX = orbX + (orbWidth - bgWidth) / 2;
		int milestoneY = baseY;
		int leftEndX = milestoneX + leftW;
		int rightStartX = leftEndX + middleW * middleCount;

		long milestoneElapsed = currentMilestoneStartTime == null ? 0 : Duration.between(currentMilestoneStartTime, Instant.now()).toMillis();
		float alpha = 1.0f;
		if (milestoneElapsed < MILESTONE_FADE_IN_MILLIS) {
			alpha = (float) milestoneElapsed / MILESTONE_FADE_IN_MILLIS;
		} else if (milestoneElapsed >= MILESTONE_SLIDE_START_MILLIS) {
			long intoSlide = milestoneElapsed - MILESTONE_SLIDE_START_MILLIS;
			long slideDuration = MILESTONE_DISPLAY_MILLIS - MILESTONE_SLIDE_START_MILLIS;
			float progress = Math.min(1f, (float) intoSlide / slideDuration);
			alpha = 1f - progress;
			int targetY = orbY + orbHeight / 2 - bgHeight / 2;
			milestoneY = (int) (baseY - (baseY - targetY) * progress);
		}

		Composite oldComposite = graphics.getComposite();
		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

		graphics.drawImage(scaledMilestoneLeft, milestoneX, milestoneY, null);
		for (int i = 0; i < middleCount; i++) {
			graphics.drawImage(scaledMilestoneMiddle, leftEndX + i * middleW, milestoneY, null);
		}
		graphics.drawImage(scaledMilestoneRight, rightStartX, milestoneY, null);

		int barCenterX = milestoneX + bgWidth / 2;
		int contentBlockWidth = (showIcon ? iconSize + MILESTONE_ICON_TEXT_GAP : 0) + textWidth;
		int contentBlockLeftX = barCenterX - contentBlockWidth / 2;

		int iconX = contentBlockLeftX;
		int textX = showIcon ? contentBlockLeftX + iconSize + MILESTONE_ICON_TEXT_GAP : barCenterX - textWidth / 2;
		int iconY = milestoneY + (bgHeight - iconSize) / 2;
		int textY = milestoneY + (bgHeight - textHeight) / 2 + fm.getAscent();

		Font oldFont = graphics.getFont();
		graphics.setFont(font);
		if (showIcon && iconImage != null) {
			BufferedImage toDraw = iconImage;
			boolean isQuestIcon = milestone.getIconItemId() == MilestoneDisplay.QUEST_ICON_ID;
			if (!isQuestIcon) {
				BufferedImage scaled = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB);
				Graphics2D sg = scaled.createGraphics();
				sg.drawImage(iconImage, 0, 0, iconSize, iconSize, null);
				sg.dispose();
				float avgLum = averageLuminance(scaled);
				if (avgLum < MILESTONE_DARK_LUMINANCE_THRESHOLD) {
					RescaleOp rescaleOp = new RescaleOp(MILESTONE_BRIGHTEN_SCALE, MILESTONE_BRIGHTEN_OFFSET, null);
					toDraw = rescaleOp.filter(scaled, null);
				} else {
					toDraw = scaled;
				}
			}
			if (toDraw.getWidth() == iconSize && toDraw.getHeight() == iconSize) {
				graphics.drawImage(toDraw, iconX, iconY, null);
			} else {
				graphics.drawImage(toDraw, iconX, iconY, iconSize, iconSize, null);
			}
		}
		graphics.setColor(Color.WHITE);
		graphics.drawString(message, textX, textY);
		graphics.setFont(oldFont);

		graphics.setComposite(oldComposite);
	}

	private static float averageLuminance(BufferedImage img) {
		int w = img.getWidth();
		int h = img.getHeight();
		if (w <= 0 || h <= 0) return 0f;
		long sum = 0;
		int count = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int rgb = img.getRGB(x, y);
				int a = (rgb >> 24) & 0xFF;
				int r = (rgb >> 16) & 0xFF;
				int g = (rgb >> 8) & 0xFF;
				int b = rgb & 0xFF;
				if (a > 0) {
					sum += (int) (0.299 * r + 0.587 * g + 0.114 * b);
					count++;
				}
			}
		}
		return count == 0 ? 0f : (sum / (float) count) / 255f;
	}

	private void drawInnerGlobe(Graphics2D graphics, LevelUpGlobe levelUp, int imageX, int imageY, int sizeX, int sizeY, float alpha, long elapsedMillis, int scalePercent) {
		BufferedImage icon = getSkillImage(levelUp.getSkill());
		if (icon == null) {
			return;
		}
		double scaleFactor = scalePercent / 100.0;
		int targetIconSize = (int) (44 * scaleFactor);
		int maxSize = (int) (Math.min(sizeX, sizeY) * 0.85);
		int iconW = icon.getWidth();
		int iconH = icon.getHeight();
		int maxDim = Math.max(iconW, iconH);
		if (maxDim <= 0) {
			return;
		}
		int scaledSize = targetIconSize;
		if (scaledSize > maxSize) {
			scaledSize = maxSize;
		}
		if (scaledSize <= 0) {
			return;
		}
		double iconScale = (double) scaledSize / maxDim;
		int scaledW = (int) (iconW * iconScale);
		int scaledH = (int) (iconH * iconScale);
		if (scaledW <= 0 || scaledH <= 0) {
			return;
		}
		BufferedImage scaledIcon = ImageUtil.resizeImage(icon, scaledW, scaledH, true);
		
		int actualScaledW = scaledIcon.getWidth();
		int actualScaledH = scaledIcon.getHeight();
		
		double circleWidth = (double) sizeX;
		double circleHeight = (double) sizeY;
		double circleX = (double) imageX;
		double circleY = (double) imageY;
		
		int iconX = (int) Math.round(circleX + (circleWidth - actualScaledW) / 2.0);
		int iconY = (int) Math.round(circleY + (circleHeight - actualScaledH) / 2.0 + (4.0 * scaleFactor));

		float iconAlpha = elapsedMillis >= ICON_DARKEN_AND_NUMBERS_START_MILLIS ? 0.5f : 1.0f;
		iconAlpha *= alpha;

		Composite old = graphics.getComposite();
		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, iconAlpha));
		graphics.drawImage(scaledIcon, iconX, iconY, null);
		graphics.setComposite(old);

		if (elapsedMillis >= ICON_DARKEN_AND_NUMBERS_START_MILLIS) {
			BufferedImage[] numbers = numberImageCache.getAllScaledImages(scalePercent);
			if (numbers != null) {
				String levelStr = String.valueOf(levelUp.getNewLevel());
				int numW = numbers[0].getWidth();
				int numH = numbers[0].getHeight();
				int totalW = numW * levelStr.length();
				int startX = imageX + (sizeX - totalW) / 2;
				int numY = imageY + (sizeY - numH) / 2;
				long timeSinceNumbersShown = elapsedMillis - ICON_DARKEN_AND_NUMBERS_START_MILLIS;
				float numberAlpha = Math.min(1f, timeSinceNumbersShown / 300f) * 0.5f * alpha;
				graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, numberAlpha));
				for (int i = 0; i < levelStr.length(); i++) {
					int digit = Character.getNumericValue(levelStr.charAt(i));
					if (digit >= 0 && digit < 10 && numbers[digit] != null) {
						graphics.drawImage(numbers[digit], startX + i * numW, numY, null);
					}
				}
				graphics.setComposite(old);
			}
		}
	}

	private static String stripAfterBr(String message) {
		if (message == null) return "";
		int i = message.indexOf("<br>");
		if (i < 0) i = message.indexOf("<br/>");
		if (i < 0) i = message.indexOf("<br />");
		if (i < 0) i = message.indexOf("<BR>");
		return i >= 0 ? message.substring(0, i).trim() : message;
	}

	private BufferedImage getSkillImage(Skill skill) {
		int iconID = Constants.SKILL_ICONS.get(skill);
		SpritePixels spriteIcon = client.getSpriteOverrides().get(iconID);
		if (spriteIcon != null) {
			return spriteIcon.toBufferedImage();
		}
		return spriteManager.getSprite(iconID, 0);
	}

	private BufferedImage getQuestIcon() {
		if (cachedQuestIcon != null) {
			return cachedQuestIcon;
		}
		int iconID = SpriteID.SideIcons.QUEST;
		SpritePixels spriteIcon = client.getSpriteOverrides().get(iconID);
		if (spriteIcon != null) {
			cachedQuestIcon = spriteIcon.toBufferedImage();
		} else {
			cachedQuestIcon = spriteManager.getSprite(iconID, 0);
		}
		return cachedQuestIcon;
	}

}
