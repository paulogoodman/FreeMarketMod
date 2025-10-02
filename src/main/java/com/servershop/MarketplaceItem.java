package com.servershop;

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
    
    public MarketplaceItem(ItemStack itemStack, int buyPrice, int sellPrice, int quantity, String seller) {
        this.itemStack = itemStack;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.quantity = quantity;
        this.seller = seller;
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
    
    public String getItemName() {
        return itemStack.getItem().getDescription().getString();
    }
}
