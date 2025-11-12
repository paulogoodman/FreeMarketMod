package com.freemarket.server.network;

import com.freemarket.common.data.FreeMarketItem;
import com.freemarket.common.data.FreeMarketItemDTO;
import com.freemarket.common.network.PacketChunking;
import com.freemarket.common.network.PacketType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.google.gson.GsonBuilder;
import java.util.List;

/**
 * Server-side utility for sending marketplace data to clients.
 */
public class ServerMarketplaceSync {
    
    /**
     * Sends marketplace data to all players in the specified level.
     * Automatically handles chunking for large payloads.
     * @param level the server level
     * @param items the marketplace items to sync
     */
    public static void syncToAllPlayers(ServerLevel level, List<FreeMarketItem> items) {
        // Convert FreeMarketItem objects to DTOs for serialization
        List<FreeMarketItemDTO> dtos = items.stream()
            .map(FreeMarketItemDTO::new)
            .collect(java.util.stream.Collectors.toList());
        
        String jsonData = new GsonBuilder().create().toJson(dtos);
        PacketChunking.sendToAllPlayersWithChunking(PacketType.MARKETPLACE_SYNC, jsonData);
    }
    
    /**
     * Sends marketplace data to a specific player.
     * Automatically handles chunking for large payloads.
     * @param player the target player
     * @param items the marketplace items to sync
     */
    public static void syncToPlayer(ServerPlayer player, List<FreeMarketItem> items) {
        // Convert FreeMarketItem objects to DTOs for serialization
        List<FreeMarketItemDTO> dtos = items.stream()
            .map(FreeMarketItemDTO::new)
            .collect(java.util.stream.Collectors.toList());
        
        String jsonData = new GsonBuilder().create().toJson(dtos);
        PacketChunking.sendToPlayerWithChunking(player, PacketType.MARKETPLACE_SYNC, jsonData);
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
