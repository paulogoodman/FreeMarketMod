package com.freemarket.common.network;

import com.freemarket.FreeMarket;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet for requesting auction data from the server.
 * Sent from client to server when opening the auction screen.
 */
public record AuctionRequestPacket() implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<AuctionRequestPacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FreeMarket.MODID, "auction_request"));
    
    public static final StreamCodec<ByteBuf, AuctionRequestPacket> STREAM_CODEC = StreamCodec.unit(new AuctionRequestPacket());
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

