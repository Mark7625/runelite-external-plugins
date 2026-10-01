package io.mark.hitsplats;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import io.mark.hitsplats.art.HitsplatStyle;
import io.mark.hitsplats.combat.CombatStyle;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class StyleResourcesTest
{
	private static final String RESOURCE_ROOT = "/io/mark/hitsplats/";

	@Test
	public void everyStyleShipsTheCoreArt()
	{
		for (HitsplatStyle style : HitsplatStyle.values())
		{
			assertResourceExists(style.getDirectory() + "/damage_normal.png");
			assertResourceExists(style.getDirectory() + "/block_normal.png");
		}
	}

	@Test
	public void everyCombatStyleShipsAnIcon()
	{
		for (CombatStyle style : CombatStyle.values())
		{
			assertResourceExists("style_icons/" + style.getFileName() + ".png");
		}
	}

	@Test
	public void attackAnimationTableParses()
	{
		assertTrue(readTable("attack_animations.tsv") > 0);
	}

	@Test
	public void projectileTableParses()
	{
		assertTrue(readTable("projectile_styles.tsv") > 0);
	}

	private static void assertResourceExists(String path)
	{
		assertNotNull("missing resource " + path, HitsplatStyle.class.getResource(RESOURCE_ROOT + path));
	}

	private static int readTable(String resource)
	{
		int rows = 0;

		try (InputStream in = HitsplatStyle.class.getResourceAsStream(RESOURCE_ROOT + resource))
		{
			assertNotNull("missing resource " + resource, in);

			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.charAt(0) == '#')
				{
					continue;
				}

				String[] parts = line.split("\t");
				assertTrue(resource + " row is not id<TAB>style: " + line, parts.length >= 2);
				Integer.parseInt(parts[0]);
				CombatStyle.valueOf(parts[1]);
				rows++;
			}
		}
		catch (IOException e)
		{
			fail("could not read " + resource + ": " + e.getMessage());
		}

		return rows;
	}
}
