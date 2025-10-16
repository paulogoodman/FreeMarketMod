package com.freemarket.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.freemarket.FreeMarket;
import com.freemarket.common.data.FreeMarketItem;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Network packet for synchronizing marketplace data between server and clients.
 * Contains the marketplace data as a JSON string for simplicity.
 */
public record MarketplaceSyncPacket(String marketplaceDataJson) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<MarketplaceSyncPacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FreeMarket.MODID, "marketplace_sync"));
    
    public static final StreamCodec<ByteBuf, MarketplaceSyncPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        MarketplaceSyncPacket::marketplaceDataJson,
        MarketplaceSyncPacket::new
    );
    
    /**
     * Creates a MarketplaceSyncPacket from a list of FreeMarketItem objects.
     */
    public static MarketplaceSyncPacket fromItems(List<FreeMarketItem> items) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
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
        
        return new MarketplaceSyncPacket(gson.toJson(marketplaceData));
    }
    
    /**
     * Converts the JSON data back to a list of FreeMarketItem objects.
     */
    public List<FreeMarketItem> toItems() {
        List<FreeMarketItem> items = new ArrayList<>();
        
        try {
            JsonElement jsonElement = JsonParser.parseString(marketplaceDataJson);
            
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
            FreeMarket.LOGGER.error("Failed to parse marketplace data from network packet: {}", e.getMessage());
        }
        
        return items;
    }
    
    /**
     * Serializes a FreeMarketItem to JSON.
     */
    private static JsonObject serializeFreeMarketItem(FreeMarketItem item) {
        JsonObject itemJson = new JsonObject();
        
        // Serialize ItemStack
        itemJson.addProperty("itemId", BuiltInRegistries.ITEM.getKey(item.getItemStack().getItem()).toString());
        itemJson.addProperty("count", item.getItemStack().getCount());
        
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
            long buyPrice = itemJson.get("buyPrice").getAsLong();
            long sellPrice = itemJson.get("sellPrice").getAsLong();
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
            FreeMarket.LOGGER.error("Failed to deserialize marketplace item from network packet: {}", e.getMessage());
            return null;
        }
    }
    
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
