package io.mark.hitsplats;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class HitsplatStylesPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(HitsplatStylesPlugin.class);
		RuneLite.main(args);
	}
}
