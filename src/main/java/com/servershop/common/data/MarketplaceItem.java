package com.servershop.common.data;

import net.minecraft.world.item.ItemStack;

/**
 * Represents an item in the marketplace with its details.
 */
public class MarketplaceItem {
    private final ItemStack itemStack;
    private final long buyPrice;
    private final long sellPrice;
    private final int quantity;
    private final String seller;
    private final String guid; // Unique identifier for this marketplace entry
    private final String componentData; // Component data as JSON string
    
    public MarketplaceItem(ItemStack itemStack, long buyPrice, long sellPrice, int quantity, String seller) {
        this.itemStack = itemStack;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.quantity = quantity;
        this.seller = seller;
        this.guid = generateRandomGuid();
        this.componentData = "{}"; // Default empty component data
    }
    
    public MarketplaceItem(ItemStack itemStack, long buyPrice, long sellPrice, int quantity, String seller, String guid) {
        this.itemStack = itemStack;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.quantity = quantity;
        this.seller = seller;
        this.guid = guid != null && !guid.isEmpty() ? guid : generateRandomGuid();
        this.componentData = "{}"; // Default empty component data
    }
    
    public MarketplaceItem(ItemStack itemStack, long buyPrice, long sellPrice, int quantity, String seller, String guid, String componentData) {
        this.itemStack = itemStack;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.quantity = quantity;
        this.seller = seller;
        this.guid = guid != null && !guid.isEmpty() ? guid : generateRandomGuid();
        this.componentData = componentData != null ? componentData : "{}";
    }
    
    /**
     * Generates a completely random GUID for new marketplace entries.
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
    
    public String getSeller() {
        return seller;
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
}
