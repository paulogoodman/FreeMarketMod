package com.freemarket.common.network;

import com.freemarket.FreeMarket;
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
     */
    @Override
    public Type<? extends CustomPacketPayload> type() {
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
     * Used for item GUID-based operations like buy/sell.
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

