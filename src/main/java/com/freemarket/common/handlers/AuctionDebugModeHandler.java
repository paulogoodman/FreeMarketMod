package com.freemarket.common.handlers;

import com.freemarket.FreeMarket;
import com.freemarket.common.network.FreeMarketPacket;
import com.freemarket.common.network.PacketType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

/**
 * Handles auction debug mode state for the FreeMarket mod.
 * When enabled, players can bid on their own auctions for testing purposes.
 * Supports client-server synchronization via network packets.
 */
public class AuctionDebugModeHandler {
    private static boolean auctionDebugMode = false;
    
    /**
     * Sets the auction debug mode status.
     * @param enabled true to enable auction debug mode, false to disable
     */
    public static void setAuctionDebugMode(boolean enabled) {
        auctionDebugMode = enabled;
        FreeMarket.LOGGER.info("Auction debug mode {} for FreeMarket", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Sets the auction debug mode status and synchronizes with all connected clients.
     * This method should be called from the server side.
     * @param enabled true to enable auction debug mode, false to disable
     * @param server the Minecraft server instance
     */
    public static void setAuctionDebugMode(boolean enabled, MinecraftServer server) {
        auctionDebugMode = enabled;
        FreeMarket.LOGGER.info("Auction debug mode {} for FreeMarket", enabled ? "enabled" : "disabled");
        
        // Send sync packet to all connected players
        FreeMarketPacket packet = FreeMarketPacket.withString(PacketType.AUCTION_DEBUG_MODE_SYNC, String.valueOf(enabled));
        net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(packet);
        
        FreeMarket.LOGGER.info("Sent auction debug mode sync packet to all clients: {}", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Gets the current auction debug mode status.
     * @return true if auction debug mode is enabled, false otherwise
     */
    public static boolean isAuctionDebugMode() {
        return auctionDebugMode;
    }
    
    /**
     * Toggles the auction debug mode status.
     * @return the new auction debug mode status
     */
    public static boolean toggleAuctionDebugMode() {
        auctionDebugMode = !auctionDebugMode;
        FreeMarket.LOGGER.info("Auction debug mode {} for FreeMarket", auctionDebugMode ? "enabled" : "disabled");
        return auctionDebugMode;
    }
    
    /**
     * Toggles the auction debug mode status and synchronizes with all connected clients.
     * This method should be called from the server side.
     * @param server the Minecraft server instance
     * @return the new auction debug mode status
     */
    public static boolean toggleAuctionDebugMode(MinecraftServer server) {
        auctionDebugMode = !auctionDebugMode;
        FreeMarket.LOGGER.info("Auction debug mode {} for FreeMarket", auctionDebugMode ? "enabled" : "disabled");
        
        // Send sync packet to all connected players
        FreeMarketPacket packet = FreeMarketPacket.withString(PacketType.AUCTION_DEBUG_MODE_SYNC, String.valueOf(auctionDebugMode));
        net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(packet);
        
        FreeMarket.LOGGER.info("Sent auction debug mode sync packet to all clients: {}", auctionDebugMode ? "enabled" : "disabled");
        return auctionDebugMode;
    }
    
    /**
     * Sends the current auction debug mode state to a specific player.
     * This should be called when a player joins the server.
     * @param player the player to send the auction debug mode state to
     */
    public static void sendAuctionDebugModeToPlayer(ServerPlayer player) {
        FreeMarketPacket packet = FreeMarketPacket.withString(PacketType.AUCTION_DEBUG_MODE_SYNC, String.valueOf(auctionDebugMode));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet);
        
        FreeMarket.LOGGER.info("Sent auction debug mode sync packet to player {}: {}", 
            player.getName().getString(), auctionDebugMode ? "enabled" : "disabled");
    }
}

