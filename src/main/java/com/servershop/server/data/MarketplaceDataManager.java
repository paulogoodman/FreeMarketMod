package com.servershop.server.data;

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
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.servershop.ServerShop;
import com.servershop.common.data.MarketplaceItem;

/**
 * Manages marketplace data persistence using JSON files in world data directory.
 * Creates empty marketplace.json file on world creation and handles reading/writing marketplace items.
 */
public class MarketplaceDataManager {
    
    private static final String MARKETPLACE_FILE_NAME = "marketplace.json";
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
            marketplaceData.addProperty("description", "ServerShop Marketplace Data");
            
            // Write to file
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(marketplaceData, writer);
            }
            
            ServerShop.LOGGER.info("Created empty marketplace.json file for world: {}", level.dimension().location());
            
        } catch (IOException e) {
            ServerShop.LOGGER.error("Failed to create marketplace.json file for world: {}", level.dimension().location(), e);
        }
    }
    
    /**
     * Loads marketplace items from the JSON file.
     * Returns empty list if file doesn't exist or is invalid.
     */
    public static List<MarketplaceItem> loadMarketplaceItems(ServerLevel level) {
        List<MarketplaceItem> items = new ArrayList<>();
        
        try {
            Path marketplaceFile = getMarketplaceFilePath(level);
            File file = marketplaceFile.toFile();
            
            if (!file.exists()) {
                ServerShop.LOGGER.warn("Marketplace file does not exist: {}", marketplaceFile);
                return items;
            }
            
            JsonElement jsonElement = JsonParser.parseString(new String(java.nio.file.Files.readAllBytes(marketplaceFile)));
            
            if (!jsonElement.isJsonObject()) {
                ServerShop.LOGGER.error("Invalid marketplace file format: {}", marketplaceFile);
                return items;
            }
            
            JsonObject marketplaceData = jsonElement.getAsJsonObject();
            JsonArray itemsArray = marketplaceData.getAsJsonArray("items");
            
            if (itemsArray != null) {
                for (JsonElement itemElement : itemsArray) {
                    if (itemElement.isJsonObject()) {
                        MarketplaceItem item = deserializeMarketplaceItem(itemElement.getAsJsonObject());
                        if (item != null) {
                            items.add(item);
                        }
                    }
                }
            }
            
            ServerShop.LOGGER.info("Loaded {} marketplace items from {}", items.size(), marketplaceFile);
            
        } catch (Exception e) {
            ServerShop.LOGGER.error("Failed to load marketplace items from world: {}", level.dimension().location(), e);
        }
        
        return items;
    }
    
    /**
     * Saves marketplace items to the JSON file.
     */
    public static void saveMarketplaceItems(ServerLevel level, List<MarketplaceItem> items) {
        try {
            Path marketplaceFile = getMarketplaceFilePath(level);
            File file = marketplaceFile.toFile();
            
            // Create parent directories if they don't exist
            file.getParentFile().mkdirs();
            
            JsonObject marketplaceData = new JsonObject();
            JsonArray itemsArray = new JsonArray();
            
            for (MarketplaceItem item : items) {
                JsonObject itemJson = serializeMarketplaceItem(item);
                itemsArray.add(itemJson);
            }
            
            marketplaceData.add("items", itemsArray);
            marketplaceData.addProperty("version", "1.0");
            marketplaceData.addProperty("description", "ServerShop Marketplace Data");
            marketplaceData.addProperty("lastUpdated", System.currentTimeMillis());
            
            // Write to file
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(marketplaceData, writer);
            }
            
            ServerShop.LOGGER.info("Saved {} marketplace items to {}", items.size(), marketplaceFile);
            
        } catch (IOException e) {
            ServerShop.LOGGER.error("Failed to save marketplace items to world: {}", level.dimension().location(), e);
        }
    }
    
    /**
     * Serializes a MarketplaceItem to JSON.
     */
    private static JsonObject serializeMarketplaceItem(MarketplaceItem item) {
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
        
        return itemJson;
    }
    
    /**
     * Deserializes a MarketplaceItem from JSON.
     */
    private static MarketplaceItem deserializeMarketplaceItem(JsonObject itemJson) {
        try {
            // Deserialize ItemStack
            String itemIdStr = itemJson.get("itemId").getAsString();
            int count = itemJson.has("count") ? itemJson.get("count").getAsInt() : 1;
            
            ResourceLocation itemId = ResourceLocation.parse(itemIdStr);
            Item item = BuiltInRegistries.ITEM.get(itemId);
            
            if (item == null) {
                ServerShop.LOGGER.warn("Unknown item ID: {}", itemIdStr);
                return null;
            }
            
            ItemStack itemStack = new ItemStack(item, count);
            
            // Deserialize marketplace data
            int buyPrice = itemJson.get("buyPrice").getAsInt();
            int sellPrice = itemJson.get("sellPrice").getAsInt();
            int quantity = itemJson.get("quantity").getAsInt();
            String seller = itemJson.get("seller").getAsString();
            
            return new MarketplaceItem(itemStack, buyPrice, sellPrice, quantity, seller);
            
        } catch (Exception e) {
            ServerShop.LOGGER.error("Failed to deserialize marketplace item: {}", e.getMessage());
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
}
