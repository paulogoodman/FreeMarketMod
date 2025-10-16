package com.freemarket.server.network;

import com.freemarket.common.data.FreeMarketItem;
import com.freemarket.common.network.MarketplaceSyncPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Server-side utility for sending marketplace data to clients.
 */
public class ServerMarketplaceSync {
    
    /**
     * Sends marketplace data to all players in the specified level.
     * @param level the server level
     * @param items the marketplace items to sync
     */
    public static void syncToAllPlayers(ServerLevel level, List<FreeMarketItem> items) {
        MarketplaceSyncPacket packet = MarketplaceSyncPacket.fromItems(items);
        net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(packet);
    }
    
    /**
     * Sends marketplace data to a specific player.
     * @param player the target player
     * @param items the marketplace items to sync
     */
    public static void syncToPlayer(ServerPlayer player, List<FreeMarketItem> items) {
        MarketplaceSyncPacket packet = MarketplaceSyncPacket.fromItems(items);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet);
    }
    
    /**
     * Sends marketplace data to all players in the level after loading from file.
     * @param level the server level
     */
    public static void syncMarketplaceData(ServerLevel level) {
        List<FreeMarketItem> items = com.freemarket.server.data.FreeMarketDataManager.loadFreeMarketItems(level);
        syncToAllPlayers(level, items);
    }
}
