package com.servershop;

/**
 * Handles admin mode state for the ServerShop mod.
 * This class manages whether admin mode is enabled or disabled.
 */
public class AdminModeHandler {
    private static boolean adminMode = false;
    
    /**
     * Sets the admin mode status.
     * @param enabled true to enable admin mode, false to disable
     */
    public static void setAdminMode(boolean enabled) {
        adminMode = enabled;
        ServerShop.LOGGER.info("Admin mode {} for ServerShop", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Gets the current admin mode status.
     * @return true if admin mode is enabled, false otherwise
     */
    public static boolean isAdminMode() {
        return adminMode;
    }
    
    /**
     * Toggles the admin mode status.
     * @return the new admin mode status
     */
    public static boolean toggleAdminMode() {
        adminMode = !adminMode;
        ServerShop.LOGGER.info("Admin mode {} for ServerShop", adminMode ? "enabled" : "disabled");
        return adminMode;
    }
}
