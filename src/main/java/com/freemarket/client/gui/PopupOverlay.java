package com.freemarket.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;

/**
 * Base class for popup overlays that render on top of existing screens.
 * This prevents rendering conflicts and overlaps by rendering as an overlay
 * instead of replacing the parent screen.
 */
public abstract class PopupOverlay implements Renderable {
    
    protected int x, y, width, height;
    protected boolean visible = false;
    protected String errorMessage = null;
    
    // UI Constants for modern design
    protected static final int BORDER_RADIUS = 6;
    
    // Color scheme
    protected static final int BACKGROUND_COLOR = 0xFF1A1A1A;
    protected static final int SURFACE_COLOR = 0xFF2D2D2D;
    protected static final int BORDER_COLOR = 0xFF404040;
    protected static final int ACCENT_COLOR = 0xFF4CAF50;
    protected static final int ERROR_COLOR = 0xFFFF6B6B;
    protected static final int SUCCESS_COLOR = 0xFF4CAF50;
    protected static final int WARNING_COLOR = 0xFFFFB74D;
    protected static final int TEXT_PRIMARY = 0xFFFFFFFF;
    protected static final int TEXT_SECONDARY = 0xFFB0B0B0;
    protected static final int TEXT_MUTED = 0xFF808080;
    
    public PopupOverlay(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    /**
     * Shows the popup overlay.
     */
    public void show() {
        this.visible = true;
        this.errorMessage = null;
    }
    
    /**
     * Hides the popup overlay.
     */
    public void hide() {
        this.visible = false;
        this.errorMessage = null;
    }
    
    /**
     * Checks if the popup is visible.
     */
    public boolean isVisible() {
        return visible;
    }
    
    /**
     * Sets an error message to display.
     */
    public void setErrorMessage(String message) {
        this.errorMessage = message;
    }
    
    /**
     * Clears the error message.
     */
    public void clearError() {
        this.errorMessage = null;
    }
    
    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) {
            return;
        }
        
        // Push pose to render popup at a higher z-level (in front of everything)
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400); // Push popup forward in z-space
        
        // Draw backdrop overlay with blur effect
        int screenWidth = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
        guiGraphics.fill(0, 0, screenWidth, screenHeight, 0x80000000);
        
        // Draw main popup background with rounded corners effect
        drawRoundedRect(guiGraphics, x, y, width, height, SURFACE_COLOR);
        
        // Draw border
        guiGraphics.fill(x, y, x + width, y + 2, BORDER_COLOR);
        guiGraphics.fill(x, y, x + 2, y + height, BORDER_COLOR);
        guiGraphics.fill(x + width - 2, y, x + width, y + height, BORDER_COLOR);
        guiGraphics.fill(x, y + height - 2, x + width, y + height, BORDER_COLOR);
        
        // Draw title
        Component title = getTitle();
        int titleWidth = net.minecraft.client.Minecraft.getInstance().font.width(title);
        int titleX = x + (width - titleWidth) / 2;
        int titleY = y + 15;
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, title, titleX, titleY, TEXT_PRIMARY);
        
        // Draw content
        renderContent(guiGraphics, mouseX, mouseY, partialTick);
        
        // Draw error message if present
        if (errorMessage != null) {
            drawErrorMessage(guiGraphics);
        }
        
        // Pop pose to restore original z-level
        guiGraphics.pose().popPose();
    }
    
    /**
     * Draws a rounded rectangle effect.
     */
    protected void drawRoundedRect(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        // Draw main rectangle
        guiGraphics.fill(x + BORDER_RADIUS, y, x + width - BORDER_RADIUS, y + height, color);
        guiGraphics.fill(x, y + BORDER_RADIUS, x + width, y + height - BORDER_RADIUS, color);
        
        // Draw rounded corners (simplified)
        guiGraphics.fill(x + BORDER_RADIUS, y + BORDER_RADIUS, x + width - BORDER_RADIUS, y + height - BORDER_RADIUS, color);
    }
    
    /**
     * Draws an error message at the bottom of the popup.
     */
    protected void drawErrorMessage(GuiGraphics guiGraphics) {
        if (errorMessage == null) return;
        
        int errorY = y + height - 25;
        int errorWidth = net.minecraft.client.Minecraft.getInstance().font.width(errorMessage);
        int errorX = x + (width - errorWidth) / 2;
        
        // Error background
        guiGraphics.fill(errorX - 8, errorY - 2, errorX + errorWidth + 8, errorY + net.minecraft.client.Minecraft.getInstance().font.lineHeight + 2, 0x4DFF6B6B);
        guiGraphics.fill(errorX - 7, errorY - 1, errorX + errorWidth + 7, errorY + net.minecraft.client.Minecraft.getInstance().font.lineHeight + 1, BACKGROUND_COLOR);
        
        // Error text
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, "⚠ " + errorMessage, errorX, errorY, ERROR_COLOR);
    }
    
    /**
     * Handles mouse clicks on the popup.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        
        // Check if click is within popup bounds
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            return handlePopupClick(mouseX, mouseY, button);
        }
        
        // If click is outside popup, hide it
        hide();
        return true;
    }
    
    /**
     * Handles key presses on the popup.
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        
        // ESC key closes popup
        if (keyCode == 256) { // ESC key
            hide();
            return true;
        }
        
        return handlePopupKeyPress(keyCode, scanCode, modifiers);
    }
    
    /**
     * Handles character typing on the popup.
     */
    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        return handlePopupCharTyped(codePoint, modifiers);
    }
    
    /**
     * Gets the title of the popup.
     */
    protected abstract Component getTitle();
    
    /**
     * Renders the content of the popup.
     */
    protected abstract void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick);
    
    /**
     * Handles mouse clicks within the popup.
     */
    protected abstract boolean handlePopupClick(double mouseX, double mouseY, int button);
    
    /**
     * Handles key presses within the popup.
     */
    protected abstract boolean handlePopupKeyPress(int keyCode, int scanCode, int modifiers);
    
    /**
     * Handles character typing within the popup.
     */
    protected abstract boolean handlePopupCharTyped(char codePoint, int modifiers);
}
