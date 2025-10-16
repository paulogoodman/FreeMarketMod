package com.freemarket.client.handlers;

import com.freemarket.FreeMarket;
import com.freemarket.common.network.WalletRequestPacket;

/**
 * Client-side wallet handler for the FreeMarket mod.
 * Handles wallet operations that require client-side caching and network requests.
 */
public class ClientWalletHandler {
    
    /**
     * Client-side method to get money for the current player.
     * This is used by the GUI when we don't have direct access to the player object.
     * In multiplayer, requests balance from server via network packet.
     * @return current money amount for the client player
     */
    public static long getPlayerMoney() {
        try {
            // Try to get the client player
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            var clientPlayer = minecraft.player;
            if (clientPlayer != null) {
                // In singleplayer, try to get the server player instead of client player
                var singleplayerServer = minecraft.getSingleplayerServer();
                if (singleplayerServer != null) {
                    // We're in singleplayer - get the server player
                    var serverPlayer = singleplayerServer.getPlayerList().getPlayer(clientPlayer.getUUID());
                    if (serverPlayer != null) {
                        long balance = com.freemarket.server.handlers.ServerWalletHandler.getPlayerMoney(serverPlayer);
                        return balance;
                    }
                }
                
                // In multiplayer, request balance from server
                WalletRequestPacket packet = new WalletRequestPacket();
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
                
                // Return cached balance while waiting for server response
                return com.freemarket.client.data.ClientWalletCache.getCurrentPlayerBalance();
            } else {
                FreeMarket.LOGGER.warn("Client player is null");
            }
        } catch (Exception e) {
            // If we can't get the player, log the error
            FreeMarket.LOGGER.error("Could not get client player money: {}", e.getMessage());
        }
        
        // Never fall back to hardcoded value - return 0 if we can't get the player
        FreeMarket.LOGGER.warn("Could not access client player, returning 0");
        return 0;
    }
    
    /**
     * Client-side method to check if player has enough money.
     * This is used by the GUI when we don't have direct access to the player object.
     * @param amount the amount to check
     * @return true if player has enough money
     */
    public static boolean hasEnoughMoney(long amount) {
        return getPlayerMoney() >= amount;
    }
}
