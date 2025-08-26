package io.mark.runecast;

import net.runelite.client.config.*;

@ConfigGroup(RuneCastConfig.GROUP)
public interface RuneCastConfig extends Config
{
	String GROUP = "runecast";

	@ConfigSection(
			name = "OBS Integration",
			description = "Settings for OBS browser source integration",
			position = 0
	)
	String obsSettings = "obssettings";

	@ConfigItem(
			keyName = "enableHttpServer",
			name = "Enable HTTP Server",
			description = "Enable the HTTP server for OBS integration",
			section = obsSettings,
			position = 1
	)
	default boolean enableHttpServer()
	{
		return true;
	}

	@ConfigItem(
			keyName = "httpPort",
			name = "HTTP Server Port",
			description = "Port for the HTTP server (default: 8080)",
			section = obsSettings,
			position = 2
	)
	default int httpPort()
	{
		return 8080;
	}

	@ConfigItem(
			keyName = "showHpBar",
			name = "Show HP Bar",
			description = "Show a visual HP bar in addition to text",
			section = obsSettings,
			position = 3
	)
	default boolean showHpBar()
	{
		return true;
	}

	@ConfigItem(
			keyName = "refreshRate",
			name = "Update Rate (ms)",
			description = "How often to send updates via Server-Sent Events (default: 1000ms)",
			section = obsSettings,
			position = 7
	)
	default int refreshRate()
	{
		return 10;
	}
}
