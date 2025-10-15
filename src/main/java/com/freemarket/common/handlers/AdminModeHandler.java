package com.freemarket.common.handlers;

import com.freemarket.FreeMarket;

/**
 * Handles admin mode state for the FreeMarket mod.
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
        FreeMarket.LOGGER.info("Admin mode {} for FreeMarket", enabled ? "enabled" : "disabled");
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
        FreeMarket.LOGGER.info("Admin mode {} for FreeMarket", adminMode ? "enabled" : "disabled");
        return adminMode;
    }
}
