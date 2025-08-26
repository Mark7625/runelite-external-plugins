package io.mark.runecast.pages;

import java.util.Map;

/**
 * Abstract base class for all pages in the RuneCast system.
 * Each page can define its own name and SSE event data.
 */
public abstract class Page {
    
    /**
     * Get the unique name identifier for this page.
     * This name will be used to register the page and identify it in the system.
     * 
     * @return The page name
     */
    public abstract String getPageName();
    
    /**
     * Get the current data for this page that should be sent via SSE.
     * This method is called whenever the page data needs to be updated.
     * 
     * @return A map containing the page's current data
     */
    public abstract Map<String, Object> getPageData();
    
    /**
     * Called when the page is initialized.
     * Override this method to perform any setup operations.
     */
    public void onInit() {
        // Default implementation does nothing
    }
    
    /**
     * Called when the page should be updated.
     * Override this method to refresh the page's data.
     */
    public void onUpdate() {
        // Default implementation does nothing
    }
    
    /**
     * Called when the page is being destroyed.
     * Override this method to perform any cleanup operations.
     */
    public void onDestroy() {
        // Default implementation does nothing
    }
}
