package io.mark.hitsplats.overlay;

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
public class NpcHeights
{
	private static final String RESOURCE = "/io/mark/hitsplats/npc_heights.tsv";

	private final ScheduledExecutorService executor;
	private volatile Map<Integer, Integer> heights = Collections.emptyMap();

	@Inject
	private NpcHeights(ScheduledExecutorService executor)
	{
		this.executor = executor;
	}

	public void load()
	{
		executor.execute(() -> heights = read());
	}

	public void clear()
	{
		heights = Collections.emptyMap();
	}

	public int get(int npcId)
	{
		Integer height = heights.get(npcId);
		return height == null ? -1 : height;
	}

	private static Map<Integer, Integer> read()
	{
		Map<Integer, Integer> parsed = new HashMap<>();

		InputStream in = NpcHeights.class.getResourceAsStream(RESOURCE);
		if (in == null)
		{
			log.warn("Missing npc height table {}", RESOURCE);
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

				parsed.put(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Unable to read npc height table", e);
		}

		log.debug("Loaded {} npc heights", parsed.size());
		return parsed;
	}
}
