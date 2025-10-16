package io.mark.hdminimap.render;

import net.runelite.api.Client;
import net.runelite.api.Rasterizer;
import net.runelite.api.Tile;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public abstract class MinimapRenderer {

    protected final int[] tmpScreenX = new int[6];
    protected final int[] tmpScreenY = new int[6];

    @Inject
    protected Client client;

    public abstract void drawMapTile(Tile tile, int tx, int ty, int px0, int py0, int px1, int py1);

    protected void fillGradient(int px0, int py0, int px1, int py1, int c00, int c10, int c01, int c11) {
        Rasterizer g3d = client.getRasterizer();
        g3d.rasterGouraud(py0, py0, py1, px0, px1, px0, c00, c10, c01);
        g3d.rasterGouraud(py0, py1, py1, px1, px0, px1, c10, c01, c11);
    }
}