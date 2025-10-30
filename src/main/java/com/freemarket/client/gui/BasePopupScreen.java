package com.freemarket.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;

/**
 * Abstract base class for popup screens in the FreeMarket GUI.
 * Provides shared popup rendering infrastructure, button rendering, and background rendering.
 */
public abstract class BasePopupScreen extends Screen {
    
    protected final FreeMarketGuiScreen parentScreen;
    protected String errorMessage = null;
    
    // UI dimensions
    protected static final int POPUP_WIDTH = 420;
    protected static final int POPUP_HEIGHT = 280;
    protected int popupX;
    protected int popupY;
    
    protected BasePopupScreen(Component title, FreeMarketGuiScreen parent) {
        super(title);
        this.parentScreen = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Calculate popup position (centered)
        popupX = (this.width - POPUP_WIDTH) / 2;
        popupY = (this.height - POPUP_HEIGHT) / 2;
    }
    
    /**
     * Renders only the background elements of the parent screen without widgets.
     */
    protected void renderParentBackground(GuiGraphics guiGraphics, float partialTick) {
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
    
    /**
     * Renders the popup background with border.
     */
    protected void renderPopupBackground(GuiGraphics guiGraphics) {
        // Draw popup background
        guiGraphics.fill(popupX, popupY, popupX + POPUP_WIDTH, popupY + POPUP_HEIGHT, 0xFF1E1E1E);
        guiGraphics.fill(popupX + 1, popupY + 1, popupX + POPUP_WIDTH - 1, popupY + POPUP_HEIGHT - 1, 0xFF2A2A2A);
        
        // Draw border
        guiGraphics.fill(popupX, popupY, popupX + POPUP_WIDTH, popupY + 2, 0xFF404040);
        guiGraphics.fill(popupX, popupY + 2, popupX + 2, popupY + POPUP_HEIGHT - 2, 0xFF404040);
        guiGraphics.fill(popupX + POPUP_WIDTH - 2, popupY + 2, popupX + POPUP_WIDTH, popupY + POPUP_HEIGHT - 2, 0xFF404040);
        guiGraphics.fill(popupX, popupY + POPUP_HEIGHT - 2, popupX + POPUP_WIDTH, popupY + POPUP_HEIGHT, 0xFF404040);
    }
    
    /**
     * Renders a button with hover effect.
     */
    protected void renderButton(GuiGraphics guiGraphics, String text, int x, int y, int width, int height, 
                                int mouseX, int mouseY, int normalColor, int hoverColor) {
        boolean isHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        int bgColor = isHovered ? hoverColor : normalColor;
        
        // Button background
        guiGraphics.fill(x, y, x + width, y + height, bgColor);
        
        // Button border
        guiGraphics.fill(x, y, x + width, y + 1, 0xFF404040);
        guiGraphics.fill(x, y + 1, x + 1, y + height - 1, 0xFF404040);
        guiGraphics.fill(x + width - 1, y + 1, x + width, y + height - 1, 0xFF404040);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, 0xFF404040);
        
        // Button text (centered)
        int textWidth = this.font.width(text);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - this.font.lineHeight) / 2;
        guiGraphics.drawString(this.font, text, textX, textY, 0xFFFFFFFF);
    }
    
    /**
     * Checks if a button is clicked.
     */
    protected boolean isButtonClicked(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
    
    /**
     * Renders the error message if present.
     */
    protected void renderErrorMessage(GuiGraphics guiGraphics) {
        if (errorMessage != null) {
            int errorY = popupY + POPUP_HEIGHT - 80;  // Move above buttons
            int errorWidth = this.font.width(errorMessage);
            int errorX = popupX + (POPUP_WIDTH - errorWidth) / 2;
            guiGraphics.drawString(this.font, errorMessage, errorX, errorY, 0xFFFF5555);
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
        guiGraphics.pose().translate(0, 0, 5000); // Push popup forward in z-space (much higher than item decorations)
        
        // Apply semi-transparent overlay
        guiGraphics.fill(0, 0, this.width, this.height, 0xA0000000);
        
        // Draw popup background
        renderPopupBackground(guiGraphics);
        
        // Render popup-specific content
        renderPopupContent(guiGraphics, mouseX, mouseY, partialTick);
        
        // Draw error message if present
        renderErrorMessage(guiGraphics);
        
        // Pop pose to restore original z-level
        guiGraphics.pose().popPose();
    }
    
    /**
     * Abstract method for popup-specific content rendering.
     */
    protected abstract void renderPopupContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick);
    
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

