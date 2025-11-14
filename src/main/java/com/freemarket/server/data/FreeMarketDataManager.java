package com.freemarket.server.data;

import com.freemarket.FreeMarket;
import com.freemarket.common.data.FreeMarketItem;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

/**
 * Manages marketplace data persistence using world NBT data.
 * Data is stored in world save data using SavedData system.
 */
public class FreeMarketDataManager {
    
    private static final String MARKETPLACE_DATA_KEY = "freemarket_marketplace";
    private static final String ITEMS_LIST_KEY = "items";
    private static final String VERSION_KEY = "version";
    private static final String LAST_UPDATED_KEY = "lastUpdated";
    private static final String INITIALIZED_KEY = "initialized";
    private static final String SAMPLE_FILE_NAME = "sample.txt";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    /**
     * Gets the marketplace data from world save data.
     * Always uses the Overworld dimension to ensure global marketplace storage.
     */
    public static List<FreeMarketItem> loadFreeMarketItems(ServerLevel level) {
        // CRITICAL: Always use Overworld for global marketplace storage
        ServerLevel overworldLevel = level.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworldLevel == null) {
            FreeMarket.LOGGER.error("[FreeMarketDataManager] ERROR: Overworld level is null! Using provided level as fallback.");
            overworldLevel = level;
        }
        
        MarketplaceSavedData savedData = overworldLevel.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(MarketplaceSavedData::new, MarketplaceSavedData::load),
            MARKETPLACE_DATA_KEY
        );
        
        List<FreeMarketItem> items = savedData.getItems();
        
        // Auto-generate test data if marketplace is empty and not initialized
        if (items.isEmpty() && !savedData.isInitialized()) {
            generateInitialTestData(overworldLevel);
            // Reload after generating test data
            savedData = overworldLevel.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MarketplaceSavedData::new, MarketplaceSavedData::load),
                MARKETPLACE_DATA_KEY
            );
            items = savedData.getItems();
        }
        
        return items;
    }
    
    /**
     * Saves marketplace items to world save data.
     * Always uses the Overworld dimension to ensure global marketplace storage.
     */
    public static void saveFreeMarketItems(ServerLevel level, List<FreeMarketItem> items) {
        // CRITICAL: Always use Overworld for global marketplace storage
        ServerLevel overworldLevel = level.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworldLevel == null) {
            FreeMarket.LOGGER.error("[FreeMarketDataManager] ERROR: Overworld level is null! Using provided level as fallback.");
            overworldLevel = level;
        }
        
        // Assign order to items that don't have one (set to last position)
        // Find the maximum order value among items that have an order
        int maxOrder = items.stream()
            .filter(item -> item.getOrder() != Integer.MAX_VALUE)
            .mapToInt(FreeMarketItem::getOrder)
            .max()
            .orElse(-1);
        
        // Assign order to items without one (starting from maxOrder + 1)
        int nextOrder = maxOrder + 1;
        for (FreeMarketItem item : items) {
            if (item.getOrder() == Integer.MAX_VALUE) {
                item.setOrder(nextOrder++);
            }
        }
        
        MarketplaceSavedData savedData = overworldLevel.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(MarketplaceSavedData::new, MarketplaceSavedData::load),
            MARKETPLACE_DATA_KEY
        );
        
        savedData.setItems(items);
        savedData.setDirty();
    }
    
    /**
     * Checks if the marketplace data exists for a given world.
     * Always uses the Overworld dimension to ensure global marketplace storage.
     */
    public static boolean marketplaceFileExists(ServerLevel level) {
        // CRITICAL: Always use Overworld for global marketplace storage
        ServerLevel overworldLevel = level.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworldLevel == null) {
            overworldLevel = level;
        }
        
        MarketplaceSavedData savedData = overworldLevel.getDataStorage().get(
            new SavedData.Factory<>(MarketplaceSavedData::new, MarketplaceSavedData::load),
            MARKETPLACE_DATA_KEY
        );
        
        return savedData != null && !savedData.getItems().isEmpty();
    }
    
    /**
     * Creates an empty marketplace data structure.
     * Called when a new world is created.
     * Always uses the Overworld dimension to ensure global marketplace storage.
     */
    public static void createEmptyMarketplaceFile(ServerLevel level) {
        // CRITICAL: Always use Overworld for global marketplace storage
        ServerLevel overworldLevel = level.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworldLevel == null) {
            FreeMarket.LOGGER.error("[FreeMarketDataManager] ERROR: Overworld level is null! Using provided level as fallback.");
            overworldLevel = level;
        }
        
        MarketplaceSavedData savedData = overworldLevel.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(MarketplaceSavedData::new, MarketplaceSavedData::load),
            MARKETPLACE_DATA_KEY
        );
        
        // Initialize with empty list if not already initialized
        if (savedData.getItems().isEmpty() && !savedData.isInitialized()) {
            savedData.setItems(new ArrayList<>());
            savedData.setDirty();
        }
    }
    
    /**
     * Generates initial test data for the marketplace if it's empty and mod hasn't been initialized.
     * This is called automatically when the marketplace is first created.
     */
    public static void generateInitialTestData(ServerLevel level) {
        try {
            // CRITICAL: Always use Overworld for global marketplace storage
            ServerLevel overworldLevel = level.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
            if (overworldLevel == null) {
                overworldLevel = level;
            }
            
            MarketplaceSavedData savedData = overworldLevel.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MarketplaceSavedData::new, MarketplaceSavedData::load),
                MARKETPLACE_DATA_KEY
            );
            
            // Only generate test data if marketplace is empty AND mod hasn't been initialized
            if (savedData.getItems().isEmpty() && !savedData.isInitialized()) {
                List<FreeMarketItem> testItems = new ArrayList<>();
                
                // Add various test items (totalStockAvailable is null by default, not yet implemented)
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.DIAMOND, 1), 100, 80, 1, null,
                    java.util.UUID.randomUUID().toString(), "{}", Integer.MAX_VALUE));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.IRON_INGOT, 1), 10, 8, 1, null,
                    java.util.UUID.randomUUID().toString(), "{}", Integer.MAX_VALUE));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.GOLD_INGOT, 1), 20, 16, 1, null,
                    java.util.UUID.randomUUID().toString(), "{}", Integer.MAX_VALUE));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.EMERALD, 1), 50, 40, 1, null,
                    java.util.UUID.randomUUID().toString(), "{}", Integer.MAX_VALUE));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.DIAMOND_SWORD, 1), 200, 160, 1, null,
                    java.util.UUID.randomUUID().toString(), "{\"minecraft:enchantments\":{\"enchantments\":{\"0\":{\"id\":\"minecraft:sharpness\",\"lvl\":3}}}}", Integer.MAX_VALUE));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.DIAMOND_PICKAXE, 1), 150, 120, 1, null,
                    java.util.UUID.randomUUID().toString(), "{\"minecraft:enchantments\":{\"enchantments\":{\"0\":{\"id\":\"minecraft:efficiency\",\"lvl\":5}}}}", Integer.MAX_VALUE));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.APPLE, 1), 2, 1, 1, null,
                    java.util.UUID.randomUUID().toString(), "{}", Integer.MAX_VALUE));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.BREAD, 1), 3, 2, 1, null,
                    java.util.UUID.randomUUID().toString(), "{}", Integer.MAX_VALUE));
                
                // Save test data
                savedData.setItems(testItems);
                savedData.setInitialized(true);
                savedData.setDirty();
                
                FreeMarket.LOGGER.info("Generated initial test data for FreeMarket marketplace");
            } else if (!savedData.getItems().isEmpty() && !savedData.isInitialized()) {
                // Marketplace has items but mod not initialized - mark as initialized anyway
                savedData.setInitialized(true);
                savedData.setDirty();
            }
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to generate initial test data: {}", e.getMessage());
        }
    }
    
    /**
     * Creates a sample marketplace JSON file in the config directory.
     * This file serves as a template for users to understand the format.
     * 
     * @param level The server level
     * @param configDir The config directory path
     */
    public static void createSampleMarketplaceFile(ServerLevel level, Path configDir) {
        try {
            Path marketDir = configDir.resolve("freemarket").resolve("market");
            Files.createDirectories(marketDir);
            
            Path sampleFile = marketDir.resolve(SAMPLE_FILE_NAME);
            
            // Only create if it doesn't exist
            if (Files.exists(sampleFile)) {
                return;
            }
            
            // Write sample file as .txt with comments - manually formatted to include comments
            try (FileWriter writer = new FileWriter(sampleFile.toFile())) {
                writer.write("{\n");
                writer.write("  \"marketListingId\": \"\", //optional, if not provided, a random market listing ID will be generated\n");
                writer.write("  \"itemId\": \"minecraft:dirt\", //required\n");
                writer.write("  \"componentData\": \"{}\", //optional, if not provided, the component data will be an empty string\n");
                writer.write("  \"stackSize\": 1, //optional, if not provided, the stackSize will be 1. This is the number of items in the stack\n");
                writer.write("  \"buyPrice\": 3, //optional, if not provided, the buy price will be 0\n");
                writer.write("  \"sellPrice\": 2, //optional, if not provided, the sell price will be 0\n");
                writer.write("  \"order\": 1 //optional, if not provided, the order will be set to the last position. listings with the same order will be sorted alphabetically by item name\n");
                writer.write("}\n");
            }
            
            FreeMarket.LOGGER.info("Created sample marketplace file at {}", sampleFile);
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to create sample marketplace file: {}", e.getMessage());
        }
    }
    
    /**
     * Dumps all marketplace items to JSON files in the config directory.
     * Each item is written to a separate file named {marketListingId}.json in config/freemarket/market/
     * Sample files are excluded from dumping.
     * 
     * @param level The server level
     * @param configDir The config directory path
     * @return The number of items successfully dumped
     */
    public static int dumpMarketplaceToJson(ServerLevel level, Path configDir) {
        try {
            // Get all marketplace items
            List<FreeMarketItem> items = loadFreeMarketItems(level);
            
            // Create market directory
            Path marketDir = configDir.resolve("freemarket").resolve("market");
            Files.createDirectories(marketDir);
            
            int dumpedCount = 0;
            
            // Write each item to a separate JSON file
            for (FreeMarketItem item : items) {
                try {
                    String marketListingId = item.getMarketListingId();
                    if (marketListingId == null || marketListingId.isEmpty()) {
                        marketListingId = java.util.UUID.randomUUID().toString();
                        FreeMarket.LOGGER.warn("Item missing market listing ID, generated new one: {}", marketListingId);
                    }
                    
                    // Create JSON object
                    JsonObject itemJson = new JsonObject();
                    ItemStack itemStack = item.getItemStack();
                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
                    
                    // Add properties in the specified order: marketListingId, ItemID, componentData, stackSize, totalStockAvailable, buyPrice, sellPrice, order
                    itemJson.addProperty("marketListingId", marketListingId);
                    itemJson.addProperty("itemId", itemId.toString());
                    itemJson.addProperty("componentData", item.getComponentData());
                    itemJson.addProperty("stackSize", item.getStackSize());
                    if (item.getTotalStockAvailable() != null) {
                        itemJson.addProperty("totalStockAvailable", item.getTotalStockAvailable());
                    }
                    itemJson.addProperty("buyPrice", item.getBuyPrice());
                    itemJson.addProperty("sellPrice", item.getSellPrice());
                    itemJson.addProperty("order", item.getOrder());
                    
                    // Write to file
                    File jsonFile = marketDir.resolve(marketListingId + ".json").toFile();
                    try (FileWriter writer = new FileWriter(jsonFile)) {
                        GSON.toJson(itemJson, writer);
                    }
                    
                    dumpedCount++;
                } catch (Exception e) {
                    FreeMarket.LOGGER.error("Failed to dump marketplace item with market listing ID {}: {}", item.getMarketListingId(), e.getMessage());
                }
            }
            
            FreeMarket.LOGGER.info("Dumped {} marketplace items to {}", dumpedCount, marketDir);
            return dumpedCount;
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to dump marketplace to JSON: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * Loads marketplace items from JSON files in the config directory.
     * Scans config/freemarket/market/ for all .json files and loads them.
     * Items with matching GUIDs will update existing items, others will be added as new items.
     * 
     * @param level The server level
     * @param configDir The config directory path
     * @return A result object containing success count, update count, and add count
     */
    public static LoadResult loadMarketplaceFromJson(ServerLevel level, Path configDir) {
        try {
            Path marketDir = configDir.resolve("freemarket").resolve("market");
            
            // Check if directory exists
            if (!Files.exists(marketDir) || !Files.isDirectory(marketDir)) {
                FreeMarket.LOGGER.warn("Market directory does not exist: {}", marketDir);
                return new LoadResult(0, 0, 0);
            }
            
            // Get current marketplace items
            List<FreeMarketItem> currentItems = loadFreeMarketItems(level);
            Map<String, Integer> marketListingIdToIndex = new HashMap<>();
            for (int i = 0; i < currentItems.size(); i++) {
                String marketListingId = currentItems.get(i).getMarketListingId();
                if (marketListingId != null && !marketListingId.isEmpty()) {
                    marketListingIdToIndex.put(marketListingId, i);
                }
            }
            
            int loadedCount = 0;
            int updatedCount = 0;
            int addedCount = 0;
            
            // Scan directory for JSON files, excluding sample files (.txt and sample.json)
            File[] jsonFiles = marketDir.toFile().listFiles((dir, name) -> 
                name.endsWith(".json") && !name.equals(SAMPLE_FILE_NAME) && !name.equals("sample.json"));
            if (jsonFiles == null) {
                return new LoadResult(0, 0, 0);
            }
            
            for (File jsonFile : jsonFiles) {
                try (FileReader reader = new FileReader(jsonFile)) {
                    JsonObject itemJson = GSON.fromJson(reader, JsonObject.class);
                    
                    if (itemJson == null) {
                        FreeMarket.LOGGER.warn("Invalid JSON in file: {}", jsonFile.getName());
                        continue;
                    }
                    
                    // Validate required field: itemId
                    if (!itemJson.has("itemId") || itemJson.get("itemId").isJsonNull()) {
                        FreeMarket.LOGGER.warn("Missing required field 'itemId' in file: {}", jsonFile.getName());
                        continue;
                    }
                    
                    // Deserialize item with proper error handling
                    String itemIdStr;
                    try {
                        itemIdStr = itemJson.get("itemId").getAsString();
                        if (itemIdStr == null || itemIdStr.isEmpty()) {
                            FreeMarket.LOGGER.warn("Empty 'itemId' in file: {}", jsonFile.getName());
                            continue;
                        }
                    } catch (Exception e) {
                        FreeMarket.LOGGER.warn("Invalid 'itemId' type in file {}: {}", jsonFile.getName(), e.getMessage());
                        continue;
                    }
                    
                    // Support both old "count" and new "stackSize" for backward compatibility
                    int stackSize = 1;
                    try {
                        if (itemJson.has("stackSize") && !itemJson.get("stackSize").isJsonNull()) {
                            stackSize = itemJson.get("stackSize").getAsInt();
                        } else if (itemJson.has("count") && !itemJson.get("count").isJsonNull()) {
                            stackSize = itemJson.get("count").getAsInt();
                        }
                        // Validate stackSize range (1 to 64, or item's max stack size)
                        if (stackSize < 1) {
                            FreeMarket.LOGGER.warn("Invalid stackSize ({}) in file {}, using 1", stackSize, jsonFile.getName());
                            stackSize = 1;
                        } else if (stackSize > 64) {
                            FreeMarket.LOGGER.warn("StackSize ({}) exceeds maximum (64) in file {}, capping to 64", stackSize, jsonFile.getName());
                            stackSize = 64;
                        }
                    } catch (Exception e) {
                        FreeMarket.LOGGER.warn("Invalid stackSize/count type in file {}: {}, using default 1", jsonFile.getName(), e.getMessage());
                        stackSize = 1;
                    }
                    
                    long buyPrice = 0;
                    long sellPrice = 0;
                    try {
                        if (itemJson.has("buyPrice") && !itemJson.get("buyPrice").isJsonNull()) {
                            buyPrice = itemJson.get("buyPrice").getAsLong();
                            if (buyPrice < 0) {
                                FreeMarket.LOGGER.warn("Negative buyPrice in file {}, setting to 0", jsonFile.getName());
                                buyPrice = 0;
                            }
                        }
                        if (itemJson.has("sellPrice") && !itemJson.get("sellPrice").isJsonNull()) {
                            sellPrice = itemJson.get("sellPrice").getAsLong();
                            if (sellPrice < 0) {
                                FreeMarket.LOGGER.warn("Negative sellPrice in file {}, setting to 0", jsonFile.getName());
                                sellPrice = 0;
                            }
                        }
                    } catch (Exception e) {
                        FreeMarket.LOGGER.warn("Invalid buyPrice/sellPrice type in file {}: {}", jsonFile.getName(), e.getMessage());
                    }
                    
                    // Support both old "quantity" and new "totalStockAvailable" for backward compatibility
                    Integer totalStockAvailable = null;
                    try {
                        if (itemJson.has("totalStockAvailable") && !itemJson.get("totalStockAvailable").isJsonNull()) {
                            totalStockAvailable = itemJson.get("totalStockAvailable").getAsInt();
                            if (totalStockAvailable < 0) {
                                FreeMarket.LOGGER.warn("Negative totalStockAvailable in file {}, ignoring", jsonFile.getName());
                                totalStockAvailable = null;
                            }
                        } else if (itemJson.has("quantity") && !itemJson.get("quantity").isJsonNull()) {
                            // Legacy support: old "quantity" field was used for stack size, so ignore it
                            // Only use it if it's not the same as stackSize (which would indicate it was actually totalStockAvailable)
                            int oldQuantity = itemJson.get("quantity").getAsInt();
                            if (oldQuantity != stackSize && oldQuantity > 0) {
                                totalStockAvailable = oldQuantity;
                            }
                        }
                    } catch (Exception e) {
                        FreeMarket.LOGGER.warn("Invalid totalStockAvailable/quantity type in file {}: {}", jsonFile.getName(), e.getMessage());
                    }
                    
                    // Support both old "guid" and new "marketListingId" for backward compatibility
                    String marketListingId = null;
                    try {
                        if (itemJson.has("marketListingId") && !itemJson.get("marketListingId").isJsonNull()) {
                            marketListingId = itemJson.get("marketListingId").getAsString();
                        } else if (itemJson.has("guid") && !itemJson.get("guid").isJsonNull()) {
                            marketListingId = itemJson.get("guid").getAsString();
                        }
                    } catch (Exception e) {
                        FreeMarket.LOGGER.warn("Invalid marketListingId/guid type in file {}: {}", jsonFile.getName(), e.getMessage());
                    }
                    
                    String componentData = "{}";
                    try {
                        if (itemJson.has("componentData") && !itemJson.get("componentData").isJsonNull()) {
                            componentData = itemJson.get("componentData").getAsString();
                            if (componentData == null) {
                                componentData = "{}";
                            }
                        }
                    } catch (Exception e) {
                        FreeMarket.LOGGER.warn("Invalid componentData type in file {}: {}, using default", jsonFile.getName(), e.getMessage());
                    }
                    
                    // Order will be set after all items are loaded to assign last position to items without order
                    int order = Integer.MAX_VALUE;
                    try {
                        if (itemJson.has("order") && !itemJson.get("order").isJsonNull()) {
                            order = itemJson.get("order").getAsInt();
                        }
                    } catch (Exception e) {
                        FreeMarket.LOGGER.warn("Invalid order type in file {}: {}, using default", jsonFile.getName(), e.getMessage());
                    }
                    
                    // Validate item ID format and existence
                    ResourceLocation itemId;
                    try {
                        itemId = ResourceLocation.parse(itemIdStr);
                    } catch (Exception e) {
                        FreeMarket.LOGGER.warn("Invalid itemId format '{}' in file {}: {}", itemIdStr, jsonFile.getName(), e.getMessage());
                        continue;
                    }
                    
                    if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
                        FreeMarket.LOGGER.warn("Unknown item ID '{}' in file: {}", itemIdStr, jsonFile.getName());
                        continue;
                    }
                    
                    // Create ItemStack with validated stackSize
                    Item item = BuiltInRegistries.ITEM.get(itemId);
                    // Clamp stackSize to item's max stack size
                    ItemStack tempStack = new ItemStack(item, 1);
                    int maxStackSize = tempStack.getMaxStackSize();
                    if (stackSize > maxStackSize) {
                        FreeMarket.LOGGER.warn("StackSize ({}) exceeds item's max stack size ({}) in file {}, capping to {}", 
                            stackSize, maxStackSize, jsonFile.getName(), maxStackSize);
                        stackSize = maxStackSize;
                    }
                    ItemStack itemStack = new ItemStack(item, stackSize);
                    
                    // Apply component data if present
                    if (componentData != null && !componentData.isEmpty() && !componentData.equals("{}")) {
                        try {
                            itemStack = com.freemarket.server.handlers.ServerItemHandler.createItemWithComponentData(
                                itemStack, componentData, level.getServer());
                        } catch (Exception e) {
                            FreeMarket.LOGGER.warn("Failed to apply component data to item from file {}: {}", jsonFile.getName(), e.getMessage());
                        }
                    }
                    
                    // Generate market listing ID if missing
                    if (marketListingId == null || marketListingId.isEmpty()) {
                        marketListingId = java.util.UUID.randomUUID().toString();
                        FreeMarket.LOGGER.info("Generated new market listing ID for item from file {}: {}", jsonFile.getName(), marketListingId);
                    }
                    
                    // Create FreeMarketItem
                    FreeMarketItem freeMarketItem = new FreeMarketItem(
                        itemStack, buyPrice, sellPrice, stackSize, totalStockAvailable, marketListingId, componentData, order);
                    
                    // Check if market listing ID exists in current items
                    if (marketListingIdToIndex.containsKey(marketListingId)) {
                        // Update existing item
                        int index = marketListingIdToIndex.get(marketListingId);
                        currentItems.set(index, freeMarketItem);
                        updatedCount++;
                    } else {
                        // Add as new item
                        currentItems.add(freeMarketItem);
                        marketListingIdToIndex.put(marketListingId, currentItems.size() - 1);
                        addedCount++;
                    }
                    
                    loadedCount++;
                    
                } catch (Exception e) {
                    FreeMarket.LOGGER.error("Failed to load marketplace item from file {}: {}", jsonFile.getName(), e.getMessage());
                }
            }
            
            // Assign order to items that don't have one (set to last position)
            // Find the maximum order value among items that have an order
            int maxOrder = currentItems.stream()
                .filter(item -> item.getOrder() != Integer.MAX_VALUE)
                .mapToInt(FreeMarketItem::getOrder)
                .max()
                .orElse(-1);
            
            // Assign order to items without one (starting from maxOrder + 1)
            int nextOrder = maxOrder + 1;
            for (FreeMarketItem item : currentItems) {
                if (item.getOrder() == Integer.MAX_VALUE) {
                    item.setOrder(nextOrder++);
                }
            }
            
            // Save updated items
            if (loadedCount > 0) {
                saveFreeMarketItems(level, currentItems);
                FreeMarket.LOGGER.info("Loaded {} marketplace items ({} updated, {} added) from {}", 
                    loadedCount, updatedCount, addedCount, marketDir);
            }
            
            return new LoadResult(loadedCount, updatedCount, addedCount);
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to load marketplace from JSON: {}", e.getMessage());
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
     * SavedData implementation for storing marketplace data in world NBT.
     */
    public static class MarketplaceSavedData extends SavedData {
        private List<FreeMarketItem> items = new ArrayList<>();
        private String version = "1.0";
        private long lastUpdated = System.currentTimeMillis();
        private boolean initialized = false;
        
        public MarketplaceSavedData() {
            // Default constructor
        }
        
        public MarketplaceSavedData(List<FreeMarketItem> items) {
            this.items = new ArrayList<>(items);
        }
        
        @Override
        public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
            ListTag itemsList = new ListTag();
            
            for (FreeMarketItem item : items) {
                CompoundTag itemTag = new CompoundTag();
                
                // Serialize ItemStack
                ItemStack itemStack = item.getItemStack();
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
                itemTag.putString("itemId", itemId.toString());
                itemTag.putInt("stackSize", item.getStackSize());
                
                // Save ItemStack with component data to NBT
                CompoundTag itemStackTag = new CompoundTag();
                itemStack.save(registries, itemStackTag);
                itemTag.put("itemStack", itemStackTag);
                
                // Serialize marketplace data
                itemTag.putLong("buyPrice", item.getBuyPrice());
                itemTag.putLong("sellPrice", item.getSellPrice());
                if (item.getTotalStockAvailable() != null) {
                    itemTag.putInt("totalStockAvailable", item.getTotalStockAvailable());
                }
                itemTag.putString("marketListingId", item.getMarketListingId());
                itemTag.putString("componentData", item.getComponentData());
                itemTag.putInt("order", item.getOrder());
                
                itemsList.add(itemTag);
            }
            
            tag.put(ITEMS_LIST_KEY, itemsList);
            tag.putString(VERSION_KEY, version);
            tag.putLong(LAST_UPDATED_KEY, System.currentTimeMillis());
            tag.putBoolean(INITIALIZED_KEY, initialized);
            
            return tag;
        }
        
        public static MarketplaceSavedData load(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
            MarketplaceSavedData data = new MarketplaceSavedData();
            
            if (tag.contains(ITEMS_LIST_KEY, Tag.TAG_LIST)) {
                ListTag itemsList = tag.getList(ITEMS_LIST_KEY, Tag.TAG_COMPOUND);
                
                for (int i = 0; i < itemsList.size(); i++) {
                    CompoundTag itemTag = itemsList.getCompound(i);
                    
                    try {
                        // Try to load ItemStack from NBT first (preferred method)
                        ItemStack itemStack = null;
                        if (itemTag.contains("itemStack", Tag.TAG_COMPOUND)) {
                            CompoundTag itemStackTag = itemTag.getCompound("itemStack");
                            try {
                                // parseOptional returns ItemStack directly (may be ItemStack.EMPTY if invalid)
                                ItemStack parsedStack = ItemStack.parseOptional(registries, itemStackTag);
                                if (parsedStack != null && !parsedStack.isEmpty()) {
                                    itemStack = parsedStack;
                                }
                            } catch (Exception e) {
                                FreeMarket.LOGGER.warn("Failed to parse ItemStack from NBT: {}", e.getMessage());
                            }
                        }
                        
                        // Fallback to legacy format (itemId + count/stackSize) if itemStack is null
                        if (itemStack == null && itemTag.contains("itemId")) {
                            String itemIdStr = itemTag.getString("itemId");
                            // Support both old "count" and new "stackSize" for backward compatibility
                            int stackSize = itemTag.contains("stackSize") ? itemTag.getInt("stackSize") : 
                                           (itemTag.contains("count") ? itemTag.getInt("count") : 1);
                            
                            ResourceLocation itemId = ResourceLocation.parse(itemIdStr);
                            if (BuiltInRegistries.ITEM.containsKey(itemId)) {
                                Item item = BuiltInRegistries.ITEM.get(itemId);
                                itemStack = new ItemStack(item, stackSize);
                            } else {
                                FreeMarket.LOGGER.warn("Unknown item ID in marketplace data: {}", itemIdStr);
                                continue;
                            }
                        }
                        
                        if (itemStack == null) {
                            FreeMarket.LOGGER.warn("Failed to deserialize ItemStack from marketplace data");
                            continue;
                        }
                        
                        // Deserialize marketplace data
                        long buyPrice = itemTag.contains("buyPrice") ? itemTag.getLong("buyPrice") : itemTag.getInt("buyPrice");
                        long sellPrice = itemTag.contains("sellPrice") ? itemTag.getLong("sellPrice") : itemTag.getInt("sellPrice");
                        // Support both old "quantity" and new "stackSize" for backward compatibility
                        int stackSize = itemTag.contains("stackSize") ? itemTag.getInt("stackSize") : 
                                       (itemTag.contains("count") ? itemTag.getInt("count") : itemStack.getCount());
                        Integer totalStockAvailable = itemTag.contains("totalStockAvailable") ? itemTag.getInt("totalStockAvailable") : null;
                        // Legacy support: if old "quantity" exists and differs from stackSize, use it as totalStockAvailable
                        if (totalStockAvailable == null && itemTag.contains("quantity")) {
                            int oldQuantity = itemTag.getInt("quantity");
                            if (oldQuantity != stackSize) {
                                totalStockAvailable = oldQuantity;
                            }
                        }
                        // Support both old "guid" and new "marketListingId" for backward compatibility
                        String marketListingId = null;
                        if (itemTag.contains("marketListingId")) {
                            marketListingId = itemTag.getString("marketListingId");
                        } else if (itemTag.contains("guid")) {
                            marketListingId = itemTag.getString("guid");
                        }
                        String componentData = itemTag.contains("componentData") ? itemTag.getString("componentData") : "{}";
                        int order = itemTag.contains("order") ? itemTag.getInt("order") : Integer.MAX_VALUE;
                        
                        // If market listing ID is missing or empty, generate a random one
                        if (marketListingId == null || marketListingId.isEmpty()) {
                            marketListingId = java.util.UUID.randomUUID().toString();
                        }
                        
                        // Note: Component data is already in the ItemStack when loaded from NBT,
                        // but we keep the componentData string for reference/display purposes
                        
                        FreeMarketItem freeMarketItem = new FreeMarketItem(
                            itemStack, buyPrice, sellPrice, stackSize, totalStockAvailable, marketListingId, componentData, order);
                        data.items.add(freeMarketItem);
                        
                    } catch (Exception e) {
                        FreeMarket.LOGGER.error("Failed to deserialize marketplace item: {}", e.getMessage());
                    }
                }
            }
            
            if (tag.contains(VERSION_KEY)) {
                data.version = tag.getString(VERSION_KEY);
            }
            if (tag.contains(LAST_UPDATED_KEY)) {
                data.lastUpdated = tag.getLong(LAST_UPDATED_KEY);
            }
            if (tag.contains(INITIALIZED_KEY)) {
                data.initialized = tag.getBoolean(INITIALIZED_KEY);
            }
            
            return data;
        }
        
        public List<FreeMarketItem> getItems() {
            return new ArrayList<>(items);
        }
        
        public void setItems(List<FreeMarketItem> items) {
            this.items = new ArrayList<>(items);
        }
        
        public String getVersion() {
            return version;
        }
        
        public long getLastUpdated() {
            return lastUpdated;
        }
        
        public boolean isInitialized() {
            return initialized;
        }
        
        public void setInitialized(boolean initialized) {
            this.initialized = initialized;
        }
    }
}
