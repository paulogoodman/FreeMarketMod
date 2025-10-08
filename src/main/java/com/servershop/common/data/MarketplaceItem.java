package com.servershop.common.data;

import net.minecraft.world.item.ItemStack;

/**
 * Represents an item in the marketplace with its details.
 */
public class MarketplaceItem {
    private final ItemStack itemStack;
    private final int buyPrice;
    private final int sellPrice;
    private final int quantity;
    private final String seller;
    private final String guid; // Unique identifier for this marketplace entry
    
    public MarketplaceItem(ItemStack itemStack, int buyPrice, int sellPrice, int quantity, String seller) {
        this.itemStack = itemStack;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.quantity = quantity;
        this.seller = seller;
        this.guid = generateRandomGuid();
    }
    
    public MarketplaceItem(ItemStack itemStack, int buyPrice, int sellPrice, int quantity, String seller, String guid) {
        this.itemStack = itemStack;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.quantity = quantity;
        this.seller = seller;
        this.guid = guid != null && !guid.isEmpty() ? guid : generateRandomGuid();
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
    
    public int getBuyPrice() {
        return buyPrice;
    }
    
    public int getSellPrice() {
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
    
    public String getItemName() {
        return itemStack.getItem().getDescription().getString();
    }
}
