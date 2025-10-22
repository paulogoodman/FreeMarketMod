package com.freemarket.common.network;

import com.freemarket.FreeMarket;
import com.freemarket.common.data.PlayerBalanceData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet for syncing leaderboard data from server to client.
 */
public record LeaderboardSyncPacket(List<PlayerBalanceData> leaderboardData) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<LeaderboardSyncPacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FreeMarket.MODID, "leaderboard_sync"));
    
    public static final StreamCodec<ByteBuf, LeaderboardSyncPacket> STREAM_CODEC = StreamCodec.composite(
        // Encode/decode list of player balance data
        ByteBufCodecs.collection(
            ArrayList::new,
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,
                PlayerBalanceData::getUuid,
                ByteBufCodecs.STRING_UTF8,
                PlayerBalanceData::getPlayerName,
                ByteBufCodecs.VAR_LONG,
                PlayerBalanceData::getBalance,
                ByteBufCodecs.VAR_LONG,
                PlayerBalanceData::getLastUpdated,
                (uuid, name, balance, lastUpdated) -> new PlayerBalanceData(uuid, name, balance, lastUpdated)
            )
        ),
        LeaderboardSyncPacket::leaderboardData,
        LeaderboardSyncPacket::new
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

