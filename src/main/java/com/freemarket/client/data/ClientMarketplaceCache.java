package com.freemarket.client.data;

import com.freemarket.common.data.FreeMarketItem;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-side cache for marketplace data received from server via networking.
 * This ensures the GUI can display marketplace data even when not connected to a local server.
 */
public class ClientMarketplaceCache {
    
    private static List<FreeMarketItem> cachedItems = new ArrayList<>();
    private static long lastCacheUpdate = 0;
    private static boolean hasCachedData = false;
    
    /**
     * Updates the cached marketplace data from server sync.
     * @param items the marketplace items from server
     */
    public static void updateCache(List<FreeMarketItem> items) {
        cachedItems = new ArrayList<>(items);
        lastCacheUpdate = System.currentTimeMillis();
        hasCachedData = true;
    }
    
    /**
     * Gets the cached marketplace data.
     * @return list of cached marketplace items
     */
    public static List<FreeMarketItem> getCachedItems() {
        return new ArrayList<>(cachedItems);
    }
    
    /**
     * Checks if we have cached marketplace data.
     * @return true if cached data is available
     */
    public static boolean hasCachedData() {
        return hasCachedData;
    }
    
    /**
     * Gets the timestamp of the last cache update.
     * @return timestamp in milliseconds
     */
    public static long getLastCacheUpdate() {
        return lastCacheUpdate;
    }
    
    /**
     * Clears the cached data.
     */
    public static void clearCache() {
        cachedItems.clear();
        hasCachedData = false;
        lastCacheUpdate = 0;
    }
}
