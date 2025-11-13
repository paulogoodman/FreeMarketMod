package com.freemarket.server.data;

import com.freemarket.FreeMarket;
import com.freemarket.common.data.PlayerAuction;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import javax.annotation.Nonnull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages auction data persistence using world NBT data.
 * Data is only loaded when needed (render/bid operations) and stored in world save data.
 */
public class AuctionDataManager {
    
    private static final String AUCTION_DATA_KEY = "freemarket_auctions";
    private static final String AUCTIONS_LIST_KEY = "auctions";
    private static final String VERSION_KEY = "version";
    private static final String LAST_UPDATED_KEY = "lastUpdated";
    private static final String SAMPLE_FILE_NAME = "sample.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    /**
     * Gets the auction data from world save data.
     * Only loads data when explicitly requested.
     */
    public static List<PlayerAuction> loadAuctions(ServerLevel level) {
        AuctionSavedData savedData = level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(AuctionSavedData::new, AuctionSavedData::load),
            AUCTION_DATA_KEY
        );
        
        return savedData.getAuctions();
    }
    
    /**
     * Saves auction data to world save data.
     */
    public static void saveAuctions(ServerLevel level, List<PlayerAuction> auctions) {
        AuctionSavedData savedData = level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(AuctionSavedData::new, AuctionSavedData::load),
            AUCTION_DATA_KEY
        );
        
        savedData.setAuctions(auctions);
        savedData.setDirty();
    }
    
    /**
     * Adds a new auction to world data.
     */
    public static void addAuction(ServerLevel level, PlayerAuction auction) {
        List<PlayerAuction> auctions = loadAuctions(level);
        
        // Assign order to new auction (last position)
        if (auction.getOrder() == Integer.MAX_VALUE) {
            int maxOrder = auctions.stream()
                .filter(a -> a.getOrder() != Integer.MAX_VALUE)
                .mapToInt(PlayerAuction::getOrder)
                .max()
                .orElse(-1);
            auction.setOrder(maxOrder + 1);
        }
        
        auctions.add(auction);
        saveAuctions(level, auctions);
    }
    
    /**
     * Removes an auction by ID from world data.
     */
    public static void removeAuction(ServerLevel level, String auctionId) {
        List<PlayerAuction> auctions = loadAuctions(level);
        auctions.removeIf(a -> a.getAuctionId().equals(auctionId));
        saveAuctions(level, auctions);
    }
    
    /**
     * Updates an existing auction in world data.
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
     * Removes all expired auctions from world data.
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
            FreeMarket.LOGGER.info("Removed {} expired auctions from world data", expired.size());
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
     * Checks if auction data exists for a given world.
     */
    public static boolean hasAuctionData(ServerLevel level) {
        return level.getDataStorage().get(new SavedData.Factory<>(AuctionSavedData::new, AuctionSavedData::load), AUCTION_DATA_KEY) != null;
    }
    
    /**
     * Creates a sample auction JSON file in the config directory.
     * This file serves as a template for users to understand the format.
     * 
     * @param level The server level
     * @param configDir The config directory path
     */
    public static void createSampleAuctionFile(ServerLevel level, Path configDir) {
        try {
            Path auctionsDir = configDir.resolve("freemarket").resolve("auctions");
            Files.createDirectories(auctionsDir);
            
            Path sampleFile = auctionsDir.resolve(SAMPLE_FILE_NAME);
            
            // Only create if it doesn't exist
            if (Files.exists(sampleFile)) {
                return;
            }
            
            // Create sample JSON object
            JsonObject sampleJson = new JsonObject();
            sampleJson.addProperty("auctionId", ""); // optional, if not provided, a random auctionId will be generated
            sampleJson.addProperty("itemId", "minecraft:diamond");
            sampleJson.addProperty("componentData", "{}"); // optional, if not provided, the component data will be an empty string
            sampleJson.addProperty("quantity", 1); // required
            sampleJson.addProperty("startingPrice", 100); // required
            sampleJson.addProperty("currentBid", 0); // required, initial bid is typically 0 or equal to startingPrice
            sampleJson.addProperty("order", 1); // optional, if not provided, the order will be set to last position
            sampleJson.addProperty("sellerUuid", ""); // optional, if not provided, will default to empty string
            sampleJson.addProperty("sellerName", "Admin"); // optional, if not provided, will default to "Admin"
            sampleJson.addProperty("expiryTime", System.currentTimeMillis() + (24 * 60 * 60 * 1000L)); // optional, if not provided, will default to 24 hours from now
            sampleJson.addProperty("createdTime", System.currentTimeMillis()); // optional, if not provided, will default to current time
            sampleJson.add("bidderUuid", null); // optional, null if no bids
            sampleJson.add("bidderName", null); // optional, null if no bids
            
            // Write sample file with pretty printing
            try (FileWriter writer = new FileWriter(sampleFile.toFile())) {
                GSON.toJson(sampleJson, writer);
            }
            
            FreeMarket.LOGGER.info("Created sample auction file at {}", sampleFile);
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to create sample auction file: {}", e.getMessage());
        }
    }
    
    /**
     * Dumps all auctions to JSON files in the config directory.
     * Each auction is written to a separate file named {auctionId}.json in config/freemarket/auctions/
     * Sample files are excluded from dumping.
     * 
     * @param level The server level
     * @param configDir The config directory path
     * @return The number of auctions successfully dumped
     */
    public static int dumpAuctionsToJson(ServerLevel level, Path configDir) {
        try {
            // Get all auctions
            List<PlayerAuction> auctions = loadAuctions(level);
            
            // Create auctions directory
            Path auctionsDir = configDir.resolve("freemarket").resolve("auctions");
            Files.createDirectories(auctionsDir);
            
            int dumpedCount = 0;
            
            // Write each auction to a separate JSON file
            for (PlayerAuction auction : auctions) {
                try {
                    String auctionId = auction.getAuctionId();
                    if (auctionId == null || auctionId.isEmpty()) {
                        auctionId = generateAuctionId();
                        FreeMarket.LOGGER.warn("Auction missing auctionId, generated new one: {}", auctionId);
                    }
                    
                    // Create JSON object
                    JsonObject auctionJson = new JsonObject();
                    // Add properties in the specified order: auctionId (like GUID), itemId, componentData, quantity, startingPrice, currentBid, order
                    auctionJson.addProperty("auctionId", auctionId);
                    auctionJson.addProperty("itemId", auction.getItemId());
                    auctionJson.addProperty("componentData", auction.getComponentData());
                    auctionJson.addProperty("quantity", auction.getQuantity());
                    auctionJson.addProperty("startingPrice", auction.getStartingPrice());
                    auctionJson.addProperty("currentBid", auction.getCurrentBid());
                    auctionJson.addProperty("order", auction.getOrder());
                    // Then add remaining fields
                    auctionJson.addProperty("sellerUuid", auction.getSellerUuid());
                    auctionJson.addProperty("sellerName", auction.getSellerName());
                    auctionJson.addProperty("expiryTime", auction.getExpiryTime());
                    auctionJson.addProperty("createdTime", auction.getCreatedTime());
                    
                    if (auction.getBidderUuid() != null) {
                        auctionJson.addProperty("bidderUuid", auction.getBidderUuid());
                    } else {
                        auctionJson.add("bidderUuid", null);
                    }
                    
                    if (auction.getBidderName() != null) {
                        auctionJson.addProperty("bidderName", auction.getBidderName());
                    } else {
                        auctionJson.add("bidderName", null);
                    }
                    
                    // Write to file
                    File jsonFile = auctionsDir.resolve(auctionId + ".json").toFile();
                    try (FileWriter writer = new FileWriter(jsonFile)) {
                        GSON.toJson(auctionJson, writer);
                    }
                    
                    dumpedCount++;
                } catch (Exception e) {
                    FreeMarket.LOGGER.error("Failed to dump auction with ID {}: {}", auction.getAuctionId(), e.getMessage());
                }
            }
            
            FreeMarket.LOGGER.info("Dumped {} auctions to {}", dumpedCount, auctionsDir);
            return dumpedCount;
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to dump auctions to JSON: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * Loads auctions from JSON files in the config directory.
     * Scans config/freemarket/auctions/ for all .json files and loads them.
     * Auctions with matching auctionIds will update existing auctions, others will be added as new auctions.
     * 
     * @param level The server level
     * @param configDir The config directory path
     * @return A result object containing success count, update count, and add count
     */
    public static LoadResult loadAuctionsFromJson(ServerLevel level, Path configDir) {
        try {
            Path auctionsDir = configDir.resolve("freemarket").resolve("auctions");
            
            // Check if directory exists
            if (!Files.exists(auctionsDir) || !Files.isDirectory(auctionsDir)) {
                FreeMarket.LOGGER.warn("Auctions directory does not exist: {}", auctionsDir);
                return new LoadResult(0, 0, 0);
            }
            
            // Get current auctions
            List<PlayerAuction> currentAuctions = loadAuctions(level);
            Map<String, Integer> auctionIdToIndex = new HashMap<>();
            for (int i = 0; i < currentAuctions.size(); i++) {
                String auctionId = currentAuctions.get(i).getAuctionId();
                if (auctionId != null && !auctionId.isEmpty()) {
                    auctionIdToIndex.put(auctionId, i);
                }
            }
            
            int loadedCount = 0;
            int updatedCount = 0;
            int addedCount = 0;
            
            // Scan directory for JSON files, excluding sample files
            File[] jsonFiles = auctionsDir.toFile().listFiles((dir, name) -> 
                name.endsWith(".json") && !name.equals(SAMPLE_FILE_NAME));
            if (jsonFiles == null) {
                return new LoadResult(0, 0, 0);
            }
            
            for (File jsonFile : jsonFiles) {
                try (FileReader reader = new FileReader(jsonFile)) {
                    JsonObject auctionJson = GSON.fromJson(reader, JsonObject.class);
                    
                    if (auctionJson == null) {
                        FreeMarket.LOGGER.warn("Invalid JSON in file: {}", jsonFile.getName());
                        continue;
                    }
                    
                    // Validate required fields
                    if (!auctionJson.has("itemId") || auctionJson.get("itemId").isJsonNull()) {
                        FreeMarket.LOGGER.warn("Missing required field 'itemId' in file: {}", jsonFile.getName());
                        continue;
                    }
                    if (!auctionJson.has("quantity")) {
                        FreeMarket.LOGGER.warn("Missing required field 'quantity' in file: {}", jsonFile.getName());
                        continue;
                    }
                    if (!auctionJson.has("startingPrice")) {
                        FreeMarket.LOGGER.warn("Missing required field 'startingPrice' in file: {}", jsonFile.getName());
                        continue;
                    }
                    if (!auctionJson.has("currentBid")) {
                        FreeMarket.LOGGER.warn("Missing required field 'currentBid' in file: {}", jsonFile.getName());
                        continue;
                    }
                    
                    // Required fields
                    String itemId = auctionJson.get("itemId").getAsString();
                    int quantity = auctionJson.get("quantity").getAsInt();
                    long startingPrice = auctionJson.get("startingPrice").getAsLong();
                    long currentBid = auctionJson.get("currentBid").getAsLong();
                    
                    // Optional fields with defaults
                    String auctionId = auctionJson.has("auctionId") && !auctionJson.get("auctionId").isJsonNull() 
                        ? auctionJson.get("auctionId").getAsString() : null;
                    String componentData = auctionJson.has("componentData") && !auctionJson.get("componentData").isJsonNull()
                        ? auctionJson.get("componentData").getAsString() : "{}";
                    String sellerUuid = auctionJson.has("sellerUuid") && !auctionJson.get("sellerUuid").isJsonNull()
                        ? auctionJson.get("sellerUuid").getAsString() : "";
                    String sellerName = auctionJson.has("sellerName") && !auctionJson.get("sellerName").isJsonNull()
                        ? auctionJson.get("sellerName").getAsString() : "Admin";
                    
                    // Default expiryTime to 24 hours from now if not specified
                    long expiryTime;
                    if (auctionJson.has("expiryTime") && !auctionJson.get("expiryTime").isJsonNull()) {
                        expiryTime = auctionJson.get("expiryTime").getAsLong();
                    } else {
                        expiryTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000L); // 24 hours
                    }
                    
                    // Default createdTime to current time if not specified
                    long createdTime = auctionJson.has("createdTime") && !auctionJson.get("createdTime").isJsonNull()
                        ? auctionJson.get("createdTime").getAsLong() : System.currentTimeMillis();
                    
                    // Bidder fields default to null
                    String bidderUuid = null;
                    if (auctionJson.has("bidderUuid") && !auctionJson.get("bidderUuid").isJsonNull()) {
                        bidderUuid = auctionJson.get("bidderUuid").getAsString();
                    }
                    
                    String bidderName = null;
                    if (auctionJson.has("bidderName") && !auctionJson.get("bidderName").isJsonNull()) {
                        bidderName = auctionJson.get("bidderName").getAsString();
                    }
                    
                    // Order will be set after all auctions are loaded to assign last position to auctions without order
                    int order = auctionJson.has("order") ? auctionJson.get("order").getAsInt() : Integer.MAX_VALUE;
                    
                    // Generate auctionId if missing
                    if (auctionId == null || auctionId.isEmpty()) {
                        auctionId = generateAuctionId();
                        FreeMarket.LOGGER.info("Generated new auctionId for auction from file {}: {}", jsonFile.getName(), auctionId);
                    }
                    
                    // Create PlayerAuction
                    PlayerAuction playerAuction = new PlayerAuction(
                        auctionId, itemId, componentData, quantity,
                        startingPrice, currentBid, sellerUuid, sellerName,
                        expiryTime, bidderUuid, bidderName, createdTime, order);
                    
                    // Check if auctionId exists in current auctions
                    if (auctionIdToIndex.containsKey(auctionId)) {
                        // Update existing auction
                        int index = auctionIdToIndex.get(auctionId);
                        currentAuctions.set(index, playerAuction);
                        updatedCount++;
                    } else {
                        // Add as new auction
                        currentAuctions.add(playerAuction);
                        auctionIdToIndex.put(auctionId, currentAuctions.size() - 1);
                        addedCount++;
                    }
                    
                    loadedCount++;
                    
                } catch (Exception e) {
                    FreeMarket.LOGGER.error("Failed to load auction from file {}: {}", jsonFile.getName(), e.getMessage());
                }
            }
            
            // Assign order to auctions that don't have one (set to last position)
            // Find the maximum order value among auctions that have an order
            int maxOrder = currentAuctions.stream()
                .filter(auction -> auction.getOrder() != Integer.MAX_VALUE)
                .mapToInt(PlayerAuction::getOrder)
                .max()
                .orElse(-1);
            
            // Assign order to auctions without one (starting from maxOrder + 1)
            int nextOrder = maxOrder + 1;
            for (PlayerAuction auction : currentAuctions) {
                if (auction.getOrder() == Integer.MAX_VALUE) {
                    auction.setOrder(nextOrder++);
                }
            }
            
            // Save updated auctions
            if (loadedCount > 0) {
                saveAuctions(level, currentAuctions);
                FreeMarket.LOGGER.info("Loaded {} auctions ({} updated, {} added) from {}", 
                    loadedCount, updatedCount, addedCount, auctionsDir);
            }
            
            return new LoadResult(loadedCount, updatedCount, addedCount);
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to load auctions from JSON: {}", e.getMessage());
            return new LoadResult(0, 0, 0);
        }
    }
    
    /**
     * Result object for load operations.
     */
    public static class LoadResult {
        public final int loaded;
        public final int updated;
        public final int added;
        
        public LoadResult(int loaded, int updated, int added) {
            this.loaded = loaded;
            this.updated = updated;
            this.added = added;
        }
    }
    
    /**
     * SavedData implementation for storing auction data in world NBT.
     */
    public static class AuctionSavedData extends SavedData {
        private List<PlayerAuction> auctions = new ArrayList<>();
        private String version = "1.0";
        private long lastUpdated = System.currentTimeMillis();
        
        public AuctionSavedData() {
            // Default constructor
        }
        
        public AuctionSavedData(List<PlayerAuction> auctions) {
            this.auctions = new ArrayList<>(auctions);
        }
        
        @Override
        public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
            ListTag auctionsList = new ListTag();
            
            for (PlayerAuction auction : auctions) {
                CompoundTag auctionTag = new CompoundTag();
                auctionTag.putString("auctionId", auction.getAuctionId());
                auctionTag.putString("itemId", auction.getItemId());
                auctionTag.putString("componentData", auction.getComponentData());
                auctionTag.putInt("quantity", auction.getQuantity());
                auctionTag.putLong("startingPrice", auction.getStartingPrice());
                auctionTag.putLong("currentBid", auction.getCurrentBid());
                auctionTag.putString("sellerUuid", auction.getSellerUuid());
                auctionTag.putString("sellerName", auction.getSellerName());
                auctionTag.putLong("expiryTime", auction.getExpiryTime());
                
                if (auction.getBidderUuid() != null) {
                    auctionTag.putString("bidderUuid", auction.getBidderUuid());
                }
                if (auction.getBidderName() != null) {
                    auctionTag.putString("bidderName", auction.getBidderName());
                }
                
                auctionTag.putLong("createdTime", auction.getCreatedTime());
                auctionTag.putInt("order", auction.getOrder());
                auctionsList.add(auctionTag);
            }
            
            tag.put(AUCTIONS_LIST_KEY, auctionsList);
            tag.putString(VERSION_KEY, version);
            tag.putLong(LAST_UPDATED_KEY, System.currentTimeMillis());
            
            return tag;
        }
        
        public static AuctionSavedData load(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
            AuctionSavedData data = new AuctionSavedData();
            
            if (tag.contains(AUCTIONS_LIST_KEY, Tag.TAG_LIST)) {
                ListTag auctionsList = tag.getList(AUCTIONS_LIST_KEY, Tag.TAG_COMPOUND);
                
                for (int i = 0; i < auctionsList.size(); i++) {
                    CompoundTag auctionTag = auctionsList.getCompound(i);
                    
                    PlayerAuction auction = new PlayerAuction();
                    auction.setAuctionId(auctionTag.getString("auctionId"));
                    auction.setItemId(auctionTag.getString("itemId"));
                    auction.setComponentData(auctionTag.getString("componentData"));
                    auction.setQuantity(auctionTag.getInt("quantity"));
                    auction.setStartingPrice(auctionTag.getLong("startingPrice"));
                    auction.setCurrentBid(auctionTag.getLong("currentBid"));
                    auction.setSellerUuid(auctionTag.getString("sellerUuid"));
                    auction.setSellerName(auctionTag.getString("sellerName"));
                    auction.setExpiryTime(auctionTag.getLong("expiryTime"));
                    
                    if (auctionTag.contains("bidderUuid")) {
                        auction.setBidderUuid(auctionTag.getString("bidderUuid"));
                    }
                    if (auctionTag.contains("bidderName")) {
                        auction.setBidderName(auctionTag.getString("bidderName"));
                    }
                    
                    auction.setCreatedTime(auctionTag.getLong("createdTime"));
                    if (auctionTag.contains("order")) {
                        auction.setOrder(auctionTag.getInt("order"));
                    } else {
                        auction.setOrder(Integer.MAX_VALUE); // Default to last position if missing
                    }
                    data.auctions.add(auction);
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
        
        public List<PlayerAuction> getAuctions() {
            return new ArrayList<>(auctions);
        }
        
        public void setAuctions(List<PlayerAuction> auctions) {
            this.auctions = new ArrayList<>(auctions);
        }
        
        public String getVersion() {
            return version;
        }
        
        public long getLastUpdated() {
            return lastUpdated;
        }
    }
}