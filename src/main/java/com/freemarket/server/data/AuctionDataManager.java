package com.freemarket.server.data;

import com.freemarket.FreeMarket;
import com.freemarket.common.data.PlayerAuction;
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
import java.util.List;
import java.util.UUID;

/**
 * Manages auction data persistence using JSON files in world data directory.
 */
public class AuctionDataManager {
    
    private static final String AUCTION_FILE_NAME = "auctions.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    /**
     * Gets the auction data file path for a given world.
     */
    public static Path getAuctionFilePath(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve(AUCTION_FILE_NAME);
    }
    
    /**
     * Creates an empty auctions.json file in the world data directory.
     */
    public static void createEmptyAuctionFile(ServerLevel level) {
        try {
            Path auctionFile = getAuctionFilePath(level);
            File file = auctionFile.toFile();
            
            file.getParentFile().mkdirs();
            
            JsonObject auctionData = new JsonObject();
            auctionData.add("auctions", new JsonArray());
            auctionData.addProperty("version", "1.0");
            auctionData.addProperty("description", "FreeMarket Auction Data");
            
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(auctionData, writer);
            }
            
            FreeMarket.LOGGER.info("Created empty auctions.json file for world: {}", level.dimension().location());
        } catch (IOException e) {
            FreeMarket.LOGGER.error("Failed to create auctions.json file for world: {}", level.dimension().location(), e);
        }
    }
    
    /**
     * Loads auction data from the JSON file.
     */
    public static List<PlayerAuction> loadAuctions(ServerLevel level) {
        List<PlayerAuction> auctions = new ArrayList<>();
        
        try {
            Path auctionFile = getAuctionFilePath(level);
            File file = auctionFile.toFile();
            
            if (!file.exists()) {
                FreeMarket.LOGGER.info("Auction file does not exist, creating empty file: {}", auctionFile);
                createEmptyAuctionFile(level);
                return auctions;
            }
            
            try (FileReader reader = new FileReader(file)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray auctionsArray = root.getAsJsonArray("auctions");
                
                if (auctionsArray != null) {
                    for (int i = 0; i < auctionsArray.size(); i++) {
                        JsonObject auctionJson = auctionsArray.get(i).getAsJsonObject();
                        
                        PlayerAuction auction = new PlayerAuction();
                        auction.setAuctionId(auctionJson.get("auctionId").getAsString());
                        auction.setItemId(auctionJson.get("itemId").getAsString());
                        auction.setComponentData(auctionJson.has("componentData") ? 
                            auctionJson.get("componentData").getAsString() : "{}");
                        auction.setQuantity(auctionJson.get("quantity").getAsInt());
                        auction.setStartingPrice(auctionJson.get("startingPrice").getAsLong());
                        auction.setCurrentBid(auctionJson.get("currentBid").getAsLong());
                        auction.setSellerUuid(auctionJson.get("sellerUuid").getAsString());
                        auction.setSellerName(auctionJson.get("sellerName").getAsString());
                        auction.setExpiryTime(auctionJson.get("expiryTime").getAsLong());
                        auction.setBidderUuid(auctionJson.has("bidderUuid") && !auctionJson.get("bidderUuid").isJsonNull() ? 
                            auctionJson.get("bidderUuid").getAsString() : null);
                        auction.setBidderName(auctionJson.has("bidderName") && !auctionJson.get("bidderName").isJsonNull() ? 
                            auctionJson.get("bidderName").getAsString() : null);
                        auction.setCreatedTime(auctionJson.has("createdTime") ? 
                            auctionJson.get("createdTime").getAsLong() : System.currentTimeMillis());
                        
                        auctions.add(auction);
                    }
                }
            }
            
            FreeMarket.LOGGER.info("Loaded {} auctions from auctions.json", auctions.size());
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to load auction data from world: {}", level.dimension().location(), e);
        }
        
        return auctions;
    }
    
    /**
     * Saves auction data to the JSON file.
     */
    public static void saveAuctions(ServerLevel level, List<PlayerAuction> auctions) {
        try {
            Path auctionFile = getAuctionFilePath(level);
            File file = auctionFile.toFile();
            
            file.getParentFile().mkdirs();
            
            JsonObject auctionData = new JsonObject();
            JsonArray auctionsArray = new JsonArray();
            
            for (PlayerAuction auction : auctions) {
                JsonObject auctionJson = new JsonObject();
                auctionJson.addProperty("auctionId", auction.getAuctionId());
                auctionJson.addProperty("itemId", auction.getItemId());
                auctionJson.addProperty("componentData", auction.getComponentData());
                auctionJson.addProperty("quantity", auction.getQuantity());
                auctionJson.addProperty("startingPrice", auction.getStartingPrice());
                auctionJson.addProperty("currentBid", auction.getCurrentBid());
                auctionJson.addProperty("sellerUuid", auction.getSellerUuid());
                auctionJson.addProperty("sellerName", auction.getSellerName());
                auctionJson.addProperty("expiryTime", auction.getExpiryTime());
                auctionJson.addProperty("bidderUuid", auction.getBidderUuid());
                auctionJson.addProperty("bidderName", auction.getBidderName());
                auctionJson.addProperty("createdTime", auction.getCreatedTime());
                auctionsArray.add(auctionJson);
            }
            
            auctionData.add("auctions", auctionsArray);
            auctionData.addProperty("version", "1.0");
            auctionData.addProperty("description", "FreeMarket Auction Data");
            auctionData.addProperty("lastUpdated", System.currentTimeMillis());
            
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(auctionData, writer);
            }
            
        } catch (IOException e) {
            FreeMarket.LOGGER.error("Failed to save auction data to world: {}", level.dimension().location(), e);
        }
    }
    
    /**
     * Adds a new auction.
     */
    public static void addAuction(ServerLevel level, PlayerAuction auction) {
        List<PlayerAuction> auctions = loadAuctions(level);
        auctions.add(auction);
        saveAuctions(level, auctions);
    }
    
    /**
     * Removes an auction by ID.
     */
    public static void removeAuction(ServerLevel level, String auctionId) {
        List<PlayerAuction> auctions = loadAuctions(level);
        auctions.removeIf(a -> a.getAuctionId().equals(auctionId));
        saveAuctions(level, auctions);
    }
    
    /**
     * Updates an existing auction.
     */
    public static void updateAuction(ServerLevel level, PlayerAuction updatedAuction) {
        List<PlayerAuction> auctions = loadAuctions(level);
        for (int i = 0; i < auctions.size(); i++) {
            if (auctions.get(i).getAuctionId().equals(updatedAuction.getAuctionId())) {
                auctions.set(i, updatedAuction);
                break;
            }
        }
        saveAuctions(level, auctions);
    }
    
    /**
     * Removes all expired auctions.
     * @return list of expired auctions that were removed
     */
    public static List<PlayerAuction> removeExpiredAuctions(ServerLevel level) {
        List<PlayerAuction> auctions = loadAuctions(level);
        List<PlayerAuction> expired = new ArrayList<>();
        
        auctions.removeIf(auction -> {
            if (auction.isExpired()) {
                expired.add(auction);
                return true;
            }
            return false;
        });
        
        if (!expired.isEmpty()) {
            saveAuctions(level, auctions);
            FreeMarket.LOGGER.info("Removed {} expired auctions", expired.size());
        }
        
        return expired;
    }
    
    /**
     * Generates a unique auction ID.
     */
    public static String generateAuctionId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Checks if the auction file exists for a given world.
     */
    public static boolean auctionFileExists(ServerLevel level) {
        Path auctionFile = getAuctionFilePath(level);
        return auctionFile.toFile().exists();
    }
}

