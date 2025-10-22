package com.freemarket.client.gui;

import com.freemarket.FreeMarket;
import com.freemarket.common.network.FreeMarketPacket;
import com.freemarket.common.network.PacketType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * Popup screen for creating auctions.
 * Simplified version - accepts item ID from player inventory, starting price, and duration.
 */
public class CreateAuctionPopupScreen extends Screen {
    
    private final FreeMarketGuiScreen parentScreen;
    private EditBox itemIdBox;
    private EditBox startingPriceBox;
    private EditBox quantityBox;
    private EditBox durationBox;
    private Button createButton;
    private Button cancelButton;
    private String errorMessage = null;
    
    public CreateAuctionPopupScreen(FreeMarketGuiScreen parent) {
        super(Component.literal("Create Auction"));
        this.parentScreen = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Calculate popup dimensions (centered, responsive)
        int popupWidth = GuiScalingHelper.responsiveWidth(480, 380, 560);
        int popupHeight = GuiScalingHelper.responsiveHeight(300, 250, 350);
        int popupX = GuiScalingHelper.centerX(popupWidth);
        int popupY = GuiScalingHelper.centerY(popupHeight);
        
        // Item ID input
        this.itemIdBox = new EditBox(
            this.font,
            popupX + GuiScalingHelper.responsiveWidth(20, 15, 30),
            popupY + GuiScalingHelper.responsiveHeight(70, 60, 85),
            GuiScalingHelper.responsiveWidth(440, 350, 500),
            GuiScalingHelper.responsiveHeight(20, 16, 26),
            Component.literal("Item ID")
        );
        this.itemIdBox.setValue("");
        this.itemIdBox.setMaxLength(256);
        this.addRenderableWidget(this.itemIdBox);
        
        // Quantity input
        this.quantityBox = new EditBox(
            this.font,
            popupX + GuiScalingHelper.responsiveWidth(20, 15, 30),
            popupY + GuiScalingHelper.responsiveHeight(110, 90, 125),
            GuiScalingHelper.responsiveWidth(120, 100, 150),
            GuiScalingHelper.responsiveHeight(20, 16, 26),
            Component.literal("Quantity")
        );
        this.quantityBox.setValue("1");
        this.addRenderableWidget(this.quantityBox);
        
        // Starting price input
        this.startingPriceBox = new EditBox(
            this.font,
            popupX + GuiScalingHelper.responsiveWidth(20, 15, 30),
            popupY + GuiScalingHelper.responsiveHeight(150, 125, 165),
            GuiScalingHelper.responsiveWidth(200, 160, 240),
            GuiScalingHelper.responsiveHeight(20, 16, 26),
            Component.literal("Starting Price")
        );
        this.startingPriceBox.setValue("100");
        this.addRenderableWidget(this.startingPriceBox);
        
        // Duration input (in minutes)
        this.durationBox = new EditBox(
            this.font,
            popupX + GuiScalingHelper.responsiveWidth(20, 15, 30),
            popupY + GuiScalingHelper.responsiveHeight(190, 160, 205),
            GuiScalingHelper.responsiveWidth(200, 160, 240),
            GuiScalingHelper.responsiveHeight(20, 16, 26),
            Component.literal("Duration (minutes)")
        );
        this.durationBox.setValue("1440"); // Default 24 hours
        this.addRenderableWidget(this.durationBox);
        
        // Create Auction button
        this.createButton = Button.builder(
            Component.literal("Create Auction"),
            button -> createAuction()
        ).bounds(
            popupX + GuiScalingHelper.responsiveWidth(20, 15, 30),
            popupY + GuiScalingHelper.responsiveHeight(240, 205, 270),
            GuiScalingHelper.responsiveWidth(180, 140, 220),
            GuiScalingHelper.responsiveHeight(20, 16, 26)
        ).build();
        this.addRenderableWidget(this.createButton);
        
        // Cancel button
        this.cancelButton = Button.builder(
            Component.literal("Cancel"),
            button -> onClose()
        ).bounds(
            popupX + GuiScalingHelper.responsiveWidth(280, 230, 330),
            popupY + GuiScalingHelper.responsiveHeight(240, 205, 270),
            GuiScalingHelper.responsiveWidth(180, 140, 220),
            GuiScalingHelper.responsiveHeight(20, 16, 26)
        ).build();
        this.addRenderableWidget(this.cancelButton);
    }
    
    private void createAuction() {
        errorMessage = null; // Clear previous error
        
        // Get input values
        String itemId = this.itemIdBox.getValue();
        String quantityStr = this.quantityBox.getValue();
        String startingPriceStr = this.startingPriceBox.getValue();
        String durationStr = this.durationBox.getValue();
        
        // Validate inputs
        if (itemId.isEmpty()) {
            errorMessage = "Please enter an item ID (e.g., minecraft:diamond_sword)";
            return;
        }
        
        // Validate item exists in player inventory
        try {
            ResourceLocation itemLocation = ResourceLocation.parse(itemId);
            if (!BuiltInRegistries.ITEM.containsKey(itemLocation)) {
                errorMessage = "Invalid item ID";
                return;
            }
            
            // Check if player has the item
            if (this.minecraft != null && this.minecraft.player != null) {
                Inventory inventory = this.minecraft.player.getInventory();
                ItemStack targetItem = new ItemStack(BuiltInRegistries.ITEM.get(itemLocation), 1);
                boolean hasItem = false;
                
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    ItemStack slotItem = inventory.getItem(i);
                    if (!slotItem.isEmpty() && slotItem.is(targetItem.getItem())) {
                        hasItem = true;
                        break;
                    }
                }
                
                if (!hasItem) {
                    errorMessage = "You don't have this item in your inventory!";
                    return;
                }
            }
        } catch (Exception e) {
            errorMessage = "Invalid item ID format";
            return;
        }
        
        // Parse and validate quantity
        int quantity;
        try {
            quantity = Integer.parseInt(quantityStr);
            if (quantity < 1 || quantity > 64) {
                errorMessage = "Quantity must be between 1 and 64";
                return;
            }
        } catch (NumberFormatException e) {
            errorMessage = "Invalid quantity";
            return;
        }
        
        // Parse and validate starting price
        long startingPrice;
        try {
            startingPrice = Long.parseLong(startingPriceStr);
            if (startingPrice < 1) {
                errorMessage = "Starting price must be at least $1";
                return;
            }
        } catch (NumberFormatException e) {
            errorMessage = "Invalid starting price";
            return;
        }
        
        // Parse and validate duration
        long durationMinutes;
        try {
            durationMinutes = Long.parseLong(durationStr);
            if (durationMinutes < 1 || durationMinutes > 10080) { // Max 1 week
                errorMessage = "Duration must be between 1 minute and 1 week (10080 minutes)";
                return;
            }
        } catch (NumberFormatException e) {
            errorMessage = "Invalid duration";
            return;
        }
        
        // Get component data from the item in inventory (if any)
        // TODO: Serialize component data from actual item in inventory
        // For now, use empty component data - server will still remove the item correctly
        String componentData = "{}";
        
        // Send auction create packet to server
        String jsonData = String.format("{\"itemId\":\"%s\",\"componentData\":\"%s\",\"quantity\":%d,\"startingPrice\":%d,\"durationMinutes\":%d}", 
            itemId, componentData, quantity, startingPrice, durationMinutes);
        FreeMarketPacket packet = FreeMarketPacket.withJson(PacketType.AUCTION_CREATE, jsonData);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
        
        FreeMarket.LOGGER.info("Created auction for {} x{} starting at ${} for {} minutes", 
            itemId, quantity, startingPrice, durationMinutes);
        
        // Close popup and return to auction screen
        onClose();
    }
    
    /**
     * Renders only the background elements of the parent screen without widgets.
     * This prevents z-order issues where item cards render on top of popups.
     */
    private void renderParentBackground(GuiGraphics guiGraphics, float partialTick) {
        // Draw wallet display in top right of screen
        parentScreen.renderWalletDisplay(guiGraphics);
        
        // Render tab navigation buttons
        parentScreen.renderTabButtons(guiGraphics, -1, -1);
        
        // Render the appropriate container based on current screen (background only)
        switch (parentScreen.getCurrentScreen()) {
            case MARKETPLACE:
                if (parentScreen.freeMarketContainer != null) {
                    parentScreen.freeMarketContainer.render(guiGraphics, -1, -1, partialTick);
                }
                break;
            case AUCTIONS:
                if (parentScreen.auctionContainer != null) {
                    parentScreen.auctionContainer.render(guiGraphics, -1, -1, partialTick);
                }
                break;
            case LEADERBOARD:
                if (parentScreen.leaderboardContainer != null) {
                    parentScreen.leaderboardContainer.render(guiGraphics, -1, -1, partialTick);
                }
                break;
        }
    }
    
    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Calculate popup dimensions first
        int popupWidth = GuiScalingHelper.responsiveWidth(480, 380, 560);
        int popupHeight = GuiScalingHelper.responsiveHeight(300, 250, 350);
        int popupX = GuiScalingHelper.centerX(popupWidth);
        int popupY = GuiScalingHelper.centerY(popupHeight);
        
        // Render only the background elements of the parent screen (not widgets)
        if (parentScreen != null) {
            renderParentBackground(guiGraphics, partialTick);
        }
        
        // Apply semi-transparent overlay with blur effect over entire screen
        guiGraphics.fill(0, 0, this.width, this.height, 0xA0000000);
        
        // Draw popup background (container) on top of the blur (matching container colors)
        guiGraphics.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, 0xFF1E1E1E);
        guiGraphics.fill(popupX + 1, popupY + 1, popupX + popupWidth - 1, popupY + popupHeight - 1, 0xFF2A2A2A);
        
        // Draw border
        guiGraphics.fill(popupX, popupY, popupX + popupWidth, popupY + 2, 0xFF404040);
        guiGraphics.fill(popupX, popupY, popupX + 2, popupY + popupHeight, 0xFF404040);
        guiGraphics.fill(popupX + popupWidth - 2, popupY, popupX + popupWidth, popupY + popupHeight, 0xFF404040);
        guiGraphics.fill(popupX, popupY + popupHeight - 2, popupX + popupWidth, popupY + popupHeight, 0xFF404040);
        
        // Draw title
        Component title = Component.literal("Create Auction");
        int titleWidth = this.font.width(title);
        int titleX = popupX + (popupWidth - titleWidth) / 2;
        int titleY = popupY + GuiScalingHelper.responsiveHeight(10, 8, 12);
        guiGraphics.drawString(this.font, title, titleX, titleY, 0xFFFFFFFF);
        
        // Draw instructions
        String instruction = "Enter the item ID from your inventory:";
        int instY = popupY + GuiScalingHelper.responsiveHeight(50, 42, 60);
        guiGraphics.drawString(this.font, instruction, popupX + 20, instY, 0xFFAAAAAA);
        
        // Draw field labels - THIRD LAYER
        int labelY1 = popupY + GuiScalingHelper.responsiveHeight(95, 80, 110);
        guiGraphics.drawString(this.font, "Quantity:", popupX + 20, labelY1, 0xFFAAAAAA);
        
        int labelY2 = popupY + GuiScalingHelper.responsiveHeight(135, 112, 150);
        guiGraphics.drawString(this.font, "Starting Price ($):", popupX + 20, labelY2, 0xFFAAAAAA);
        
        int labelY3 = popupY + GuiScalingHelper.responsiveHeight(175, 147, 190);
        guiGraphics.drawString(this.font, "Duration (minutes):", popupX + 20, labelY3, 0xFFAAAAAA);
        
        // Render widgets (text boxes and buttons) - FOURTH LAYER
        if (itemIdBox != null) itemIdBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (quantityBox != null) quantityBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (startingPriceBox != null) startingPriceBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (durationBox != null) durationBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (createButton != null) createButton.render(guiGraphics, mouseX, mouseY, partialTick);
        if (cancelButton != null) cancelButton.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Draw error message if present - FIFTH LAYER (on top)
        if (errorMessage != null) {
            int errorY = popupY + GuiScalingHelper.responsiveHeight(220, 190, 250);
            int errorWidth = this.font.width(errorMessage);
            int errorX = popupX + (popupWidth - errorWidth) / 2;
            guiGraphics.drawString(this.font, errorMessage, errorX, errorY, 0xFFFF5555);
        }
    }
    
    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parentScreen);
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        return false; // Don't pause game in multiplayer
    }
}

