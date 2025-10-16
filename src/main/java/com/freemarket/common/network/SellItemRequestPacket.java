package com.freemarket.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.freemarket.FreeMarket;

/**
 * Network packet for requesting to sell an item to the marketplace.
 * Client sends this to server to initiate a sale.
 */
public record SellItemRequestPacket(String itemGuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SellItemRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FreeMarket.MODID, "sell_item_request"));

    public static final StreamCodec<ByteBuf, SellItemRequestPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        SellItemRequestPacket::itemGuid,
        SellItemRequestPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
