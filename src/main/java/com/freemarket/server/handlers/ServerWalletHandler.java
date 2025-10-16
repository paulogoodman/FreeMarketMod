package com.freemarket.server.handlers;

import com.freemarket.FreeMarket;
import com.freemarket.common.attachments.PlayerWalletAttachment;
import net.minecraft.world.entity.player.Player;

/**
 * Server-side wallet handler for the FreeMarket mod.
 * Handles all wallet operations that require server-side data attachments.
 */
public class ServerWalletHandler {
    
    /**
     * Gets the current player money amount from their wallet attachment.
     * @param player the player to get money for
     * @return current money amount
     */
    public static long getPlayerMoney(Player player) {
        if (player == null) {
            FreeMarket.LOGGER.warn("Cannot get money for null player");
            return 0;
        }
        
        PlayerWalletAttachment wallet = player.getData(PlayerWalletAttachment.WALLET);
        long balance = wallet.getBalance();
        return balance;
    }
    
    /**
     * Sets the player money amount in their wallet attachment.
     * @param player the player to set money for
     * @param amount the new money amount
     */
    public static void setPlayerMoney(Player player, long amount) {
        if (player == null) {
            FreeMarket.LOGGER.warn("Cannot set money for null player");
            return;
        }
        
        PlayerWalletAttachment wallet = player.getData(PlayerWalletAttachment.WALLET);
        wallet.setBalance(amount);
        
        FreeMarket.LOGGER.debug("Set {} money to: {}", player.getName().getString(), amount);
    }
    
    /**
     * Adds money to the player's wallet.
     * @param player the player to add money to
     * @param amount the amount to add
     */
    public static void addMoney(Player player, long amount) {
        if (player == null) {
            FreeMarket.LOGGER.warn("Cannot add money for null player");
            return;
        }
        
        PlayerWalletAttachment wallet = player.getData(PlayerWalletAttachment.WALLET);
        wallet.addBalance(amount);
    }
    
    /**
     * Removes money from the player's wallet.
     * @param player the player to remove money from
     * @param amount the amount to remove
     * @return true if successful, false if insufficient funds
     */
    public static boolean removeMoney(Player player, long amount) {
        if (player == null) {
            FreeMarket.LOGGER.warn("Cannot remove money for null player");
            return false;
        }
        
        PlayerWalletAttachment wallet = player.getData(PlayerWalletAttachment.WALLET);
        if (wallet.removeBalance(amount)) {
            return true;
        }
        
        FreeMarket.LOGGER.info("Insufficient funds for {}. Required: {}, Available: {}", player.getName().getString(), amount, wallet.getBalance());
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
}
