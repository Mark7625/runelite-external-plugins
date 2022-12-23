package io.mark.hdminimap;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class HDMinimapPluginLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(HDMinimapPlugin.class);
		RuneLite.main(args);
	}
}