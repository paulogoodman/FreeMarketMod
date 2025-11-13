package com.freemarket.common.data;

import net.minecraft.world.item.ItemStack;

/**
 * Represents an item in the free market with its details.
 */
public class FreeMarketItem {
    private final ItemStack itemStack;
    private final long buyPrice;
    private final long sellPrice;
    private final int quantity;
    private final String guid; // Unique identifier for this free market entry
    private final String componentData; // Component data as JSON string
    private int order; // Display order for sorting (lower numbers appear first) - mutable for updates
    
    public FreeMarketItem(ItemStack itemStack, long buyPrice, long sellPrice, int quantity) {
        this.itemStack = itemStack;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.quantity = quantity;
        this.guid = generateRandomGuid();
        this.componentData = "{}"; // Default empty component data
        this.order = Integer.MAX_VALUE; // Default to last position
    }
    
    public FreeMarketItem(ItemStack itemStack, long buyPrice, long sellPrice, int quantity, String guid) {
        this.itemStack = itemStack;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.quantity = quantity;
        this.guid = guid != null && !guid.isEmpty() ? guid : generateRandomGuid();
        this.componentData = "{}"; // Default empty component data
        this.order = Integer.MAX_VALUE; // Default to last position
    }
    
    public FreeMarketItem(ItemStack itemStack, long buyPrice, long sellPrice, int quantity, String guid, String componentData) {
        this.itemStack = itemStack;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.quantity = quantity;
        this.guid = guid != null && !guid.isEmpty() ? guid : generateRandomGuid();
        this.componentData = componentData != null ? componentData : "{}";
        this.order = Integer.MAX_VALUE; // Default to last position
    }
    
    public FreeMarketItem(ItemStack itemStack, long buyPrice, long sellPrice, int quantity, String guid, String componentData, int order) {
        this.itemStack = itemStack;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.quantity = quantity;
        this.guid = guid != null && !guid.isEmpty() ? guid : generateRandomGuid();
        this.componentData = componentData != null ? componentData : "{}";
        this.order = order;
    }
    
    /**
     * Generates a completely random GUID for new free market entries.
     * This ensures each item has a unique identifier regardless of properties.
     */
    private String generateRandomGuid() {
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
    
    public int getQuantity() {
        return quantity;
    }
    
    public String getGuid() {
        return guid;
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
