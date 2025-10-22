package com.freemarket.client.data;

import com.freemarket.common.data.PlayerAuction;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side cache for auction data received from the server.
 */
public class ClientAuctionCache {
    
    private static List<PlayerAuction> cachedAuctions = new ArrayList<>();
    private static long lastUpdateTime = 0;
    
    /**
     * Updates the cached auction data.
     */
    public static void updateAuctions(List<PlayerAuction> auctions) {
        cachedAuctions = new ArrayList<>(auctions);
        lastUpdateTime = System.currentTimeMillis();
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
    }
}

