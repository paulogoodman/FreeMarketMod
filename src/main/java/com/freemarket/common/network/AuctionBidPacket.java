package com.freemarket.common.network;

import com.freemarket.FreeMarket;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet for placing a bid on an auction.
 * Sent from client to server.
 */
public record AuctionBidPacket(String auctionId, long bidAmount) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<AuctionBidPacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FreeMarket.MODID, "auction_bid"));
    
    public static final StreamCodec<ByteBuf, AuctionBidPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        AuctionBidPacket::auctionId,
        ByteBufCodecs.VAR_LONG,
        AuctionBidPacket::bidAmount,
        AuctionBidPacket::new
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

