package com.servershop.common.handlers;

import com.servershop.ServerShop;
import com.servershop.common.attachments.PlayerWalletAttachment;
import net.minecraft.world.entity.player.Player;

/**
 * Handles player wallet/money system for the ServerShop mod.
 * This class manages player money balances using NeoForge Data Attachments.
 * Uses PlayerWalletAttachment for proper persistence across deaths.
 */
public class WalletHandler {
    
    /**
     * Gets the current player money amount from their wallet attachment.
     * @param player the player to get money for
     * @return current money amount
     */
    public static long getPlayerMoney(Player player) {
        if (player == null) {
            ServerShop.LOGGER.warn("Cannot get money for null player");
            return 0;
        }
        
        PlayerWalletAttachment wallet = player.getData(PlayerWalletAttachment.WALLET);
        long balance = wallet.getBalance();
        // Only log debug messages, not every retrieval
        return balance;
    }
    
    /**
     * Sets the player money amount in their wallet attachment.
     * @param player the player to set money for
     * @param amount the new money amount
     */
    public static void setPlayerMoney(Player player, long amount) {
        if (player == null) {
            ServerShop.LOGGER.warn("Cannot set money for null player");
            return;
        }
        
        PlayerWalletAttachment wallet = player.getData(PlayerWalletAttachment.WALLET);
        wallet.setBalance(amount);
        
        // Only log significant wallet changes, not every set operation
        ServerShop.LOGGER.debug("Set {} money to: {}", player.getName().getString(), amount);
    }
    
    /**
     * Adds money to the player's wallet.
     * @param player the player to add money to
     * @param amount the amount to add
     */
    public static void addMoney(Player player, long amount) {
        if (player == null) {
            ServerShop.LOGGER.warn("Cannot add money for null player");
            return;
        }
        
        PlayerWalletAttachment wallet = player.getData(PlayerWalletAttachment.WALLET);
        wallet.addBalance(amount);
        
        ServerShop.LOGGER.info("Added {} coins to {}. New balance: {}", amount, player.getName().getString(), wallet.getBalance());
    }
    
    /**
     * Removes money from the player's wallet.
     * @param player the player to remove money from
     * @param amount the amount to remove
     * @return true if successful, false if insufficient funds
     */
    public static boolean removeMoney(Player player, long amount) {
        if (player == null) {
            ServerShop.LOGGER.warn("Cannot remove money for null player");
            return false;
        }
        
        PlayerWalletAttachment wallet = player.getData(PlayerWalletAttachment.WALLET);
        if (wallet.removeBalance(amount)) {
            ServerShop.LOGGER.info("Removed {} coins from {}. New balance: {}", amount, player.getName().getString(), wallet.getBalance());
            return true;
        }
        
        ServerShop.LOGGER.info("Insufficient funds for {}. Required: {}, Available: {}", player.getName().getString(), amount, wallet.getBalance());
        return false;
    }
    
    /**
     * Checks if the player has enough money.
     * @param player the player to check
     * @param amount the amount to check
     * @return true if player has enough money
     */
    public static boolean hasEnoughMoney(Player player, long amount) {
        if (player == null) {
            return false;
        }
        
        PlayerWalletAttachment wallet = player.getData(PlayerWalletAttachment.WALLET);
        return wallet.hasEnoughBalance(amount);
    }
    
    /**
     * Client-side method to get money for the current player.
     * This is used by the GUI when we don't have direct access to the player object.
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
                        long balance = getPlayerMoney(serverPlayer);
                        return balance;
                    }
                }
                
                // Fallback to client player
                long balance = getPlayerMoney(clientPlayer);
                return balance;
            } else {
                ServerShop.LOGGER.warn("Client player is null");
            }
        } catch (Exception e) {
            // If we can't get the player, log the error
            ServerShop.LOGGER.error("Could not get client player money: {}", e.getMessage());
        }
        
        // Never fall back to hardcoded value - return 0 if we can't get the player
        ServerShop.LOGGER.warn("Could not access client player, returning 0");
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
