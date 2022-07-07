package io.mark.f2p;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class F2pPluginLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(F2pPlugin.class);
		RuneLite.main(args);
	}
}