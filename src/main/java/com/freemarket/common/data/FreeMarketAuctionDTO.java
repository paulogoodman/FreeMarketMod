package com.freemarket.common.data;

/**
 * Data Transfer Object for PlayerAuction network communication.
 * Contains only serializable fields for JSON transmission.
 */
public class FreeMarketAuctionDTO {
    private String auctionId;
    private String itemId;
    private String componentData;
    private int stackSize;
    private long startingPrice;
    private long currentBid;
    private String sellerUuid;
    private String sellerName;
    private long expiryTime;
    private String bidderUuid;
    private String bidderName;
    private long createdTime;
    private int order;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public FreeMarketAuctionDTO() {
        this.auctionId = "";
        this.itemId = "";
        this.componentData = "{}";
        this.stackSize = 1;
        this.startingPrice = 0;
        this.currentBid = 0;
        this.sellerUuid = "";
        this.sellerName = "";
        this.expiryTime = System.currentTimeMillis();
        this.bidderUuid = null;
        this.bidderName = null;
        this.createdTime = System.currentTimeMillis();
        this.order = Integer.MAX_VALUE;
    }
    
    /**
     * Creates a DTO from a PlayerAuction.
     */
    public FreeMarketAuctionDTO(PlayerAuction auction) {
        this.auctionId = auction.getAuctionId();
        this.itemId = auction.getItemId();
        this.componentData = auction.getComponentData();
        this.stackSize = auction.getStackSize();
        this.startingPrice = auction.getStartingPrice();
        this.currentBid = auction.getCurrentBid();
        this.sellerUuid = auction.getSellerUuid();
        this.sellerName = auction.getSellerName();
        this.expiryTime = auction.getExpiryTime();
        this.bidderUuid = auction.getBidderUuid();
        this.bidderName = auction.getBidderName();
        this.createdTime = auction.getCreatedTime();
        this.order = auction.getOrder();
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
    
    public int getStackSize() {
        return stackSize;
    }
    
    public void setStackSize(int stackSize) {
        this.stackSize = stackSize;
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
    
    public int getOrder() {
        return order;
    }
    
    public void setOrder(int order) {
        this.order = order;
    }
    
    /**
     * Converts this DTO back to a PlayerAuction.
     */
    public PlayerAuction toPlayerAuction() {
        return new PlayerAuction(
            auctionId,
            itemId,
            componentData,
            stackSize,
            startingPrice,
            currentBid,
            sellerUuid,
            sellerName,
            expiryTime,
            bidderUuid,
            bidderName,
            createdTime,
            order
        );
    }
}

