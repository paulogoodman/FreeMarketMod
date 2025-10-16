package com.freemarket.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.freemarket.FreeMarket;

/**
 * Network packet for requesting to buy an item from the marketplace.
 * Client sends this to server to initiate a purchase.
 */
public record BuyItemRequestPacket(String itemGuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BuyItemRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FreeMarket.MODID, "buy_item_request"));

    public static final StreamCodec<ByteBuf, BuyItemRequestPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        BuyItemRequestPacket::itemGuid,
        BuyItemRequestPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
