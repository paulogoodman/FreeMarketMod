package com.freemarket.server.data;

import com.freemarket.FreeMarket;
import com.freemarket.common.data.FreeMarketItem;
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

import java.util.ArrayList;
import java.util.List;

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
                String seller = "FreeMarket";
                
                // Add various test items
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.DIAMOND, 1), 100, 80, 1, seller, 
                    java.util.UUID.randomUUID().toString(), "{}"));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.IRON_INGOT, 1), 10, 8, 1, seller, 
                    java.util.UUID.randomUUID().toString(), "{}"));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.GOLD_INGOT, 1), 20, 16, 1, seller, 
                    java.util.UUID.randomUUID().toString(), "{}"));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.EMERALD, 1), 50, 40, 1, seller, 
                    java.util.UUID.randomUUID().toString(), "{}"));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.DIAMOND_SWORD, 1), 200, 160, 1, seller, 
                    java.util.UUID.randomUUID().toString(), "{\"minecraft:enchantments\":{\"enchantments\":{\"0\":{\"id\":\"minecraft:sharpness\",\"lvl\":3}}}}"));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.DIAMOND_PICKAXE, 1), 150, 120, 1, seller, 
                    java.util.UUID.randomUUID().toString(), "{\"minecraft:enchantments\":{\"enchantments\":{\"0\":{\"id\":\"minecraft:efficiency\",\"lvl\":5}}}}"));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.APPLE, 1), 2, 1, 1, seller, 
                    java.util.UUID.randomUUID().toString(), "{}"));
                
                testItems.add(new FreeMarketItem(
                    new ItemStack(Items.BREAD, 1), 3, 2, 1, seller, 
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
                itemTag.putString("seller", item.getSeller());
                itemTag.putString("guid", item.getGuid());
                itemTag.putString("componentData", item.getComponentData());
                
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
                        String seller = itemTag.getString("seller");
                        String guid = itemTag.contains("guid") ? itemTag.getString("guid") : null;
                        String componentData = itemTag.contains("componentData") ? itemTag.getString("componentData") : "{}";
                        
                        // If GUID is missing or empty, generate a random one
                        if (guid == null || guid.isEmpty()) {
                            guid = java.util.UUID.randomUUID().toString();
                        }
                        
                        // Note: Component data is already in the ItemStack when loaded from NBT,
                        // but we keep the componentData string for reference/display purposes
                        
                        FreeMarketItem freeMarketItem = new FreeMarketItem(
                            itemStack, buyPrice, sellPrice, quantity, seller, guid, componentData);
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
