package io.mark.hditemicons;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class HdItemIconsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(HdItemIconsPlugin.class);
		RuneLite.main(args);
	}
}