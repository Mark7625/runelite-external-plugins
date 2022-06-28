package io.mark.hitsplats.combat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class CombatStyleTables
{
	private static final String ANIMATION_RESOURCE = "/io/mark/hitsplats/attack_animations.tsv";
	private static final String PROJECTILE_RESOURCE = "/io/mark/hitsplats/projectile_styles.tsv";

	private final ScheduledExecutorService executor;
	private volatile Map<Integer, CombatStyle> animations = Collections.emptyMap();
	private volatile Map<Integer, CombatStyle> projectiles = Collections.emptyMap();

	@Inject
	private CombatStyleTables(ScheduledExecutorService executor)
	{
		this.executor = executor;
	}

	public void load()
	{
		executor.execute(() ->
		{
			animations = read(ANIMATION_RESOURCE);
			projectiles = read(PROJECTILE_RESOURCE);
		});
	}

	public void clear()
	{
		animations = Collections.emptyMap();
		projectiles = Collections.emptyMap();
	}

	public CombatStyle forAnimation(int animationId)
	{
		return animationId == -1 ? null : animations.get(animationId);
	}

	public CombatStyle forProjectile(int projectileId)
	{
		return projectiles.get(projectileId);
	}

	private static Map<Integer, CombatStyle> read(String resource)
	{
		Map<Integer, CombatStyle> parsed = new HashMap<>();

		InputStream in = CombatStyleTables.class.getResourceAsStream(resource);
		if (in == null)
		{
			log.warn("Missing combat style table {}", resource);
			return parsed;
		}

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.charAt(0) == '#')
				{
					continue;
				}

				String[] parts = line.split("\t");
				if (parts.length < 2)
				{
					continue;
				}

				parsed.put(Integer.parseInt(parts[0]), CombatStyle.valueOf(parts[1]));
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Unable to read combat style table {}", resource, e);
		}

		log.debug("Loaded {} rows from {}", parsed.size(), resource);
		return parsed;
	}
}
