package io.mark.runecast;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class PageManager {
    private final RuneCastConfig config;
    private final Map<String, String> pageCache = new HashMap<>();
    
    public PageManager(RuneCastConfig config) {
        this.config = config;
    }
    
    public String getPage(String pageName) {
        if (pageCache.containsKey(pageName)) {
            return pageCache.get(pageName);
        }
        
        String html = loadPage(pageName);
        pageCache.put(pageName, html);
        return html;
    }
    
    private String loadPage(String pageName) {
        try {
            String resourcePath = "/" + pageName + ".html";
            InputStream inputStream = getClass().getResourceAsStream(resourcePath);
            if (inputStream != null) {
                String htmlTemplate = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                inputStream.close();
                return htmlTemplate;
            }
            
            return getSimplePage(pageName);
            
        } catch (IOException e) {
            System.err.println("Error loading page " + pageName + ": " + e.getMessage());
            return getErrorPage(pageName);
        }
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
    
    public String getStatusJson() {
        return "{\"status\":\"running\",\"pages\":[\"hp\"],\"timestamp\":" + System.currentTimeMillis() + "}";
    }
    
    public void clearCache() {
        pageCache.clear();
    }
}
