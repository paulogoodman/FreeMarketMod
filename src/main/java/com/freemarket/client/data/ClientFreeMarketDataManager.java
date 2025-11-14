package com.freemarket.client.data;

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

import com.freemarket.FreeMarket;
import com.freemarket.common.data.FreeMarketItem;

/**
 * Client-side marketplace data manager for reading marketplace data from world files.
 * This allows the client to load marketplace items from the JSON file in the world data directory.
 * Includes caching to reduce frequent file reads.
 */
public class ClientFreeMarketDataManager {
    
    private static final String MARKETPLACE_FILE_NAME = "marketplace.json";
    
    // Cache for marketplace items to reduce file reads
    private static List<FreeMarketItem> cachedItems = null;
    private static long lastCacheUpdate = 0;
    private static String lastWorldPath = null;
    private static final long CACHE_DURATION_MS = 5000; // Cache for 5 seconds
    
    /**
     * Loads marketplace items from the current world's JSON file with caching.
     * Returns cached data if available and recent, otherwise reads from file.
     */
    public static List<FreeMarketItem> loadFreeMarketItems() {
        // Get current world path for cache validation
        String currentWorldPath = getCurrentWorldPath();
        if (currentWorldPath == null) {
            return new ArrayList<>();
        }
        
        // Check if cache is still valid
        long currentTime = System.currentTimeMillis();
        if (cachedItems != null && 
            currentTime - lastCacheUpdate < CACHE_DURATION_MS && 
            currentWorldPath.equals(lastWorldPath)) {
            return new ArrayList<>(cachedItems); // Return copy to prevent external modification
        }
        
        // Cache is invalid or expired, reload from file
        List<FreeMarketItem> items = loadFreeMarketItemsFromFile();
        
        // Update cache
        cachedItems = new ArrayList<>(items);
        lastCacheUpdate = currentTime;
        lastWorldPath = currentWorldPath;
        
        return items;
    }
    
    /**
     * Forces a cache refresh by clearing the cache.
     * Call this after marketplace modifications to ensure fresh data.
     */
    public static void invalidateCache() {
        cachedItems = null;
        lastCacheUpdate = 0;
        lastWorldPath = null;
    }
    
    /**
     * Gets the current world path for cache validation.
     */
    private static String getCurrentWorldPath() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            var level = minecraft.level;
            if (level == null) {
                return null;
            }
            
            if (level.dimension() == null) {
                return null;
            }
            
            // Try to get the marketplace file path directly from the integrated server
            var singleplayerServer = minecraft.getSingleplayerServer();
            if (singleplayerServer != null) {
                Path worldDataPath = singleplayerServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                return worldDataPath.toString();
            } else {
                // Fallback: try to get world path from level data
                if (level != null) {
                    var levelServer = level.getServer();
                    if (levelServer != null) {
                        Path worldDataPath = levelServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                        return worldDataPath.toString();
                    }
                }
            }
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Could not determine current world path: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Loads marketplace items from the current world's JSON file (without caching).
     * This is the actual file reading implementation.
     */
    private static List<FreeMarketItem> loadFreeMarketItemsFromFile() {
        List<FreeMarketItem> items = new ArrayList<>();
        
        try {
            Minecraft minecraft = Minecraft.getInstance();
            var level = minecraft.level;
            if (level == null) {
                return items; // No world loaded
            }
            
            if (level.dimension() == null) {
                return items;
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
                    // Fallback failed
                }
            }
            
            // If we still can't get the world path, we can't proceed
            if (marketplaceFile == null) {
                return items;
            }
            
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
            FreeMarket.LOGGER.error("Failed to load marketplace items from world", e);
        }
        
        return items;
    }
    
    /**
     * Deserializes a FreeMarketItem from JSON.
     */
    private static FreeMarketItem deserializeFreeMarketItem(JsonObject itemJson) {
        try {
            // Validate required field: itemId
            if (!itemJson.has("itemId") || itemJson.get("itemId").isJsonNull()) {
                FreeMarket.LOGGER.warn("Missing required field 'itemId' in marketplace item");
                return null;
            }
            
            // Deserialize ItemStack with proper error handling
            String itemIdStr;
            try {
                itemIdStr = itemJson.get("itemId").getAsString();
                if (itemIdStr == null || itemIdStr.isEmpty()) {
                    FreeMarket.LOGGER.warn("Empty 'itemId' in marketplace item");
                    return null;
                }
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Invalid 'itemId' type in marketplace item: {}", e.getMessage());
                return null;
            }
            
            // Support both old "count" and new "stackSize" for backward compatibility
            int stackSize = 1;
            try {
                if (itemJson.has("stackSize") && !itemJson.get("stackSize").isJsonNull()) {
                    stackSize = itemJson.get("stackSize").getAsInt();
                } else if (itemJson.has("count") && !itemJson.get("count").isJsonNull()) {
                    stackSize = itemJson.get("count").getAsInt();
                }
                // Validate stackSize range
                if (stackSize < 1) {
                    FreeMarket.LOGGER.warn("Invalid stackSize ({}), using 1", stackSize);
                    stackSize = 1;
                } else if (stackSize > 64) {
                    FreeMarket.LOGGER.warn("StackSize ({}) exceeds maximum (64), capping to 64", stackSize);
                    stackSize = 64;
                }
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Invalid stackSize/count type: {}, using default 1", e.getMessage());
                stackSize = 1;
            }
            
            ResourceLocation itemId;
            try {
                itemId = ResourceLocation.parse(itemIdStr);
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Invalid itemId format '{}': {}", itemIdStr, e.getMessage());
                return null;
            }
            
            if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
                FreeMarket.LOGGER.warn("Unknown item ID '{}' in marketplace item", itemIdStr);
                return null;
            }
            
            Item item = BuiltInRegistries.ITEM.get(itemId);
            // Clamp stackSize to item's max stack size
            ItemStack tempStack = new ItemStack(item, 1);
            int maxStackSize = tempStack.getMaxStackSize();
            if (stackSize > maxStackSize) {
                FreeMarket.LOGGER.warn("StackSize ({}) exceeds item's max stack size ({}), capping to {}", 
                    stackSize, maxStackSize, maxStackSize);
                stackSize = maxStackSize;
            }
            ItemStack itemStack = new ItemStack(item, stackSize);
            
            // Deserialize component data if present
            if (itemJson.has("componentData") && !itemJson.get("componentData").isJsonNull()) {
                try {
                    String componentDataString = itemJson.get("componentData").getAsString();
                    if (componentDataString != null && !componentDataString.isEmpty()) {
                        // Try to use server-side processing for proper registry access
                        Minecraft minecraft = Minecraft.getInstance();
                        var singleplayerServer = minecraft.getSingleplayerServer();
                        
                        if (singleplayerServer != null) {
                            // Use server-side handler with registry access
                            itemStack = com.freemarket.server.handlers.ServerItemHandler.createItemWithComponentData(
                                itemStack, componentDataString, singleplayerServer);
                        } else {
                            // No server available, component data will be applied when item is purchased
                        }
                    }
                } catch (Exception e) {
                    FreeMarket.LOGGER.warn("Failed to deserialize component data: {}", e.getMessage());
                }
            }
            
            // Deserialize marketplace data with proper error handling
            int buyPrice = 0;
            int sellPrice = 0;
            try {
                if (itemJson.has("buyPrice") && !itemJson.get("buyPrice").isJsonNull()) {
                    buyPrice = itemJson.get("buyPrice").getAsInt();
                    if (buyPrice < 0) {
                        FreeMarket.LOGGER.warn("Negative buyPrice, setting to 0");
                        buyPrice = 0;
                    }
                }
                if (itemJson.has("sellPrice") && !itemJson.get("sellPrice").isJsonNull()) {
                    sellPrice = itemJson.get("sellPrice").getAsInt();
                    if (sellPrice < 0) {
                        FreeMarket.LOGGER.warn("Negative sellPrice, setting to 0");
                        sellPrice = 0;
                    }
                }
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Invalid buyPrice/sellPrice type: {}", e.getMessage());
            }
            // Support both old "quantity" and new "totalStockAvailable" for backward compatibility
            Integer totalStockAvailable = null;
            try {
                if (itemJson.has("totalStockAvailable") && !itemJson.get("totalStockAvailable").isJsonNull()) {
                    totalStockAvailable = itemJson.get("totalStockAvailable").getAsInt();
                    if (totalStockAvailable < 0) {
                        FreeMarket.LOGGER.warn("Negative totalStockAvailable, ignoring");
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
                FreeMarket.LOGGER.warn("Invalid totalStockAvailable/quantity type: {}", e.getMessage());
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
                FreeMarket.LOGGER.warn("Invalid marketListingId/guid type: {}", e.getMessage());
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
                FreeMarket.LOGGER.warn("Invalid componentData type: {}, using default", e.getMessage());
            }

            // If market listing ID is missing or empty, generate a random one
            if (marketListingId == null || marketListingId.isEmpty()) {
                marketListingId = java.util.UUID.randomUUID().toString();
            }

            return new FreeMarketItem(itemStack, buyPrice, sellPrice, stackSize, totalStockAvailable, marketListingId, componentData, Integer.MAX_VALUE);
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to deserialize marketplace item: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Adds a new marketplace item to the JSON file.
     * This is a client-side operation for admin mode.
     */
    public static void addFreeMarketItem(FreeMarketItem item) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            var level = minecraft.level;
            if (level == null) {
                return;
            }
            
            if (level.dimension() == null) {
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
                    FreeMarket.LOGGER.error("Could not determine world path from level server: {}", e.getMessage());
                }
            }
            
            // If we still can't get the world path, we can't proceed
            if (marketplaceFile == null) {
                FreeMarket.LOGGER.error("Could not determine current world path - cannot add marketplace item");
                return;
            }
            
            // Load existing items
            List<FreeMarketItem> existingItems = loadFreeMarketItems();
            
            // Add new item
            existingItems.add(item);
            
            // Save back to file
            saveFreeMarketItems(marketplaceFile, existingItems);
            
            // Invalidate cache since we modified the marketplace
            invalidateCache();
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to add marketplace item", e);
        }
    }
    
    /**
     * Saves marketplace items to the JSON file.
     */
    private static void saveFreeMarketItems(Path marketplaceFile, List<FreeMarketItem> items) {
        try {
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
            try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
                gson.toJson(marketplaceData, writer);
            }
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to save marketplace items", e);
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
        itemJson.addProperty("stackSize", item.getStackSize());
        
        // Serialize component data (use stored component data from FreeMarketItem)
        String componentData = item.getComponentData();
        itemJson.addProperty("componentData", componentData);
        
        // Serialize marketplace data
        itemJson.addProperty("buyPrice", item.getBuyPrice());
        itemJson.addProperty("sellPrice", item.getSellPrice());
        if (item.getTotalStockAvailable() != null) {
            itemJson.addProperty("totalStockAvailable", item.getTotalStockAvailable());
        }
        itemJson.addProperty("marketListingId", item.getMarketListingId());
        
        return itemJson;
    }
    
        /**
         * Removes a marketplace item from the JSON file.
         * This is a client-side operation for admin mode.
         */
        public static void removeFreeMarketItem(FreeMarketItem itemToRemove) {
            try {
                Minecraft minecraft = Minecraft.getInstance();
                var level = minecraft.level;
                if (level == null) {
                    return;
                }

                if (level.dimension() == null) {
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
                        FreeMarket.LOGGER.error("Could not determine world path from level server: {}", e.getMessage());
                    }
                }

                // If we still can't get the world path, we can't proceed
                if (marketplaceFile == null) {
                    FreeMarket.LOGGER.error("Could not determine current world path - cannot remove marketplace item");
                    return;
                }

                // Load existing items
                List<FreeMarketItem> existingItems = loadFreeMarketItems();

                // Remove the item by market listing ID (exact match)
                boolean removed = existingItems.removeIf(item -> 
                    item.getMarketListingId().equals(itemToRemove.getMarketListingId())
                );

                if (removed) {
                    // Save back to file
                    saveFreeMarketItems(marketplaceFile, existingItems);
                    
                    // Invalidate cache since we modified the marketplace
                    invalidateCache();
                    
                } else {
                    // Could not find marketplace item to remove
                }

            } catch (Exception e) {
                FreeMarket.LOGGER.error("Failed to remove marketplace item", e);
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
                    FreeMarket.LOGGER.error("Could not determine world path from level server: {}", e.getMessage());
                }
            }
            
            // If we still can't get the world path, we can't proceed
            if (marketplaceFile == null) {
                FreeMarket.LOGGER.error("Could not determine current world path - cannot check marketplace file");
                return false;
            }
            
            return marketplaceFile.toFile().exists();
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to check marketplace file existence", e);
            return false;
        }
    }
}
