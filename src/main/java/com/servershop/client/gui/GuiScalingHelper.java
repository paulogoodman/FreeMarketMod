package com.servershop.client.gui;

import net.minecraft.client.Minecraft;

/**
 * GUI scaling helper that provides responsive scaling based on window size.
 */
public class GuiScalingHelper {
    
    /**
     * Gets the current screen dimensions from Minecraft (already scaled by GUI scale).
     */
    public static int getScreenWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }
    
    public static int getScreenHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
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
     * Responsive scaling that adapts to window size.
     * Scales elements based on screen size with min/max limits.
     */
    public static int responsiveWidth(int baseWidth, int minWidth, int maxWidth) {
        int screenWidth = getScreenWidth();
        // Scale based on screen width, but clamp between min and max
        int scaledWidth = Math.round(baseWidth * (float) screenWidth / 1920f);
        return Math.max(minWidth, Math.min(maxWidth, scaledWidth));
    }
    
    public static int responsiveHeight(int baseHeight, int minHeight, int maxHeight) {
        int screenHeight = getScreenHeight();
        // Scale based on screen height, but clamp between min and max
        int scaledHeight = Math.round(baseHeight * (float) screenHeight / 1080f);
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
