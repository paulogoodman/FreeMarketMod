package com.freemarket.server.data;

import com.freemarket.FreeMarket;
import com.freemarket.common.data.PlayerBalanceData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages leaderboard data persistence using JSON files in world data directory.
 * Stores player balances for offline player access in leaderboard.
 */
public class LeaderboardDataManager {
    
    private static final String LEADERBOARD_FILE_NAME = "leaderboard.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // In-memory cache of player balances
    private static final Map<String, PlayerBalanceData> balanceCache = new HashMap<>();
    
    /**
     * Gets the leaderboard data file path for a given world.
     */
    public static Path getLeaderboardFilePath(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve(LEADERBOARD_FILE_NAME);
    }
    
    /**
     * Creates an empty leaderboard.json file in the world data directory.
     * Called when a new world is created.
     */
    public static void createEmptyLeaderboardFile(ServerLevel level) {
        try {
            Path leaderboardFile = getLeaderboardFilePath(level);
            File file = leaderboardFile.toFile();
            
            // Create parent directories if they don't exist
            file.getParentFile().mkdirs();
            
            // Create empty leaderboard structure
            JsonObject leaderboardData = new JsonObject();
            leaderboardData.add("players", new JsonArray());
            leaderboardData.addProperty("version", "1.0");
            leaderboardData.addProperty("description", "FreeMarket Leaderboard Data");
            
            // Write to file
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(leaderboardData, writer);
            }
            
            FreeMarket.LOGGER.info("Created empty leaderboard.json file for world: {}", level.dimension().location());
        } catch (IOException e) {
            FreeMarket.LOGGER.error("Failed to create leaderboard.json file for world: {}", level.dimension().location(), e);
        }
    }
    
    /**
     * Loads player balance data from the JSON file.
     * Returns empty list if file doesn't exist or is invalid.
     */
    public static List<PlayerBalanceData> loadLeaderboardData(ServerLevel level) {
        List<PlayerBalanceData> players = new ArrayList<>();
        
        try {
            Path leaderboardFile = getLeaderboardFilePath(level);
            File file = leaderboardFile.toFile();
            
            if (!file.exists()) {
                FreeMarket.LOGGER.info("Leaderboard file does not exist, creating empty file: {}", leaderboardFile);
                createEmptyLeaderboardFile(level);
                return players;
            }
            
            try (FileReader reader = new FileReader(file)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray playersArray = root.getAsJsonArray("players");
                
                if (playersArray != null) {
                    for (int i = 0; i < playersArray.size(); i++) {
                        JsonObject playerJson = playersArray.get(i).getAsJsonObject();
                        
                        String uuid = playerJson.get("uuid").getAsString();
                        String playerName = playerJson.get("playerName").getAsString();
                        long balance = playerJson.get("balance").getAsLong();
                        long lastUpdated = playerJson.has("lastUpdated") ? 
                            playerJson.get("lastUpdated").getAsLong() : System.currentTimeMillis();
                        
                        PlayerBalanceData data = new PlayerBalanceData(uuid, playerName, balance, lastUpdated);
                        players.add(data);
                        
                        // Update cache
                        balanceCache.put(uuid, data);
                    }
                }
            }
            
            FreeMarket.LOGGER.info("Loaded {} player balances from leaderboard.json", players.size());
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to load leaderboard data from world: {}", level.dimension().location(), e);
        }
        
        return players;
    }
    
    /**
     * Saves player balance data to the JSON file.
     */
    public static void saveLeaderboardData(ServerLevel level, List<PlayerBalanceData> players) {
        try {
            Path leaderboardFile = getLeaderboardFilePath(level);
            File file = leaderboardFile.toFile();
            
            // Create parent directories if they don't exist
            file.getParentFile().mkdirs();
            
            JsonObject leaderboardData = new JsonObject();
            JsonArray playersArray = new JsonArray();
            
            for (PlayerBalanceData player : players) {
                JsonObject playerJson = new JsonObject();
                playerJson.addProperty("uuid", player.getUuid());
                playerJson.addProperty("playerName", player.getPlayerName());
                playerJson.addProperty("balance", player.getBalance());
                playerJson.addProperty("lastUpdated", player.getLastUpdated());
                playersArray.add(playerJson);
            }
            
            leaderboardData.add("players", playersArray);
            leaderboardData.addProperty("version", "1.0");
            leaderboardData.addProperty("description", "FreeMarket Leaderboard Data");
            leaderboardData.addProperty("lastUpdated", System.currentTimeMillis());
            
            // Write to file
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(leaderboardData, writer);
            }
            
            // Update cache
            balanceCache.clear();
            for (PlayerBalanceData player : players) {
                balanceCache.put(player.getUuid(), player);
            }
            
        } catch (IOException e) {
            FreeMarket.LOGGER.error("Failed to save leaderboard data to world: {}", level.dimension().location(), e);
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
     * Checks if the leaderboard.json file exists for a given world.
     * @param level the server level
     * @return true if the file exists
     */
    public static boolean leaderboardFileExists(ServerLevel level) {
        Path leaderboardFile = getLeaderboardFilePath(level);
        return leaderboardFile.toFile().exists();
    }
}

