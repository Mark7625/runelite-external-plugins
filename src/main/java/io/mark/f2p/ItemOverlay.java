package io.mark.f2p;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import io.mark.f2p.config.OverlayMode;
import io.mark.f2p.overlay.CacheKey;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutionException;

@Slf4j
public class ItemOverlay extends WidgetItemOverlay {

    private final ItemManager itemManager;
    private final F2pConfig config;
    private final F2pPlugin plugin;
    private final Cache<CacheKey, BufferedImage> imageCache;
    private final Cache<Integer, Boolean> membersCache;

    @Inject
    private ItemOverlay(ItemManager itemManager, F2pPlugin plugin, F2pConfig config) {
        this.itemManager = itemManager;
        this.config = config;
        this.plugin = plugin;
        this.imageCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .build();
        this.membersCache = CacheBuilder.newBuilder()
                .maximumSize(1300)
                .build();

        showOnEquipment();
        showOnInventory();
        showOnBank();
    }



    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
    {
        if (!plugin.isActive()) {
            return;
        }
        if (!isMembers(itemId)) {
            return;
        }

        OverlayMode mode = config.overlayMode();
        Rectangle bounds = widgetItem.getCanvasBounds();
        Color color = null;
        
        if (mode == OverlayMode.OUTLINE || mode == OverlayMode.FILL) {
            Color baseColor = config.overlayColor();
            int alpha = config.overlayAlpha();
            color = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alpha);
        }

        try {
            BufferedImage cachedImage = getCachedImage(itemId, widgetItem.getQuantity(), mode, color);
            if (cachedImage != null) {
                graphics.drawImage(cachedImage, (int) bounds.getX(), (int) bounds.getY(), null);
            }
        } catch (ExecutionException e) {
            log.info("Unable to Render item: " + itemId);
        }
    }

    private boolean isMembers(int itemId) {
        try {
            return membersCache.get(itemId, () -> {
                try {
                    return itemManager.getItemComposition(itemId).isMembers();
                } catch (Exception e) {
                    log.warn("Error checking members status for item: " + itemId, e);
                    return false;
                }
            });
        } catch (ExecutionException e) {
            log.warn("Error getting members status from cache for item: " + itemId, e);
            return false;
        }
    }

    private BufferedImage getCachedImage(int itemId, int quantity, OverlayMode mode, Color color) throws ExecutionException {
        CacheKey key = new CacheKey(itemId, quantity, mode, color);
        return imageCache.get(key, () -> {
            if (mode == OverlayMode.BLACK_AND_WHITE) {
                return createBlackAndWhiteImage(itemId, quantity);
            }
            else if (mode == OverlayMode.OUTLINE) {
                return itemManager.getItemOutline(itemId, quantity, color);
            }
            else if (mode == OverlayMode.FILL) {
                return createFillImage(itemId, quantity, color);
            }

            return null;
        });
    }

    private BufferedImage createBlackAndWhiteImage(int itemId, int quantity) {
        BufferedImage itemImage = itemManager.getImage(itemId, quantity, false);
        if (itemImage == null) {
            return null;
        }
        BufferedImage grayImage = new BufferedImage(itemImage.getWidth(), itemImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = grayImage.createGraphics();
        g2d.drawImage(itemImage, 0, 0, null);
        g2d.dispose();

        for (int y = 0; y < grayImage.getHeight(); y++) {
            for (int x = 0; x < grayImage.getWidth(); x++) {
                int rgb = grayImage.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xFF;
                if (alpha > 0) {
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                    grayImage.setRGB(x, y, (alpha << 24) | (gray << 16) | (gray << 8) | gray);
                }
            }
        }
        return grayImage;
    }

    private BufferedImage createFillImage(int itemId, int quantity, Color color) {
        BufferedImage itemImage = itemManager.getImage(itemId, quantity, false);
        if (itemImage == null) {
            return null;
        }
        BufferedImage filledImage = new BufferedImage(itemImage.getWidth(), itemImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = filledImage.createGraphics();
        g2d.drawImage(itemImage, 0, 0, null);
        g2d.setComposite(AlphaComposite.SrcAtop);
        g2d.setColor(color);
        g2d.fillRect(0, 0, itemImage.getWidth(), itemImage.getHeight());
        g2d.dispose();
        return filledImage;
    }


    public void invalidateCache() {
        imageCache.invalidateAll();
    }

    public void invalidateMembersCache() {
        membersCache.invalidateAll();
    }

    public void invalidateAllCaches() {
        imageCache.invalidateAll();
        membersCache.invalidateAll();
    }

}
