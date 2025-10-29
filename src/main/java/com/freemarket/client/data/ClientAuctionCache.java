package com.freemarket.client.data;

import com.freemarket.common.data.PlayerAuction;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side cache for auction data received from the server.
 * Server now uses NBT storage, but client still receives JSON over network for compatibility.
 */
public class ClientAuctionCache {
    
    private static List<PlayerAuction> cachedAuctions = new ArrayList<>();
    private static long lastUpdateTime = 0;
    
    /**
     * Updates the cached auction data and timing cache.
     */
    public static void updateAuctions(List<PlayerAuction> auctions) {
        cachedAuctions = new ArrayList<>(auctions);
        lastUpdateTime = System.currentTimeMillis();
        
        // Update timing cache with fresh data
        ClientAuctionTimingCache.updateTimingCache(auctions);
    }
    
    /**
     * Gets the cached auction data.
     */
    public static List<PlayerAuction> getCachedAuctions() {
        return new ArrayList<>(cachedAuctions);
    }
    
    /**
     * Checks if we have cached auction data.
     */
    public static boolean hasCachedData() {
        return !cachedAuctions.isEmpty();
    }
    
    /**
     * Gets the timestamp of the last update.
     */
    public static long getLastUpdateTime() {
        return lastUpdateTime;
    }
    
    /**
     * Clears the cache.
     */
    public static void clearCache() {
        cachedAuctions.clear();
        lastUpdateTime = 0;
        ClientAuctionTimingCache.clearCache();
    }
}

