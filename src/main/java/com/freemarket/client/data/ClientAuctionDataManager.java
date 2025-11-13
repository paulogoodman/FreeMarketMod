package com.freemarket.client.data;

import com.freemarket.FreeMarket;
import com.freemarket.common.data.PlayerAuction;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-side auction data manager for loading auction data.
 * Used as fallback for singleplayer when server data is not available.
 */
public class ClientAuctionDataManager {
    
    private static final String AUCTION_FILE_NAME = "auctions.json";
    
    // Cache for loaded auctions
    private static List<PlayerAuction> cachedAuctions = null;
    private static long lastCacheTime = 0;
    private static final long CACHE_DURATION = 5000; // 5 seconds
    
    /**
     * Loads auction data from the JSON file.
     */
    public static List<PlayerAuction> loadAuctions() {
        // Check cache first
        if (cachedAuctions != null && (System.currentTimeMillis() - lastCacheTime) < CACHE_DURATION) {
            return new ArrayList<>(cachedAuctions);
        }
        
        List<PlayerAuction> auctions = new ArrayList<>();
        
        try {
            Minecraft minecraft = Minecraft.getInstance();
            var singleplayerServer = minecraft.getSingleplayerServer();
            
            if (singleplayerServer == null) {
                // In multiplayer, rely on network sync
                return auctions;
            }
            
            Path worldDataPath = singleplayerServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            Path auctionFile = worldDataPath.resolve("data").resolve(AUCTION_FILE_NAME);
            File file = auctionFile.toFile();
            
            if (!file.exists()) {
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
                        // Support both old "quantity" and new "stackSize" for backward compatibility
                        if (auctionJson.has("stackSize")) {
                            auction.setStackSize(auctionJson.get("stackSize").getAsInt());
                        } else if (auctionJson.has("quantity")) {
                            auction.setStackSize(auctionJson.get("quantity").getAsInt());
                        } else {
                            auction.setStackSize(1);
                        }
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
                        // Order is optional
                        if (auctionJson.has("order")) {
                            auction.setOrder(auctionJson.get("order").getAsInt());
                        } else {
                            auction.setOrder(Integer.MAX_VALUE);
                        }
                        
                        auctions.add(auction);
                    }
                }
            }
            
            // Update cache
            cachedAuctions = new ArrayList<>(auctions);
            lastCacheTime = System.currentTimeMillis();
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to load auction data from client: {}", e.getMessage());
        }
        
        return auctions;
    }
    
    /**
     * Invalidates the cache, forcing a reload on next access.
     */
    public static void invalidateCache() {
        cachedAuctions = null;
        lastCacheTime = 0;
    }
}

