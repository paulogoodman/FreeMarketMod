package com.servershop;

/**
 * Handles player wallet/money system for the ServerShop mod.
 * This class manages player money balances.
 */
public class WalletHandler {
    private static int playerMoney = 1000; // Starting money for demo
    
    /**
     * Gets the current player money amount.
     * @return current money amount
     */
    public static int getPlayerMoney() {
        return playerMoney;
    }
    
    /**
     * Sets the player money amount.
     * @param amount the new money amount
     */
    public static void setPlayerMoney(int amount) {
        playerMoney = amount;
        ServerShop.LOGGER.info("Player money set to: {}", amount);
    }
    
    /**
     * Adds money to the player's wallet.
     * @param amount the amount to add
     */
    public static void addMoney(int amount) {
        playerMoney += amount;
        ServerShop.LOGGER.info("Added {} coins. New balance: {}", amount, playerMoney);
    }
    
    /**
     * Removes money from the player's wallet.
     * @param amount the amount to remove
     * @return true if successful, false if insufficient funds
     */
    public static boolean removeMoney(int amount) {
        if (playerMoney >= amount) {
            playerMoney -= amount;
            ServerShop.LOGGER.info("Removed {} coins. New balance: {}", amount, playerMoney);
            return true;
        }
        ServerShop.LOGGER.info("Insufficient funds. Required: {}, Available: {}", amount, playerMoney);
        return false;
    }
    
    /**
     * Checks if the player has enough money.
     * @param amount the amount to check
     * @return true if player has enough money
     */
    public static boolean hasEnoughMoney(int amount) {
        return playerMoney >= amount;
    }
}
