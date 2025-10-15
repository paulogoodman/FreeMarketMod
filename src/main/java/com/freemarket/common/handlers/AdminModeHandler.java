package com.freemarket.common.handlers;

import com.freemarket.FreeMarket;
import com.freemarket.common.network.AdminModeSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

/**
 * Handles admin mode state for the FreeMarket mod.
 * This class manages whether admin mode is enabled or disabled.
 * Supports client-server synchronization via network packets.
 */
public class AdminModeHandler {
    private static boolean adminMode = false;
    
    /**
     * Sets the admin mode status.
     * @param enabled true to enable admin mode, false to disable
     */
    public static void setAdminMode(boolean enabled) {
        adminMode = enabled;
        FreeMarket.LOGGER.info("Admin mode {} for FreeMarket", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Sets the admin mode status and synchronizes with all connected clients.
     * This method should be called from the server side.
     * @param enabled true to enable admin mode, false to disable
     * @param server the Minecraft server instance
     */
    public static void setAdminMode(boolean enabled, MinecraftServer server) {
        adminMode = enabled;
        FreeMarket.LOGGER.info("Admin mode {} for FreeMarket", enabled ? "enabled" : "disabled");
        
        // Send sync packet to all connected players
        AdminModeSyncPacket packet = new AdminModeSyncPacket(enabled);
        net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(packet);
        
        FreeMarket.LOGGER.info("Sent admin mode sync packet to all clients: {}", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Gets the current admin mode status.
     * @return true if admin mode is enabled, false otherwise
     */
    public static boolean isAdminMode() {
        return adminMode;
    }
    
    /**
     * Toggles the admin mode status.
     * @return the new admin mode status
     */
    public static boolean toggleAdminMode() {
        adminMode = !adminMode;
        FreeMarket.LOGGER.info("Admin mode {} for FreeMarket", adminMode ? "enabled" : "disabled");
        return adminMode;
    }
    
    /**
     * Toggles the admin mode status and synchronizes with all connected clients.
     * This method should be called from the server side.
     * @param server the Minecraft server instance
     * @return the new admin mode status
     */
    public static boolean toggleAdminMode(MinecraftServer server) {
        adminMode = !adminMode;
        FreeMarket.LOGGER.info("Admin mode {} for FreeMarket", adminMode ? "enabled" : "disabled");
        
        // Send sync packet to all connected players
        AdminModeSyncPacket packet = new AdminModeSyncPacket(adminMode);
        net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(packet);
        
        FreeMarket.LOGGER.info("Sent admin mode sync packet to all clients: {}", adminMode ? "enabled" : "disabled");
        return adminMode;
    }
    
    /**
     * Sends the current admin mode state to a specific player.
     * This should be called when a player joins the server.
     * @param player the player to send the admin mode state to
     */
    public static void sendAdminModeToPlayer(ServerPlayer player) {
        AdminModeSyncPacket packet = new AdminModeSyncPacket(adminMode);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet);
        
        FreeMarket.LOGGER.info("Sent admin mode sync packet to player {}: {}", 
            player.getName().getString(), adminMode ? "enabled" : "disabled");
    }
}