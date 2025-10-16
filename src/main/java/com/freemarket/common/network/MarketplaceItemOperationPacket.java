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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Network packet for marketplace item operations (add/remove).
 * Contains operation type and item data as JSON string.
 */
public record MarketplaceItemOperationPacket(String operation, String itemDataJson) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MarketplaceItemOperationPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FreeMarket.MODID, "marketplace_item_operation"));

    public static final StreamCodec<ByteBuf, MarketplaceItemOperationPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        MarketplaceItemOperationPacket::operation,
        ByteBufCodecs.STRING_UTF8,
        MarketplaceItemOperationPacket::itemDataJson,
        MarketplaceItemOperationPacket::new
    );

    /**
     * Creates a packet for adding an item to the marketplace.
     */
    public static MarketplaceItemOperationPacket addItem(FreeMarketItem item) {
        String itemDataJson = serializeFreeMarketItem(item);
        return new MarketplaceItemOperationPacket("add", itemDataJson);
    }

    /**
     * Creates a packet for removing an item from the marketplace.
     */
    public static MarketplaceItemOperationPacket removeItem(FreeMarketItem item) {
        String itemDataJson = serializeFreeMarketItem(item);
        return new MarketplaceItemOperationPacket("remove", itemDataJson);
    }

    /**
     * Converts the JSON data back to a FreeMarketItem object.
     */
    public FreeMarketItem toItem() {
        try {
            JsonElement jsonElement = JsonParser.parseString(itemDataJson);
            if (!jsonElement.isJsonObject()) {
                return null;
            }
            return deserializeFreeMarketItem(jsonElement.getAsJsonObject());
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to parse item data from network packet: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Serializes a FreeMarketItem to JSON.
     */
    private static String serializeFreeMarketItem(FreeMarketItem item) {
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

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(itemJson);
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
