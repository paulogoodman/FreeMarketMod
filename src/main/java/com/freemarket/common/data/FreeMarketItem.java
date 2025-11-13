package com.freemarket.common.data;

import net.minecraft.world.item.ItemStack;

/**
 * Represents an item in the free market with its details.
 */
public class FreeMarketItem {
    private final ItemStack itemStack;
    private final long buyPrice;
    private final long sellPrice;
    private final int stackSize;
    private final Integer totalStockAvailable; // Optional: total stock available (not yet implemented)
    private final String marketListingId; // Unique identifier for this free market entry
    private final String componentData; // Component data as JSON string
    private int order; // Display order for sorting (lower numbers appear first) - mutable for updates
    
    public FreeMarketItem(ItemStack itemStack, long buyPrice, long sellPrice, int stackSize) {
        this.itemStack = itemStack;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.stackSize = stackSize;
        this.totalStockAvailable = null; // Default to null (not yet implemented)
        this.marketListingId = generateRandomMarketListingId();
        this.componentData = "{}"; // Default empty component data
        this.order = Integer.MAX_VALUE; // Default to last position
    }
    
    public FreeMarketItem(ItemStack itemStack, long buyPrice, long sellPrice, int stackSize, String marketListingId) {
        this.itemStack = itemStack;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.stackSize = stackSize;
        this.totalStockAvailable = null; // Default to null (not yet implemented)
        this.marketListingId = marketListingId != null && !marketListingId.isEmpty() ? marketListingId : generateRandomMarketListingId();
        this.componentData = "{}"; // Default empty component data
        this.order = Integer.MAX_VALUE; // Default to last position
    }
    
    public FreeMarketItem(ItemStack itemStack, long buyPrice, long sellPrice, int stackSize, String marketListingId, String componentData) {
        this.itemStack = itemStack;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.stackSize = stackSize;
        this.totalStockAvailable = null; // Default to null (not yet implemented)
        this.marketListingId = marketListingId != null && !marketListingId.isEmpty() ? marketListingId : generateRandomMarketListingId();
        this.componentData = componentData != null ? componentData : "{}";
        this.order = Integer.MAX_VALUE; // Default to last position
    }
    
    public FreeMarketItem(ItemStack itemStack, long buyPrice, long sellPrice, int stackSize, String marketListingId, String componentData, int order) {
        this.itemStack = itemStack;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.stackSize = stackSize;
        this.totalStockAvailable = null; // Default to null (not yet implemented)
        this.marketListingId = marketListingId != null && !marketListingId.isEmpty() ? marketListingId : generateRandomMarketListingId();
        this.componentData = componentData != null ? componentData : "{}";
        this.order = order;
    }
    
    public FreeMarketItem(ItemStack itemStack, long buyPrice, long sellPrice, int stackSize, Integer totalStockAvailable, String marketListingId, String componentData, int order) {
        this.itemStack = itemStack;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.stackSize = stackSize;
        this.totalStockAvailable = totalStockAvailable; // Can be null
        this.marketListingId = marketListingId != null && !marketListingId.isEmpty() ? marketListingId : generateRandomMarketListingId();
        this.componentData = componentData != null ? componentData : "{}";
        this.order = order;
    }
    
    /**
     * Generates a completely random market listing ID for new free market entries.
     * This ensures each item has a unique identifier regardless of properties.
     */
    private String generateRandomMarketListingId() {
        return java.util.UUID.randomUUID().toString();
    }
    
    public ItemStack getItemStack() {
        return itemStack;
    }
    
    public long getBuyPrice() {
        return buyPrice;
    }
    
    public long getSellPrice() {
        return sellPrice;
    }
    
    public int getStackSize() {
        return stackSize;
    }
    
    public Integer getTotalStockAvailable() {
        return totalStockAvailable;
    }
    
    public String getMarketListingId() {
        return marketListingId;
    }
    
    public String getComponentData() {
        return componentData;
    }
    
    public String getItemName() {
        return itemStack.getItem().getDescription().getString();
    }
    
    public int getOrder() {
        return order;
    }
    
    public void setOrder(int order) {
        this.order = order;
    }
}
