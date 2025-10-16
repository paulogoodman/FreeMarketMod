package com.freemarket.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.freemarket.FreeMarket;

/**
 * Network packet for synchronizing wallet balance between server and client.
 * Contains player UUID and current balance.
 */
public record WalletSyncPacket(String playerUuid, long balance) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WalletSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FreeMarket.MODID, "wallet_sync"));

    public static final StreamCodec<ByteBuf, WalletSyncPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        WalletSyncPacket::playerUuid,
        ByteBufCodecs.VAR_LONG,
        WalletSyncPacket::balance,
        WalletSyncPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
