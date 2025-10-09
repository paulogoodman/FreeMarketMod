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
import com.servershop.common.attachments.ItemComponentHandler;

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
    private EditBox componentDataBox;
    private Button addButton;
    private Button cancelButton;
    
    // Current selected item
    private ItemStack selectedItem = null;
    private String itemIdError = null;
    private String componentDataError = null;
    
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
        
        // Pre-validate the default diamond value to show icon immediately
        onItemIdChanged("minecraft:diamond");
        
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
        
        // Component data input (optional) - stores all item components as JSON
        this.componentDataBox = new EditBox(this.font, popupX + GuiScalingHelper.responsiveWidth(20, 15, 30), popupY + GuiScalingHelper.responsiveHeight(210, 175, 230), 
            GuiScalingHelper.responsiveWidth(450, 360, 550), GuiScalingHelper.responsiveHeight(20, 16, 26), 
            Component.translatable("gui.servershop.add_item.component_data"));
        this.componentDataBox.setValue(""); // Start empty - users can add their own
        this.componentDataBox.setResponder(this::onComponentDataChanged);
        this.componentDataBox.setMaxLength(Integer.MAX_VALUE);
        this.addRenderableWidget(this.componentDataBox);
        
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
    
    private void onComponentDataChanged(String componentDataString) {
        componentDataError = null;
        try {
            if (componentDataString != null && !componentDataString.trim().isEmpty()) {
                // Validate component data JSON format
                net.minecraft.nbt.TagParser.parseTag(componentDataString.trim());
            }
        } catch (Exception e) {
            componentDataError = "Invalid component data format";
        }
    }
    
    
    private ItemStack createItemWithComponents(Item item, int count, String componentDataString) {
        ItemStack itemStack = new ItemStack(item, count);
        
        // Apply component data if provided
        if (componentDataString != null && !componentDataString.trim().isEmpty()) {
            ServerShop.LOGGER.info("Applying component data to item: {}", componentDataString);
            ItemComponentHandler.applyComponentData(itemStack, componentDataString);
            ServerShop.LOGGER.info("Component data applied. Item now has components: {}", 
                ItemComponentHandler.hasComponentData(itemStack));
        } else {
            ServerShop.LOGGER.info("No component data provided for item");
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
            
            // Validate item ID first (in case user clicked Add without losing focus)
            onItemIdChanged(itemId);
            
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
            
            // Validate component data if provided
            if (componentDataError != null) {
                ServerShop.LOGGER.warn("Component data validation failed: {}", componentDataError);
                return;
            }
            
            // Get the selected item stack with component data applied
            ItemStack itemStack = createItemWithComponents(selectedItem.getItem(), quantity, 
                this.componentDataBox.getValue());
            
            // Create marketplace item with component data
            MarketplaceItem marketplaceItem = new MarketplaceItem(
                itemStack, 
                buyPrice, 
                sellPrice, 
                quantity, 
                "admin", // TODO: Get actual player name
                null, // GUID will be generated
                this.componentDataBox.getValue() // Pass the component data from the text field
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
        
        // Call super.render() FIRST to draw the blur overlay behind everything
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Draw popup background on top of the blur (matching MarketplaceContainer colors)
        guiGraphics.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, 0xFF1E1E1E);
        guiGraphics.fill(popupX + 1, popupY + 1, popupX + popupWidth - 1, popupY + popupHeight - 1, 0xFF2A2A2A);
        

        // Draw popup border
        guiGraphics.fill(popupX, popupY, popupX + popupWidth, popupY + 1, 0xFF808080);
        guiGraphics.fill(popupX, popupY, popupX + 1, popupY + popupHeight, 0xFF808080);
        guiGraphics.fill(popupX + popupWidth - 1, popupY, popupX + popupWidth, popupY + popupHeight, 0xFF808080);
        guiGraphics.fill(popupX, popupY + popupHeight - 1, popupX + popupWidth, popupY + popupHeight, 0xFF808080);
        
        // Render all widgets on top of the background
        if (itemIdBox != null) itemIdBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (buyPriceBox != null) buyPriceBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (sellPriceBox != null) sellPriceBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (quantityBox != null) quantityBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (componentDataBox != null) componentDataBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (addButton != null) addButton.render(guiGraphics, mouseX, mouseY, partialTick);
        if (cancelButton != null) cancelButton.render(guiGraphics, mouseX, mouseY, partialTick);
        
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
        guiGraphics.drawString(this.font, Component.translatable("gui.servershop.add_item.component_data"), 
            popupX + GuiScalingHelper.responsiveWidth(20, 15, 30), popupY + GuiScalingHelper.responsiveHeight(200, 165, 200), 0xCCCCCC);
        
        // Draw item preview and error messages
        if (selectedItem != null) {
            // Calculate item preview size and position (50x50 with responsive scaling)
            int itemPreviewSize = GuiScalingHelper.responsiveWidth(50, 40, 60);
            int itemPreviewX = popupX + GuiScalingHelper.responsiveWidth(300, 250, 350);
            int itemPreviewY = popupY + GuiScalingHelper.responsiveHeight(50, 40, 65);
            
            // Draw item icon with scaling (similar to MarketplaceContainer)
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(itemPreviewX, itemPreviewY, 0);
            float scale = (float) itemPreviewSize / 16f; // Scale to fit the responsive size
            guiGraphics.pose().scale(scale, scale, 1.0f);
            guiGraphics.renderItem(selectedItem, 0, 0);
            guiGraphics.renderItemDecorations(this.font, selectedItem, 0, 0);
            guiGraphics.pose().popPose();
            
            // Draw item name (centered underneath the item image)
            String itemName = selectedItem.getItem().getDescription().getString();
            if (itemName.length() > 15) {
                itemName = itemName.substring(0, 15) + "...";
            }
            int itemNameWidth = this.font.width(itemName);
            int itemNameX = itemPreviewX + (itemPreviewSize - itemNameWidth) / 2; // Center horizontally
            guiGraphics.drawString(this.font, itemName, itemNameX, itemPreviewY + itemPreviewSize + GuiScalingHelper.responsiveHeight(5, 4, 8), 0xFFFFFF);
        } else if (itemIdError != null) {
            // Calculate error preview size and position (50x50 with responsive scaling)
            int errorPreviewSize = GuiScalingHelper.responsiveWidth(50, 40, 60);
            int errorPreviewX = popupX + GuiScalingHelper.responsiveWidth(300, 250, 350);
            int errorPreviewY = popupY + GuiScalingHelper.responsiveHeight(50, 40, 65);
            
            // Draw barrier item (red X) with scaling
            ItemStack barrierItem = new ItemStack(net.minecraft.world.item.Items.BARRIER);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(errorPreviewX, errorPreviewY, 0);
            float scale = (float) errorPreviewSize / 16f; // Scale to fit the responsive size
            guiGraphics.pose().scale(scale, scale, 1.0f);
            guiGraphics.renderItem(barrierItem, 0, 0);
            guiGraphics.renderItemDecorations(this.font, barrierItem, 0, 0);
            guiGraphics.pose().popPose();
            
            // Draw "Invalid Item ID" text (centered underneath the barrier item)
            String errorText = "Invalid Item ID";
            int errorTextWidth = this.font.width(errorText);
            int errorTextX = errorPreviewX + (errorPreviewSize - errorTextWidth) / 2; // Center horizontally
            guiGraphics.drawString(this.font, errorText, errorTextX, errorPreviewY + errorPreviewSize + GuiScalingHelper.responsiveHeight(5, 4, 8), 0xFF6666);
        }
        
        // Draw component data error message
        if (componentDataError != null) {
            guiGraphics.drawString(this.font, componentDataError, popupX + GuiScalingHelper.responsiveWidth(20, 15, 30), popupY + GuiScalingHelper.responsiveHeight(230, 195, 250), 0xFF6666);
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


