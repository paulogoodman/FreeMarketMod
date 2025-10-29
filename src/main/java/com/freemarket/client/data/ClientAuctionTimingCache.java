package com.freemarket.client.data;

import com.freemarket.common.data.PlayerAuction;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * Client-side cache for auction timing data to optimize countdown calculations.
 * Stores auction start times and expiry times to avoid repeated System.currentTimeMillis() calls.
 */
public class ClientAuctionTimingCache {
    
    private static final Map<String, AuctionTimingData> timingCache = new HashMap<>();
    private static long lastSyncTime = 0;
    private static final long SYNC_INTERVAL_MS = 30000; // Sync every 30 seconds
    
    /**
     * Timing data for a single auction.
     */
    public static class AuctionTimingData {
        public final long startTime;
        public final long expiryTime;
        public final long durationMs;
        
        public AuctionTimingData(long startTime, long expiryTime) {
            this.startTime = startTime;
            this.expiryTime = expiryTime;
            this.durationMs = expiryTime - startTime;
        }
        
        /**
         * Gets the time remaining using client-side time calculation.
         */
        public long getTimeRemaining() {
            long currentTime = System.currentTimeMillis();
            long remaining = expiryTime - currentTime;
            return Math.max(0, remaining);
        }
        
        /**
         * Checks if the auction has expired using client-side time calculation.
         */
        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
        
        /**
         * Gets the elapsed time since auction started.
         */
        public long getElapsedTime() {
            long currentTime = System.currentTimeMillis();
            return Math.max(0, currentTime - startTime);
        }
    }
    
    /**
     * Updates the timing cache with fresh auction data from server.
     */
    public static void updateTimingCache(List<PlayerAuction> auctions) {
        timingCache.clear();
        
        for (PlayerAuction auction : auctions) {
            AuctionTimingData timingData = new AuctionTimingData(
                auction.getCreatedTime(),
                auction.getExpiryTime()
            );
            timingCache.put(auction.getAuctionId(), timingData);
        }
        
        lastSyncTime = System.currentTimeMillis();
    }
    
    /**
     * Gets timing data for a specific auction.
     */
    public static AuctionTimingData getTimingData(String auctionId) {
        return timingCache.get(auctionId);
    }
    
    /**
     * Gets the time remaining for an auction using cached timing data.
     */
    public static long getTimeRemaining(String auctionId) {
        AuctionTimingData timingData = timingCache.get(auctionId);
        if (timingData == null) {
            return 0; // Auction not found or expired
        }
        return timingData.getTimeRemaining();
    }
    
    /**
     * Checks if an auction has expired using cached timing data.
     */
    public static boolean isExpired(String auctionId) {
        AuctionTimingData timingData = timingCache.get(auctionId);
        if (timingData == null) {
            return true; // Auction not found, consider expired
        }
        return timingData.isExpired();
    }
    
    /**
     * Removes expired auctions from the cache.
     */
    public static void removeExpiredAuctions() {
        timingCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * Removes a specific auction from the cache.
     */
    public static void removeAuction(String auctionId) {
        timingCache.remove(auctionId);
    }
    
    /**
     * Checks if we need to sync with the server based on time interval.
     */
    public static boolean needsSync() {
        return System.currentTimeMillis() - lastSyncTime > SYNC_INTERVAL_MS;
    }
    
    /**
     * Gets all cached auction IDs.
     */
    public static List<String> getCachedAuctionIds() {
        return new ArrayList<>(timingCache.keySet());
    }
    
    /**
     * Gets the timestamp of the last sync.
     */
    public static long getLastSyncTime() {
        return lastSyncTime;
    }
    
    /**
     * Clears the entire timing cache.
     */
    public static void clearCache() {
        timingCache.clear();
        lastSyncTime = 0;
    }
    
    /**
     * Gets the number of cached auctions.
     */
    public static int getCacheSize() {
        return timingCache.size();
    }
}
