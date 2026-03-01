/*
 * Copyright (c) 2022, Mark
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.mark.f2p;

import io.mark.f2p.config.ActiveType;
import io.mark.f2p.config.OverlayMode;
import net.runelite.client.config.*;

import java.awt.*;

@ConfigGroup(F2pConfig.GROUP)
public interface F2pConfig extends Config
{
	String GROUP = "f2p";

    @ConfigItem(
            keyName = "overlayActive",
            name = "Overlay Active",
            description = "When to show the overlay: Always, Never, or Only on Members Worlds",
            position = 0
    )
    default ActiveType overlayActive()
    {
        return ActiveType.FREE_WORLDS_ONLY;
    }


    @ConfigSection(
			name = "Item Overlay",
			description = "Item Overlay Settings",
			position = 1
	)
	String itemOverlaySettings = "itemoverlaysettings";

	@Range(
			min = -1,
			max = 390
	)
	@ConfigItem(
			keyName = "icon",
			name = "Item Icon",
			description = "Icon that shows if f2p (-1 = none)",
			position = 1,
			section = itemOverlaySettings
	)
	default int icon()
	{
		return -1;
	}

	@ConfigItem(
			keyName = "overlayMode",
			name = "Item Mode",
			description = "Select the rendering mode for item overlays: Black and White (grayscale), Outline (colored outline), or Fill (colored fill)",
			position = 2,
			section = itemOverlaySettings
	)
	default OverlayMode overlayMode()
	{
		return OverlayMode.BLACK_AND_WHITE;
	}

	@ConfigItem(
			keyName = "overlayColor",
			name = "Item Overlay Color",
			description = "Color for outline and fill overlay modes",
			position = 3,
			section = itemOverlaySettings
	)
	default Color overlayColor()
	{
		return Color.RED;
	}

	@Range(max = 255)
	@ConfigItem(
			keyName = "overlayAlpha",
			name = "Item Overlay Alpha",
			description = "Transparency for overlay (0 = fully transparent, 255 = fully opaque)",
			position = 4,
			section = itemOverlaySettings
	)
	default int overlayAlpha()
	{
		return 125;
	}

	@ConfigItem(
			keyName = "excludeKeybind",
			name = "Exclude Keybind",
			description = "Hold this key and right-click an item to add Exclude/Include options to the menu",
			position = 5,
			section = itemOverlaySettings
	)
	default Keybind excludeKeybind()
	{
		return Keybind.SHIFT;
	}

	@ConfigSection(
			name = "World Map",
			description = "World Map Overlay Settings",
			position = 1
	)
	String worldMapSettings = "worldmapsettings";

	@ConfigItem(
			keyName = "showMapOverlay",
			name = "Show Map Overlay",
			description = "Show the grayscale overlay and border on the world map",
			position = 1,
			section = worldMapSettings
	)
	default boolean showMapOverlay()
	{
		return true;
	}

	@ConfigItem(
			keyName = "mapBorderColor",
			name = "Border Color",
			description = "Color for the F2P area border on the world map",
			position = 2,
			section = worldMapSettings
	)
	default Color mapBorderColor()
	{
		return Color.BLACK;
	}

	@Range(
			min = 1,
			max = 10
	)
	@ConfigItem(
			keyName = "mapBorderThickness",
			name = "Border Thickness",
			description = "Thickness of the F2P area border (1-10)",
			position = 3,
			section = worldMapSettings
	)
	default int mapBorderThickness()
	{
		return 2;
	}

	@ConfigItem(
			keyName = "mapFillColor",
			name = "Fill Color",
			description = "Color for the grayscale overlay fill (outside F2P area)",
			position = 4,
			section = worldMapSettings
	)
	default Color mapFillColor()
	{
		return new Color(128, 128, 128);
	}

	@Range(
			min = 0,
			max = 255
	)
	@ConfigItem(
			keyName = "mapAlpha",
			name = "Overlay Alpha",
			description = "Transparency for the grayscale overlay (0 = fully transparent, 255 = fully opaque)",
			position = 5,
			section = worldMapSettings
	)
	default int mapAlpha()
	{
		return 125;
	}

}
