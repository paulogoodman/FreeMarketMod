package com.freemarket.client.gui;

import net.minecraft.client.Minecraft;

/**
 * GUI scaling helper that provides responsive scaling based on window size.
 */
public class GuiScalingHelper {
    
    /**
     * Gets the current GUI-scaled screen dimensions from Minecraft.
     */
    public static int getScreenWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }
    
    public static int getScreenHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }
    
    /**
     * Gets the current GUI scale factor from Minecraft.
     * This is used for font scaling and other GUI elements.
     */
    public static double getGuiScaleFactor() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }
    
    /**
     * Gets the raw screen width (not GUI-scaled)
     */
    public static int getRawScreenWidth() {
        return Minecraft.getInstance().getWindow().getWidth();
    }
    
    /**
     * Gets the raw screen height (not GUI-scaled)
     */
    public static int getRawScreenHeight() {
        return Minecraft.getInstance().getWindow().getHeight();
    }
    
    /**
     * Gets the resolution/screen size scale factor used by responsive scaling
     */
    public static double getResolutionScaleFactor() {
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();
        
        // Scale based on the smaller dimension to prevent oversized UI
        int smallerDimension = Math.min(screenWidth, screenHeight);
        return Math.min(smallerDimension / 1080.0, 1.5); // Cap at 1.5x scaling
    }
    
    
    /**
     * Simple font scaling that works with Minecraft's built-in GUI scaling.
     * No need for complex calculations - Minecraft handles this automatically.
     */
    public static float getFontScale() {
        return 1.0f; // Let Minecraft handle font scaling automatically
    }
    
    /**
     * Simple button text scaling - just ensure text fits within bounds.
     */
    public static float getFontScaleForButton(int buttonWidth, int buttonHeight, String text) {
        Minecraft minecraft = Minecraft.getInstance();
        int textWidth = minecraft.font.width(text);
        int textHeight = minecraft.font.lineHeight;
        
        // Simple check: if text is too wide, scale down
        if (textWidth > buttonWidth - 8) { // 8 pixels padding
            return Math.max(0.5f, (float)(buttonWidth - 8) / textWidth);
        }
        
        // If text is too tall, scale down
        if (textHeight > buttonHeight - 4) { // 4 pixels padding
            return Math.max(0.5f, (float)(buttonHeight - 4) / textHeight);
        }
        
        return 1.0f; // Text fits fine
    }
    
    /**
     * Calculates centered X position for an element.
     */
    public static int centerX(int elementWidth) {
        return (getScreenWidth() - elementWidth) / 2;
    }
    
    /**
     * Calculates centered Y position for an element.
     */
    public static int centerY(int elementHeight) {
        return (getScreenHeight() - elementHeight) / 2;
    }
    
    /**
     * Calculates position as percentage of screen width.
     */
    public static int percentageX(float percentage) {
        return Math.round(getScreenWidth() * percentage);
    }
    
    /**
     * Calculates position as percentage of screen height.
     */
    public static int percentageY(float percentage) {
        return Math.round(getScreenHeight() * percentage);
    }
    
    /**
     * Simple responsive scaling using Minecraft's built-in GUI scaling.
     * The width and height fields automatically scale with window resizing.
     */
    public static int responsiveWidth(int baseWidth, int minWidth, int maxWidth) {
        // Use a simple scale factor based on screen size
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();
        
        // Scale based on the smaller dimension to prevent oversized UI
        int smallerDimension = Math.min(screenWidth, screenHeight);
        double scaleFactor = Math.min(smallerDimension / 1080.0, 1.5); // Cap at 1.5x scaling
        
        // Scale the base width and clamp between min and max
        int scaledWidth = Math.round(baseWidth * (float) scaleFactor);
        return Math.max(minWidth, Math.min(maxWidth, scaledWidth));
    }
    
    public static int responsiveHeight(int baseHeight, int minHeight, int maxHeight) {
        // Use a simple scale factor based on screen size
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();
        
        // Scale based on the smaller dimension to prevent oversized UI
        int smallerDimension = Math.min(screenWidth, screenHeight);
        double scaleFactor = Math.min(smallerDimension / 1080.0, 1.5); // Cap at 1.5x scaling
        
        // Scale the base height and clamp between min and max
        int scaledHeight = Math.round(baseHeight * (float) scaleFactor);
        return Math.max(minHeight, Math.min(maxHeight, scaledHeight));
    }
    
    /**
     * Simple responsive scaling with reasonable defaults.
     */
    public static int responsiveWidth(int baseWidth) {
        return responsiveWidth(baseWidth, baseWidth / 2, baseWidth * 2);
    }
    
    public static int responsiveHeight(int baseHeight) {
        return responsiveHeight(baseHeight, baseHeight / 2, baseHeight * 2);
    }
    
    /**
     * Legacy methods for backward compatibility - now use responsive scaling.
     */
    public static int scaleWidth(int baseWidth) {
        return responsiveWidth(baseWidth);
    }
    
    public static int scaleHeight(int baseHeight) {
        return responsiveHeight(baseHeight);
    }
}
