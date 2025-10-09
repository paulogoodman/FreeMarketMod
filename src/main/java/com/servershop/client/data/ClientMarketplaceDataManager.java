package com.servershop.client.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.servershop.ServerShop;
import com.servershop.common.data.MarketplaceItem;
import com.servershop.common.attachments.ItemComponentHandler;

/**
 * Client-side marketplace data manager for reading marketplace data from world files.
 * This allows the client to load marketplace items from the JSON file in the world data directory.
 */
public class ClientMarketplaceDataManager {
    
    private static final String MARKETPLACE_FILE_NAME = "marketplace.json";
    
    /**
     * Loads marketplace items from the current world's JSON file.
     * Returns empty list if file doesn't exist or is invalid.
     */
    public static List<MarketplaceItem> loadMarketplaceItems() {
        List<MarketplaceItem> items = new ArrayList<>();
        
        try {
            Minecraft minecraft = Minecraft.getInstance();
            var level = minecraft.level;
            if (level == null) {
                ServerShop.LOGGER.warn("No world loaded - cannot load marketplace data");
                return items; // No world loaded
            }
            
            if (level.dimension() == null) {
                ServerShop.LOGGER.warn("No dimension loaded - cannot load marketplace data");
                return items;
            }
            ServerShop.LOGGER.debug("Attempting to load marketplace data...");
            ServerShop.LOGGER.debug("Singleplayer server available: {}", minecraft.getSingleplayerServer() != null);
            ServerShop.LOGGER.debug("Level server available: {}", level != null && level.getServer() != null);
            
            // Get the world data directory - use integrated server's world path directly
            Path marketplaceFile = null;
            
            // Try to get the marketplace file path directly from the integrated server
            var singleplayerServer = minecraft.getSingleplayerServer();
            if (singleplayerServer != null) {
                // Use the server's world path directly - this is the most reliable method
                Path worldDataPath = singleplayerServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                marketplaceFile = worldDataPath.resolve("data").resolve(MARKETPLACE_FILE_NAME);
                ServerShop.LOGGER.info("Using integrated server world path: {}", worldDataPath);
            } else {
                // Fallback: try to get world path from level data
                try {
                    if (level != null) {
                        var levelServer = level.getServer();
                        if (levelServer != null) {
                            Path worldDataPath = levelServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                            marketplaceFile = worldDataPath.resolve("data").resolve(MARKETPLACE_FILE_NAME);
                            ServerShop.LOGGER.info("Using level server world path: {}", worldDataPath);
                        }
                    }
                } catch (Exception e) {
                    ServerShop.LOGGER.error("Could not determine world path from level server: {}", e.getMessage());
                }
            }
            
            // If we still can't get the world path, we can't proceed
            if (marketplaceFile == null) {
                ServerShop.LOGGER.error("Could not determine current world path - cannot load marketplace data");
                return items;
            }
            
            ServerShop.LOGGER.info("Loading marketplace from: {}", marketplaceFile);
            
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
            ServerShop.LOGGER.error("Failed to load marketplace items from world", e);
        }
        
        return items;
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
            
            if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
                ServerShop.LOGGER.warn("Unknown item ID: {}", itemIdStr);
                return null;
            }
            
            ItemStack itemStack = new ItemStack(item, count);
            
            // Deserialize component data if present
            if (itemJson.has("componentData")) {
                try {
                    String componentDataString = itemJson.get("componentData").getAsString();
                    if (!componentDataString.isEmpty()) {
                        // Try to use server-side processing for proper registry access
                        Minecraft minecraft = Minecraft.getInstance();
                        var singleplayerServer = minecraft.getSingleplayerServer();
                        
                        if (singleplayerServer != null) {
                            // Use server-side handler with registry access
                            itemStack = com.servershop.server.handlers.ServerItemHandler.createItemWithComponentData(
                                itemStack, componentDataString, singleplayerServer);
                        } else {
                            ServerShop.LOGGER.warn("ClientMarketplaceDataManager: No server available, skipping component data application");
                            ServerShop.LOGGER.warn("Component data will be applied when item is purchased");
                        }
                    }
                } catch (Exception e) {
                    ServerShop.LOGGER.warn("Failed to deserialize component data for item: {}", e.getMessage());
                }
            }
            
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

            return new MarketplaceItem(itemStack, buyPrice, sellPrice, quantity, seller, guid, componentData);
            
        } catch (Exception e) {
            ServerShop.LOGGER.error("Failed to deserialize marketplace item: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Adds a new marketplace item to the JSON file.
     * This is a client-side operation for admin mode.
     */
    public static void addMarketplaceItem(MarketplaceItem item) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            var level = minecraft.level;
            if (level == null) {
                ServerShop.LOGGER.warn("No world loaded - cannot add marketplace item");
                return;
            }
            
            if (level.dimension() == null) {
                ServerShop.LOGGER.warn("No dimension loaded - cannot add marketplace item");
                return;
            }
            
            // Get the world data directory - use integrated server's world path directly
            Path marketplaceFile = null;
            
            // Try to get the marketplace file path directly from the integrated server
            var singleplayerServer = minecraft.getSingleplayerServer();
            if (singleplayerServer != null) {
                // Use the server's world path directly - this is the most reliable method
                Path worldDataPath = singleplayerServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                marketplaceFile = worldDataPath.resolve("data").resolve(MARKETPLACE_FILE_NAME);
            } else {
                // Fallback: try to get world path from level data
                try {
                    if (level != null) {
                        var levelServer = level.getServer();
                        if (levelServer != null) {
                            Path worldDataPath = levelServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                            marketplaceFile = worldDataPath.resolve("data").resolve(MARKETPLACE_FILE_NAME);
                        }
                    }
                } catch (Exception e) {
                    ServerShop.LOGGER.error("Could not determine world path from level server: {}", e.getMessage());
                }
            }
            
            // If we still can't get the world path, we can't proceed
            if (marketplaceFile == null) {
                ServerShop.LOGGER.error("Could not determine current world path - cannot add marketplace item");
                return;
            }
            
            // Load existing items
            List<MarketplaceItem> existingItems = loadMarketplaceItems();
            
            // Add new item
            existingItems.add(item);
            
            // Save back to file
            saveMarketplaceItems(marketplaceFile, existingItems);
            
            ServerShop.LOGGER.info("Added marketplace item: {}", item.getItemName());
            
        } catch (Exception e) {
            ServerShop.LOGGER.error("Failed to add marketplace item", e);
        }
    }
    
    /**
     * Saves marketplace items to the JSON file.
     */
    private static void saveMarketplaceItems(Path marketplaceFile, List<MarketplaceItem> items) {
        try {
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
            try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
                gson.toJson(marketplaceData, writer);
            }
            
            ServerShop.LOGGER.info("Saved {} marketplace items to {}", items.size(), marketplaceFile);
            
        } catch (Exception e) {
            ServerShop.LOGGER.error("Failed to save marketplace items", e);
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
        
        // Serialize component data (use stored component data from MarketplaceItem)
        String componentData = item.getComponentData();
        ServerShop.LOGGER.info("Serializing component data for item {}: {}", itemStack.getItem().getDescription().getString(), componentData);
        itemJson.addProperty("componentData", componentData);
        
        // Serialize marketplace data
        itemJson.addProperty("buyPrice", item.getBuyPrice());
        itemJson.addProperty("sellPrice", item.getSellPrice());
        itemJson.addProperty("quantity", item.getQuantity());
        itemJson.addProperty("seller", item.getSeller());
        itemJson.addProperty("guid", item.getGuid());
        
        return itemJson;
    }
    
        /**
         * Removes a marketplace item from the JSON file.
         * This is a client-side operation for admin mode.
         */
        public static void removeMarketplaceItem(MarketplaceItem itemToRemove) {
            try {
                Minecraft minecraft = Minecraft.getInstance();
                var level = minecraft.level;
                if (level == null) {
                    ServerShop.LOGGER.warn("No world loaded - cannot remove marketplace item");
                    return;
                }

                if (level.dimension() == null) {
                    ServerShop.LOGGER.warn("No dimension loaded - cannot remove marketplace item");
                    return;
                }

                // Get the world data directory - use integrated server's world path directly
                Path marketplaceFile = null;

                // Try to get the marketplace file path directly from the integrated server
                var singleplayerServer = minecraft.getSingleplayerServer();
                if (singleplayerServer != null) {
                    // Use the server's world path directly - this is the most reliable method
                    Path worldDataPath = singleplayerServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                    marketplaceFile = worldDataPath.resolve("data").resolve(MARKETPLACE_FILE_NAME);
                } else {
                    // Fallback: try to get world path from level data
                    try {
                        if (level != null) {
                            var levelServer = level.getServer();
                            if (levelServer != null) {
                                Path worldDataPath = levelServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                                marketplaceFile = worldDataPath.resolve("data").resolve(MARKETPLACE_FILE_NAME);
                            }
                        }
                    } catch (Exception e) {
                        ServerShop.LOGGER.error("Could not determine world path from level server: {}", e.getMessage());
                    }
                }

                // If we still can't get the world path, we can't proceed
                if (marketplaceFile == null) {
                    ServerShop.LOGGER.error("Could not determine current world path - cannot remove marketplace item");
                    return;
                }

                // Load existing items
                List<MarketplaceItem> existingItems = loadMarketplaceItems();

                // Remove the item by GUID (exact match)
                boolean removed = existingItems.removeIf(item -> 
                    item.getGuid().equals(itemToRemove.getGuid())
                );

                if (removed) {
                    // Save back to file
                    saveMarketplaceItems(marketplaceFile, existingItems);
                    ServerShop.LOGGER.info("Removed marketplace item: {}", itemToRemove.getItemName());
                } else {
                    ServerShop.LOGGER.warn("Could not find marketplace item to remove: {}", itemToRemove.getItemName());
                }

            } catch (Exception e) {
                ServerShop.LOGGER.error("Failed to remove marketplace item", e);
            }
        }
    public static boolean marketplaceFileExists() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            var level = minecraft.level;
            if (level == null) {
                return false; // No world loaded
            }
            
            if (level.dimension() == null) {
                return false; // No dimension loaded
            }
            
            // Get the world data directory - use integrated server's world path directly
            Path marketplaceFile = null;
            
            // Try to get the marketplace file path directly from the integrated server
            var singleplayerServer = minecraft.getSingleplayerServer();
            if (singleplayerServer != null) {
                // Use the server's world path directly - this is the most reliable method
                Path worldDataPath = singleplayerServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                marketplaceFile = worldDataPath.resolve("data").resolve(MARKETPLACE_FILE_NAME);
            } else {
                // Fallback: try to get world path from level data
                try {
                    if (level != null) {
                        var levelServer = level.getServer();
                        if (levelServer != null) {
                            Path worldDataPath = levelServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                            marketplaceFile = worldDataPath.resolve("data").resolve(MARKETPLACE_FILE_NAME);
                        }
                    }
                } catch (Exception e) {
                    ServerShop.LOGGER.error("Could not determine world path from level server: {}", e.getMessage());
                }
            }
            
            // If we still can't get the world path, we can't proceed
            if (marketplaceFile == null) {
                ServerShop.LOGGER.error("Could not determine current world path - cannot check marketplace file");
                return false;
            }
            
            return marketplaceFile.toFile().exists();
            
        } catch (Exception e) {
            ServerShop.LOGGER.error("Failed to check marketplace file existence", e);
            return false;
        }
    }
}
