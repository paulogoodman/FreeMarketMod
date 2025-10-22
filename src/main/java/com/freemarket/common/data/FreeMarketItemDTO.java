package com.freemarket.common.data;

/**
 * Data Transfer Object for FreeMarketItem network communication.
 * Contains only serializable fields for JSON transmission.
 */
public class FreeMarketItemDTO {
    private String itemId;
    private long buyPrice;
    private long sellPrice;
    private int quantity;
    private String seller;
    private String guid;
    private String componentData;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public FreeMarketItemDTO() {
        this.itemId = "";
        this.buyPrice = 0;
        this.sellPrice = 0;
        this.quantity = 1;
        this.seller = "";
        this.guid = "";
        this.componentData = "{}";
    }
    
    /**
     * Creates a DTO from a FreeMarketItem.
     */
    public FreeMarketItemDTO(FreeMarketItem item) {
        this.itemId = item.getItemStack().getItem().toString();
        this.buyPrice = item.getBuyPrice();
        this.sellPrice = item.getSellPrice();
        this.quantity = item.getQuantity();
        this.seller = item.getSeller();
        this.guid = item.getGuid();
        this.componentData = item.getComponentData();
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
    
    public int getQuantity() {
        return quantity;
    }
    
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
    
    public String getSeller() {
        return seller;
    }
    
    public void setSeller(String seller) {
        this.seller = seller;
    }
    
    public String getGuid() {
        return guid;
    }
    
    public void setGuid(String guid) {
        this.guid = guid;
    }
    
    public String getComponentData() {
        return componentData;
    }
    
    public void setComponentData(String componentData) {
        this.componentData = componentData;
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
