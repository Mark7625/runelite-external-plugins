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
import net.runelite.client.config.*;

import java.awt.*;

@ConfigGroup(F2pConfig.GROUP)
public interface F2pConfig extends Config
{
	String GROUP = "f2p";

	@ConfigSection(
			name = "Global",
			description = "Global Item Settings",
			position = 0
	)
	String globalSettings = "globalsettings";

	@ConfigItem(
			keyName = "active",
			name = "Active Mode",
			description = "Pick when you want the effects to show",
			position = 1,
			section = globalSettings
	)
	default ActiveType globalActive()
	{
		return ActiveType.ALWAYS;
	}

	@Range(
			min = -1,
			max = 390
	)
	@ConfigItem(
			keyName = "icon",
			name = "Icon",
			description = "Icon that shows if f2p (-1 = none)",
			position = 1,
			section = globalSettings
	)
	default int icon()
	{
		return -1;
	}

	@ConfigSection(
			name = "Grand Exchange",
			description = "Grand Exchange",
			position = 0
	)
	String grandexchangeSettings = "grandexchange";

	@ConfigItem(
			keyName = "geactive",
			name = "Active Mode",
			description = "Pick when you want the effects to show",
			position = 1,
			section = grandexchangeSettings
	)
	default ActiveType active()
	{
		return ActiveType.ALWAYS;
	}

	@ConfigItem(
			keyName = "geresultColor",
			name = "Result Color",
			description = "Displays the items in this color when p2p",
			position = 2,
			section = grandexchangeSettings
	)
	default Color textColor()
	{
		return Color.BLACK;
	}
}
