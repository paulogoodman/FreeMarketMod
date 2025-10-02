package com.servershop;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Semi-transparent dark overlay GUI for the ServerShop mod.
 * Opens with the O keybind and displays a dark overlay with conditional admin button.
 */
public class ShopGuiScreen extends Screen {
    
    private Button plusButton;
    
    public ShopGuiScreen() {
        super(Component.translatable("gui.servershop.shop.title"));
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Create the '+' button
        this.plusButton = Button.builder(
            Component.literal("+"),
            button -> {
                // Handle button click - for now just log
                ServerShop.LOGGER.info("Plus button clicked!");
            }
        )
        .bounds(this.width / 2 + 100, this.height / 2 - 50, 20, 20)
        .build();
        
        // Only add the button if admin mode is enabled
        if (AdminModeHandler.isAdminMode()) {
            this.addRenderableWidget(this.plusButton);
        }
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw a semi-transparent dark rectangle covering the entire screen
        int overlayAlpha = 150; // Semi-transparent (0-255)
        guiGraphics.fill(0, 0, this.width, this.height, (overlayAlpha << 24) | 0x000000);
        
        // Call super.render() first to handle any background elements
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Draw title at the top center (after super.render to ensure it's on top)
        int titleWidth = this.font.width(this.title);
        int titleX = (this.width - titleWidth) / 2;
        int titleY = 20;
        guiGraphics.drawString(this.font, this.title, titleX, titleY, 0xFFFFFF);
        
        // Draw instruction text
        Component instruction = Component.translatable("gui.servershop.shop.instruction");
        int instructionWidth = this.font.width(instruction);
        int instructionX = (this.width - instructionWidth) / 2;
        int instructionY = titleY + 30;
        guiGraphics.drawString(this.font, instruction, instructionX, instructionY, 0xCCCCCC);
        
        // Draw admin mode status if enabled
        if (AdminModeHandler.isAdminMode()) {
            Component adminStatus = Component.translatable("gui.servershop.admin_mode");
            int adminWidth = this.font.width(adminStatus);
            int adminX = (this.width - adminWidth) / 2;
            int adminY = instructionY + 20;
            guiGraphics.drawString(this.font, adminStatus, adminX, adminY, 0x00FF00);
        }
    }
    
    /**
     * Refreshes the GUI to update button visibility based on admin mode.
     * Call this when admin mode changes.
     */
    public void refreshAdminMode() {
        // Clear existing widgets
        this.clearWidgets();
        
        // Reinitialize to add/remove the plus button based on admin mode
        this.init();
    }
    
    @Override
    public boolean isPauseScreen() {
        return false; // Don't pause the game when GUI is open
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return true; // Close GUI when ESC is pressed
    }
    
    @Override
    public void onClose() {
        super.onClose();
    }
}
