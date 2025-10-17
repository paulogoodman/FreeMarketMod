package com.freemarket.client.gui;

import com.freemarket.common.handlers.AdminModeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders modern item cards with proper GUI scaling support
 * Works like a widget with internal layout management
 */
public class ItemCardRenderer {
    
    // Texture locations for delete icons
    private static final ResourceLocation DELETE_ICON_UNSELECTED = ResourceLocation.fromNamespaceAndPath("freemarket", "textures/gui/trash_can_icon_unselected.png");
    private static final ResourceLocation DELETE_ICON_SELECTED = ResourceLocation.fromNamespaceAndPath("freemarket", "textures/gui/trash_can_model.png");
    
    /**
     * Represents the layout/bounds of a button within the card
     */
    public static class ButtonBounds {
        public final int x, y, width, height;
        
        public ButtonBounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
        
        public boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + width &&
                   mouseY >= y && mouseY <= y + height;
        }
    }
    
    /**
     * Represents the complete layout of an item card
     * All bounds are calculated once and stored
     */
    public static class CardLayout {
        public final int cardX, cardY, cardWidth, cardHeight;
        public final ButtonBounds buyButton;
        public final ButtonBounds sellButton;
        
        public CardLayout(int x, int y, int cardWidth, int cardHeight, float guiScale) {
            this.cardX = x;
            this.cardY = y;
            this.cardWidth = cardWidth;
            this.cardHeight = cardHeight;
            
            // Calculate button dimensions
            int buttonPadding = Math.max(2, cardWidth / 20);
            int buttonWidth = cardWidth - (buttonPadding * 2);
            int buttonX = x + buttonPadding;
            int buttonHeight = Math.max(8, cardHeight / 8);
            
            // Calculate button area (reserved space at bottom of card)
            int buttonAreaHeight = cardHeight / 3;
            int buttonGap = Math.max(1, cardHeight / 100);
            int buttonAreaY = y + cardHeight - buttonAreaHeight;
            
            // Create button bounds
            int buyButtonY = buttonAreaY;
            this.buyButton = new ButtonBounds(buttonX, buyButtonY, buttonWidth, buttonHeight);
            
            int sellButtonY = buyButtonY + buttonHeight + buttonGap;
            this.sellButton = new ButtonBounds(buttonX, sellButtonY, buttonWidth, buttonHeight);
        }
    }
    
    /**
     * Calculates the actual space used by the icon after dynamic adjustments
     * This matches the logic in renderItemIcon to ensure consistent positioning
     */
    private static int calculateActualIconSpaceUsed(int x, int y, int cardWidth, int cardHeight, float guiScale) {
        // Calculate icon size and position with proper boundaries (same as renderItemIcon)
        int iconPadding = Math.max(2, cardWidth / 20); // Minimum 2px padding
        int maxIconSize = cardWidth - (iconPadding * 2); // Maximum size with padding
        int minIconSize = Math.max(8, (int)(12 / guiScale)); // Scale minimum size inversely with GUI scale
        
        // Calculate icon size to fit within card boundaries
        int iconSize = Math.max(minIconSize, Math.min(maxIconSize, cardWidth / 4)); // Maximum 25% of card width
        int iconY = y + iconPadding; // Position from top with padding
        
        // Ensure icon doesn't overlap with buttons
        int buttonAreaHeight = cardHeight / 3; // Reserve space for buttons (roughly 33%)
        int maxIconY = y + cardHeight - buttonAreaHeight - iconPadding;
        if (iconY + iconSize > maxIconY) {
            // Reduce icon size if it would overlap with buttons
            iconSize = Math.max(minIconSize, maxIconY - iconY);
        }
        
        // Return the actual space used by the icon (from top to bottom)
        return iconY + iconSize - y; // Space used from card top
    }
    public void renderItemCard(GuiGraphics guiGraphics, ItemStack itemStack, int x, int y, 
                              int cardWidth, int cardHeight, int mouseX, int mouseY, float guiScale,
                              boolean canBuy, boolean canSell, boolean isBuyCooldown, boolean isSellCooldown,
                              long buyPrice, long sellPrice) {
        // Render card background (layer 1)
        renderCardBackground(guiGraphics, x, y, cardWidth, cardHeight, mouseX, mouseY);
        
        // Render delete button if admin mode (layer 2 - before icon so icon can overlap)
        if (AdminModeHandler.isAdminMode()) {
            renderDeleteButton(guiGraphics, x, y, cardWidth, cardHeight, mouseX, mouseY);
        }
        
        // Render item icon with proper scaling (layer 3)
        renderItemIcon(guiGraphics, itemStack, x, y, cardWidth, cardHeight, guiScale);
        
        // Render tooltip on top of icon if hovering (layer 4)
        if (isMouseOverIcon(x, y, cardWidth, cardHeight, mouseX, mouseY)) {
            renderItemTooltip(guiGraphics, itemStack, mouseX, mouseY);
        }
        
        // Render action buttons on top of everything (layer 5)
        renderActionButtons(guiGraphics, x, y, cardWidth, cardHeight, mouseX, mouseY, guiScale,
                           canBuy, canSell, isBuyCooldown, isSellCooldown, buyPrice, sellPrice);
    }
    
    /**
     * Legacy method for backward compatibility
     */
    public void renderItemCard(GuiGraphics guiGraphics, ItemStack itemStack, int x, int y, 
                              int cardWidth, int cardHeight, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        float guiScale = (float) client.getWindow().getGuiScale();
        renderItemCard(guiGraphics, itemStack, x, y, cardWidth, cardHeight, mouseX, mouseY, guiScale,
                      true, true, false, false, 0, 0); // Default: enabled buttons, no cooldown, no prices
    }
    
    /**
     * Legacy method for backward compatibility
     */
    public void renderItemCard(GuiGraphics guiGraphics, ItemStack itemStack, int x, int y, 
                              int cardWidth, int cardHeight, int mouseX, int mouseY, float guiScale) {
        renderItemCard(guiGraphics, itemStack, x, y, cardWidth, cardHeight, mouseX, mouseY, guiScale,
                      true, true, false, false, 0, 0); // Default: enabled buttons, no cooldown, no prices
    }
    
    /**
     * Renders the card background (no hover effect on the card itself)
     */
    private void renderCardBackground(GuiGraphics guiGraphics, int x, int y, int cardWidth, int cardHeight, int mouseX, int mouseY) {
        // Card background colors (semi-transparent) - no hover effect
        int backgroundColor = 0x801A1A1A; // 50% opacity
        int borderColor = 0x80404040; // 50% opacity
        
        // Draw card background
        guiGraphics.fill(x, y, x + cardWidth, y + cardHeight, backgroundColor);
        
        // Draw card border
        guiGraphics.fill(x, y, x + cardWidth, y + 2, borderColor); // Top
        guiGraphics.fill(x, y, x + 2, y + cardHeight, borderColor); // Left
        guiGraphics.fill(x + cardWidth - 2, y, x + cardWidth, y + cardHeight, borderColor); // Right
        guiGraphics.fill(x, y + cardHeight - 2, x + cardWidth, y + cardHeight, borderColor); // Bottom
    }
    
    /**
     * Renders a delete button in the top-right corner of the card (admin mode only)
     */
    private void renderDeleteButton(GuiGraphics guiGraphics, int x, int y, int cardWidth, int cardHeight, int mouseX, int mouseY) {
        int deleteButtonSize = (int)(cardWidth * 0.12); // 12% of card width (smaller)
        int margin = 0; // No margin - position at absolute edge
        int deleteButtonX = x + cardWidth - deleteButtonSize - margin; // Right at the edge
        int deleteButtonY = y + margin; // Top at the edge
        
        boolean isHovered = mouseX >= deleteButtonX && mouseX <= deleteButtonX + deleteButtonSize &&
                           mouseY >= deleteButtonY && mouseY <= deleteButtonY + deleteButtonSize;
        
        // Calculate icon dimensions within the button area (37.5% scale - 50% larger than 25%)
        int iconWidth = (int)(deleteButtonSize * 0.375); // 37.5% of button size
        int iconHeight = (int)(deleteButtonSize * 0.375); // 37.5% of button size
        int iconX = deleteButtonX + (deleteButtonSize - iconWidth) / 2; // Center horizontally
        int iconY = deleteButtonY + (deleteButtonSize - iconHeight) / 2; // Center vertically
        
        // Choose texture based on hover state
        ResourceLocation iconTexture = isHovered ? DELETE_ICON_SELECTED : DELETE_ICON_UNSELECTED;
        
        // Render the appropriate PNG texture
        guiGraphics.blit(iconTexture, iconX, iconY, 0, 0, iconWidth, iconHeight, iconWidth, iconHeight);
    }
    
    /**
     * Renders the item icon with proper GUI scaling
     * Based on EMI's implementation
     */
    private void renderItemIcon(GuiGraphics guiGraphics, ItemStack itemStack, int x, int y, 
                               int cardWidth, int cardHeight, float guiScale) {
        // Calculate icon size and position with proper boundaries
        int iconPadding = Math.max(2, cardWidth / 20); // Minimum 2px padding
        int maxIconSize = cardWidth - (iconPadding * 2); // Maximum size with padding
        int minIconSize = Math.max(8, (int)(12 / guiScale)); // Scale minimum size inversely with GUI scale
        
        // Calculate icon size to fit within card boundaries
        int iconSize = Math.max(minIconSize, Math.min(maxIconSize, cardWidth / 4)); // Maximum 25% of card width
        int iconX = x + (cardWidth - iconSize) / 2; // Center horizontally
        int iconY = y + iconPadding; // Position from top with padding
        
        // Ensure icon doesn't overlap with buttons
        int buttonAreaHeight = cardHeight / 3; // Reserve space for buttons (roughly 33%)
        int maxIconY = y + cardHeight - buttonAreaHeight - iconPadding;
        if (iconY + iconSize > maxIconY) {
            // Reduce icon size if it would overlap with buttons
            iconSize = Math.max(minIconSize, maxIconY - iconY);
            iconX = x + (cardWidth - iconSize) / 2; // Re-center
        }
        
        // Use proper matrix transformations for GUI scaling
        guiGraphics.pose().pushPose();
        
        // Translate to center of the icon area
        guiGraphics.pose().translate(iconX + iconSize / 2, iconY + iconSize / 2, 0);
        
        // Apply proper scaling for pixel-perfect rendering
        float scale = (float) iconSize / 16.0f; // Scale to desired size
        guiGraphics.pose().scale(scale, scale, scale);
        
        // Render item at the center (offset by half size)
        guiGraphics.renderItem(itemStack, -8, -8);
        
        // Render item decorations (stack count, durability, etc.) - this handles proper layering
        Minecraft client = Minecraft.getInstance();
        guiGraphics.renderItemDecorations(client.font, itemStack, -8, -8);
        
        guiGraphics.pose().popPose();
    }
    
    /**
     * Renders action buttons (Buy/Sell) with proper scaling and cooldown states
     * Uses CardLayout for consistent positioning
     */
    private void renderActionButtons(GuiGraphics guiGraphics, int x, int y, int cardWidth, int cardHeight, int mouseX, int mouseY, float guiScale,
                                   boolean canBuy, boolean canSell, boolean isBuyCooldown, boolean isSellCooldown, long buyPrice, long sellPrice) {
        // Create layout once - this is our "div" with all bounds calculated
        CardLayout layout = new CardLayout(x, y, cardWidth, cardHeight, guiScale);
        
        // Check hover states using CardLayout (same logic as click detection)
        boolean isBuyHovered = layout.buyButton.contains(mouseX, mouseY);
        boolean isSellHovered = layout.sellButton.contains(mouseX, mouseY);
        
        // For now, pressed state is false (would need mouse button state from container)
        boolean isPressed = false;
        
        // Render Buy button only if buy price > 0
        if (buyPrice > 0) {
            String buyText = "Buy $" + formatPrice(buyPrice);
            boolean buyEnabled = canBuy && !isBuyCooldown;
            renderButtonWithState(guiGraphics, buyText, layout.buyButton.x, layout.buyButton.y, layout.buyButton.width, layout.buyButton.height,
                                mouseX, mouseY, 0xFF4CAF50, buyEnabled, isBuyCooldown, isBuyHovered, isPressed); // Green color
        }

        // Render Sell button only if sell price > 0
        if (sellPrice > 0) {
            String sellText = "Sell $" + formatPrice(sellPrice);
            boolean sellEnabled = canSell && !isSellCooldown;
            renderButtonWithState(guiGraphics, sellText, layout.sellButton.x, layout.sellButton.y, layout.sellButton.width, layout.sellButton.height,
                                mouseX, mouseY, 0xFF2196F3, sellEnabled, isSellCooldown, isSellHovered, isPressed); // Blue color
        }
    }
    
    /**
     * Renders a single button with hover effects, press effects, and cooldown states
     */
    private void renderButtonWithState(GuiGraphics guiGraphics, String text, int x, int y, int width, int height, 
                                      int mouseX, int mouseY, int baseColor, boolean enabled, boolean isCooldown, boolean isHovered, boolean isPressed) {
        // isHovered and isPressed are now passed in from CardLayout-based calculation
        
        // Determine button colors based on state
        int backgroundColor;
        int textColor;
        
        if (isCooldown) {
            // In cooldown - gray color
            backgroundColor = isPressed ? 0x90707070 : 
                             isHovered ? 0xCC9E9E9E : 0x999E9E9E;
            textColor = 0xFFFFFFFF; // White text for cooldown
        } else if (enabled) {
            // Enabled - normal colors with press state
            if (isPressed) {
                backgroundColor = 0xE0808080; // Gray when pressed (high opacity)
            } else if (isHovered) {
                // Brighter version of base color with higher opacity
                int r = (baseColor >> 16) & 0xFF;
                int g = (baseColor >> 8) & 0xFF;
                int b = baseColor & 0xFF;
                backgroundColor = 0xCC000000 | (r << 16) | (g << 8) | b; // 80% opacity
            } else {
                // Normal base color with lower opacity
                int r = (baseColor >> 16) & 0xFF;
                int g = (baseColor >> 8) & 0xFF;
                int b = baseColor & 0xFF;
                backgroundColor = 0x99000000 | (r << 16) | (g << 8) | b; // 60% opacity
            }
            textColor = 0xFFFFFFFF; // White text
        } else {
            // Disabled - muted colors
            backgroundColor = isPressed ? 0x90555555 :
                             isHovered ? 0xCC666666 : 0x99666666;
            textColor = 0xFF999999; // Gray text
        }
        
        // Draw button background
        guiGraphics.fill(x, y, x + width, y + height, backgroundColor);
        
        // Draw button border
        guiGraphics.fill(x, y, x + width, y + 1, 0x80404040); // Top
        guiGraphics.fill(x, y, x + 1, y + height, 0x80404040); // Left
        guiGraphics.fill(x + width - 1, y, x + width, y + height, 0x80404040); // Right
        guiGraphics.fill(x, y + height - 1, x + width, y + height, 0x80404040); // Bottom
        
        // Draw button text (centered within the inner area, accounting for border)
        Minecraft client = Minecraft.getInstance();
        int borderThickness = 1; // Border is 1 pixel on each side
        int innerWidth = width - (borderThickness * 2);
        int innerHeight = height - (borderThickness * 2);
        
        // Truncate text if it doesn't fit within the button
        String displayText = truncateTextToWidth(text, innerWidth);
        int textWidth = client.font.width(displayText);
        int textHeight = client.font.lineHeight;
        int textX = x + borderThickness + (innerWidth - textWidth) / 2;
        int textY = y + borderThickness + (innerHeight - textHeight) / 2;
        
        guiGraphics.drawString(client.font, displayText, textX, textY, textColor);
    }
    
    /**
     * Formats a price number to be shorter for display with intelligent decimal handling.
     * Only abbreviates when there are trailing zeros, otherwise shows full number.
     * Examples: 1000 -> 1K, 1001 -> 1001, 1100000 -> 1.1M, 1000001 -> 1000001
     */
    private static String formatPrice(long price) {
        if (price < 1000) {
            return String.valueOf(price);
        } else if (price < 1000000) {
            // Thousands - only abbreviate if all trailing digits are zero
            if (price % 1000 == 0) {
                double thousands = price / 1000.0;
                if (thousands == Math.floor(thousands)) {
                    return String.format("%.0fK", thousands);
                } else {
                    return String.format("%.1fK", thousands);
                }
            } else {
                // Has non-zero trailing digits, show full number
                return String.valueOf(price);
            }
        } else if (price < 1000000000) {
            // Millions - only abbreviate if all trailing digits are zero
            if (price % 1000000 == 0) {
                double millions = price / 1000000.0;
                if (millions == Math.floor(millions)) {
                    return String.format("%.0fM", millions);
                } else {
                    return String.format("%.1fM", millions);
                }
            } else {
                // Has non-zero trailing digits, show full number
                return String.valueOf(price);
            }
        } else if (price < 1000000000000L) {
            // Billions - only abbreviate if all trailing digits are zero
            if (price % 1000000000 == 0) {
                double billions = price / 1000000000.0;
                if (billions == Math.floor(billions)) {
                    return String.format("%.0fB", billions);
                } else {
                    return String.format("%.1fB", billions);
                }
            } else {
                // Has non-zero trailing digits, show full number
                return String.valueOf(price);
            }
        } else {
            // Trillions - only abbreviate if all trailing digits are zero
            if (price % 1000000000000L == 0) {
                double trillions = price / 1000000000000.0;
                if (trillions == Math.floor(trillions)) {
                    return String.format("%.0fT", trillions);
                } else {
                    return String.format("%.1fT", trillions);
                }
            } else {
                // Has non-zero trailing digits, show full number
                return String.valueOf(price);
            }
        }
    }
    
    /**
     * Truncates text to fit within the specified width, adding ellipsis if needed.
     * Examples: "Buy $1000000" -> "Buy $1000..", "Sell $500" -> "Sell $500"
     */
    private static String truncateTextToWidth(String text, int maxWidth) {
        Minecraft client = Minecraft.getInstance();
        int textWidth = client.font.width(text);
        
        // If text fits, return as-is
        if (textWidth <= maxWidth) {
            return text;
        }
        
        // Binary search to find the maximum characters that fit
        int left = 0;
        int right = text.length();
        String bestFit = "";
        
        while (left <= right) {
            int mid = (left + right) / 2;
            String candidate = text.substring(0, mid) + "..";
            int candidateWidth = client.font.width(candidate);
            
            if (candidateWidth <= maxWidth) {
                bestFit = candidate;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        
        return bestFit.isEmpty() ? ".." : bestFit;
    }

    /**
     * Static method to check if a click occurred on a buy button for a specific item
     * Uses CardLayout for proper component-based hit detection
     * Returns false if buy price is 0 (button not rendered)
     */
    public static boolean isBuyButtonClicked(int x, int y, int cardWidth, int cardHeight, int mouseX, int mouseY, float guiScale, long buyPrice) {
        if (buyPrice <= 0) {
            return false; // Button not rendered if price is 0
        }
        CardLayout layout = new CardLayout(x, y, cardWidth, cardHeight, guiScale);
        return layout.buyButton.contains(mouseX, mouseY);
    }

    /**
     * Static method to check if a click occurred on a sell button for a specific item
     * Uses CardLayout for proper component-based hit detection
     * Returns false if sell price is 0 (button not rendered)
     */
    public static boolean isSellButtonClicked(int x, int y, int cardWidth, int cardHeight, int mouseX, int mouseY, float guiScale, long sellPrice) {
        if (sellPrice <= 0) {
            return false; // Button not rendered if price is 0
        }
        CardLayout layout = new CardLayout(x, y, cardWidth, cardHeight, guiScale);
        return layout.sellButton.contains(mouseX, mouseY);
    }
    
    /**
     * Checks if a click occurred on a buy button (legacy method for backward compatibility)
     */
    public boolean isBuyButtonClicked(int x, int y, int cardWidth, int cardHeight, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        float guiScale = (float) client.getWindow().getGuiScale();
        return isBuyButtonClicked(x, y, cardWidth, cardHeight, mouseX, mouseY, guiScale, 1); // Default price 1 for legacy compatibility
    }
    
    /**
     * Checks if a click occurred on a sell button (legacy method for backward compatibility)
     */
    public boolean isSellButtonClicked(int x, int y, int cardWidth, int cardHeight, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        float guiScale = (float) client.getWindow().getGuiScale();
        return isSellButtonClicked(x, y, cardWidth, cardHeight, mouseX, mouseY, guiScale, 1); // Default price 1 for legacy compatibility
    }
    
    /**
     * Checks if the mouse is hovering over the item icon area
     */
    private boolean isMouseOverIcon(int x, int y, int cardWidth, int cardHeight, int mouseX, int mouseY) {
        // Calculate icon size and position (same logic as renderItemIcon)
        int iconPadding = Math.max(2, cardWidth / 20); // Minimum 2px padding
        int maxIconSize = cardWidth - (iconPadding * 2); // Maximum size with padding
        int minIconSize = Math.max(8, cardWidth / 4); // Minimum size
        int iconSize = Math.max(minIconSize, Math.min(maxIconSize, cardWidth / 4)); // Maximum 25% of card width
        int iconX = x + (cardWidth - iconSize) / 2; // Center horizontally
        int iconY = y + iconPadding; // Position from top with padding
        
        // Ensure icon doesn't overlap with buttons
        int buttonAreaHeight = cardHeight / 3; // Reserve space for buttons (roughly 33%)
        int maxIconY = y + cardHeight - buttonAreaHeight - iconPadding;
        if (iconY + iconSize > maxIconY) {
            // Reduce icon size if it would overlap with buttons
            iconSize = Math.max(minIconSize, maxIconY - iconY);
            iconX = x + (cardWidth - iconSize) / 2; // Re-center
        }
        
        // Check if mouse is within icon bounds
        return mouseX >= iconX && mouseX <= iconX + iconSize &&
               mouseY >= iconY && mouseY <= iconY + iconSize;
    }
    
    /**
     * Renders the item tooltip when hovering over an item card
     */
    private void renderItemTooltip(GuiGraphics guiGraphics, ItemStack itemStack, int mouseX, int mouseY) {
        // Get the item's tooltip components
        java.util.List<net.minecraft.network.chat.Component> tooltip = itemStack.getTooltipLines(
            net.minecraft.world.item.Item.TooltipContext.EMPTY,
            Minecraft.getInstance().player,
            net.minecraft.world.item.TooltipFlag.Default.NORMAL
        );
        
        // Render the tooltip
        guiGraphics.renderTooltip(
            Minecraft.getInstance().font,
            tooltip,
            itemStack.getTooltipImage(),
            mouseX, mouseY
        );
    }
}
