package com.freemarket.client.gui;

import com.freemarket.FreeMarket;
import com.freemarket.common.attachments.ItemComponentHandler;
import com.freemarket.common.network.FreeMarketPacket;
import com.freemarket.common.network.PacketType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Popup screen for adding items to the marketplace with visual inventory selector.
 * Three-phase UI: Inventory Selection → Price Configuration → Confirmation
 */
public class AddItemPopupScreen extends BasePopupScreen {
    
    /**
     * Enum for tracking the current UI phase
     */
    private enum PopupState {
        INVENTORY_SELECTION,  // Phase 1: showing inventory grid
        FORM_INPUT,          // Phase 2: enter buy/sell prices
        CONFIRMATION         // Phase 3: confirm selected item
    }
    
    private PopupState currentState = PopupState.INVENTORY_SELECTION;
    
    // Selected item state
    private ItemStack selectedItem = ItemStack.EMPTY;
    private int selectedSlotIndex = -1;
    
    // Form input fields (Phase 2)
    private EditBox buyPriceBox;
    private EditBox sellPriceBox;
    
    public AddItemPopupScreen(FreeMarketGuiScreen parent) {
        super(Component.literal("Add Item to Marketplace"), parent);
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Initialize form fields for Phase 2
        initializeFormFields();
    }
    
    private void initializeFormFields() {
        // Buy price input
        this.buyPriceBox = new EditBox(
            this.font,
            popupX + 20,
            popupY + 125,
            200,
            20,
            Component.literal("Buy Price")
        );
        this.buyPriceBox.setValue("100");
        this.buyPriceBox.setMaxLength(10);
        
        // Sell price input
        this.sellPriceBox = new EditBox(
            this.font,
            popupX + 20,
            popupY + 175,
            200,
            20,
            Component.literal("Sell Price")
        );
        this.sellPriceBox.setValue("80");
        this.sellPriceBox.setMaxLength(10);
    }
    
    @Override
    protected void renderPopupContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Render content based on current state
        switch (currentState) {
            case INVENTORY_SELECTION:
                renderInventorySelection(guiGraphics, mouseX, mouseY, partialTick);
                break;
            case CONFIRMATION:
                renderConfirmation(guiGraphics, mouseX, mouseY, partialTick);
                break;
            case FORM_INPUT:
                renderFormInput(guiGraphics, mouseX, mouseY, partialTick);
                break;
        }
    }
    
    /**
     * Phase 1: Render inventory selection screen
     */
    private void renderInventorySelection(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw title
        Component title = Component.literal("Add Item to Marketplace");
        int titleWidth = this.font.width(title);
        int titleX = popupX + (POPUP_WIDTH - titleWidth) / 2;
        int titleY = popupY + 15;
        guiGraphics.drawString(this.font, title, titleX, titleY, 0xFFFFFFFF);
        
        // Draw instructions
        String instruction = "Select an item from your inventory:";
        int instWidth = this.font.width(instruction);
        int instX = popupX + (POPUP_WIDTH - instWidth) / 2;
        int instY = popupY + 40;
        guiGraphics.drawString(this.font, instruction, instX, instY, 0xFFAAAAAA);
        
        // Render inventory grid
        renderInventoryGrid(guiGraphics, mouseX, mouseY);
        
        // Render cancel button
        renderButton(guiGraphics, "Cancel", popupX + (POPUP_WIDTH - 180) / 2, popupY + POPUP_HEIGHT - 50, 180, 20, mouseX, mouseY, 0x99666666, 0xCC666666);
    }
    
    /**
     * Phase 3: Render confirmation dialog
     */
    private void renderConfirmation(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw title
        Component title = Component.literal("Confirm Marketplace Item");
        int titleWidth = this.font.width(title);
        int titleX = popupX + (POPUP_WIDTH - titleWidth) / 2;
        int titleY = popupY + 15;
        guiGraphics.drawString(this.font, title, titleX, titleY, 0xFFFFFFFF);
        
        // Draw large item icon with stack count
        int iconSize = 48;
        int iconX = popupX + (POPUP_WIDTH - iconSize) / 2;
        int iconY = popupY + 80;
        
        // Check if mouse is over icon for tooltip
        boolean isHovered = mouseX >= iconX && mouseX <= iconX + iconSize &&
                           mouseY >= iconY && mouseY <= iconY + iconSize;
        
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(iconX + iconSize / 2, iconY + iconSize / 2, 0);
        float scale = (float) iconSize / 16.0f;
        guiGraphics.pose().scale(scale, scale, scale);
        guiGraphics.renderItem(selectedItem, -8, -8);
        guiGraphics.renderItemDecorations(this.font, selectedItem, -8, -8);
        guiGraphics.pose().popPose();
        
        // Render tooltip if hovered
        if (isHovered) {
            guiGraphics.renderTooltip(this.font, selectedItem, mouseX, mouseY);
        }
        
        // Draw item name
        Component itemName = selectedItem.getHoverName();
        int nameWidth = this.font.width(itemName);
        int nameX = popupX + (POPUP_WIDTH - nameWidth) / 2;
        int nameY = iconY + iconSize + 20;
        guiGraphics.drawString(this.font, itemName, nameX, nameY, 0xFFFFFFFF);
        
        // Draw item details
        int detailsY = nameY + 25;
        int detailsX = popupX + 20;
        
        // Buy price
        String buyPriceText = "Buy Price: $" + buyPriceBox.getValue();
        guiGraphics.drawString(this.font, buyPriceText, detailsX, detailsY, 0xFFAAAAAA);
        
        // Sell price
        String sellPriceText = "Sell Price: $" + sellPriceBox.getValue();
        guiGraphics.drawString(this.font, sellPriceText, detailsX, detailsY + 15, 0xFFAAAAAA);
        
        // Quantity
        String quantityText = "Quantity: " + selectedItem.getCount();
        guiGraphics.drawString(this.font, quantityText, detailsX, detailsY + 30, 0xFFAAAAAA);
        
        // Render buttons
        int buttonY = popupY + POPUP_HEIGHT - 50;
        int buttonSpacing = 20;
        int buttonWidth = 180;
        
        // Back button (gray)
        renderButton(guiGraphics, "Back", popupX + (POPUP_WIDTH / 2) - buttonWidth - (buttonSpacing / 2), buttonY, buttonWidth, 20, mouseX, mouseY, 0x99666666, 0xCC666666);
        
        // Add to Marketplace button (green)
        renderButton(guiGraphics, "Add to Marketplace", popupX + (POPUP_WIDTH / 2) + (buttonSpacing / 2), buttonY, buttonWidth, 20, mouseX, mouseY, 0x994CAF50, 0xCC4CAF50);
    }
    
    /**
     * Phase 2: Render price configuration form
     */
    private void renderFormInput(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw title
        Component title = Component.literal("Add Item to Marketplace");
        int titleWidth = this.font.width(title);
        int titleX = popupX + (POPUP_WIDTH - titleWidth) / 2;
        int titleY = popupY + 15;
        guiGraphics.drawString(this.font, title, titleX, titleY, 0xFFFFFFFF);
        
        // Draw selected item display
        int iconSize = 32;
        int iconX = popupX + 20;
        int iconY = popupY + 60;
        
        // Check if mouse is over icon for tooltip
        boolean isIconHovered = mouseX >= iconX && mouseX <= iconX + iconSize &&
                               mouseY >= iconY && mouseY <= iconY + iconSize;
        
        // Item icon background
        guiGraphics.fill(iconX - 2, iconY - 2, iconX + iconSize + 2, iconY + iconSize + 2, 0xFF404040);
        guiGraphics.fill(iconX - 1, iconY - 1, iconX + iconSize + 1, iconY + iconSize + 1, 0xFF2A2A2A);
        
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(iconX + iconSize / 2, iconY + iconSize / 2, 0);
        float scale = (float) iconSize / 16.0f;
        guiGraphics.pose().scale(scale, scale, scale);
        guiGraphics.renderItem(selectedItem, -8, -8);
        guiGraphics.renderItemDecorations(this.font, selectedItem, -8, -8);
        guiGraphics.pose().popPose();
        
        // Render tooltip if hovered
        if (isIconHovered) {
            guiGraphics.renderTooltip(this.font, selectedItem, mouseX, mouseY);
        }
        
        // Item name next to icon
        Component itemName = selectedItem.getHoverName();
        guiGraphics.drawString(this.font, itemName, iconX + iconSize + 10, iconY + 5, 0xFFFFFFFF);
        
        // Draw form labels and fields
        int formStartY = popupY + 110;
        
        // Buy Price
        guiGraphics.drawString(this.font, "Buy Price ($):", popupX + 20, formStartY, 0xFFAAAAAA);
        buyPriceBox.setPosition(popupX + 20, formStartY + 15);
        buyPriceBox.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Sell Price
        guiGraphics.drawString(this.font, "Sell Price ($):", popupX + 20, formStartY + 50, 0xFFAAAAAA);
        sellPriceBox.setPosition(popupX + 20, formStartY + 65);
        sellPriceBox.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Render buttons
        int buttonY = popupY + POPUP_HEIGHT - 50;
        int buttonSpacing = 20;
        int buttonWidth = 180;
        
        // Back button (gray)
        renderButton(guiGraphics, "Back", popupX + (POPUP_WIDTH / 2) - buttonWidth - (buttonSpacing / 2), buttonY, buttonWidth, 20, mouseX, mouseY, 0x99666666, 0xCC666666);
        
        // Continue button (green)
        renderButton(guiGraphics, "Continue", popupX + (POPUP_WIDTH / 2) + (buttonSpacing / 2), buttonY, buttonWidth, 20, mouseX, mouseY, 0x994CAF50, 0xCC4CAF50);
    }
    
    /**
     * Renders the inventory grid (9x4 layout)
     */
    private void renderInventoryGrid(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int gridWidth = InventoryGridHelper.getGridWidth();
        int gridStartX = popupX + (POPUP_WIDTH - gridWidth) / 2;
        int gridStartY = popupY + 80;
        
        InventoryGridHelper.renderInventoryGrid(guiGraphics, this.font, mouseX, mouseY, gridStartX, gridStartY);
    }
    
    /**
     * Gets the clicked slot index from mouse coordinates
     */
    private int getClickedSlot(double mouseX, double mouseY) {
        int gridWidth = InventoryGridHelper.getGridWidth();
        int gridStartX = popupX + (POPUP_WIDTH - gridWidth) / 2;
        int gridStartY = popupY + 80;
        
        return InventoryGridHelper.getClickedSlot(mouseX, mouseY, gridStartX, gridStartY);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false; // Only left click
        
        errorMessage = null; // Clear error on any click
        
        switch (currentState) {
            case INVENTORY_SELECTION:
                return handleInventorySelectionClick(mouseX, mouseY);
            case CONFIRMATION:
                return handleConfirmationClick(mouseX, mouseY);
            case FORM_INPUT:
                return handleFormInputClick(mouseX, mouseY);
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    /**
     * Handle clicks in inventory selection phase
     */
    private boolean handleInventorySelectionClick(double mouseX, double mouseY) {
        // Check for slot click
        int slotIndex = getClickedSlot(mouseX, mouseY);
        Minecraft mc = Minecraft.getInstance();
        if (slotIndex >= 0 && mc != null && mc.player != null) {
            ItemStack stack = mc.player.getInventory().getItem(slotIndex);
            if (!stack.isEmpty()) {
                // Item selected - transition to form input
                if (mc.player != null) {
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                }
                selectedItem = stack.copy();
                selectedSlotIndex = slotIndex;
                currentState = PopupState.FORM_INPUT;
                return true;
            } else {
                errorMessage = "This slot is empty";
                return true;
            }
        }
        
        // Check for cancel button
        int cancelX = popupX + (POPUP_WIDTH - 180) / 2;
        int cancelY = popupY + POPUP_HEIGHT - 50;
        if (isButtonClicked(mouseX, mouseY, cancelX, cancelY, 180, 20)) {
            onClose();
            return true;
        }
        
        return false;
    }
    
    /**
     * Handle clicks in confirmation phase
     */
    private boolean handleConfirmationClick(double mouseX, double mouseY) {
        int buttonY = popupY + POPUP_HEIGHT - 50;
        int buttonSpacing = 20;
        int buttonWidth = 180;
        
        // Back button
        int backX = popupX + (POPUP_WIDTH / 2) - buttonWidth - (buttonSpacing / 2);
        if (isButtonClicked(mouseX, mouseY, backX, buttonY, buttonWidth, 20)) {
            // Return to form input
            currentState = PopupState.FORM_INPUT;
            return true;
        }
        
        // Add to Marketplace button
        int addX = popupX + (POPUP_WIDTH / 2) + (buttonSpacing / 2);
        if (isButtonClicked(mouseX, mouseY, addX, buttonY, buttonWidth, 20)) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
            }
            addItemToMarketplace();
            return true;
        }
        
        return false;
    }
    
    /**
     * Handle clicks in form input phase
     */
    private boolean handleFormInputClick(double mouseX, double mouseY) {
        // Handle text field clicks
        if (buyPriceBox.mouseClicked(mouseX, mouseY, 0)) {
            buyPriceBox.setFocused(true);
            sellPriceBox.setFocused(false);
            return true;
        }
        if (sellPriceBox.mouseClicked(mouseX, mouseY, 0)) {
            sellPriceBox.setFocused(true);
            buyPriceBox.setFocused(false);
            return true;
        }
        
        // Handle button clicks
        int buttonY = popupY + POPUP_HEIGHT - 50;
        int buttonSpacing = 20;
        int buttonWidth = 180;
        
        // Back button
        int backX = popupX + (POPUP_WIDTH / 2) - buttonWidth - (buttonSpacing / 2);
        if (isButtonClicked(mouseX, mouseY, backX, buttonY, buttonWidth, 20)) {
            currentState = PopupState.INVENTORY_SELECTION;
            selectedItem = ItemStack.EMPTY;
            selectedSlotIndex = -1;
            return true;
        }
        
        // Continue button
        int continueX = popupX + (POPUP_WIDTH / 2) + (buttonSpacing / 2);
        if (isButtonClicked(mouseX, mouseY, continueX, buttonY, buttonWidth, 20)) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
            }
            currentState = PopupState.CONFIRMATION;
            return true;
        }
        
        return false;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC key handling
        if (keyCode == 256) { // ESC
            if (currentState == PopupState.CONFIRMATION) {
                currentState = PopupState.FORM_INPUT;
                return true;
            } else if (currentState == PopupState.FORM_INPUT) {
                currentState = PopupState.INVENTORY_SELECTION;
                selectedItem = ItemStack.EMPTY;
                selectedSlotIndex = -1;
                return true;
            } else {
                onClose();
                return true;
            }
        }
        
        // Handle text field key presses in form input phase
        if (currentState == PopupState.FORM_INPUT) {
            if (buyPriceBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (sellPriceBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // Handle text field character typing in form input phase
        if (currentState == PopupState.FORM_INPUT) {
            if (buyPriceBox.charTyped(codePoint, modifiers)) {
                return true;
            }
            if (sellPriceBox.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        
        return super.charTyped(codePoint, modifiers);
    }
    
    /**
     * Adds the item to the marketplace with validation
     */
    private void addItemToMarketplace() {
        errorMessage = null;
        
        if (selectedItem.isEmpty()) {
            errorMessage = "No item selected";
            return;
        }
        
        // Verify player still has the exact item (including components)
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            Inventory inventory = mc.player.getInventory();
            ItemStack currentStack = inventory.getItem(selectedSlotIndex);
            
            // Check if slot is empty
            if (currentStack.isEmpty()) {
                errorMessage = "Item no longer in inventory!";
                return;
            }
            
            // Check if item type and components match exactly
            if (!ItemStack.isSameItemSameComponents(currentStack, selectedItem)) {
                errorMessage = "Item has been modified or moved!";
                return;
            }
            
            if (currentStack.getCount() < selectedItem.getCount()) {
                errorMessage = "Not enough items in inventory!";
                return;
            }
        }
        
        // Use the stack size from the selected item stack
        int stackSize = selectedItem.getCount();
        if (stackSize < 1 || stackSize > 64) {
            errorMessage = "Invalid item stack size";
            return;
        }
        
        // Parse and validate buy price
        long buyPrice;
        try {
            buyPrice = Long.parseLong(buyPriceBox.getValue());
            if (buyPrice < 0) {
                errorMessage = "Buy price cannot be negative";
                return;
            }
        } catch (NumberFormatException e) {
            errorMessage = "Invalid buy price";
            return;
        }
        
        // Parse and validate sell price
        long sellPrice;
        try {
            sellPrice = Long.parseLong(sellPriceBox.getValue());
            if (sellPrice < 0) {
                errorMessage = "Sell price cannot be negative";
                return;
            }
        } catch (NumberFormatException e) {
            errorMessage = "Invalid sell price";
            return;
        }
        
        // Validate that at least one price is greater than zero
        if (buyPrice <= 0 && sellPrice <= 0) {
            errorMessage = "At least one price must be greater than zero";
            return;
        }
        
        // Extract item ID from ItemStack
        String itemId = BuiltInRegistries.ITEM.getKey(selectedItem.getItem()).toString();
        
        // Serialize component data from actual item using ItemComponentHandler
        String componentData = ItemComponentHandler.getComponentData(selectedItem);
        
        // Escape the component data string for JSON
        String escapedComponentData = componentData
            .replace("\\", "\\\\")  // Escape backslashes first
            .replace("\"", "\\\"")  // Escape quotes
            .replace("\n", "\\n")   // Escape newlines
            .replace("\r", "\\r")   // Escape carriage returns
            .replace("\t", "\\t");  // Escape tabs
        
        // Send add item packet to server
        String jsonData = String.format("{\"itemId\":\"%s\",\"componentData\":\"%s\",\"buyPrice\":%d,\"sellPrice\":%d,\"stackSize\":%d}", 
            itemId, escapedComponentData, buyPrice, sellPrice, stackSize);
        FreeMarketPacket packet = FreeMarketPacket.withJson(PacketType.MARKETPLACE_ADD_ITEM, jsonData);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
        
        FreeMarket.LOGGER.info("Added item to marketplace: {} x{} - Buy: ${}, Sell: ${}", 
            itemId, stackSize, buyPrice, sellPrice);
        
        // Close popup and return to marketplace screen
        onClose();
    }
    
    @Override
    public void onClose() {
        // Refresh the marketplace before returning to parent screen
        if (parentScreen != null) {
            parentScreen.refreshMarketplace();
        }
        
        super.onClose();
    }
}
