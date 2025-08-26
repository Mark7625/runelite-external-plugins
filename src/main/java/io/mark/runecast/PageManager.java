package io.mark.runecast;

import com.google.inject.Singleton;
import io.mark.runecast.resourcepacks.ResourcePackManager;
import io.mark.runecast.utils.ResourcePath;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Singleton
public class PageManager {

    ResourcePackManager resourcePackManager;

    @Getter
    RuneCastConfig config;

    private final Map<String, String> pageCache = new HashMap<>();

    
    public PageManager(RuneCastConfig config,ResourcePackManager resourcePackManager) {
        this.config = config;
        this.resourcePackManager = resourcePackManager;
    }


    public String getPage(String pageName) {
        if (pageCache.containsKey(pageName)) {
            return pageCache.get(pageName);
        }

        String html = loadPage(pageName);
        pageCache.put(pageName, html);
        return html;
    }
    
    public byte[] getAsset(String assetPath) {
        try {

            // Try to find the asset in different common locations
            ResourcePath path = resourcePackManager.locateFile("assets", assetPath);
            if (path != null && path.exists()) {
                log.debug("Found asset in assets folder: {}", path);
                return path.toInputStream().readAllBytes();
            }

            path = resourcePackManager.locateFile(assetPath);
            if (path != null && path.exists()) {
                log.debug("Found asset in root: {}", path);
                return path.toInputStream().readAllBytes();
            }
            

            String[] commonDirs = {"images", "img", "icons", "css", "js", "fonts"};
            for (String dir : commonDirs) {
                path = resourcePackManager.locateFile(dir, assetPath);
                if (path != null && path.exists()) {
                    log.debug("Found asset in {} folder: {}", dir, path);
                    return path.toInputStream().readAllBytes();
                }
            }
            
            log.debug("Asset not found: {}", assetPath);
            return null;
        } catch (Exception ex) {
            log.warn("Unable to load asset: {}", assetPath, ex);
            return null;
        }
    }
    
    public String getAssetMimeType(String assetPath) {
        String lowerPath = assetPath.toLowerCase();
        if (lowerPath.endsWith(".png")) return "image/png";
        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) return "image/jpeg";
        if (lowerPath.endsWith(".gif")) return "image/gif";
        if (lowerPath.endsWith(".svg")) return "image/svg+xml";
        if (lowerPath.endsWith(".css")) return "text/css";
        if (lowerPath.endsWith(".js")) return "application/javascript";
        if (lowerPath.endsWith(".ico")) return "image/x-icon";
        if (lowerPath.endsWith(".woff")) return "font/woff";
        if (lowerPath.endsWith(".woff2")) return "font/woff2";
        if (lowerPath.endsWith(".ttf")) return "font/ttf";
        if (lowerPath.endsWith(".eot")) return "application/vnd.ms-fontobject";
        return "application/octet-stream";
    }
    
    public String getAvailableAssetsDebug() {
        StringBuilder debug = new StringBuilder();
        debug.append("Available assets:\n");
        
        // List assets from each resource pack
        for (var pack : resourcePackManager.getInstalledPacks()) {
            debug.append("Pack: ").append(pack.getManifest().getDisplayName()).append("\n");
            try {
                ResourcePath packPath = pack.path;
                if (packPath.exists()) {
                    debug.append("  Path: ").append(packPath).append("\n");
                    // You could add more detailed listing here if needed
                }
            } catch (Exception e) {
                debug.append("  Error accessing pack: ").append(e.getMessage()).append("\n");
            }
        }
        
        return debug.toString();
    }

    private String loadPage(String pageName) {
        String html = null;

        ResourcePath path = resourcePackManager.locateFile("pages", pageName + "." + "html");
        try {
            html = path.loadString();
        } catch (Exception ex) {
            log.trace("Unable to load page: {}", path, ex);
        }


        if (html == null) {
            html = getSimplePage(pageName);
        }

        if (html == null) {
            html = getErrorPage(pageName);
        }
        
        return html;
    }

    private String getSimplePage(String pageName) {
        try {
            InputStream inputStream = getClass().getResourceAsStream("/simple.html");
            if (inputStream != null) {
                String htmlTemplate = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                inputStream.close();
                return htmlTemplate.replace("Page", pageName.substring(0, 1).toUpperCase() + pageName.substring(1));
            }
        } catch (IOException e) {
            System.err.println("Error loading simple template: " + e.getMessage());
        }
        
        return "<!DOCTYPE html><html><head><title>RuneCast " + pageName + "</title></head><body><h1>" + pageName + "</h1><p>Page loaded</p></body></html>";
    }
    
    private String getErrorPage(String pageName) {
        try {
            InputStream inputStream = getClass().getResourceAsStream("/error.html");
            if (inputStream != null) {
                String htmlTemplate = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                inputStream.close();
                return htmlTemplate;
            }
        } catch (IOException e) {
            System.err.println("Error loading error template: " + e.getMessage());
        }
        
        return "<!DOCTYPE html><html><head><title>Error</title></head><body><h1>Plugin not started</h1></body></html>";
    }

    public void clearCache() {
        pageCache.clear();
    }

}
