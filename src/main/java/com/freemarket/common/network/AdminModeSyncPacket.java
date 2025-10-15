package com.freemarket.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.freemarket.FreeMarket;

/**
 * Network packet for synchronizing admin mode state between server and clients.
 * Sent from server to all clients when admin mode is enabled/disabled.
 */
public record AdminModeSyncPacket(boolean adminMode) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<AdminModeSyncPacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FreeMarket.MODID, "admin_mode_sync"));
    
    public static final StreamCodec<ByteBuf, AdminModeSyncPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        AdminModeSyncPacket::adminMode,
        AdminModeSyncPacket::new
    );
    
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
