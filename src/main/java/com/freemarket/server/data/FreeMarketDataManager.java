package com.freemarket.server.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import com.freemarket.FreeMarket;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.freemarket.common.data.FreeMarketItem;

/**
 * Manages marketplace data persistence using JSON files in world data directory.
 * Creates empty marketplace.json file on world creation and handles reading/writing marketplace items.
 */
public class FreeMarketDataManager {
    
    private static final String MARKETPLACE_FILE_NAME = "marketplace.json";
    private static final String INITIALIZATION_FLAG_FILE_NAME = "FreeMarket_initialized.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    /**
     * Gets the marketplace data file path for a given world.
     */
    public static Path getMarketplaceFilePath(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve(MARKETPLACE_FILE_NAME);
    }
    
    /**
     * Creates an empty marketplace.json file in the world data directory.
     * Called when a new world is created.
     */
    public static void createEmptyMarketplaceFile(ServerLevel level) {
        try {
            Path marketplaceFile = getMarketplaceFilePath(level);
            File file = marketplaceFile.toFile();
            
            // Create parent directories if they don't exist
            file.getParentFile().mkdirs();
            
            // Create empty marketplace structure
            JsonObject marketplaceData = new JsonObject();
            marketplaceData.add("items", new JsonArray());
            marketplaceData.addProperty("version", "1.0");
            marketplaceData.addProperty("description", "FreeMarket Marketplace Data");
            
            // Write to file
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(marketplaceData, writer);
            }
            
        } catch (IOException e) {
            FreeMarket.LOGGER.error("Failed to create marketplace.json file for world: {}", level.dimension().location(), e);
        }
    }
    
    /**
     * Loads marketplace items from the JSON file.
     * Returns empty list if file doesn't exist or is invalid.
     */
    public static List<FreeMarketItem> loadFreeMarketItems(ServerLevel level) {
        List<FreeMarketItem> items = new ArrayList<>();
        
        try {
            Path marketplaceFile = getMarketplaceFilePath(level);
            File file = marketplaceFile.toFile();
            
            if (!file.exists()) {
                return items;
            }
            
            JsonElement jsonElement = JsonParser.parseString(new String(java.nio.file.Files.readAllBytes(marketplaceFile)));
            
            if (!jsonElement.isJsonObject()) {
                FreeMarket.LOGGER.error("Invalid marketplace file format: {}", marketplaceFile);
                return items;
            }
            
            JsonObject marketplaceData = jsonElement.getAsJsonObject();
            JsonArray itemsArray = marketplaceData.getAsJsonArray("items");
            
            if (itemsArray != null) {
                for (JsonElement itemElement : itemsArray) {
                    if (itemElement.isJsonObject()) {
                        FreeMarketItem item = deserializeFreeMarketItem(itemElement.getAsJsonObject());
                        if (item != null) {
                            items.add(item);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to load marketplace items from world: {}", level.dimension().location(), e);
        }
        
        // Auto-generate test data if marketplace is empty
        if (items.isEmpty()) {
            generateInitialTestData(level);
            // Reload after generating test data
            try {
                items = loadFreeMarketItemsFromFile(level);
            } catch (Exception e) {
                FreeMarket.LOGGER.error("Failed to reload marketplace items after generating test data: {}", e.getMessage());
            }
        }
        
        return items;
    }
    
    /**
     * Internal method to load marketplace items from file without auto-generation.
     * Used to reload after generating test data to avoid infinite recursion.
     */
    private static List<FreeMarketItem> loadFreeMarketItemsFromFile(ServerLevel level) {
        List<FreeMarketItem> items = new ArrayList<>();
        
        try {
            Path marketplaceFile = getMarketplaceFilePath(level);
            File file = marketplaceFile.toFile();
            
            if (!file.exists()) {
                return items;
            }
            
            JsonElement jsonElement = JsonParser.parseString(new String(java.nio.file.Files.readAllBytes(marketplaceFile)));
            
            if (!jsonElement.isJsonObject()) {
                return items;
            }
            
            JsonObject marketplaceData = jsonElement.getAsJsonObject();
            JsonArray itemsArray = marketplaceData.getAsJsonArray("items");
            
            if (itemsArray != null) {
                for (JsonElement itemElement : itemsArray) {
                    if (itemElement.isJsonObject()) {
                        FreeMarketItem item = deserializeFreeMarketItem(itemElement.getAsJsonObject());
                        if (item != null) {
                            items.add(item);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to load marketplace items from file: {}", e.getMessage());
        }
        
        return items;
    }
    
    /**
     * Saves marketplace items to the JSON file.
     */
    public static void saveFreeMarketItems(ServerLevel level, List<FreeMarketItem> items) {
        try {
            Path marketplaceFile = getMarketplaceFilePath(level);
            File file = marketplaceFile.toFile();
            
            // Create parent directories if they don't exist
            file.getParentFile().mkdirs();
            
            JsonObject marketplaceData = new JsonObject();
            JsonArray itemsArray = new JsonArray();
            
            for (FreeMarketItem item : items) {
                JsonObject itemJson = serializeFreeMarketItem(item);
                itemsArray.add(itemJson);
            }
            
            marketplaceData.add("items", itemsArray);
            marketplaceData.addProperty("version", "1.0");
            marketplaceData.addProperty("description", "FreeMarket Marketplace Data");
            marketplaceData.addProperty("lastUpdated", System.currentTimeMillis());
            
            // Write to file
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(marketplaceData, writer);
            }
            
        } catch (IOException e) {
            FreeMarket.LOGGER.error("Failed to save marketplace items to world: {}", level.dimension().location(), e);
        }
    }
    
    /**
     * Serializes a FreeMarketItem to JSON.
     */
    private static JsonObject serializeFreeMarketItem(FreeMarketItem item) {
        JsonObject itemJson = new JsonObject();
        
        // Serialize ItemStack
        ItemStack itemStack = item.getItemStack();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        itemJson.addProperty("itemId", itemId.toString());
        itemJson.addProperty("count", itemStack.getCount());
        
        // Serialize marketplace data
        itemJson.addProperty("buyPrice", item.getBuyPrice());
        itemJson.addProperty("sellPrice", item.getSellPrice());
        itemJson.addProperty("quantity", item.getQuantity());
        itemJson.addProperty("seller", item.getSeller());
        itemJson.addProperty("guid", item.getGuid());
        itemJson.addProperty("componentData", item.getComponentData());
        
        return itemJson;
    }
    
    /**
     * Deserializes a FreeMarketItem from JSON.
     */
    private static FreeMarketItem deserializeFreeMarketItem(JsonObject itemJson) {
        try {
            // Deserialize ItemStack
            String itemIdStr = itemJson.get("itemId").getAsString();
            int count = itemJson.has("count") ? itemJson.get("count").getAsInt() : 1;
            
            ResourceLocation itemId = ResourceLocation.parse(itemIdStr);
            Item item = BuiltInRegistries.ITEM.get(itemId);
            
            if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
                return null;
            }
            
            ItemStack itemStack = new ItemStack(item, count);
            
            // Deserialize marketplace data
            int buyPrice = itemJson.get("buyPrice").getAsInt();
            int sellPrice = itemJson.get("sellPrice").getAsInt();
            int quantity = itemJson.get("quantity").getAsInt();
            String seller = itemJson.get("seller").getAsString();
            String guid = itemJson.has("guid") ? itemJson.get("guid").getAsString() : null;
            String componentData = itemJson.has("componentData") ? itemJson.get("componentData").getAsString() : "{}";
            
            // If GUID is missing or empty, generate a random one
            if (guid == null || guid.isEmpty()) {
                guid = java.util.UUID.randomUUID().toString();
            }
            
            return new FreeMarketItem(itemStack, buyPrice, sellPrice, quantity, seller, guid, componentData);
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to deserialize marketplace item: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Checks if the marketplace file exists for a given world.
     */
    public static boolean marketplaceFileExists(ServerLevel level) {
        Path marketplaceFile = getMarketplaceFilePath(level);
        return marketplaceFile.toFile().exists();
    }
    
    /**
     * Checks if FreeMarket has been initialized for this world (test data generated).
     */
    private static boolean isModInitialized(ServerLevel level) {
        try {
            Path initFlagFile = getInitializationFlagPath(level);
            return initFlagFile.toFile().exists();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Marks FreeMarket as initialized for this world.
     */
    private static void markModAsInitialized(ServerLevel level) {
        try {
            Path initFlagFile = getInitializationFlagPath(level);
            File file = initFlagFile.toFile();
            
            // Create parent directories if they don't exist
            file.getParentFile().mkdirs();
            
            // Create a simple JSON file to mark initialization
            JsonObject initData = new JsonObject();
            initData.addProperty("initialized", true);
            initData.addProperty("timestamp", System.currentTimeMillis());
            initData.addProperty("version", "1.0.2");
            
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(initData, writer);
            }
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to mark mod as initialized: {}", e.getMessage());
        }
    }
    
    /**
     * Gets the initialization flag file path for a given world.
     */
    private static Path getInitializationFlagPath(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve(INITIALIZATION_FLAG_FILE_NAME);
    }
    
    /**
     * Generates initial test data for the marketplace if it's empty and mod hasn't been initialized.
     * This is called automatically when the marketplace is first created.
     */
    public static void generateInitialTestData(ServerLevel level) {
        try {
            // Only generate test data if mod hasn't been initialized yet
            if (isModInitialized(level)) {
                return;
            }
            
            List<FreeMarketItem> existingItems = loadFreeMarketItemsFromFile(level);
            
            // Only generate test data if marketplace is empty AND mod hasn't been initialized
            if (existingItems.isEmpty()) {
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
                saveFreeMarketItems(level, testItems);
                
                // Mark mod as initialized to prevent future auto-generation
                markModAsInitialized(level);
                
            } else {
                // Marketplace has items but mod not initialized - mark as initialized anyway
                markModAsInitialized(level);
            }
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to generate initial test data: {}", e.getMessage());
        }
    }
}
