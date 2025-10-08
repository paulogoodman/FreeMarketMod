package com.servershop.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;

import com.servershop.ServerShop;
import com.servershop.common.data.MarketplaceItem;
import com.servershop.client.data.ClientMarketplaceDataManager;

/**
 * Popup screen for adding items to the marketplace.
 * This screen appears when the '+' button is clicked in the main shop GUI.
 */
public class AddItemPopupScreen extends Screen {
    
    private final ShopGuiScreen parentScreen;
    private EditBox itemIdBox;
    private EditBox buyPriceBox;
    private EditBox sellPriceBox;
    private EditBox quantityBox;
    private Button addButton;
    private Button cancelButton;
    
    // Current selected item
    private ItemStack selectedItem = null;
    private String itemIdError = null;
    
    public AddItemPopupScreen(ShopGuiScreen parent) {
        super(Component.translatable("gui.servershop.add_item.title"));
        this.parentScreen = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Calculate popup dimensions and position (centered)
        int popupWidth = 350;
        int popupHeight = 280;
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;
        
        // Item ID input
        this.itemIdBox = new EditBox(this.font, popupX + 20, popupY + 50, 200, 20, 
            Component.translatable("gui.servershop.add_item.item_id"));
        this.itemIdBox.setValue("minecraft:diamond");
        this.itemIdBox.setResponder(this::onItemIdChanged);
        this.addRenderableWidget(this.itemIdBox);
        
        // Buy price input
        this.buyPriceBox = new EditBox(this.font, popupX + 20, popupY + 90, 120, 20, 
            Component.translatable("gui.servershop.add_item.buy_price"));
        this.buyPriceBox.setValue("100");
        this.addRenderableWidget(this.buyPriceBox);
        
        // Sell price input
        this.sellPriceBox = new EditBox(this.font, popupX + 20, popupY + 130, 120, 20, 
            Component.translatable("gui.servershop.add_item.sell_price"));
        this.sellPriceBox.setValue("80");
        this.addRenderableWidget(this.sellPriceBox);
        
        // Quantity input
        this.quantityBox = new EditBox(this.font, popupX + 20, popupY + 170, 120, 20, 
            Component.translatable("gui.servershop.add_item.quantity"));
        this.quantityBox.setValue("1");
        this.addRenderableWidget(this.quantityBox);
        
        // Add button
        this.addButton = Button.builder(
            Component.translatable("gui.servershop.add_item.add"),
            button -> addItemToList()
        ).bounds(popupX + 20, popupY + 220, 80, 20).build();
        this.addRenderableWidget(this.addButton);
        
        // Cancel button
        this.cancelButton = Button.builder(
            Component.translatable("gui.servershop.add_item.cancel"),
            button -> onClose()
        ).bounds(popupX + 120, popupY + 220, 80, 20).build();
        this.addRenderableWidget(this.cancelButton);
    }
    
    private void onItemIdChanged(String itemId) {
        itemIdError = null;
        selectedItem = null;
        
        if (itemId.isEmpty()) {
            return;
        }
        
        try {
            ResourceLocation itemLocation = ResourceLocation.parse(itemId);
            
            if (BuiltInRegistries.ITEM.containsKey(itemLocation)) {
                Item item = BuiltInRegistries.ITEM.get(itemLocation);
                selectedItem = new ItemStack(item, 1);
            } else {
                itemIdError = "Invalid item ID: " + itemId;
            }
        } catch (Exception e) {
            itemIdError = "Invalid format: " + itemId;
        }
    }
    
    private void addItemToList() {
        try {
            // Get input values
            String itemId = this.itemIdBox.getValue();
            String buyPriceStr = this.buyPriceBox.getValue();
            String sellPriceStr = this.sellPriceBox.getValue();
            String quantityStr = this.quantityBox.getValue();
            
            // Validate inputs
            if (itemId.isEmpty() || buyPriceStr.isEmpty() || sellPriceStr.isEmpty() || quantityStr.isEmpty()) {
                ServerShop.LOGGER.warn("All fields must be filled");
                return;
            }
            
            if (selectedItem == null) {
                ServerShop.LOGGER.warn("Please enter a valid item ID");
                return;
            }
            
            int buyPrice, sellPrice, quantity;
            try {
                buyPrice = Integer.parseInt(buyPriceStr);
                sellPrice = Integer.parseInt(sellPriceStr);
                quantity = Integer.parseInt(quantityStr);
            } catch (NumberFormatException e) {
                ServerShop.LOGGER.warn("Invalid number format in price or quantity fields");
                return;
            }
            
            // Get the selected item stack
            ItemStack itemStack = selectedItem.copy();
            
            // Create marketplace item
            MarketplaceItem marketplaceItem = new MarketplaceItem(
                itemStack, 
                buyPrice, 
                sellPrice, 
                quantity, 
                "admin" // TODO: Get actual player name
            );
            
            // Add to marketplace via client data manager
            ClientMarketplaceDataManager.addMarketplaceItem(marketplaceItem);
            
            ServerShop.LOGGER.info("Added item to marketplace: {} - Buy: {} - Sell: {} - Quantity: {}", 
                itemStack.getItem().getDescription().getString(), buyPrice, sellPrice, quantity);
            
            // Close the popup
            onClose();
            
        } catch (Exception e) {
            ServerShop.LOGGER.error("Failed to add item to marketplace", e);
        }
    }
    
    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Calculate popup dimensions and position
        int popupWidth = 350;
        int popupHeight = 280;
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;
        
        // Draw semi-transparent background FIRST (behind everything)
        int overlayAlpha = 200; // More opaque than main screen
        guiGraphics.fill(0, 0, this.width, this.height, (overlayAlpha << 24) | 0x000000);
        
        // Draw popup background (on top of blur)
        guiGraphics.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, 0xFF2C2C2C);
        guiGraphics.fill(popupX + 1, popupY + 1, popupX + popupWidth - 1, popupY + popupHeight - 1, 0xFF404040);
        
        // Draw popup border
        guiGraphics.fill(popupX, popupY, popupX + popupWidth, popupY + 1, 0xFF808080);
        guiGraphics.fill(popupX, popupY, popupX + 1, popupY + popupHeight, 0xFF808080);
        guiGraphics.fill(popupX + popupWidth - 1, popupY, popupX + popupWidth, popupY + popupHeight, 0xFF808080);
        guiGraphics.fill(popupX, popupY + popupHeight - 1, popupX + popupWidth, popupY + popupHeight, 0xFF808080);
        
        // Render all widgets first
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Draw title (after widgets to ensure it's on top)
        int titleWidth = this.font.width(this.title);
        int titleX = popupX + (popupWidth - titleWidth) / 2;
        guiGraphics.drawString(this.font, this.title, titleX, popupY + 10, 0xFFFFFF);
        
        // Draw labels (after widgets to ensure they're on top)
        guiGraphics.drawString(this.font, Component.translatable("gui.servershop.add_item.item_id"), 
            popupX + 20, popupY + 40, 0xCCCCCC);
        guiGraphics.drawString(this.font, Component.translatable("gui.servershop.add_item.buy_price"), 
            popupX + 20, popupY + 80, 0xCCCCCC);
        guiGraphics.drawString(this.font, Component.translatable("gui.servershop.add_item.sell_price"), 
            popupX + 20, popupY + 120, 0xCCCCCC);
        guiGraphics.drawString(this.font, Component.translatable("gui.servershop.add_item.quantity"), 
            popupX + 20, popupY + 160, 0xCCCCCC);
        
        // Draw item preview and error messages
        if (selectedItem != null) {
            // Draw item icon
            guiGraphics.renderItem(selectedItem, popupX + 250, popupY + 50);
            guiGraphics.renderItemDecorations(this.font, selectedItem, popupX + 250, popupY + 50);
            
            // Draw item name
            String itemName = selectedItem.getItem().getDescription().getString();
            if (itemName.length() > 15) {
                itemName = itemName.substring(0, 15) + "...";
            }
            guiGraphics.drawString(this.font, itemName, popupX + 250, popupY + 70, 0xFFFFFF);
        } else if (itemIdError != null) {
            // Draw error message
            guiGraphics.drawString(this.font, itemIdError, popupX + 250, popupY + 50, 0xFF6666);
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
    
        @Override
        public void onClose() {
            // Refresh the marketplace before returning to parent screen
            if (parentScreen != null) {
                parentScreen.refreshMarketplace();
            }
            
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parentScreen);
            }
        }
}
