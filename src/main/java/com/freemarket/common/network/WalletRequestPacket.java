package com.freemarket.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.freemarket.FreeMarket;

/**
 * Network packet for requesting wallet balance from server.
 * Client sends this to get their current balance.
 */
public record WalletRequestPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WalletRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FreeMarket.MODID, "wallet_request"));

    public static final StreamCodec<ByteBuf, WalletRequestPacket> STREAM_CODEC = StreamCodec.unit(new WalletRequestPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
