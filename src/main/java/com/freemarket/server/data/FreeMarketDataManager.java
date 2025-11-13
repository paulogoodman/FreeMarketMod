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
                
                // Add various test items
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.DIAMOND, 1), 100, 80, 1, 
                    java.util.UUID.randomUUID().toString(), "{}"));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.IRON_INGOT, 1), 10, 8, 1, 
                    java.util.UUID.randomUUID().toString(), "{}"));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.GOLD_INGOT, 1), 20, 16, 1, 
                    java.util.UUID.randomUUID().toString(), "{}"));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.EMERALD, 1), 50, 40, 1, 
                    java.util.UUID.randomUUID().toString(), "{}"));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.DIAMOND_SWORD, 1), 200, 160, 1, 
                    java.util.UUID.randomUUID().toString(), "{\"minecraft:enchantments\":{\"enchantments\":{\"0\":{\"id\":\"minecraft:sharpness\",\"lvl\":3}}}}"));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.DIAMOND_PICKAXE, 1), 150, 120, 1, 
                    java.util.UUID.randomUUID().toString(), "{\"minecraft:enchantments\":{\"enchantments\":{\"0\":{\"id\":\"minecraft:efficiency\",\"lvl\":5}}}}"));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.APPLE, 1), 2, 1, 1, 
                    java.util.UUID.randomUUID().toString(), "{}"));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.BREAD, 1), 3, 2, 1, 
                    java.util.UUID.randomUUID().toString(), "{}"));
                
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
     * Dumps all marketplace items to JSON files in the config directory.
     * Each item is written to a separate file named {guid}.json in config/freemarket/market/
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
                    String guid = item.getGuid();
                    if (guid == null || guid.isEmpty()) {
                        guid = java.util.UUID.randomUUID().toString();
                        FreeMarket.LOGGER.warn("Item missing GUID, generated new one: {}", guid);
                    }
                    
                    // Create JSON object
                    JsonObject itemJson = new JsonObject();
                    ItemStack itemStack = item.getItemStack();
                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
                    
                    itemJson.addProperty("itemId", itemId.toString());
                    itemJson.addProperty("count", itemStack.getCount());
                    itemJson.addProperty("buyPrice", item.getBuyPrice());
                    itemJson.addProperty("sellPrice", item.getSellPrice());
                    itemJson.addProperty("quantity", item.getQuantity());
                    itemJson.addProperty("guid", guid);
                    itemJson.addProperty("componentData", item.getComponentData());
                    itemJson.addProperty("order", item.getOrder());
                    
                    // Write to file
                    File jsonFile = marketDir.resolve(guid + ".json").toFile();
                    try (FileWriter writer = new FileWriter(jsonFile)) {
                        GSON.toJson(itemJson, writer);
                    }
                    
                    dumpedCount++;
                } catch (Exception e) {
                    FreeMarket.LOGGER.error("Failed to dump marketplace item with GUID {}: {}", item.getGuid(), e.getMessage());
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
            Map<String, Integer> guidToIndex = new HashMap<>();
            for (int i = 0; i < currentItems.size(); i++) {
                String guid = currentItems.get(i).getGuid();
                if (guid != null && !guid.isEmpty()) {
                    guidToIndex.put(guid, i);
                }
            }
            
            int loadedCount = 0;
            int updatedCount = 0;
            int addedCount = 0;
            
            // Scan directory for JSON files
            File[] jsonFiles = marketDir.toFile().listFiles((dir, name) -> name.endsWith(".json"));
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
                    
                    // Deserialize item
                    String itemIdStr = itemJson.get("itemId").getAsString();
                    int count = itemJson.has("count") ? itemJson.get("count").getAsInt() : 1;
                    long buyPrice = itemJson.has("buyPrice") ? itemJson.get("buyPrice").getAsLong() : 0;
                    long sellPrice = itemJson.has("sellPrice") ? itemJson.get("sellPrice").getAsLong() : 0;
                    int quantity = itemJson.has("quantity") ? itemJson.get("quantity").getAsInt() : 1;
                    String guid = itemJson.has("guid") ? itemJson.get("guid").getAsString() : null;
                    String componentData = itemJson.has("componentData") ? itemJson.get("componentData").getAsString() : "{}";
                    // Order will be set after all items are loaded to assign last position to items without order
                    int order = itemJson.has("order") ? itemJson.get("order").getAsInt() : Integer.MAX_VALUE;
                    
                    // Validate item ID
                    ResourceLocation itemId = ResourceLocation.parse(itemIdStr);
                    if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
                        FreeMarket.LOGGER.warn("Unknown item ID in file {}: {}", jsonFile.getName(), itemIdStr);
                        continue;
                    }
                    
                    // Create ItemStack
                    Item item = BuiltInRegistries.ITEM.get(itemId);
                    ItemStack itemStack = new ItemStack(item, count);
                    
                    // Apply component data if present
                    if (componentData != null && !componentData.isEmpty() && !componentData.equals("{}")) {
                        try {
                            itemStack = com.freemarket.server.handlers.ServerItemHandler.createItemWithComponentData(
                                itemStack, componentData, level.getServer());
                        } catch (Exception e) {
                            FreeMarket.LOGGER.warn("Failed to apply component data to item from file {}: {}", jsonFile.getName(), e.getMessage());
                        }
                    }
                    
                    // Generate GUID if missing
                    if (guid == null || guid.isEmpty()) {
                        guid = java.util.UUID.randomUUID().toString();
                        FreeMarket.LOGGER.info("Generated new GUID for item from file {}: {}", jsonFile.getName(), guid);
                    }
                    
                    // Create FreeMarketItem
                    FreeMarketItem freeMarketItem = new FreeMarketItem(
                        itemStack, buyPrice, sellPrice, quantity, guid, componentData, order);
                    
                    // Check if GUID exists in current items
                    if (guidToIndex.containsKey(guid)) {
                        // Update existing item
                        int index = guidToIndex.get(guid);
                        currentItems.set(index, freeMarketItem);
                        updatedCount++;
                    } else {
                        // Add as new item
                        currentItems.add(freeMarketItem);
                        guidToIndex.put(guid, currentItems.size() - 1);
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
                itemTag.putInt("count", itemStack.getCount());
                
                // Save ItemStack with component data to NBT
                CompoundTag itemStackTag = new CompoundTag();
                itemStack.save(registries, itemStackTag);
                itemTag.put("itemStack", itemStackTag);
                
                // Serialize marketplace data
                itemTag.putLong("buyPrice", item.getBuyPrice());
                itemTag.putLong("sellPrice", item.getSellPrice());
                itemTag.putInt("quantity", item.getQuantity());
                itemTag.putString("guid", item.getGuid());
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
                        
                        // Fallback to legacy format (itemId + count) if itemStack is null
                        if (itemStack == null && itemTag.contains("itemId")) {
                            String itemIdStr = itemTag.getString("itemId");
                            int count = itemTag.contains("count") ? itemTag.getInt("count") : 1;
                            
                            ResourceLocation itemId = ResourceLocation.parse(itemIdStr);
                            if (BuiltInRegistries.ITEM.containsKey(itemId)) {
                                Item item = BuiltInRegistries.ITEM.get(itemId);
                                itemStack = new ItemStack(item, count);
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
                        int quantity = itemTag.getInt("quantity");
                        String guid = itemTag.contains("guid") ? itemTag.getString("guid") : null;
                        String componentData = itemTag.contains("componentData") ? itemTag.getString("componentData") : "{}";
                        int order = itemTag.contains("order") ? itemTag.getInt("order") : Integer.MAX_VALUE;
                        
                        // If GUID is missing or empty, generate a random one
                        if (guid == null || guid.isEmpty()) {
                            guid = java.util.UUID.randomUUID().toString();
                        }
                        
                        // Note: Component data is already in the ItemStack when loaded from NBT,
                        // but we keep the componentData string for reference/display purposes
                        
                        FreeMarketItem freeMarketItem = new FreeMarketItem(
                            itemStack, buyPrice, sellPrice, quantity, guid, componentData, order);
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
