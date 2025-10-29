package com.freemarket.common.data;

/**
 * Data model for player balance information.
 * Used for leaderboard display and offline player balance tracking.
 */
public class PlayerBalanceData {
    
    private String uuid;
    private String playerName;
    private long balance;
    private long lastUpdated;
    
    /**
     * Creates a new PlayerBalanceData instance.
     * @param uuid The player's UUID
     * @param playerName The player's display name
     * @param balance The player's current balance
     * @param lastUpdated Timestamp when this data was last updated
     */
    public PlayerBalanceData(String uuid, String playerName, long balance, long lastUpdated) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.balance = balance;
        this.lastUpdated = lastUpdated;
    }
    
    /**
     * Default constructor for JSON deserialization.
     */
    public PlayerBalanceData() {
        this.uuid = "";
        this.playerName = "";
        this.balance = 0;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public String getUuid() {
        return uuid;
    }
    
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
    
    public long getBalance() {
        return balance;
    }
    
    public void setBalance(long balance) {
        this.balance = balance;
    }
    
    public long getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    
    @Override
    public String toString() {
        return "PlayerBalanceData{" +
                "uuid='" + uuid + '\'' +
                ", playerName='" + playerName + '\'' +
                ", balance=" + balance +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}

