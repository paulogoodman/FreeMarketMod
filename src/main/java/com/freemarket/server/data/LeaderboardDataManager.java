package com.freemarket.server.data;

import com.freemarket.FreeMarket;
import com.freemarket.common.data.PlayerBalanceData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages leaderboard data persistence using world NBT data.
 * Data is stored in world save data using SavedData.
 */
public class LeaderboardDataManager {
    
    private static final String LEADERBOARD_DATA_KEY = "freemarket_leaderboard";
    private static final String PLAYERS_LIST_KEY = "players";
    private static final String VERSION_KEY = "version";
    private static final String LAST_UPDATED_KEY = "lastUpdated";
    
    // In-memory cache of player balances
    private static final Map<String, PlayerBalanceData> balanceCache = new HashMap<>();
    
    /**
     * Gets the leaderboard data from world save data.
     * Only loads data when explicitly requested.
     */
    public static List<PlayerBalanceData> loadLeaderboardData(ServerLevel level) {
        LeaderboardSavedData savedData = level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(LeaderboardSavedData::new, LeaderboardSavedData::load),
            LEADERBOARD_DATA_KEY
        );
        
        List<PlayerBalanceData> players = savedData.getPlayers();
        
        // Update cache
        balanceCache.clear();
        for (PlayerBalanceData player : players) {
            balanceCache.put(player.getUuid(), player);
        }
        
        return players;
    }
    
    /**
     * Saves leaderboard data to world save data.
     */
    public static void saveLeaderboardData(ServerLevel level, List<PlayerBalanceData> players) {
        LeaderboardSavedData savedData = level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(LeaderboardSavedData::new, LeaderboardSavedData::load),
            LEADERBOARD_DATA_KEY
        );
        
        savedData.setPlayers(players);
        savedData.setDirty();
        
        // Update cache
        balanceCache.clear();
        for (PlayerBalanceData player : players) {
            balanceCache.put(player.getUuid(), player);
        }
    }
    
    /**
     * Updates a single player's balance in the leaderboard.
     * If player doesn't exist, adds them. If they exist, updates their balance.
     */
    public static void updatePlayerBalance(ServerLevel level, String uuid, String playerName, long balance) {
        List<PlayerBalanceData> players = loadLeaderboardData(level);
        
        // Find existing player or create new entry
        boolean found = false;
        for (PlayerBalanceData player : players) {
            if (player.getUuid().equals(uuid)) {
                player.setBalance(balance);
                player.setPlayerName(playerName); // Update name in case it changed
                player.setLastUpdated(System.currentTimeMillis());
                found = true;
                break;
            }
        }
        
        if (!found) {
            // Add new player
            PlayerBalanceData newPlayer = new PlayerBalanceData(uuid, playerName, balance, System.currentTimeMillis());
            players.add(newPlayer);
        }
        
        // Save updated data
        saveLeaderboardData(level, players);
    }
    
    /**
     * Gets a player's balance from the cache.
     * Returns null if player not found.
     */
    public static PlayerBalanceData getCachedPlayerBalance(String uuid) {
        return balanceCache.get(uuid);
    }
    
    /**
     * Clears the in-memory cache.
     */
    public static void clearCache() {
        balanceCache.clear();
    }
    
    /**
     * Checks if leaderboard data exists for a given world.
     */
    public static boolean hasLeaderboardData(ServerLevel level) {
        return level.getDataStorage().get(new SavedData.Factory<>(LeaderboardSavedData::new, LeaderboardSavedData::load), LEADERBOARD_DATA_KEY) != null;
    }
    
    /**
     * @deprecated This method is no longer needed as data is stored in NBT.
     * Kept for backwards compatibility but does nothing.
     */
    @Deprecated
    public static void createEmptyLeaderboardFile(ServerLevel level) {
        // No-op: data is now stored in NBT, not JSON files
        FreeMarket.LOGGER.debug("createEmptyLeaderboardFile called but leaderboard now uses NBT storage");
    }
    
    /**
     * @deprecated This method is no longer needed as data is stored in NBT.
     * Kept for backwards compatibility but returns false.
     */
    @Deprecated
    public static boolean leaderboardFileExists(ServerLevel level) {
        // No-op: data is now stored in NBT, not JSON files
        return hasLeaderboardData(level);
    }
    
    /**
     * SavedData implementation for storing leaderboard data in world NBT.
     */
    public static class LeaderboardSavedData extends SavedData {
        private List<PlayerBalanceData> players = new ArrayList<>();
        private String version = "1.0";
        private long lastUpdated = System.currentTimeMillis();
        
        public LeaderboardSavedData() {
            // Default constructor
        }
        
        public LeaderboardSavedData(List<PlayerBalanceData> players) {
            this.players = new ArrayList<>(players);
        }
        
        @Override
        public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
            ListTag playersList = new ListTag();
            
            for (PlayerBalanceData player : players) {
                CompoundTag playerTag = new CompoundTag();
                playerTag.putString("uuid", player.getUuid());
                playerTag.putString("playerName", player.getPlayerName());
                playerTag.putLong("balance", player.getBalance());
                playerTag.putLong("lastUpdated", player.getLastUpdated());
                playersList.add(playerTag);
            }
            
            tag.put(PLAYERS_LIST_KEY, playersList);
            tag.putString(VERSION_KEY, version);
            tag.putLong(LAST_UPDATED_KEY, System.currentTimeMillis());
            
            return tag;
        }
        
        public static LeaderboardSavedData load(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
            LeaderboardSavedData data = new LeaderboardSavedData();
            
            if (tag.contains(PLAYERS_LIST_KEY, Tag.TAG_LIST)) {
                ListTag playersList = tag.getList(PLAYERS_LIST_KEY, Tag.TAG_COMPOUND);
                
                for (int i = 0; i < playersList.size(); i++) {
                    CompoundTag playerTag = playersList.getCompound(i);
                    
                    String uuid = playerTag.getString("uuid");
                    String playerName = playerTag.getString("playerName");
                    long balance = playerTag.getLong("balance");
                    long lastUpdated = playerTag.contains("lastUpdated") 
                        ? playerTag.getLong("lastUpdated") 
                        : System.currentTimeMillis();
                    
                    PlayerBalanceData player = new PlayerBalanceData(uuid, playerName, balance, lastUpdated);
                    data.players.add(player);
                }
            }
            
            if (tag.contains(VERSION_KEY)) {
                data.version = tag.getString(VERSION_KEY);
            }
            if (tag.contains(LAST_UPDATED_KEY)) {
                data.lastUpdated = tag.getLong(LAST_UPDATED_KEY);
            }
            
            return data;
        }
        
        public List<PlayerBalanceData> getPlayers() {
            return new ArrayList<>(players);
        }
        
        public void setPlayers(List<PlayerBalanceData> players) {
            this.players = new ArrayList<>(players);
        }
        
        public String getVersion() {
            return version;
        }
        
        public long getLastUpdated() {
            return lastUpdated;
        }
    }
}
