package com.freemarket.common.network;

import com.freemarket.FreeMarket;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet for creating a new auction.
 * Sent from client to server.
 */
public record AuctionCreatePacket(String itemId, String componentData, int quantity, long startingPrice, long durationMinutes) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<AuctionCreatePacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FreeMarket.MODID, "auction_create"));
    
    public static final StreamCodec<ByteBuf, AuctionCreatePacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        AuctionCreatePacket::itemId,
        ByteBufCodecs.STRING_UTF8,
        AuctionCreatePacket::componentData,
        ByteBufCodecs.VAR_INT,
        AuctionCreatePacket::quantity,
        ByteBufCodecs.VAR_LONG,
        AuctionCreatePacket::startingPrice,
        ByteBufCodecs.VAR_LONG,
        AuctionCreatePacket::durationMinutes,
        AuctionCreatePacket::new
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

