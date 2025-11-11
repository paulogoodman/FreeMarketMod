package com.freemarket.server.network;

import com.freemarket.FreeMarket;
import com.freemarket.common.data.PlayerAuction;
import com.freemarket.common.network.FreeMarketPacket;
import com.freemarket.common.network.PacketType;
import com.freemarket.server.data.AuctionDataManager;
import com.google.gson.Gson;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Server-side utility for sending auction data to clients.
 * Ensures reliable broadcasting to all players on the server.
 */
public class ServerAuctionSync {
    
    private static final Gson GSON = new Gson();
    
    /**
     * Sends auction data to all players on the server.
     * Iterates through all players to ensure reliable delivery.
     * @param level the server level
     * @param auctions the auction data to sync
     */
    public static void syncToAllPlayers(ServerLevel level, List<PlayerAuction> auctions) {
        try {
            FreeMarketPacket packet = FreeMarketPacket.withJson(PacketType.AUCTION_SYNC, GSON.toJson(auctions));
            
            // Get server and iterate through all players for reliable delivery
            List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
            
            for (ServerPlayer player : players) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to sync auctions to all players: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Sends auction data to a specific player.
     * @param player the target player
     * @param auctions the auction data to sync
     */
    public static void syncToPlayer(ServerPlayer player, List<PlayerAuction> auctions) {
        try {
            FreeMarketPacket packet = FreeMarketPacket.withJson(PacketType.AUCTION_SYNC, GSON.toJson(auctions));
            PacketDistributor.sendToPlayer(player, packet);
            FreeMarket.LOGGER.info("Sent {} auctions to player {}", auctions.size(), player.getName().getString());
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to sync auctions to player {}: {}", 
                player.getName().getString(), e.getMessage(), e);
        }
    }
    
    /**
     * Loads and sends auction data to all players.
     * @param level the server level
     */
    public static void syncAuctionData(ServerLevel level) {
        List<PlayerAuction> auctions = AuctionDataManager.loadAuctions(level);
        syncToAllPlayers(level, auctions);
    }
    
    /**
     * Loads and sends auction data to a specific player.
     * @param player the target player
     */
    public static void syncAuctionDataToPlayer(ServerPlayer player) {
        List<PlayerAuction> auctions = AuctionDataManager.loadAuctions(player.serverLevel());
        syncToPlayer(player, auctions);
    }
}

