package io.mark.globes.model;

import java.awt.image.BufferedImage;
import java.time.Instant;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Skill;

@Getter
@Setter
@Data
public class XpGlobe {
	private Skill skill;
	private int currentXp;
	private int currentLevel;
	private Instant time;
	private int size;
	private BufferedImage skillIcon;
	private double animatedProgress;
	private long lastUpdateTime;
	private Instant firstCreatedTime;

	public XpGlobe(Skill skill, int currentXp, int currentLevel, Instant time) {
		this.skill = skill;
		this.currentXp = currentXp;
		this.currentLevel = currentLevel;
		this.time = time;
		this.animatedProgress = 0.0;
		this.lastUpdateTime = System.currentTimeMillis();
		this.firstCreatedTime = time;
	}
}
