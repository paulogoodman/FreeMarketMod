package com.freemarket.server.data;

/**
 * Represents a pending reward for an offline player.
 * Can hold money and/or items that need to be given to the player when they log in.
 * Items are stored in a registry-independent format to avoid serialization issues.
 */
public class PendingReward {
    private final String uuid;
    private final String playerName;
    private long moneyAmount;
    // Store item data as strings to avoid registry reference issues
    private String itemId;
    private String componentData;
    private int itemCount;
    private String reason; // e.g., "Auction completed", "Auction returned", etc.
    private long timestamp;
    
    /**
     * Creates a new pending reward for money.
     */
    public PendingReward(String uuid, String playerName, long moneyAmount, String reason) {
        this(uuid, playerName, moneyAmount, null, null, 0, reason, System.currentTimeMillis());
    }
    
    /**
     * Creates a new pending reward for an item.
     */
    public PendingReward(String uuid, String playerName, String itemId, String componentData, int itemCount, String reason) {
        this(uuid, playerName, 0, itemId, componentData, itemCount, reason, System.currentTimeMillis());
    }
    
    /**
     * Creates a new pending reward for both money and an item.
     */
    public PendingReward(String uuid, String playerName, long moneyAmount, String itemId, String componentData, int itemCount, String reason) {
        this(uuid, playerName, moneyAmount, itemId, componentData, itemCount, reason, System.currentTimeMillis());
    }
    
    /**
     * Creates a new pending reward with custom timestamp (for loading from NBT).
     */
    public PendingReward(String uuid, String playerName, long moneyAmount, String itemId, String componentData, int itemCount, String reason, long timestamp) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.moneyAmount = moneyAmount;
        this.itemId = itemId;
        this.componentData = componentData;
        this.itemCount = itemCount;
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
    
    public String getItemId() {
        return itemId;
    }
    
    public String getComponentData() {
        return componentData;
    }
    
    public int getItemCount() {
        return itemCount;
    }
    
    public boolean hasItem() {
        return itemId != null && !itemId.isEmpty() && itemCount > 0;
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

