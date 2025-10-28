package com.freemarket.server.data;

import net.minecraft.world.item.ItemStack;

/**
 * Represents a pending reward for an offline player.
 * Can hold money and/or items that need to be given to the player when they log in.
 */
public class PendingReward {
    private final String uuid;
    private final String playerName;
    private long moneyAmount;
    private ItemStack itemStack;
    private String reason; // e.g., "Auction completed", "Auction returned", etc.
    private long timestamp;
    
    /**
     * Creates a new pending reward for money.
     */
    public PendingReward(String uuid, String playerName, long moneyAmount, String reason) {
        this(uuid, playerName, moneyAmount, null, reason, System.currentTimeMillis());
    }
    
    /**
     * Creates a new pending reward for an item.
     */
    public PendingReward(String uuid, String playerName, ItemStack itemStack, String reason) {
        this(uuid, playerName, 0, itemStack, reason, System.currentTimeMillis());
    }
    
    /**
     * Creates a new pending reward for both money and an item.
     */
    public PendingReward(String uuid, String playerName, long moneyAmount, ItemStack itemStack, String reason) {
        this(uuid, playerName, moneyAmount, itemStack, reason, System.currentTimeMillis());
    }
    
    /**
     * Creates a new pending reward with custom timestamp (for loading from NBT).
     */
    public PendingReward(String uuid, String playerName, long moneyAmount, ItemStack itemStack, String reason, long timestamp) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.moneyAmount = moneyAmount;
        this.itemStack = itemStack;
        this.reason = reason;
        this.timestamp = timestamp;
    }
    
    public String getUuid() {
        return uuid;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public long getMoneyAmount() {
        return moneyAmount;
    }
    
    public boolean hasMoney() {
        return moneyAmount > 0;
    }
    
    public ItemStack getItemStack() {
        return itemStack;
    }
    
    public boolean hasItem() {
        return itemStack != null && !itemStack.isEmpty();
    }
    
    public String getReason() {
        return reason;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public boolean hasAnyReward() {
        return hasMoney() || hasItem();
    }
}

