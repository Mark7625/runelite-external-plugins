package io.mark.hdminimap.render.impl;

import io.mark.hdminimap.render.MinimapRenderer;
import io.mark.hdminimap.render.MinimapStyle;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;

import javax.inject.Inject;
import javax.inject.Singleton;

import static io.mark.hdminimap.render.MinimapRenderUtils.blend;
import static net.runelite.api.Constants.EXTENDED_SCENE_SIZE;
import static net.runelite.api.Constants.SCENE_SIZE;

@Singleton
@Slf4j
public class HD117Renderer extends MinimapRenderer {

    private static final int SCENE_OFFSET = (EXTENDED_SCENE_SIZE - SCENE_SIZE) / 2;

    @Setter
    public int[][][][] minimapTilePaintColorsLighting;
    @Setter
    public int[][][][][] minimapTileModelColorsLighting;

    @Inject
    private HDRenderer hdRenderer;

    private static final int DEFAULT_COLOR = 12345678;

    @Override
    public void drawMapTile(Tile tile, int tx, int ty, int px0, int py0, int px1, int py1) {
        if (minimapTileModelColorsLighting == null) {
            hdRenderer.drawMapTile(tile,tx,ty,px0,py0,px1,py1);
            return;
        }
        Rasterizer rasterizer = client.getRasterizer();
        rasterizer.setRasterGouraudLowRes(false);

        if (tile == null) {
            return;
        }

        try {
            int plane = tile.getPlane();
            int tileExX = tile.getSceneLocation().getX() + SCENE_OFFSET;
            int tileExY = tile.getSceneLocation().getY() + SCENE_OFFSET;

            var paint = tile.getSceneTilePaint();
            if (paint != null) {

                int[] colors = getTilePaintColor(plane, tileExX, tileExY);

                int swColor = colors[0];
                int seColor = colors[1];
                int nwColor = colors[2];
                int neColor = colors[3];

                int swTexture = colors[4];
                int seTexture = colors[5];
                int nwTexture = colors[6];
                int neTexture = colors[7];

                int tex = paint.getTexture();
                if (tex == -1) {
                    if (paint.getNwColor() != 12345678) {
                        fillGradient(px0, py0, px1, py1, nwColor, neColor, swColor, seColor);
                    } else {
                        client.getRasterizer().fillRectangle(px0, py0, px1 - px0, py1 - py0, paint.getRBG());
                    }
                } else {
                    boolean hasTexture = nwTexture != 0;
                    fillGradient(
                            px0,
                            py0,
                            px1,
                            py1,
                            hasTexture ? nwTexture : nwColor,
                            hasTexture ? neTexture : neColor,
                            hasTexture ? swTexture : swColor,
                            hasTexture ? seTexture : seColor
                    );
                }
            }

            var model = tile.getSceneTileModel();
            if (model != null) {
                int[] vertexX = model.getVertexX();
                int[] vertexZ = model.getVertexZ();

                int[] indicies1 = model.getFaceX();
                int[] indicies2 = model.getFaceY();
                int[] indicies3 = model.getFaceZ();

                int[] color1 = model.getTriangleColorA();
                int[] textures = model.getTriangleTextureId();

                int localX = tx << Perspective.LOCAL_COORD_BITS;
                int localY = ty << Perspective.LOCAL_COORD_BITS;

                int w = px1 - px0;
                int h = py1 - py0;

                for (int vert = 0; vert < vertexX.length; ++vert) {
                    tmpScreenX[vert] = px0 + (((vertexX[vert] - localX) * w) >> Perspective.LOCAL_COORD_BITS);
                    tmpScreenY[vert] = py0 + (((Perspective.LOCAL_TILE_SIZE - (vertexZ[vert] - localY)) * h) >> Perspective.LOCAL_COORD_BITS);
                }

                for (int face = 0; face < indicies1.length; ++face) {
                    int idx1 = indicies1[face];
                    int idx2 = indicies2[face];
                    int idx3 = indicies3[face];

                    int[] colors = getTileModelColor(plane, tileExX, tileExY, face);

                    int c1 = colors[0];
                    int c2 = colors[1];
                    int c3 = colors[2];


                    int mc1 = colors[3];
                    int mc2 = colors[4];
                    int mc3 = colors[5];


                    if ((textures != null && textures[face] != -1)) {
                        client.getRasterizer().rasterGouraud(
                                tmpScreenY[idx1], tmpScreenY[idx2], tmpScreenY[idx3],
                                tmpScreenX[idx1], tmpScreenX[idx2], tmpScreenX[idx3],
                                mc1, mc2, mc3
                        );
                    } else if (color1[face] != 12345678) {
                        client.getRasterizer().rasterGouraud(
                                tmpScreenY[idx1], tmpScreenY[idx2], tmpScreenY[idx3],
                                tmpScreenX[idx1], tmpScreenX[idx2], tmpScreenX[idx3],
                                mc1 == 0 ? c1 : mc1, mc2 == 0 ? c2 : mc2, mc3 == 0 ? c3 : mc3
                        );
                    }

                }
            }
        } catch (Exception e) {
            rasterizer.setRasterGouraudLowRes(true);
        }
    }


    public int[] getTileModelColor(int plane, int tileExX, int tileExY, int face) {
        return minimapTileModelColorsLighting[plane][tileExX][tileExY][face];
    }

    public int[] getTilePaintColor(int plane, int tileExX, int tileExY) {
        return minimapTilePaintColorsLighting[plane][tileExX][tileExY];
    }


}