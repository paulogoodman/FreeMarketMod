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
    private EditBox nbtBox;
    private Button addButton;
    private Button cancelButton;
    
    // Current selected item
    private ItemStack selectedItem = null;
    private String itemIdError = null;
    private String nbtError = null;
    
    public AddItemPopupScreen(ShopGuiScreen parent) {
        super(Component.translatable("gui.servershop.add_item.title"));
        this.parentScreen = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Calculate popup dimensions and position (centered) with responsive scaling
        int popupWidth = GuiScalingHelper.responsiveWidth(500, 400, 600);
        int popupHeight = GuiScalingHelper.responsiveHeight(320, 260, 400);
        int popupX = GuiScalingHelper.centerX(popupWidth);
        int popupY = GuiScalingHelper.centerY(popupHeight);
        
        // Item ID input
        this.itemIdBox = new EditBox(this.font, popupX + GuiScalingHelper.responsiveWidth(20, 15, 30), popupY + GuiScalingHelper.responsiveHeight(50, 40, 65), 
            GuiScalingHelper.responsiveWidth(200, 160, 250), GuiScalingHelper.responsiveHeight(20, 16, 26), 
            Component.translatable("gui.servershop.add_item.item_id"));
        this.itemIdBox.setValue("minecraft:diamond");
        this.itemIdBox.setResponder(this::onItemIdChanged);
        this.addRenderableWidget(this.itemIdBox);
        
        // Buy price input
        this.buyPriceBox = new EditBox(this.font, popupX + GuiScalingHelper.responsiveWidth(20, 15, 30), popupY + GuiScalingHelper.responsiveHeight(90, 75, 110), 
            GuiScalingHelper.responsiveWidth(120, 100, 150), GuiScalingHelper.responsiveHeight(20, 16, 26), 
            Component.translatable("gui.servershop.add_item.buy_price"));
        this.buyPriceBox.setValue("100");
        this.addRenderableWidget(this.buyPriceBox);
        
        // Sell price input
        this.sellPriceBox = new EditBox(this.font, popupX + GuiScalingHelper.responsiveWidth(20, 15, 30), popupY + GuiScalingHelper.responsiveHeight(130, 110, 150), 
            GuiScalingHelper.responsiveWidth(120, 100, 150), GuiScalingHelper.responsiveHeight(20, 16, 26), 
            Component.translatable("gui.servershop.add_item.sell_price"));
        this.sellPriceBox.setValue("80");
        this.addRenderableWidget(this.sellPriceBox);
        
        // Quantity input
        this.quantityBox = new EditBox(this.font, popupX + GuiScalingHelper.responsiveWidth(20, 15, 30), popupY + GuiScalingHelper.responsiveHeight(170, 145, 190), 
            GuiScalingHelper.responsiveWidth(120, 100, 150), GuiScalingHelper.responsiveHeight(20, 16, 26), 
            Component.translatable("gui.servershop.add_item.quantity"));
        this.quantityBox.setValue("1");
        this.addRenderableWidget(this.quantityBox);
        
        // NBT input (optional) - much wider for long NBT strings
        this.nbtBox = new EditBox(this.font, popupX + GuiScalingHelper.responsiveWidth(20, 15, 30), popupY + GuiScalingHelper.responsiveHeight(210, 175, 230), 
            GuiScalingHelper.responsiveWidth(450, 360, 550), GuiScalingHelper.responsiveHeight(20, 16, 26), 
            Component.translatable("gui.servershop.add_item.nbt"));
        this.nbtBox.setValue("");
        this.nbtBox.setResponder(this::onNbtChanged);
        // Remove character limit for NBT field to allow long strings
        this.nbtBox.setMaxLength(Integer.MAX_VALUE);
        this.addRenderableWidget(this.nbtBox);
        
        // Add button
        this.addButton = Button.builder(
            Component.translatable("gui.servershop.add_item.add"),
            button -> addItemToList()
        ).bounds(popupX + GuiScalingHelper.responsiveWidth(20, 15, 30), popupY + GuiScalingHelper.responsiveHeight(250, 220, 280), 
            GuiScalingHelper.responsiveWidth(80, 60, 100), GuiScalingHelper.responsiveHeight(20, 16, 26)).build();
        this.addRenderableWidget(this.addButton);
        
        // Cancel button
        this.cancelButton = Button.builder(
            Component.translatable("gui.servershop.add_item.cancel"),
            button -> onClose()
        ).bounds(popupX + GuiScalingHelper.responsiveWidth(120, 100, 150), popupY + GuiScalingHelper.responsiveHeight(250, 220, 280), 
            GuiScalingHelper.responsiveWidth(80, 60, 100), GuiScalingHelper.responsiveHeight(20, 16, 26)).build();
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
    
    private void onNbtChanged(String nbtString) {
        nbtError = null;
        
        if (nbtString.trim().isEmpty()) {
            return;
        }
        
        try {
            // Validate NBT string format
            net.minecraft.nbt.TagParser.parseTag(nbtString.trim());
        } catch (Exception e) {
            nbtError = "Invalid NBT format";
        }
    }
    
    private ItemStack createItemWithNbt(Item item, int count, String nbtString) {
        ItemStack itemStack = new ItemStack(item, count);
        
        if (nbtString != null && !nbtString.trim().isEmpty()) {
            // TODO: Implement NBT application for NeoForge 1.21
            // The NBT methods have changed in NeoForge 1.21 and need to be researched
            ServerShop.LOGGER.info("NBT data provided but not yet implemented: {}", nbtString);
        }
        
        return itemStack;
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
            
            // Validate NBT if provided
            String nbtString = this.nbtBox.getValue().trim();
            if (!nbtString.isEmpty() && nbtError != null) {
                ServerShop.LOGGER.warn("Invalid NBT format");
                return;
            }
            
            // Get the selected item stack with NBT applied
            ItemStack itemStack = createItemWithNbt(selectedItem.getItem(), quantity, nbtString);
            
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
        // Calculate popup dimensions and position with responsive scaling
        int popupWidth = GuiScalingHelper.responsiveWidth(500, 400, 600);
        int popupHeight = GuiScalingHelper.responsiveHeight(320, 260, 400);
        int popupX = GuiScalingHelper.centerX(popupWidth);
        int popupY = GuiScalingHelper.centerY(popupHeight);
        
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
        guiGraphics.drawString(this.font, this.title, titleX, popupY + GuiScalingHelper.responsiveHeight(10, 8, 15), 0xFFFFFF);
        
        // Draw labels (after widgets to ensure they're on top)
        guiGraphics.drawString(this.font, Component.translatable("gui.servershop.add_item.item_id"), 
            popupX + GuiScalingHelper.responsiveWidth(20, 15, 30), popupY + GuiScalingHelper.responsiveHeight(40, 32, 50), 0xCCCCCC);
        guiGraphics.drawString(this.font, Component.translatable("gui.servershop.add_item.buy_price"), 
            popupX + GuiScalingHelper.responsiveWidth(20, 15, 30), popupY + GuiScalingHelper.responsiveHeight(80, 65, 95), 0xCCCCCC);
        guiGraphics.drawString(this.font, Component.translatable("gui.servershop.add_item.sell_price"), 
            popupX + GuiScalingHelper.responsiveWidth(20, 15, 30), popupY + GuiScalingHelper.responsiveHeight(120, 95, 140), 0xCCCCCC);
        guiGraphics.drawString(this.font, Component.translatable("gui.servershop.add_item.quantity"), 
            popupX + GuiScalingHelper.responsiveWidth(20, 15, 30), popupY + GuiScalingHelper.responsiveHeight(160, 130, 180), 0xCCCCCC);
        guiGraphics.drawString(this.font, Component.translatable("gui.servershop.add_item.nbt"), 
            popupX + GuiScalingHelper.responsiveWidth(20, 15, 30), popupY + GuiScalingHelper.responsiveHeight(200, 165, 200), 0xCCCCCC);
        
        // Draw item preview and error messages
        if (selectedItem != null) {
            // Draw item icon
            guiGraphics.renderItem(selectedItem, popupX + GuiScalingHelper.responsiveWidth(250, 200, 300), popupY + GuiScalingHelper.responsiveHeight(50, 40, 65));
            guiGraphics.renderItemDecorations(this.font, selectedItem, popupX + GuiScalingHelper.responsiveWidth(250, 200, 300), popupY + GuiScalingHelper.responsiveHeight(50, 40, 65));
            
            // Draw item name
            String itemName = selectedItem.getItem().getDescription().getString();
            if (itemName.length() > 15) {
                itemName = itemName.substring(0, 15) + "...";
            }
            guiGraphics.drawString(this.font, itemName, popupX + GuiScalingHelper.responsiveWidth(250, 200, 300), popupY + GuiScalingHelper.responsiveHeight(70, 55, 85), 0xFFFFFF);
        } else if (itemIdError != null) {
            // Draw error message
            guiGraphics.drawString(this.font, itemIdError, popupX + GuiScalingHelper.responsiveWidth(250, 200, 300), popupY + GuiScalingHelper.responsiveHeight(50, 40, 65), 0xFF6666);
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
