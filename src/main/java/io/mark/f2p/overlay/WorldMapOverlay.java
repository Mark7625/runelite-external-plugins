package io.mark.f2p.overlay;

import com.google.inject.Singleton;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.mark.f2p.F2pConfig;
import io.mark.f2p.F2pPlugin;
import io.mark.f2p.utils.WorldPerspective;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.*;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;


@Slf4j
@Singleton
public class WorldMapOverlay extends Overlay {

    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private F2pConfig config;

    @Inject
    private F2pPlugin f2pPlugin;

    @Inject
    private Gson gson;

    private List<int[]> pts;
    private List<OverrideArea> overrideNonMembers;
    private List<OverrideArea> overrideMembers;
    
    private float cachedZoom = -1;
    private Point cachedMapPosition = null;
    private Rectangle cachedMapViewArea = null;
    private GeneralPath cachedF2pPolygon = null;
    private List<GeneralPath> cachedOverridePolygons = null;
    private Rectangle cachedGrayscaleBoundsRect = null;
    private Area cachedF2pArea = null;

    public void loadPointsFromJson() {
        try {
            InputStream inputStream = WorldMapOverlay.class.getResourceAsStream("/f2p-area.json");
            if (inputStream == null) {
                log.warn("Could not find f2p-area.json resource, using empty lists");
                this.pts = new ArrayList<>();
                this.overrideNonMembers = new ArrayList<>();
                this.overrideMembers = new ArrayList<>();
                return;
            }

            F2pAreaData data = gson.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), F2pAreaData.class);
            
            if (data != null) {
                this.pts = data.points != null ? data.points : new ArrayList<>();
                this.overrideNonMembers = data.overrideNonMembers != null ? data.overrideNonMembers : new ArrayList<>();
                this.overrideMembers = data.overrideMembers != null ? data.overrideMembers : new ArrayList<>();
            } else {
                this.pts = new ArrayList<>();
                this.overrideNonMembers = new ArrayList<>();
                this.overrideMembers = new ArrayList<>();
            }
            
            inputStream.close();
            log.info("Loaded {} points, {} override non-members, and {} override members from f2p-area.json", 
                this.pts.size(), this.overrideNonMembers.size(), this.overrideMembers.size());
        } catch (Exception e) {
            log.error("Error loading points from JSON", e);
            this.pts = new ArrayList<>();
            this.overrideNonMembers = new ArrayList<>();
            this.overrideMembers = new ArrayList<>();
        }
    }
    
    private static class F2pAreaData {
        List<int[]> points;
        List<OverrideArea> overrideNonMembers;
        List<OverrideArea> overrideMembers;
    }
    
    private static class OverrideArea {
        String name;
        List<int[]> points;
    }


    public WorldMapOverlay() {
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.DYNAMIC);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!f2pPlugin.isActive()) {
            return null;
        }
        Rectangle mapViewArea = WorldPerspective.getWorldMapClipArea(client);
        if (mapViewArea == null) {
            return null;
        }

        if (!config.showMapOverlay()) {
            return null;
        }
        
        var worldMap = client.getWorldMap();
        if (worldMap == null) {
            return null;
        }
        
        float currentZoom = worldMap.getWorldMapZoom();
        Point currentMapPosition = worldMap.getWorldMapPosition();
        boolean mapStateChanged = cachedZoom != currentZoom || 
                                  cachedMapPosition == null || 
                                  !cachedMapPosition.equals(currentMapPosition);
        
        boolean viewAreaChanged = cachedMapViewArea == null || 
                                  !cachedMapViewArea.equals(mapViewArea);
        
        if (mapStateChanged) {
            invalidateMapCache();
            cachedZoom = currentZoom;
            cachedMapPosition = currentMapPosition;
        }
        
        if (viewAreaChanged) {
            cachedGrayscaleBoundsRect = null;
            cachedMapViewArea = mapViewArea;
        }

        applyGrayscaleOverlay(graphics, client, mapViewArea, mapStateChanged, viewAreaChanged);
        drawBlackBorder(graphics, client, mapViewArea);

        return null;
    }
    
    private void invalidateMapCache() {
        cachedF2pPolygon = null;
        cachedOverridePolygons = null;
        cachedGrayscaleBoundsRect = null;
        cachedF2pArea = null;
        cachedMapViewArea = null;
    }

    private Rectangle getGrayscaleBoundsRect(Client client, Rectangle mapViewArea) {
        if (cachedGrayscaleBoundsRect != null) {
            return cachedGrayscaleBoundsRect;
        }
        
        try {
            int bottomRightX = 4030, bottomRightY = 2049;
            int bottomLeftX = 960, bottomLeftY = 2049;
            int topLeftX = 960, topLeftY = 4222;
            int topRightX = 4030, topRightY = 4222;
            
            net.runelite.api.Point topLeft = WorldPerspective.mapWorldPointToGraphicsPoint(client, new WorldPoint(topLeftX, topLeftY, 0));
            net.runelite.api.Point topRight = WorldPerspective.mapWorldPointToGraphicsPoint(client, new WorldPoint(topRightX, topRightY, 0));
            net.runelite.api.Point bottomLeft = WorldPerspective.mapWorldPointToGraphicsPoint(client, new WorldPoint(bottomLeftX, bottomLeftY, 0));
            net.runelite.api.Point bottomRight = WorldPerspective.mapWorldPointToGraphicsPoint(client, new WorldPoint(bottomRightX, bottomRightY, 0));
            
            if (topLeft != null && topRight != null && bottomLeft != null && bottomRight != null) {
                int minX = Math.min(Math.min(topLeft.getX(), topRight.getX()), Math.min(bottomLeft.getX(), bottomRight.getX()));
                int maxX = Math.max(Math.max(topLeft.getX(), topRight.getX()), Math.max(bottomLeft.getX(), bottomRight.getX()));
                int minY = Math.min(Math.min(topLeft.getY(), topRight.getY()), Math.min(bottomLeft.getY(), bottomRight.getY()));
                int maxY = Math.max(Math.max(topLeft.getY(), topRight.getY()), Math.max(bottomLeft.getY(), bottomRight.getY()));
                
                cachedGrayscaleBoundsRect = new Rectangle(minX, minY, maxX - minX, maxY - minY);
                return cachedGrayscaleBoundsRect;
            }
            
            cachedGrayscaleBoundsRect = mapViewArea;
            return cachedGrayscaleBoundsRect;
        } catch (Exception e) {
            log.warn("Error creating grayscale bounds rect", e);
            cachedGrayscaleBoundsRect = mapViewArea;
            return cachedGrayscaleBoundsRect;
        }
    }

    private GeneralPath createPolygonFromPoints(Client client, List<int[]> points) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        
        GeneralPath polygon = new GeneralPath(Path2D.WIND_EVEN_ODD);
        boolean first = true;
        int validPoints = 0;
        
        for (int[] point : points) {
            int x = point[0];
            int y = point[1];
            net.runelite.api.Point graphicsPoint = WorldPerspective.mapWorldPointToGraphicsPoint(client, new WorldPoint(x, y, 0));
            if (graphicsPoint == null) {
                continue;
            }
            
            if (first) {
                polygon.moveTo(graphicsPoint.getX(), graphicsPoint.getY());
                first = false;
                validPoints++;
            } else {
                polygon.lineTo(graphicsPoint.getX(), graphicsPoint.getY());
                validPoints++;
            }
        }
        
        if (validPoints < 3) {
            return null;
        }
        
        polygon.closePath();
        return polygon;
    }
    
    private GeneralPath getOrCreateF2pPolygon(Client client, boolean forceRecalculate) {
        if (!forceRecalculate && cachedF2pPolygon != null) {
            return cachedF2pPolygon;
        }
        
        cachedF2pPolygon = createPolygonFromPoints(client, pts);
        return cachedF2pPolygon;
    }
    
    private List<GeneralPath> getOrCreateOverridePolygons(Client client, boolean forceRecalculate) {
        if (!forceRecalculate && cachedOverridePolygons != null) {
            return cachedOverridePolygons;
        }
        
        cachedOverridePolygons = new ArrayList<>();
        for (OverrideArea overrideArea : overrideNonMembers) {
            if (overrideArea.points != null) {
                GeneralPath overridePath = createPolygonFromPoints(client, overrideArea.points);
                if (overridePath != null) {
                    cachedOverridePolygons.add(overridePath);
                }
            }
        }
        return cachedOverridePolygons;
    }
    
    private List<GeneralPath> getOrCreateOverrideMembersPolygons(Client client, boolean forceRecalculate) {
        List<GeneralPath> overrideMembersPolygons = new ArrayList<>();
        for (OverrideArea overrideArea : overrideMembers) {
            if (overrideArea.points != null) {
                GeneralPath overridePath = createPolygonFromPoints(client, overrideArea.points);
                if (overridePath != null) {
                    overrideMembersPolygons.add(overridePath);
                }
            }
        }
        return overrideMembersPolygons;
    }
    
    private Area getOrCreateF2pArea(Client client, boolean forceRecalculate) {
        if (!forceRecalculate && cachedF2pArea != null) {
            return cachedF2pArea;
        }
        
        GeneralPath f2pPolygon = getOrCreateF2pPolygon(client, forceRecalculate);
        if (f2pPolygon == null) {
            return null;
        }
        
        cachedF2pArea = new Area(f2pPolygon);
        
        List<GeneralPath> overridePolygons = getOrCreateOverridePolygons(client, forceRecalculate);
        for (GeneralPath overridePath : overridePolygons) {
            cachedF2pArea.subtract(new Area(overridePath));
        }
        
        return cachedF2pArea;
    }

    private void applyGrayscaleOverlay(Graphics2D graphics, Client client, Rectangle mapViewArea, boolean mapStateChanged, boolean viewAreaChanged) {
        try {
            Rectangle grayscaleBoundsRect = getGrayscaleBoundsRect(client, mapViewArea);
            if (grayscaleBoundsRect == null || grayscaleBoundsRect.isEmpty()) {
                return;
            }
            
            Area grayscaleArea = new Area(grayscaleBoundsRect);
            
            Area f2pArea = getOrCreateF2pArea(client, mapStateChanged);
            if (f2pArea != null) {
                grayscaleArea.subtract(f2pArea);
                
                List<GeneralPath> overridePolygons = getOrCreateOverridePolygons(client, mapStateChanged);
                for (GeneralPath overridePath : overridePolygons) {
                    grayscaleArea.add(new Area(overridePath));
                }
            }
            
            List<GeneralPath> overrideMembersPolygons = getOrCreateOverrideMembersPolygons(client, mapStateChanged);
            for (GeneralPath overridePath : overrideMembersPolygons) {
                grayscaleArea.subtract(new Area(overridePath));
            }
            
            grayscaleArea.intersect(new Area(mapViewArea));
            
            if (grayscaleArea.isEmpty()) {
                return;
            }

            Composite originalComposite = graphics.getComposite();
            Shape originalClip = graphics.getClip();
            
            Rectangle clipRect = mapViewArea.intersection(grayscaleBoundsRect);
            graphics.setClip(clipRect);
            
            Color fillColor = config.mapFillColor();
            int alpha = config.mapAlpha();
            Color fillColorWithAlpha = new Color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), alpha);
            
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255.0f));
            graphics.setColor(fillColorWithAlpha);
            graphics.fill(grayscaleArea);
            
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.4f));
            graphics.setColor(new Color(150, 150, 150, 180));
            graphics.fill(grayscaleArea);
            
            graphics.setComposite(originalComposite);
            graphics.setClip(originalClip);
        } catch (Exception e) {
            log.warn("Error applying grayscale overlay", e);
        }
    }

    private void drawBlackBorder(Graphics2D graphics, Client client, Rectangle mapViewArea) {
        try {
            GeneralPath f2pPolygon = getOrCreateF2pPolygon(client, false);
            if (f2pPolygon == null) {
                return;
            }

            Stroke originalStroke = graphics.getStroke();
            Shape originalClip = graphics.getClip();
            
            graphics.setClip(mapViewArea);
            
            Color borderColor = config.mapBorderColor();
            float borderThickness = config.mapBorderThickness();
            
            graphics.setStroke(new BasicStroke(borderThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.setColor(borderColor);
            graphics.draw(f2pPolygon);
            
            List<GeneralPath> overridePolygons = getOrCreateOverridePolygons(client, false);
            for (GeneralPath overridePath : overridePolygons) {
                graphics.draw(overridePath);
            }
            
            graphics.setStroke(originalStroke);
            graphics.setClip(originalClip);
        } catch (Exception e) {
            log.warn("Error drawing border", e);
        }
    }

}