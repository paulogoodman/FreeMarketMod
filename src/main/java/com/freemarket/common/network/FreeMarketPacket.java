package com.freemarket.common.network;

import com.freemarket.FreeMarket;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;

/**
 * Unified packet for all FreeMarket network communication.
 * Uses a discriminator-based approach to handle multiple packet types with a single class.
 * 
 * <p><b>Design Philosophy:</b>
 * Instead of creating 15+ separate packet classes, this single packet handles all communication
 * by using a {@link PacketType} discriminator and a JSON data payload. This approach is:
 * <ul>
 *   <li>Industry standard in Minecraft modding</li>
 *   <li>Much easier to maintain</li>
 *   <li>Reduces code duplication by ~90%</li>
 *   <li>Simplifies packet registration</li>
 * </ul>
 * 
 * <p><b>Security:</b>
 * The packet handler validates all data server-side. The data payload is only used as an
 * identifier or request parameter - actual game state (prices, balances, inventory) is always
 * loaded from server-authoritative DataManagers.
 * 
 * @param packetType The packet discriminator indicating what action to perform
 * @param data JSON string containing the packet's data payload (empty string for requests with no data)
 */
public record FreeMarketPacket(PacketType packetType, String data) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<FreeMarketPacket> CLIENT_TO_SERVER_TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FreeMarket.MODID, "client_to_server"));
    
    public static final CustomPacketPayload.Type<FreeMarketPacket> SERVER_TO_CLIENT_TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FreeMarket.MODID, "server_to_client"));
    
    // Custom StreamCodec for PacketType enum
    private static final StreamCodec<ByteBuf, PacketType> PACKET_TYPE_CODEC = new StreamCodec<>() {
        @Override
        public PacketType decode(@Nonnull ByteBuf buf) {
            int ordinal = ByteBufCodecs.VAR_INT.decode(buf);
            return PacketType.values()[ordinal];
        }
        
        @Override
        public void encode(@Nonnull ByteBuf buf, @Nonnull PacketType type) {
            ByteBufCodecs.VAR_INT.encode(buf, type.ordinal());
        }
    };
    
    public static final StreamCodec<ByteBuf, FreeMarketPacket> STREAM_CODEC = StreamCodec.composite(
        PACKET_TYPE_CODEC,
        FreeMarketPacket::packetType,
        ByteBufCodecs.STRING_UTF8,
        FreeMarketPacket::data,
        FreeMarketPacket::new
    );
    
    /**
     * Returns the appropriate packet type based on the packet direction.
     * Client-to-server packets use CLIENT_TO_SERVER_TYPE, server-to-client use SERVER_TO_CLIENT_TYPE.
     * 
     * For chunk packets (CHUNK_START, CHUNK_DATA, CHUNK_END), the direction is determined
     * from the original packet type stored in the JSON data, not from the chunk type itself.
     */
    @Override
    public Type<? extends CustomPacketPayload> type() {
        // For chunk packets, determine direction from the original packet type in the JSON data
        if (packetType == PacketType.CHUNK_START || packetType == PacketType.CHUNK_DATA || packetType == PacketType.CHUNK_END) {
            try {
                if (data != null && !data.isEmpty()) {
                    JsonObject json = JsonParser.parseString(data).getAsJsonObject();
                    if (json.has("originalType")) {
                        String originalTypeName = json.get("originalType").getAsString();
                        PacketType originalType = PacketType.valueOf(originalTypeName);
                        // Use the original packet type's direction
                        return originalType.isClientToServer() ? CLIENT_TO_SERVER_TYPE : SERVER_TO_CLIENT_TYPE;
                    }
                }
            } catch (Exception e) {
                // If parsing fails, fall back to chunk type's direction (though this shouldn't happen)
                FreeMarket.LOGGER.warn("Failed to parse chunk packet JSON to determine direction: {}", e.getMessage());
            }
        }
        
        // For non-chunk packets, use the packet type's direction directly
        return packetType.isClientToServer() ? CLIENT_TO_SERVER_TYPE : SERVER_TO_CLIENT_TYPE;
    }
    
    /**
     * Creates an empty request packet (no data payload).
     * Used for simple requests like wallet balance, auction list, leaderboard data.
     */
    public static FreeMarketPacket emptyRequest(PacketType packetType) {
        return new FreeMarketPacket(packetType, "");
    }
    
    /**
     * Creates a packet with a simple string parameter.
     * Used for item market listing ID-based operations like buy/sell.
     */
    public static FreeMarketPacket withString(PacketType packetType, String value) {
        return new FreeMarketPacket(packetType, value);
    }
    
    /**
     * Creates a packet with JSON data.
     * Used for complex data structures like auction creation, sync packets, etc.
     */
    public static FreeMarketPacket withJson(PacketType packetType, String jsonData) {
        return new FreeMarketPacket(packetType, jsonData);
    }
    
}

