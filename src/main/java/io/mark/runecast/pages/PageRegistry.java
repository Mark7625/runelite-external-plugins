package io.mark.runecast.pages;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing all pages in the RuneCast system.
 * Provides centralized access to page data and manages page lifecycle.
 */
@Slf4j
@Singleton
public class PageRegistry {
    
    private final Map<String, Page> registeredPages = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> pageDataCache = new ConcurrentHashMap<>();
    
    /**
     * Register a page with the registry.
     * 
     * @param page The page to register
     * @return true if registration was successful, false otherwise
     */
    public boolean registerPage(Page page) {
        if (page == null) {
            log.error("Cannot register null page");
            return false;
        }
        
        String pageName = page.getPageName();
        if (pageName == null || pageName.trim().isEmpty()) {
            log.error("Page name cannot be null or empty");
            return false;
        }
        
        if (registeredPages.containsKey(pageName)) {
            log.warn("Page '{}' is already registered, overwriting", pageName);
        }
        
        try {
            page.onInit();
            registeredPages.put(pageName, page);
            log.info("Successfully registered page: {}", pageName);
            return true;
        } catch (Exception e) {
            log.error("Failed to initialize page '{}': {}", pageName, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Unregister a page from the registry.
     * 
     * @param pageName The name of the page to unregister
     * @return true if unregistration was successful, false otherwise
     */
    public boolean unregisterPage(String pageName) {
        Page page = registeredPages.get(pageName);
        if (page == null) {
            log.warn("Page '{}' is not registered", pageName);
            return false;
        }
        
        try {
            page.onDestroy();
            registeredPages.remove(pageName);
            pageDataCache.remove(pageName);
            log.info("Successfully unregistered page: {}", pageName);
            return true;
        } catch (Exception e) {
            log.error("Failed to destroy page '{}': {}", pageName, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Get a registered page by name.
     * 
     * @param pageName The name of the page to retrieve
     * @return The page if found, null otherwise
     */
    public Page getPage(String pageName) {
        return registeredPages.get(pageName);
    }
    
    /**
     * Check if a page is registered.
     * 
     * @param pageName The name of the page to check
     * @return true if the page is registered, false otherwise
     */
    public boolean isPageRegistered(String pageName) {
        return registeredPages.containsKey(pageName);
    }
    
    /**
     * Get all registered page names.
     * 
     * @return A set of all registered page names
     */
    public java.util.Set<String> getRegisteredPageNames() {
        return registeredPages.keySet();
    }
    
    /**
     * Update all registered pages and cache their data.
     */
    public void updateAllPages() {
        for (Map.Entry<String, Page> entry : registeredPages.entrySet()) {
            String pageName = entry.getKey();
            Page page = entry.getValue();
            
            try {
                page.onUpdate();
                Map<String, Object> pageData = page.getPageData();
                if (pageData != null) {
                    pageDataCache.put(pageName, new HashMap<>(pageData));
                }
            } catch (Exception e) {
                log.error("Failed to update page '{}': {}", pageName, e.getMessage(), e);
            }
        }
    }
    
    /**
     * Get the cached data for a specific page.
     * 
     * @param pageName The name of the page
     * @return The cached page data, or null if not found
     */
    public Map<String, Object> getPageData(String pageName) {
        return pageDataCache.get(pageName);
    }
    
    /**
     * Get all cached page data as a combined map.
     * 
     * @return A map containing all page data, with page names as keys
     */
    public Map<String, Map<String, Object>> getAllPageData() {
        return new HashMap<>(pageDataCache);
    }
    
    /**
     * Get the total number of registered pages.
     * 
     * @return The number of registered pages
     */
    public int getPageCount() {
        return registeredPages.size();
    }
    
    /**
     * Clear all registered pages and cached data.
     */
    public void clearAllPages() {
        for (Page page : registeredPages.values()) {
            try {
                page.onDestroy();
            } catch (Exception e) {
                log.error("Failed to destroy page during clear: {}", e.getMessage(), e);
            }
        }
        
        registeredPages.clear();
        pageDataCache.clear();
        log.info("Cleared all pages and cached data");
    }
}
