package com.freemarket.client.gui;

import com.freemarket.FreeMarket;
import com.freemarket.common.network.FreeMarketPacket;
import com.freemarket.common.network.PacketType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Popup overlay for creating auctions.
 * Renders on top of the existing screen without replacing it.
 */
public class CreateAuctionPopupOverlay extends PopupOverlay {
    
    private EditBox itemIdBox;
    private EditBox startingPriceBox;
    private EditBox quantityBox;
    private EditBox durationBox;
    
    // UI Constants
    private static final int POPUP_WIDTH = 480;
    private static final int POPUP_HEIGHT = 300;
    
    public CreateAuctionPopupOverlay() {
        super(0, 0, POPUP_WIDTH, POPUP_HEIGHT);
    }
    
    @Override
    public void show() {
        super.show();
        
        // Calculate popup position (centered)
        int screenWidth = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
        
        // Update position to be centered
        this.x = (screenWidth - POPUP_WIDTH) / 2;
        this.y = (screenHeight - POPUP_HEIGHT) / 2;
        
        // Initialize input fields
        initializeInputFields();
    }
    
    private void initializeInputFields() {
        Minecraft minecraft = Minecraft.getInstance();
        
        // Item ID input
        this.itemIdBox = new EditBox(
            minecraft.font,
            x + 20,
            y + 70,
            POPUP_WIDTH - 40,
            20,
            Component.literal("Item ID")
        );
        this.itemIdBox.setValue("");
        this.itemIdBox.setMaxLength(256);
        
        // Quantity input
        this.quantityBox = new EditBox(
            minecraft.font,
            x + 20,
            y + 110,
            120,
            20,
            Component.literal("Quantity")
        );
        this.quantityBox.setValue("1");
        
        // Starting price input
        this.startingPriceBox = new EditBox(
            minecraft.font,
            x + 160,
            y + 110,
            120,
            20,
            Component.literal("Starting Price")
        );
        this.startingPriceBox.setValue("100");
        
        // Duration input
        this.durationBox = new EditBox(
            minecraft.font,
            x + 300,
            y + 110,
            120,
            20,
            Component.literal("Duration (minutes)")
        );
        this.durationBox.setValue("60");
    }
    
    @Override
    protected Component getTitle() {
        return Component.literal("Create Auction");
    }
    
    @Override
    protected void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        
        // Draw instructions
        String instruction = "Enter the item ID from your inventory:";
        guiGraphics.drawString(minecraft.font, instruction, x + 20, y + 50, TEXT_SECONDARY);
        
        // Draw labels
        guiGraphics.drawString(minecraft.font, "Item ID:", x + 20, y + 60, TEXT_SECONDARY);
        guiGraphics.drawString(minecraft.font, "Quantity:", x + 20, y + 100, TEXT_SECONDARY);
        guiGraphics.drawString(minecraft.font, "Starting Price:", x + 160, y + 100, TEXT_SECONDARY);
        guiGraphics.drawString(minecraft.font, "Duration (min):", x + 300, y + 100, TEXT_SECONDARY);
        
        // Render input fields
        if (itemIdBox != null) {
            itemIdBox.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (quantityBox != null) {
            quantityBox.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (startingPriceBox != null) {
            startingPriceBox.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (durationBox != null) {
            durationBox.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        
        // Draw buttons
        renderButtons(guiGraphics, mouseX, mouseY);
        
        // Draw help text
        String helpText = "Example: minecraft:diamond_sword";
        guiGraphics.drawString(minecraft.font, helpText, x + 20, y + 95, TEXT_MUTED);
    }
    
    private void renderButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        
        // Create button
        int createButtonX = x + 20;
        int createButtonY = y + 240;
        int createButtonWidth = 180;
        int createButtonHeight = 20;
        
        boolean createHovered = mouseX >= createButtonX && mouseX <= createButtonX + createButtonWidth &&
                               mouseY >= createButtonY && mouseY <= createButtonY + createButtonHeight;
        
        int createBgColor = createHovered ? 0xCC4CAF50 : 0x994CAF50;
        guiGraphics.fill(createButtonX, createButtonY, createButtonX + createButtonWidth, createButtonY + createButtonHeight, createBgColor);
        guiGraphics.fill(createButtonX, createButtonY, createButtonX + createButtonWidth, createButtonY + 1, BORDER_COLOR);
        guiGraphics.fill(createButtonX, createButtonY, createButtonX + 1, createButtonY + createButtonHeight, BORDER_COLOR);
        guiGraphics.fill(createButtonX + createButtonWidth - 1, createButtonY, createButtonX + createButtonWidth, createButtonY + createButtonHeight, BORDER_COLOR);
        guiGraphics.fill(createButtonX, createButtonY + createButtonHeight - 1, createButtonX + createButtonWidth, createButtonY + createButtonHeight, BORDER_COLOR);
        
        String createText = "Create Auction";
        int createTextWidth = minecraft.font.width(createText);
        int createTextX = createButtonX + (createButtonWidth - createTextWidth) / 2;
        int createTextY = createButtonY + (createButtonHeight - minecraft.font.lineHeight) / 2;
        guiGraphics.drawString(minecraft.font, createText, createTextX, createTextY, TEXT_PRIMARY);
        
        // Cancel button
        int cancelButtonX = x + 280;
        int cancelButtonY = y + 240;
        int cancelButtonWidth = 180;
        int cancelButtonHeight = 20;
        
        boolean cancelHovered = mouseX >= cancelButtonX && mouseX <= cancelButtonX + cancelButtonWidth &&
                               mouseY >= cancelButtonY && mouseY <= cancelButtonY + cancelButtonHeight;
        
        int cancelBgColor = cancelHovered ? 0xCC666666 : 0x99666666;
        guiGraphics.fill(cancelButtonX, cancelButtonY, cancelButtonX + cancelButtonWidth, cancelButtonY + cancelButtonHeight, cancelBgColor);
        guiGraphics.fill(cancelButtonX, cancelButtonY, cancelButtonX + cancelButtonWidth, cancelButtonY + 1, BORDER_COLOR);
        guiGraphics.fill(cancelButtonX, cancelButtonY, cancelButtonX + 1, cancelButtonY + cancelButtonHeight, BORDER_COLOR);
        guiGraphics.fill(cancelButtonX + cancelButtonWidth - 1, cancelButtonY, cancelButtonX + cancelButtonWidth, cancelButtonY + cancelButtonHeight, BORDER_COLOR);
        guiGraphics.fill(cancelButtonX, cancelButtonY + cancelButtonHeight - 1, cancelButtonX + cancelButtonWidth, cancelButtonY + cancelButtonHeight, BORDER_COLOR);
        
        String cancelText = "Cancel";
        int cancelTextWidth = minecraft.font.width(cancelText);
        int cancelTextX = cancelButtonX + (cancelButtonWidth - cancelTextWidth) / 2;
        int cancelTextY = cancelButtonY + (cancelButtonHeight - minecraft.font.lineHeight) / 2;
        guiGraphics.drawString(minecraft.font, cancelText, cancelTextX, cancelTextY, TEXT_PRIMARY);
    }
    
    @Override
    protected boolean handlePopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false; // Only left click
        
        // Handle input field clicks
        if (itemIdBox != null && itemIdBox.mouseClicked(mouseX, mouseY, button)) {
            itemIdBox.setFocused(true);
            return true;
        }
        if (quantityBox != null && quantityBox.mouseClicked(mouseX, mouseY, button)) {
            quantityBox.setFocused(true);
            return true;
        }
        if (startingPriceBox != null && startingPriceBox.mouseClicked(mouseX, mouseY, button)) {
            startingPriceBox.setFocused(true);
            return true;
        }
        if (durationBox != null && durationBox.mouseClicked(mouseX, mouseY, button)) {
            durationBox.setFocused(true);
            return true;
        }
        
        // Handle button clicks
        int createButtonX = x + 20;
        int createButtonY = y + 240;
        int createButtonWidth = 180;
        int createButtonHeight = 20;
        
        if (mouseX >= createButtonX && mouseX <= createButtonX + createButtonWidth &&
            mouseY >= createButtonY && mouseY <= createButtonY + createButtonHeight) {
            createAuction();
            return true;
        }
        
        int cancelButtonX = x + 280;
        int cancelButtonY = y + 240;
        int cancelButtonWidth = 180;
        int cancelButtonHeight = 20;
        
        if (mouseX >= cancelButtonX && mouseX <= cancelButtonX + cancelButtonWidth &&
            mouseY >= cancelButtonY && mouseY <= cancelButtonY + cancelButtonHeight) {
            hide();
            return true;
        }
        
        return false;
    }
    
    @Override
    protected boolean handlePopupKeyPress(int keyCode, int scanCode, int modifiers) {
        // Handle input field key presses
        if (itemIdBox != null && itemIdBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (quantityBox != null && quantityBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (startingPriceBox != null && startingPriceBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (durationBox != null && durationBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        
        return false;
    }
    
    @Override
    protected boolean handlePopupCharTyped(char codePoint, int modifiers) {
        // Handle input field character typing
        if (itemIdBox != null && itemIdBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (quantityBox != null && quantityBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (startingPriceBox != null && startingPriceBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (durationBox != null && durationBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        
        return false;
    }
    
    private void createAuction() {
        clearError();
        
        // Get input values
        String itemId = itemIdBox.getValue();
        String quantityStr = quantityBox.getValue();
        String startingPriceStr = startingPriceBox.getValue();
        String durationStr = durationBox.getValue();
        
        // Validate inputs
        if (itemId.isEmpty()) {
            setErrorMessage("Please enter an item ID (e.g., minecraft:diamond_sword)");
            return;
        }
        
        // Validate item exists
        try {
            ResourceLocation itemLocation = ResourceLocation.parse(itemId);
            if (!BuiltInRegistries.ITEM.containsKey(itemLocation)) {
                setErrorMessage("Item not found: " + itemId);
                return;
            }
        } catch (Exception e) {
            setErrorMessage("Invalid item ID format");
            return;
        }
        
        // Parse and validate quantity
        int quantity;
        try {
            quantity = Integer.parseInt(quantityStr);
            if (quantity < 1) {
                setErrorMessage("Quantity must be at least 1");
                return;
            }
        } catch (NumberFormatException e) {
            setErrorMessage("Invalid quantity");
            return;
        }
        
        // Parse and validate starting price
        long startingPrice;
        try {
            startingPrice = Long.parseLong(startingPriceStr);
            if (startingPrice < 1) {
                setErrorMessage("Starting price must be at least $1");
                return;
            }
        } catch (NumberFormatException e) {
            setErrorMessage("Invalid starting price");
            return;
        }
        
        // Parse and validate duration
        long durationMinutes;
        try {
            durationMinutes = Long.parseLong(durationStr);
            if (durationMinutes < 1 || durationMinutes > 10080) { // Max 1 week
                setErrorMessage("Duration must be between 1 minute and 1 week (10080 minutes)");
                return;
            }
        } catch (NumberFormatException e) {
            setErrorMessage("Invalid duration");
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
        
        // Hide popup
        hide();
    }
}
