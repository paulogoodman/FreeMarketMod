package com.freemarket.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.freemarket.FreeMarket;

/**
 * Network packet for sell item response from server to client.
 * Contains success status and updated balance.
 */
public record SellItemResponsePacket(boolean success, String message, long newBalance) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SellItemResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FreeMarket.MODID, "sell_item_response"));

    public static final StreamCodec<ByteBuf, SellItemResponsePacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        SellItemResponsePacket::success,
        ByteBufCodecs.STRING_UTF8,
        SellItemResponsePacket::message,
        ByteBufCodecs.VAR_LONG,
        SellItemResponsePacket::newBalance,
        SellItemResponsePacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
