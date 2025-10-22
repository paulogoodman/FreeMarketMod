package com.freemarket.common.network;

import com.freemarket.FreeMarket;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet for requesting leaderboard data from the server.
 * Sent from client to server when opening the leaderboard screen.
 */
public record LeaderboardRequestPacket() implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<LeaderboardRequestPacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FreeMarket.MODID, "leaderboard_request"));
    
    public static final StreamCodec<ByteBuf, LeaderboardRequestPacket> STREAM_CODEC = StreamCodec.unit(new LeaderboardRequestPacket());
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

