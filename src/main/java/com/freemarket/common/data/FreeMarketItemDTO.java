package com.freemarket.common.data;

/**
 * Data Transfer Object for FreeMarketItem network communication.
 * Contains only serializable fields for JSON transmission.
 */
public class FreeMarketItemDTO {
    private String itemId;
    private long buyPrice;
    private long sellPrice;
    private int stackSize;
    private Integer totalStockAvailable;
    private String marketListingId;
    private String componentData;
    private int order;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public FreeMarketItemDTO() {
        this.itemId = "";
        this.buyPrice = 0;
        this.sellPrice = 0;
        this.stackSize = 1;
        this.totalStockAvailable = null;
        this.marketListingId = "";
        this.componentData = "{}";
        this.order = Integer.MAX_VALUE;
    }
    
    /**
     * Creates a DTO from a FreeMarketItem.
     */
    public FreeMarketItemDTO(FreeMarketItem item) {
        this.itemId = item.getItemStack().getItem().toString();
        this.buyPrice = item.getBuyPrice();
        this.sellPrice = item.getSellPrice();
        this.stackSize = item.getStackSize();
        this.totalStockAvailable = item.getTotalStockAvailable();
        this.marketListingId = item.getMarketListingId();
        this.componentData = item.getComponentData();
        this.order = item.getOrder();
    }
    
    // Getters and setters
    public String getItemId() {
        return itemId;
    }
    
    public void setItemId(String itemId) {
        this.itemId = itemId;
    }
    
    public long getBuyPrice() {
        return buyPrice;
    }
    
    public void setBuyPrice(long buyPrice) {
        this.buyPrice = buyPrice;
    }
    
    public long getSellPrice() {
        return sellPrice;
    }
    
    public void setSellPrice(long sellPrice) {
        this.sellPrice = sellPrice;
    }
    
    public int getStackSize() {
        return stackSize;
    }
    
    public void setStackSize(int stackSize) {
        this.stackSize = stackSize;
    }
    
    public Integer getTotalStockAvailable() {
        return totalStockAvailable;
    }
    
    public void setTotalStockAvailable(Integer totalStockAvailable) {
        this.totalStockAvailable = totalStockAvailable;
    }
    
    public String getMarketListingId() {
        return marketListingId;
    }
    
    public void setMarketListingId(String marketListingId) {
        this.marketListingId = marketListingId;
    }
    
    public String getComponentData() {
        return componentData;
    }
    
    public void setComponentData(String componentData) {
        this.componentData = componentData;
    }
    
    public int getOrder() {
        return order;
    }
    
    public void setOrder(int order) {
        this.order = order;
    }
    
    /**
     * Converts this DTO back to a FreeMarketItem.
     * Note: This requires server-side context to recreate the ItemStack.
     */
    public FreeMarketItem toFreeMarketItem() {
        // This method would need server-side context to recreate ItemStack
        // For now, we'll handle this in the packet handler
        throw new UnsupportedOperationException("DTO conversion requires server context");
    }
}
