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

import com.google.inject.Provides;
import io.mark.f2p.config.ActiveType;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.ItemComposition;
import net.runelite.api.ScriptID;
import net.runelite.api.events.PostItemComposition;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.http.api.worlds.WorldType;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;

@PluginDescriptor(
	name = "F2P Highlight",
	description = "Highlight F2P items and areas!",
	tags = {"f2p","grand exchange"}
)

public class F2pPlugin extends Plugin
{

	@Inject
	@Getter
	private Client client;

	@Inject
	@Getter
	private F2pConfig config;

	@Inject
	@Getter
	private ItemManager itemManager;

	@Provides
	F2pConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(F2pConfig.class);
	}

	@Inject
	private ClientThread clientThread;

	@Inject
	WorldService worldService;

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.GE_ITEM_SEARCH)
		{
			highlightSearchMatches();
		}
	}


	public BufferedImage toBufferedImage(IndexedSprite sprite) {
		BufferedImage image = new BufferedImage(sprite.getWidth(), sprite.getHeight(), 2);
		toBufferedImage(image, sprite);
		return image;
	}


	public void toBufferedImage(BufferedImage img, IndexedSprite sprite) throws IllegalArgumentException {
		int width = sprite.getWidth();
		int height = sprite.getHeight();
		int[] pixels = sprite.getPalette();
		int[] palette = new int[pixels.length];
		for (int pixel = 0; pixel < pixels.length; pixel++) {
			if (pixels[pixel] != 0) {
				palette[pixel] = pixels[pixel] | 0xFF000000;
			}
		}
		img.setRGB(0, 0, width, height, palette, 0, width);
	}

	@Subscribe
	public void onPostItemComposition(PostItemComposition event) {
		ItemComposition item = event.getItemComposition();
		if(!item.isMembers() && isActive(config.globalActive()) && config.icon() != -1) {
			event.getItemComposition().setName(formatName(event.getItemComposition().getName()));
		}
	}

	private String formatName(String name) {
		return "<img=" + config.icon() + ">" + name;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{

		if (!event.getGroup().equals("f2p"))
		{
			return;
		}

		String key = event.getKey();

		switch (key) {
			case "geresultColor":
			case "geactive":
				clientThread.invoke(() -> highlightSearchMatches());
			break;
		}

	}

	public boolean isMembers(int item) {
		return itemManager.getItemComposition(item).isMembers();
	}

	private void highlightSearchMatches()
	{

		if(isActive(config.active())) {
			Widget results = client.getWidget(WidgetInfo.CHATBOX_GE_SEARCH_RESULTS);
			Widget[] children = results.getDynamicChildren();
			int resultCount = children.length / 3;

			for (int i = 0; i < resultCount; i++)
			{
				Widget itemNameWidget = children[i * 3 + 1];
				Widget item = children[i * 3 + 2];
				if (!isMembers(item.getItemId())) {
					itemNameWidget.setTextColor(fromRGB(config.textColor()));
				}
			}
		}
	}

	public static int fromRGB(Color c)
	{
		return fromRGB(c.getRed(), c.getGreen(), c.getBlue());
	}

	protected static int fromRGB(int r, int g, int b)
	{
		return (r << 16) + (g << 8) + b;
	}

	@Override
	public void shutDown()
	{

	}

	private boolean isActive(ActiveType type) {
		if(type == ActiveType.ALWAYS) {
			return true;
		}
		if (type == ActiveType.MEMBERS_WORLD) {
			return worldService.getWorlds().findWorld(client.getWorld()).getTypes().contains(WorldType.MEMBERS);
		}
		return false;
	}

}
