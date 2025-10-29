package com.freemarket.client.data;

import com.freemarket.common.data.PlayerBalanceData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Client-side cache for leaderboard data received from the server.
 * This ensures the GUI can display leaderboard information even in multiplayer.
 */
public class ClientLeaderboardCache {
    
    private static List<PlayerBalanceData> cachedLeaderboard = new ArrayList<>();
    private static long lastUpdateTime = 0;
    
    /**
     * Updates the cached leaderboard data.
     * Sorts by balance in descending order.
     * @param leaderboardData the leaderboard data from server
     */
    public static void updateLeaderboard(List<PlayerBalanceData> leaderboardData) {
        cachedLeaderboard = new ArrayList<>(leaderboardData);
        // Sort by balance descending
        cachedLeaderboard.sort(Comparator.comparingLong(PlayerBalanceData::getBalance).reversed());
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Gets the cached leaderboard data.
     * @return the cached leaderboard (sorted by balance descending)
     */
    public static List<PlayerBalanceData> getCachedLeaderboard() {
        return new ArrayList<>(cachedLeaderboard);
    }
    
    /**
     * Gets the top N players from the leaderboard.
     * @param limit the maximum number of players to return
     * @return the top N players
     */
    public static List<PlayerBalanceData> getTopPlayers(int limit) {
        int actualLimit = Math.min(limit, cachedLeaderboard.size());
        return new ArrayList<>(cachedLeaderboard.subList(0, actualLimit));
    }
    
    /**
     * Checks if we have cached leaderboard data.
     * @return true if cached data is available
     */
    public static boolean hasCachedData() {
        return !cachedLeaderboard.isEmpty();
    }
    
    /**
     * Gets the timestamp of the last update.
     * @return the last update time in milliseconds
     */
    public static long getLastUpdateTime() {
        return lastUpdateTime;
    }
    
    /**
     * Clears the cache.
     */
    public static void clearCache() {
        cachedLeaderboard.clear();
        lastUpdateTime = 0;
    }
}

