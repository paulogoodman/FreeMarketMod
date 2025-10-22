package com.freemarket.common.data;

/**
 * Data model for player auctions.
 * Represents an item listed for auction by a player.
 */
public class PlayerAuction {
    
    private String auctionId; // Unique identifier for the auction
    private String itemId; // Item registry ID (e.g., "minecraft:diamond_sword")
    private String componentData; // NBT/Component data as JSON string
    private int quantity; // Number of items in the stack
    private long startingPrice; // Starting bid price
    private long currentBid; // Current highest bid
    private String sellerUuid; // UUID of the seller
    private String sellerName; // Display name of the seller
    private long expiryTime; // Timestamp when auction expires
    private String bidderUuid; // UUID of the current highest bidder (null if no bids)
    private String bidderName; // Display name of the current highest bidder (null if no bids)
    private long createdTime; // Timestamp when auction was created
    
    /**
     * Creates a new PlayerAuction instance.
     */
    public PlayerAuction(String auctionId, String itemId, String componentData, int quantity,
                        long startingPrice, long currentBid, String sellerUuid, String sellerName,
                        long expiryTime, String bidderUuid, String bidderName, long createdTime) {
        this.auctionId = auctionId;
        this.itemId = itemId;
        this.componentData = componentData;
        this.quantity = quantity;
        this.startingPrice = startingPrice;
        this.currentBid = currentBid;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.expiryTime = expiryTime;
        this.bidderUuid = bidderUuid;
        this.bidderName = bidderName;
        this.createdTime = createdTime;
    }
    
    /**
     * Default constructor for JSON deserialization.
     */
    public PlayerAuction() {
        this.auctionId = "";
        this.itemId = "";
        this.componentData = "{}";
        this.quantity = 1;
        this.startingPrice = 0;
        this.currentBid = 0;
        this.sellerUuid = "";
        this.sellerName = "";
        this.expiryTime = System.currentTimeMillis();
        this.bidderUuid = null;
        this.bidderName = null;
        this.createdTime = System.currentTimeMillis();
    }
    
    // Getters and setters
    
    public String getAuctionId() {
        return auctionId;
    }
    
    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
    }
    
    public String getItemId() {
        return itemId;
    }
    
    public void setItemId(String itemId) {
        this.itemId = itemId;
    }
    
    public String getComponentData() {
        return componentData;
    }
    
    public void setComponentData(String componentData) {
        this.componentData = componentData;
    }
    
    public int getQuantity() {
        return quantity;
    }
    
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
    
    public long getStartingPrice() {
        return startingPrice;
    }
    
    public void setStartingPrice(long startingPrice) {
        this.startingPrice = startingPrice;
    }
    
    public long getCurrentBid() {
        return currentBid;
    }
    
    public void setCurrentBid(long currentBid) {
        this.currentBid = currentBid;
    }
    
    public String getSellerUuid() {
        return sellerUuid;
    }
    
    public void setSellerUuid(String sellerUuid) {
        this.sellerUuid = sellerUuid;
    }
    
    public String getSellerName() {
        return sellerName;
    }
    
    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }
    
    public long getExpiryTime() {
        return expiryTime;
    }
    
    public void setExpiryTime(long expiryTime) {
        this.expiryTime = expiryTime;
    }
    
    public String getBidderUuid() {
        return bidderUuid;
    }
    
    public void setBidderUuid(String bidderUuid) {
        this.bidderUuid = bidderUuid;
    }
    
    public String getBidderName() {
        return bidderName;
    }
    
    public void setBidderName(String bidderName) {
        this.bidderName = bidderName;
    }
    
    public long getCreatedTime() {
        return createdTime;
    }
    
    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }
    
    /**
     * Checks if the auction has expired.
     * @return true if the auction has expired
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expiryTime;
    }
    
    /**
     * Checks if the auction has any bids.
     * @return true if there is at least one bid
     */
    public boolean hasBids() {
        return bidderUuid != null && !bidderUuid.isEmpty();
    }
    
    /**
     * Gets the time remaining until expiry in milliseconds.
     * @return time remaining, or 0 if expired
     */
    public long getTimeRemaining() {
        long remaining = expiryTime - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
    
    @Override
    public String toString() {
        return "PlayerAuction{" +
                "auctionId='" + auctionId + '\'' +
                ", itemId='" + itemId + '\'' +
                ", quantity=" + quantity +
                ", startingPrice=" + startingPrice +
                ", currentBid=" + currentBid +
                ", sellerName='" + sellerName + '\'' +
                ", bidderName='" + bidderName + '\'' +
                ", expiryTime=" + expiryTime +
                ", expired=" + isExpired() +
                '}';
    }
}

