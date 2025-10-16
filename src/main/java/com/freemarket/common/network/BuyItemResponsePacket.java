package com.freemarket.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.freemarket.FreeMarket;

/**
 * Network packet for buy item response from server to client.
 * Contains success status and updated balance.
 */
public record BuyItemResponsePacket(boolean success, String message, long newBalance) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BuyItemResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FreeMarket.MODID, "buy_item_response"));

    public static final StreamCodec<ByteBuf, BuyItemResponsePacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        BuyItemResponsePacket::success,
        ByteBufCodecs.STRING_UTF8,
        BuyItemResponsePacket::message,
        ByteBufCodecs.VAR_LONG,
        BuyItemResponsePacket::newBalance,
        BuyItemResponsePacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
