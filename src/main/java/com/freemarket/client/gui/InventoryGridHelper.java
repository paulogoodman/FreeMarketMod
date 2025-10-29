package com.freemarket.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Static utility class for rendering player inventory grids in popup screens.
 * Provides shared inventory grid rendering logic for consistent UI across popups.
 */
public class InventoryGridHelper {
    
    // Inventory grid constants
    public static final int SLOT_SIZE = 18;
    public static final int SLOT_MARGIN = 2;
    public static final int TOTAL_SLOT_SIZE = SLOT_SIZE + SLOT_MARGIN;
    public static final int HOTBAR_SPACING = 4;
    
    /**
     * Renders the inventory grid (9x4 layout: 3 rows of main inventory + 1 row hotbar).
     * 
     * @param guiGraphics Graphics context
     * @param font Font renderer
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param gridStartX X position to start grid rendering
     * @param gridStartY Y position to start grid rendering
     */
    public static void renderInventoryGrid(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY, 
                                          int gridStartX, int gridStartY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        
        var player = mc.player;
        if (player == null) return;
        
        Inventory inventory = player.getInventory();
        
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
            guiGraphics.fill(slotX, slotY + 1, slotX + 1, slotY + SLOT_SIZE - 1, 0xFF404040);
            guiGraphics.fill(slotX + SLOT_SIZE - 1, slotY + 1, slotX + SLOT_SIZE, slotY + SLOT_SIZE - 1, 0xFF404040);
            guiGraphics.fill(slotX, slotY + SLOT_SIZE - 1, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0xFF404040);
            
            // Render item
            if (!stack.isEmpty()) {
                guiGraphics.renderItem(stack, slotX + 1, slotY + 1);
                guiGraphics.renderItemDecorations(font, stack, slotX + 1, slotY + 1);
                
                // Render tooltip on hover
                if (isHovered) {
                    guiGraphics.renderTooltip(font, stack, mouseX, mouseY);
                }
            }
        }
    }
    
    /**
     * Checks if mouse is over a specific slot.
     */
    public static boolean isMouseOverSlot(int mouseX, int mouseY, int slotX, int slotY) {
        return mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
               mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
    }
    
    /**
     * Gets the clicked slot index from mouse coordinates.
     * 
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param gridStartX X position where grid starts
     * @param gridStartY Y position where grid starts
     * @return Slot index (0-35) or -1 if no slot clicked
     */
    public static int getClickedSlot(double mouseX, double mouseY, int gridStartX, int gridStartY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return -1;
        
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
     * Calculates the width of the inventory grid.
     */
    public static int getGridWidth() {
        return 9 * TOTAL_SLOT_SIZE;
    }
}

