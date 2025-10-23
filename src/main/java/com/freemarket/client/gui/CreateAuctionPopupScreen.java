package com.freemarket.client.gui;

import com.freemarket.FreeMarket;
import com.freemarket.common.network.FreeMarketPacket;
import com.freemarket.common.network.PacketType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * Popup screen for creating auctions with visual inventory selector.
 * Three-phase UI: Inventory Selection → Confirmation → Price/Duration Form
 */
public class CreateAuctionPopupScreen extends Screen {
    
    /**
     * Enum for tracking the current UI phase
     */
    private enum PopupState {
        INVENTORY_SELECTION,  // Phase 1: showing inventory grid
        CONFIRMATION,         // Phase 2: confirm selected item
        FORM_INPUT           // Phase 3: enter price/duration
    }
    
    private final FreeMarketGuiScreen parentScreen;
    private PopupState currentState = PopupState.INVENTORY_SELECTION;
    
    // Selected item state
    private ItemStack selectedItem = ItemStack.EMPTY;
    private int selectedSlotIndex = -1;
    
    // Form input fields (Phase 3)
    private EditBox startingPriceBox;
    private EditBox durationBox;
    
    private String errorMessage = null;
    
    // UI dimensions (matching PlaceBidPopupOverlay)
    private static final int POPUP_WIDTH = 420;
    private static final int POPUP_HEIGHT = 320;
    private int popupX;
    private int popupY;
    
    // Inventory grid constants
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_MARGIN = 2;
    private static final int TOTAL_SLOT_SIZE = SLOT_SIZE + SLOT_MARGIN;
    private static final int HOTBAR_SPACING = 4;
    
    public CreateAuctionPopupScreen(FreeMarketGuiScreen parent) {
        super(Component.literal("Create Auction"));
        this.parentScreen = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Calculate popup position (centered)
        popupX = (this.width - POPUP_WIDTH) / 2;
        popupY = (this.height - POPUP_HEIGHT) / 2;
        
        // Initialize form fields for Phase 3
        initializeFormFields();
    }
    
    private void initializeFormFields() {
        // Starting price input
        this.startingPriceBox = new EditBox(
            this.font,
            popupX + 20,
            popupY + 125,
            200,
            20,
            Component.literal("Starting Price")
        );
        this.startingPriceBox.setValue("100");
        this.startingPriceBox.setMaxLength(10);
        
        // Duration input (in minutes)
        this.durationBox = new EditBox(
            this.font,
            popupX + 20,
            popupY + 175,
            200,
            20,
            Component.literal("Duration (minutes)")
        );
        this.durationBox.setValue("1440"); // Default 24 hours
        this.durationBox.setMaxLength(6);
    }
    
    /**
     * Renders only the background elements of the parent screen without widgets.
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
        // Render parent screen background
        if (parentScreen != null) {
            renderParentBackground(guiGraphics, partialTick);
        }
        
        // Push pose to render popup at a higher z-level (in front of everything)
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400); // Push popup forward in z-space
        
        // Apply semi-transparent overlay
        guiGraphics.fill(0, 0, this.width, this.height, 0xA0000000);
        
        // Draw popup background
        guiGraphics.fill(popupX, popupY, popupX + POPUP_WIDTH, popupY + POPUP_HEIGHT, 0xFF1E1E1E);
        guiGraphics.fill(popupX + 1, popupY + 1, popupX + POPUP_WIDTH - 1, popupY + POPUP_HEIGHT - 1, 0xFF2A2A2A);
        
        // Draw border
        guiGraphics.fill(popupX, popupY, popupX + POPUP_WIDTH, popupY + 2, 0xFF404040);
        guiGraphics.fill(popupX, popupY, popupX + 2, popupY + POPUP_HEIGHT, 0xFF404040);
        guiGraphics.fill(popupX + POPUP_WIDTH - 2, popupY, popupX + POPUP_WIDTH, popupY + POPUP_HEIGHT, 0xFF404040);
        guiGraphics.fill(popupX, popupY + POPUP_HEIGHT - 2, popupX + POPUP_WIDTH, popupY + POPUP_HEIGHT, 0xFF404040);
        
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
        
        // Draw error message if present
        if (errorMessage != null) {
            int errorY = popupY + POPUP_HEIGHT - 30;
            int errorWidth = this.font.width(errorMessage);
            int errorX = popupX + (POPUP_WIDTH - errorWidth) / 2;
            guiGraphics.drawString(this.font, errorMessage, errorX, errorY, 0xFFFF5555);
        }
        
        // Pop pose to restore original z-level
        guiGraphics.pose().popPose();
    }
    
    /**
     * Phase 1: Render inventory selection screen
     */
    private void renderInventorySelection(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw title
        Component title = Component.literal("Create Auction");
        int titleWidth = this.font.width(title);
        int titleX = popupX + (POPUP_WIDTH - titleWidth) / 2;
        int titleY = popupY + 15;
        guiGraphics.drawString(this.font, title, titleX, titleY, 0xFFFFFFFF);
        
        // Draw instructions
        String instruction = "Select an item from your inventory to auction:";
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
     * Phase 2: Render confirmation dialog
     */
    private void renderConfirmation(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw title
        Component title = Component.literal("Confirm Auction Item");
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
        
        // Render buttons
        int buttonY = popupY + POPUP_HEIGHT - 50;
        int buttonSpacing = 20;
        int buttonWidth = 180;
        
        // Continue button (green)
        renderButton(guiGraphics, "Continue", popupX + (POPUP_WIDTH / 2) - buttonWidth - (buttonSpacing / 2), buttonY, buttonWidth, 20, mouseX, mouseY, 0x994CAF50, 0xCC4CAF50);
        
        // Back button (gray)
        renderButton(guiGraphics, "Back", popupX + (POPUP_WIDTH / 2) + (buttonSpacing / 2), buttonY, buttonWidth, 20, mouseX, mouseY, 0x99666666, 0xCC666666);
    }
    
    /**
     * Phase 3: Render price/duration form
     */
    private void renderFormInput(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw title
        Component title = Component.literal("Create Auction");
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
        
        // Starting Price
        guiGraphics.drawString(this.font, "Starting Price ($):", popupX + 20, formStartY, 0xFFAAAAAA);
        startingPriceBox.setPosition(popupX + 20, formStartY + 15);
        startingPriceBox.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Duration
        guiGraphics.drawString(this.font, "Duration (minutes):", popupX + 20, formStartY + 50, 0xFFAAAAAA);
        guiGraphics.drawString(this.font, "Default: 1440 (24 hours)", popupX + 180, formStartY + 50, 0xFF808080);
        durationBox.setPosition(popupX + 20, formStartY + 65);
        durationBox.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Render buttons
        int buttonY = popupY + POPUP_HEIGHT - 50;
        int buttonSpacing = 10;
        int createButtonWidth = 180;
        int backButtonWidth = 100;
        int cancelButtonWidth = 100;
        
        // Create Auction button (green)
        renderButton(guiGraphics, "Create Auction", popupX + 20, buttonY, createButtonWidth, 20, mouseX, mouseY, 0x994CAF50, 0xCC4CAF50);
        
        // Back button (gray)
        renderButton(guiGraphics, "Back", popupX + 20 + createButtonWidth + buttonSpacing, buttonY, backButtonWidth, 20, mouseX, mouseY, 0x99666666, 0xCC666666);
        
        // Cancel button (gray)
        renderButton(guiGraphics, "Cancel", popupX + 20 + createButtonWidth + buttonSpacing + backButtonWidth + buttonSpacing, buttonY, cancelButtonWidth, 20, mouseX, mouseY, 0x99666666, 0xCC666666);
    }
    
    /**
     * Renders the inventory grid (9x4 layout)
     */
    @SuppressWarnings("null")
    private void renderInventoryGrid(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        
        Inventory inventory = mc.player.getInventory();
        int gridWidth = 9 * TOTAL_SLOT_SIZE;
        int gridStartX = popupX + (POPUP_WIDTH - gridWidth) / 2;
        int gridStartY = popupY + 80;
        
        // Draw section labels
        guiGraphics.drawString(this.font, "Main Inventory", gridStartX, gridStartY - 15, 0xFFAAAAAA);
        guiGraphics.drawString(this.font, "Hotbar", gridStartX, gridStartY + (3 * TOTAL_SLOT_SIZE) + HOTBAR_SPACING - 15, 0xFFAAAAAA);
        
        for (int i = 0; i < 36; i++) {
            // Map inventory slot index to display position
            // Slots 9-35 are main inventory (display at top)
            // Slots 0-8 are hotbar (display at bottom)
            int displayIndex;
            if (i < 9) {
                // Hotbar: map to bottom row (slots 27-35 in display)
                displayIndex = i + 27;
            } else {
                // Main inventory: map to top 3 rows (slots 0-26 in display)
                displayIndex = i - 9;
            }
            
            int row = displayIndex / 9;
            int col = displayIndex % 9;
            int slotX = gridStartX + (col * TOTAL_SLOT_SIZE);
            int slotY = gridStartY + (row * TOTAL_SLOT_SIZE);
            
            // Add spacing between main inventory and hotbar
            if (displayIndex >= 27) {
                slotY += HOTBAR_SPACING;
            }
            
            ItemStack stack = inventory.getItem(i);
            boolean isHovered = isMouseOverSlot(mouseX, mouseY, slotX, slotY);
            
            // Render slot background
            int bgColor = isHovered ? 0xFF3A3A3A : 0xFF2A2A2A;
            guiGraphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, bgColor);
            
            // Render slot border
            guiGraphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + 1, 0xFF404040);
            guiGraphics.fill(slotX, slotY, slotX + 1, slotY + SLOT_SIZE, 0xFF404040);
            guiGraphics.fill(slotX + SLOT_SIZE - 1, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0xFF404040);
            guiGraphics.fill(slotX, slotY + SLOT_SIZE - 1, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0xFF404040);
            
            // Render item
            if (!stack.isEmpty()) {
                guiGraphics.renderItem(stack, slotX + 1, slotY + 1);
                guiGraphics.renderItemDecorations(this.font, stack, slotX + 1, slotY + 1);
                
                // Render tooltip on hover
                if (isHovered) {
                    guiGraphics.renderTooltip(this.font, stack, mouseX, mouseY);
                }
            }
        }
    }
    
    /**
     * Checks if mouse is over a specific slot
     */
    private boolean isMouseOverSlot(int mouseX, int mouseY, int slotX, int slotY) {
        return mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
               mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
    }
    
    /**
     * Gets the clicked slot index from mouse coordinates
     */
    private int getClickedSlot(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return -1;
        
        int gridWidth = 9 * TOTAL_SLOT_SIZE;
        int gridStartX = popupX + (POPUP_WIDTH - gridWidth) / 2;
        int gridStartY = popupY + 80;
        
        for (int i = 0; i < 36; i++) {
            // Map inventory slot index to display position (same as rendering)
            int displayIndex;
            if (i < 9) {
                displayIndex = i + 27;
            } else {
                displayIndex = i - 9;
            }
            
            int row = displayIndex / 9;
            int col = displayIndex % 9;
            int slotX = gridStartX + (col * TOTAL_SLOT_SIZE);
            int slotY = gridStartY + (row * TOTAL_SLOT_SIZE);
            
            if (displayIndex >= 27) {
                slotY += HOTBAR_SPACING;
            }
            
            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
                mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Renders a button with hover effect
     */
    private void renderButton(GuiGraphics guiGraphics, String text, int x, int y, int width, int height, int mouseX, int mouseY, int normalColor, int hoverColor) {
        boolean isHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        int bgColor = isHovered ? hoverColor : normalColor;
        
        // Button background
        guiGraphics.fill(x, y, x + width, y + height, bgColor);
        
        // Button border
        guiGraphics.fill(x, y, x + width, y + 1, 0xFF404040);
        guiGraphics.fill(x, y, x + 1, y + height, 0xFF404040);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, 0xFF404040);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, 0xFF404040);
        
        // Button text (centered)
        int textWidth = this.font.width(text);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - this.font.lineHeight) / 2;
        guiGraphics.drawString(this.font, text, textX, textY, 0xFFFFFFFF);
    }
    
    /**
     * Checks if a button is clicked
     */
    private boolean isButtonClicked(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
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
                // Item selected - transition to confirmation
                selectedItem = stack.copy();
                selectedSlotIndex = slotIndex;
                currentState = PopupState.CONFIRMATION;
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
        
        // Continue button
        int continueX = popupX + (POPUP_WIDTH / 2) - buttonWidth - (buttonSpacing / 2);
        if (isButtonClicked(mouseX, mouseY, continueX, buttonY, buttonWidth, 20)) {
            // Transition to form input
            currentState = PopupState.FORM_INPUT;
            return true;
        }
        
        // Back button
        int backX = popupX + (POPUP_WIDTH / 2) + (buttonSpacing / 2);
        if (isButtonClicked(mouseX, mouseY, backX, buttonY, buttonWidth, 20)) {
            // Return to inventory selection
            currentState = PopupState.INVENTORY_SELECTION;
            selectedItem = ItemStack.EMPTY;
            selectedSlotIndex = -1;
            return true;
        }
        
        return false;
    }
    
    /**
     * Handle clicks in form input phase
     */
    private boolean handleFormInputClick(double mouseX, double mouseY) {
        // Handle text field clicks
        if (startingPriceBox.mouseClicked(mouseX, mouseY, 0)) {
            startingPriceBox.setFocused(true);
            durationBox.setFocused(false);
            return true;
        }
        if (durationBox.mouseClicked(mouseX, mouseY, 0)) {
            durationBox.setFocused(true);
            startingPriceBox.setFocused(false);
            return true;
        }
        
        // Handle button clicks
        int buttonY = popupY + POPUP_HEIGHT - 50;
        int buttonSpacing = 10;
        int createButtonWidth = 180;
        int backButtonWidth = 100;
        int cancelButtonWidth = 100;
        
        // Create Auction button
        int createX = popupX + 20;
        if (isButtonClicked(mouseX, mouseY, createX, buttonY, createButtonWidth, 20)) {
            createAuction();
            return true;
        }
        
        // Back button
        int backX = popupX + 20 + createButtonWidth + buttonSpacing;
        if (isButtonClicked(mouseX, mouseY, backX, buttonY, backButtonWidth, 20)) {
            currentState = PopupState.CONFIRMATION;
            return true;
        }
        
        // Cancel button
        int cancelX = popupX + 20 + createButtonWidth + buttonSpacing + backButtonWidth + buttonSpacing;
        if (isButtonClicked(mouseX, mouseY, cancelX, buttonY, cancelButtonWidth, 20)) {
            onClose();
            return true;
        }
        
        return false;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC key handling
        if (keyCode == 256) { // ESC
            if (currentState == PopupState.FORM_INPUT) {
                currentState = PopupState.CONFIRMATION;
                return true;
            } else if (currentState == PopupState.CONFIRMATION) {
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
            if (startingPriceBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (durationBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // Handle text field character typing in form input phase
        if (currentState == PopupState.FORM_INPUT) {
            if (startingPriceBox.charTyped(codePoint, modifiers)) {
                return true;
            }
            if (durationBox.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        
        return super.charTyped(codePoint, modifiers);
    }
    
    /**
     * Creates the auction with validation
     */
    private void createAuction() {
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
            
            // Debug logging
            FreeMarket.LOGGER.info("=== Auction Creation Validation ===");
            FreeMarket.LOGGER.info("Selected item: {} (count: {})", selectedItem.getItem(), selectedItem.getCount());
            FreeMarket.LOGGER.info("Current stack: {} (count: {})", currentStack.getItem(), currentStack.getCount());
            FreeMarket.LOGGER.info("Items match: {}", currentStack.is(selectedItem.getItem()));
            FreeMarket.LOGGER.info("Same item same components: {}", ItemStack.isSameItemSameComponents(currentStack, selectedItem));
            FreeMarket.LOGGER.info("Selected components: {}", selectedItem.getComponents());
            FreeMarket.LOGGER.info("Current components: {}", currentStack.getComponents());
            
            // Check if item type and components match exactly
            // This prevents exploits like auctioning a Sharpness 5 sword but only having Sharpness 1
            if (!ItemStack.isSameItemSameComponents(currentStack, selectedItem)) {
                errorMessage = "Item has been modified or moved!";
                FreeMarket.LOGGER.warn("Validation failed - items don't match");
                return;
            }
            
            // This check is redundant since isSameItemSameComponents includes count,
            // but we keep it for clarity and better error messages
            if (currentStack.getCount() < selectedItem.getCount()) {
                errorMessage = "Not enough items in inventory!";
                return;
            }
        }
        
        // Use the quantity from the selected item stack
        int quantity = selectedItem.getCount();
        if (quantity < 1 || quantity > 64) {
            errorMessage = "Invalid item quantity";
            return;
        }
        
        // Parse and validate starting price
        long startingPrice;
        try {
            startingPrice = Long.parseLong(startingPriceBox.getValue());
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
            durationMinutes = Long.parseLong(durationBox.getValue());
            if (durationMinutes < 1 || durationMinutes > 10080) {
                errorMessage = "Duration must be between 1 minute and 1 week (10080 minutes)";
                return;
            }
        } catch (NumberFormatException e) {
            errorMessage = "Invalid duration";
            return;
        }
        
        // Extract item ID from ItemStack
        String itemId = BuiltInRegistries.ITEM.getKey(selectedItem.getItem()).toString();
        
        // Serialize component data from actual item using ItemComponentHandler
        String componentData = com.freemarket.common.attachments.ItemComponentHandler.getComponentData(selectedItem);
        
        // Escape the component data string for JSON
        String escapedComponentData = componentData
            .replace("\\", "\\\\")  // Escape backslashes first
            .replace("\"", "\\\"")  // Escape quotes
            .replace("\n", "\\n")   // Escape newlines
            .replace("\r", "\\r")   // Escape carriage returns
            .replace("\t", "\\t");  // Escape tabs
        
        // Send auction create packet to server
        String jsonData = String.format("{\"itemId\":\"%s\",\"componentData\":\"%s\",\"quantity\":%d,\"startingPrice\":%d,\"durationMinutes\":%d}", 
            itemId, escapedComponentData, quantity, startingPrice, durationMinutes);
        FreeMarketPacket packet = FreeMarketPacket.withJson(PacketType.AUCTION_CREATE, jsonData);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
        
        FreeMarket.LOGGER.info("Created auction for {} x{} starting at ${} for {} minutes", 
            itemId, quantity, startingPrice, durationMinutes);
        
        // Close popup and return to auction screen
        onClose();
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
