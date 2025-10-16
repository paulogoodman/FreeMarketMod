package com.freemarket.client.data;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side cache for wallet balances received from the server.
 * This ensures the GUI can display wallet information even in multiplayer.
 */
public class ClientWalletCache {
    
    private static final Map<String, Long> cachedBalances = new HashMap<>();
    private static String currentPlayerUuid = null;
    
    /**
     * Updates the cached balance for a player.
     * @param playerUuid the player's UUID
     * @param balance the current balance
     */
    public static void updateBalance(String playerUuid, long balance) {
        cachedBalances.put(playerUuid, balance);
        
        // If this is the current player, update their balance
        if (playerUuid.equals(currentPlayerUuid)) {
            // Balance updated for current player
        }
    }
    
    /**
     * Sets the current player's UUID for balance tracking.
     * @param playerUuid the current player's UUID
     */
    public static void setCurrentPlayer(String playerUuid) {
        currentPlayerUuid = playerUuid;
    }
    
    /**
     * Gets the cached balance for the current player.
     * @return the cached balance, or 0 if not available
     */
    public static long getCurrentPlayerBalance() {
        if (currentPlayerUuid != null) {
            return cachedBalances.getOrDefault(currentPlayerUuid, 0L);
        }
        return 0L;
    }
    
    /**
     * Checks if we have cached balance data for the current player.
     * @return true if cached data is available
     */
    public static boolean hasCachedBalance() {
        return currentPlayerUuid != null && cachedBalances.containsKey(currentPlayerUuid);
    }
    
    /**
     * Clears the cache.
     */
    public static void clearCache() {
        cachedBalances.clear();
        currentPlayerUuid = null;
    }
}
