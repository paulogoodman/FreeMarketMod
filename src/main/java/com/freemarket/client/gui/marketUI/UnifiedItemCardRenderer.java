package com.freemarket.client.gui.marketUI;

import com.freemarket.client.gui.commonUI.ButtonType;
import com.freemarket.client.gui.commonUI.CardButtonConfig;
import com.freemarket.client.gui.commonUI.CardType;
import com.freemarket.client.gui.commonUI.GuiScalingHelper;
import com.freemarket.common.handlers.AdminModeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified card renderer that handles both marketplace and auction cards.
 * Uses CardButtonConfig to determine which buttons to display.
 */
public class UnifiedItemCardRenderer {
    
    // Texture locations for delete icons
    private static final ResourceLocation DELETE_ICON_UNSELECTED = ResourceLocation.fromNamespaceAndPath("freemarket", "textures/gui/trash_can_icon_unselected.png");
    private static final ResourceLocation DELETE_ICON_SELECTED = ResourceLocation.fromNamespaceAndPath("freemarket", "textures/gui/trash_can_model.png");
    
    // Cache for CardLayout instances
    private static CardLayout cachedLayout;
    private static int cachedX, cachedY, cachedWidth, cachedHeight;
    private static float cachedGuiScale;
    private static int cachedButtonCount;
    
    // Cache for text truncation
    private final Map<String, String> textTruncationCache = new HashMap<>();
    
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
     * Represents the complete layout of a card with dynamic button positioning
     */
    public static class CardLayout {
        public final int cardX, cardY, cardWidth, cardHeight;
        public final List<ButtonBounds> buttons;
        
        public CardLayout(int x, int y, int cardWidth, int cardHeight, float guiScale, int numButtons) {
            this.cardX = x;
            this.cardY = y;
            this.cardWidth = cardWidth;
            this.cardHeight = cardHeight;
            this.buttons = new ArrayList<>();
            
            // Calculate button dimensions
            int buttonPadding = Math.max(2, cardWidth / 20);
            int buttonWidth = cardWidth - (buttonPadding * 2);
            int buttonX = x + buttonPadding;
            int buttonHeight = Math.max(8, cardHeight / 8);
            
            // Calculate button area (reserved space at bottom of card)
            int buttonAreaHeight = cardHeight / 3;
            int buttonAreaY = y + cardHeight - buttonAreaHeight;
            
            // Dynamically position buttons based on count
            if (numButtons == 1) {
                // Single button: full width, centered vertically in button area
                int singleButtonY = buttonAreaY + (buttonAreaHeight - buttonHeight) / 2;
                buttons.add(new ButtonBounds(buttonX, singleButtonY, buttonWidth, buttonHeight));
            } else if (numButtons == 2) {
                // Two buttons: stacked vertically (Buy/Sell style)
                int buttonGap = Math.max(1, cardHeight / 100);
                buttons.add(new ButtonBounds(buttonX, buttonAreaY, buttonWidth, buttonHeight));
                buttons.add(new ButtonBounds(buttonX, buttonAreaY + buttonHeight + buttonGap, 
                                            buttonWidth, buttonHeight));
            } else if (numButtons == 0) {
                // No buttons - card layout still valid
            }
        }
    }
    
    /**
     * Main render method for unified card rendering.
     * @param infoText Optional info text to display (e.g., auction details). Pass null for marketplace.
     * @param popupVisible Whether any popup is currently visible (prevents tooltip rendering)
     * @return The ItemStack if tooltip should be shown, null otherwise (to be rendered after all cards)
     */
    public ItemStack renderCard(GuiGraphics guiGraphics, ItemStack itemStack, CardButtonConfig config,
                          String infoText, int x, int y, int cardWidth, int cardHeight,
                          int mouseX, int mouseY, float guiScale, boolean popupVisible) {
        // Render card background
        renderCardBackground(guiGraphics, x, y, cardWidth, cardHeight);
        
        // Render delete button if admin mode (for both marketplace and auction cards)
        if (AdminModeHandler.isAdminMode() && (config.type == CardType.MARKETPLACE || config.type == CardType.AUCTION)) {
            renderDeleteButton(guiGraphics, x, y, cardWidth, cardHeight, mouseX, mouseY);
        }
        
        // Render item icon
        renderItemIcon(guiGraphics, itemStack, x, y, cardWidth, cardHeight, guiScale);
        
        // Check if tooltip should be shown (but don't render it yet - return for deferred rendering)
        ItemStack tooltipStack = null;
        if (!popupVisible && isMouseOverIcon(x, y, cardWidth, cardHeight, mouseX, mouseY, guiScale)) {
            tooltipStack = itemStack;
        }
        
        // Render info text if provided (auction cards)
        if (infoText != null && !infoText.isEmpty()) {
            renderInfoText(guiGraphics, infoText, x, y, cardWidth, cardHeight, guiScale);
        }
        
        // Render buttons based on config
        renderButtons(guiGraphics, config, x, y, cardWidth, cardHeight, mouseX, mouseY, guiScale);
        
        return tooltipStack;
    }
    
    private void renderCardBackground(GuiGraphics guiGraphics, int x, int y, int cardWidth, int cardHeight) {
        int backgroundColor = 0x801A1A1A;
        int borderColor = 0x80404040;
        int borderWidth = 2;
        
        // Fill background
        guiGraphics.fill(x, y, x + cardWidth, y + cardHeight, backgroundColor);
        
        // Draw borders without corner overlaps
        // Top border: 2 rows, full width (creates top corners)
        guiGraphics.fill(x, y, x + cardWidth, y + borderWidth, borderColor);
        // Left border: 2 columns, skip top and bottom rows
        guiGraphics.fill(x, y + borderWidth, x + borderWidth, y + cardHeight - borderWidth, borderColor);
        // Right border: 2 columns, skip top and bottom rows
        guiGraphics.fill(x + cardWidth - borderWidth, y + borderWidth, x + cardWidth, y + cardHeight - borderWidth, borderColor);
        // Bottom border: 2 rows, full width (creates bottom corners)
        guiGraphics.fill(x, y + cardHeight - borderWidth, x + cardWidth, y + cardHeight, borderColor);
    }
    
    private void renderDeleteButton(GuiGraphics guiGraphics, int x, int y, int cardWidth, int cardHeight, int mouseX, int mouseY) {
        int deleteButtonSize = (int)(cardWidth * 0.12);
        int margin = 0;
        int deleteButtonX = x + cardWidth - deleteButtonSize - margin;
        int deleteButtonY = y + margin;
        
        boolean isHovered = mouseX >= deleteButtonX && mouseX <= deleteButtonX + deleteButtonSize &&
                           mouseY >= deleteButtonY && mouseY <= deleteButtonY + deleteButtonSize;
        
        int iconWidth = (int)(deleteButtonSize * 0.375);
        int iconHeight = (int)(deleteButtonSize * 0.375);
        int iconX = deleteButtonX + (deleteButtonSize - iconWidth) / 2;
        int iconY = deleteButtonY + (deleteButtonSize - iconHeight) / 2;
        
        ResourceLocation iconTexture = isHovered ? DELETE_ICON_SELECTED : DELETE_ICON_UNSELECTED;
        guiGraphics.blit(iconTexture, iconX, iconY, 0, 0, iconWidth, iconHeight, iconWidth, iconHeight);
    }
    
    private void renderItemIcon(GuiGraphics guiGraphics, ItemStack itemStack, int x, int y, 
                               int cardWidth, int cardHeight, float guiScale) {
        int iconPadding = Math.max(2, cardWidth / 20);
        int maxIconSize = cardWidth - (iconPadding * 2);
        int minIconSize = Math.max(8, (int)(12 / guiScale));
        
        int iconSize = Math.max(minIconSize, Math.min(maxIconSize, cardWidth / 4));
        int iconX = x + (cardWidth - iconSize) / 2;
        int iconY = y + iconPadding;
        
        int buttonAreaHeight = cardHeight / 3;
        int maxIconY = y + cardHeight - buttonAreaHeight - iconPadding;
        if (iconY + iconSize > maxIconY) {
            iconSize = Math.max(minIconSize, maxIconY - iconY);
            iconX = x + (cardWidth - iconSize) / 2;
        }
        
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(iconX + iconSize / 2, iconY + iconSize / 2, 0);
        float scale = (float) iconSize / 16.0f;
        guiGraphics.pose().scale(scale, scale, scale);
        guiGraphics.renderItem(itemStack, -8, -8);
        Minecraft client = Minecraft.getInstance();
        guiGraphics.renderItemDecorations(client.font, itemStack, -8, -8);
        guiGraphics.pose().popPose();
    }
    
    private void renderInfoText(GuiGraphics guiGraphics, String infoText, int x, int y, 
                               int cardWidth, int cardHeight, float guiScale) {
        Minecraft client = Minecraft.getInstance();
        
        int iconAreaHeight = cardHeight / 3;
        int infoAreaY = y + iconAreaHeight;
        
        int textX = x + 4;
        int textWidth = cardWidth - 8;
        int currentY = infoAreaY + 2;
        
        // Calculate button area position for bid text rendering
        int buttonAreaHeight = cardHeight / 3;
        int buttonAreaY = y + cardHeight - buttonAreaHeight;
        
        // Split info text by newlines and render each line
        String[] lines = infoText.split("\n");
        int lineHeight = client.font.lineHeight + 1;
        
        for (String line : lines) {
            // Check if this is the bid line (starts with "Current Bid:" or "Starting Bid:")
            if (line.startsWith("Current Bid:") || line.startsWith("Starting Bid:")) {
                // Render bid text in the gap between icon area and button area
                String truncated = truncateTextToWidth(line, textWidth);
                
                // Position horizontally centered (like a button would be)
                int bidTextWidth = client.font.width(truncated);
                int bidTextX = x + (cardWidth - bidTextWidth) / 2;
                
                // Calculate where icon actually ends (matches renderItemIcon logic)
                int iconPadding = Math.max(2, cardWidth / 20);
                int minIconSize = Math.max(8, (int)(12 / guiScale));
                int maxIconSize = cardWidth - (iconPadding * 2);
                int iconSize = Math.max(minIconSize, Math.min(maxIconSize, cardWidth / 4));
                int iconY = y + iconPadding;
                int maxIconY = y + cardHeight - buttonAreaHeight - iconPadding;
                int actualIconSize = iconSize;
                if (iconY + iconSize > maxIconY) {
                    actualIconSize = Math.max(minIconSize, maxIconY - iconY);
                }
                int iconEndY = iconY + actualIconSize;
                
                // Calculate button area start
                // Buttons are at the bottom 1/3 of the card
                int buttonY = buttonAreaY; // Top of button area
                
                // Position bid text in the middle of the gap between icon and buttons
                // Add extra margin from icon for better readability
                int gapBetween = buttonY - iconEndY;
                int marginFromIcon = (int)(gapBetween * 0.4); // Use 40% of gap as margin from icon
                int bidTextY = iconEndY + marginFromIcon; // Position with margin from icon
                
                int color = 0xFFFFD700; // Gold
                guiGraphics.drawString(client.font, truncated, bidTextX, bidTextY, color);
            } else if (line.startsWith("🕐")) {
                // Render time remaining in top left corner
                String timeText = truncateTextToWidth(line, cardWidth - 8);
                
                // Position at top left with margin from border
                int timeX = x + 4; // 4px padding from left edge
                int timeY = y + 4; // 4px padding from top for margin
                
                // Determine color based on urgency
                int color;
                if (line.contains("Expired")) {
                    color = 0xFFFF0000; // Red
                } else {
                    color = 0xFF4CAF50; // Green (default for time)
                }
                
                guiGraphics.drawString(client.font, timeText, timeX, timeY, color);
            } else {
                // Render other info text in the middle area as normal
                String truncated = truncateTextToWidth(line, textWidth);
                
                // Determine color based on line content (simple heuristic)
                int color = 0xFFAAAAAA; // Default gray
                if (line.startsWith("Bid:")) {
                    color = 0xFFFFD700; // Gold
                } else if (line.contains("By:")) {
                    color = 0xFF888888; // Darker gray
                }
                
                guiGraphics.drawString(client.font, truncated, textX, currentY, color);
                currentY += lineHeight;
            }
        }
    }
    
    private void renderButtons(GuiGraphics guiGraphics, CardButtonConfig config, int x, int y,
                              int cardWidth, int cardHeight, int mouseX, int mouseY, float guiScale) {
        CardLayout layout = getCachedCardLayout(x, y, cardWidth, cardHeight, guiScale, config.getButtonCount());
        
        int buttonIndex = 0;
        
        // Render Buy button if configured
        if (config.showBuy && config.buyPrice > 0) {
            ButtonBounds bounds = layout.buttons.get(buttonIndex++);
            boolean isHovered = bounds.contains(mouseX, mouseY);
            String text = "Buy $" + formatPrice(config.buyPrice);
            boolean enabled = config.canBuy && !config.isBuyCooldown;
            renderButtonWithState(guiGraphics, text, bounds.x, bounds.y, bounds.width, bounds.height,
                                 0xFF4CAF50, enabled, config.isBuyCooldown, isHovered, false);
        }
        
        // Render Sell button if configured
        if (config.showSell && config.sellPrice > 0) {
            ButtonBounds bounds = layout.buttons.get(buttonIndex++);
            boolean isHovered = bounds.contains(mouseX, mouseY);
            String text = "Sell $" + formatPrice(config.sellPrice);
            boolean enabled = config.canSell && !config.isSellCooldown;
            renderButtonWithState(guiGraphics, text, bounds.x, bounds.y, bounds.width, bounds.height,
                                 0xFF2196F3, enabled, config.isSellCooldown, isHovered, false);
        }
        
        // Render Bid button if configured
        if (config.showBid) {
            ButtonBounds bounds = layout.buttons.get(buttonIndex++);
            boolean isHovered = bounds.contains(mouseX, mouseY);
            
            // Determine bid button text and color based on state
            String text;
            int color;
            boolean enabled;
            
            if (config.isHighestBidder) {
                text = "Highest Bid";
                color = 0xFF4CAF50;
                enabled = false;
            } else if (!config.canBid) {
                text = "Insufficient Funds";
                color = 0xFF888888;
                enabled = false;
            } else {
                text = "Place Bid";
                color = 0xFF4CAF50;
                enabled = !config.isBidCooldown;
            }
            
            renderButtonWithState(guiGraphics, text, bounds.x, bounds.y, bounds.width, bounds.height,
                                 color, enabled, config.isBidCooldown, isHovered, false);
        }
        
        // Render Cancel Auction button if configured
        if (config.showCancelAuction) {
            ButtonBounds bounds = layout.buttons.get(buttonIndex++);
            boolean isHovered = bounds.contains(mouseX, mouseY);
            
            // Cancel button is always red and enabled
            String text = "Cancel Auction";
            int color = 0xFFE53935; // Red color
            boolean enabled = true;
            
            renderButtonWithState(guiGraphics, text, bounds.x, bounds.y, bounds.width, bounds.height,
                                 color, enabled, false, isHovered, false);
        }
    }
    
    private void renderButtonWithState(GuiGraphics guiGraphics, String text, int x, int y, int width, int height,
                                      int baseColor, boolean enabled, boolean isCooldown, boolean isHovered, boolean isPressed) {
        int backgroundColor;
        int textColor;
        
        if (isCooldown) {
            backgroundColor = isPressed ? 0x90707070 : 
                             isHovered ? 0xCC9E9E9E : 0x999E9E9E;
            textColor = 0xFFFFFFFF;
        } else if (enabled) {
            if (isPressed) {
                backgroundColor = 0xE0808080;
            } else if (isHovered) {
                int r = (baseColor >> 16) & 0xFF;
                int g = (baseColor >> 8) & 0xFF;
                int b = baseColor & 0xFF;
                backgroundColor = 0xCC000000 | (r << 16) | (g << 8) | b;
            } else {
                int r = (baseColor >> 16) & 0xFF;
                int g = (baseColor >> 8) & 0xFF;
                int b = baseColor & 0xFF;
                backgroundColor = 0x99000000 | (r << 16) | (g << 8) | b;
            }
            textColor = 0xFFFFFFFF;
        } else {
            backgroundColor = isPressed ? 0x90555555 :
                             isHovered ? 0xCC666666 : 0x99666666;
            textColor = 0xFF999999;
        }
        
        guiGraphics.fill(x, y, x + width, y + height, backgroundColor);
        guiGraphics.fill(x, y, x + width, y + 1, 0x80404040);
        guiGraphics.fill(x, y + 1, x + 1, y + height - 1, 0x80404040);
        guiGraphics.fill(x + width - 1, y + 1, x + width, y + height - 1, 0x80404040);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, 0x80404040);
        
        Minecraft client = Minecraft.getInstance();
        int borderThickness = 1;
        int horizontalPadding = 4; // Padding inside the border so text doesn't touch edges
        int innerWidth = width - (borderThickness * 2);
        int innerHeight = height - (borderThickness * 2);
        
        // Account for padding when truncating text
        int availableTextWidth = innerWidth - (horizontalPadding * 2);
        String displayText = truncateTextToWidth(text, availableTextWidth);
        int textWidth = client.font.width(displayText);
        int textHeight = client.font.lineHeight;
        int textX = x + borderThickness + (innerWidth - textWidth) / 2;
        int textY = y + borderThickness + (innerHeight - textHeight) / 2;
        
        guiGraphics.drawString(client.font, displayText, textX, textY, textColor);
    }
    
    /**
     * Checks which button (if any) was clicked on a card.
     */
    public static ButtonType checkButtonClick(int x, int y, int cardWidth, int cardHeight,
                                             int mouseX, int mouseY, float guiScale,
                                             CardButtonConfig config) {
        if (config.getButtonCount() == 0) {
            return ButtonType.NONE;
        }
        
        CardLayout layout = getCachedCardLayout(x, y, cardWidth, cardHeight, guiScale, config.getButtonCount());
        int buttonIndex = 0;
        
        if (config.showBuy && config.buyPrice > 0) {
            if (layout.buttons.get(buttonIndex++).contains(mouseX, mouseY)) {
                return ButtonType.BUY;
            }
        }
        
        if (config.showSell && config.sellPrice > 0) {
            if (layout.buttons.get(buttonIndex++).contains(mouseX, mouseY)) {
                return ButtonType.SELL;
            }
        }
        
        if (config.showBid) {
            if (layout.buttons.get(buttonIndex++).contains(mouseX, mouseY)) {
                return ButtonType.BID;
            }
        }
        
        if (config.showCancelAuction) {
            if (layout.buttons.get(buttonIndex++).contains(mouseX, mouseY)) {
                return ButtonType.CANCEL_AUCTION;
            }
        }
        
        return ButtonType.NONE;
    }
    
    private static String formatPrice(long price) {
        if (price < 1000) {
            return String.valueOf(price);
        } else if (price < 1000000) {
            if (price % 1000 == 0) {
                double thousands = price / 1000.0;
                return thousands == Math.floor(thousands) ? String.format("%.0fK", thousands) : String.format("%.1fK", thousands);
            }
            return String.valueOf(price);
        } else if (price < 1000000000) {
            if (price % 1000000 == 0) {
                double millions = price / 1000000.0;
                return millions == Math.floor(millions) ? String.format("%.0fM", millions) : String.format("%.1fM", millions);
            }
            return String.valueOf(price);
        } else if (price < 1000000000000L) {
            if (price % 1000000000 == 0) {
                double billions = price / 1000000000.0;
                return billions == Math.floor(billions) ? String.format("%.0fB", billions) : String.format("%.1fB", billions);
            }
            return String.valueOf(price);
        } else {
            if (price % 1000000000000L == 0) {
                double trillions = price / 1000000000000.0;
                return trillions == Math.floor(trillions) ? String.format("%.0fT", trillions) : String.format("%.1fT", trillions);
            }
            return String.valueOf(price);
        }
    }
    
    private String truncateTextToWidth(String text, int maxWidth) {
        String cacheKey = text + "|" + maxWidth;
        
        if (textTruncationCache.containsKey(cacheKey)) {
            return textTruncationCache.get(cacheKey);
        }
        
        Minecraft client = Minecraft.getInstance();
        int textWidth = client.font.width(text);
        
        String result;
        if (textWidth <= maxWidth) {
            result = text;
        } else {
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
            
            result = bestFit.isEmpty() ? ".." : bestFit;
        }
        
        textTruncationCache.put(cacheKey, result);
        
        if (textTruncationCache.size() > 1000) {
            textTruncationCache.clear();
        }
        
        return result;
    }
    
    private boolean isMouseOverIcon(int x, int y, int cardWidth, int cardHeight, int mouseX, int mouseY, float guiScale) {
        int iconPadding = Math.max(2, cardWidth / 20);
        int maxIconSize = cardWidth - (iconPadding * 2);
        int minIconSize = Math.max(8, cardWidth / 4);
        int iconSize = Math.max(minIconSize, Math.min(maxIconSize, cardWidth / 4));
        int iconX = x + (cardWidth - iconSize) / 2;
        int iconY = y + iconPadding;
        
        int buttonAreaHeight = cardHeight / 3;
        int maxIconY = y + cardHeight - buttonAreaHeight - iconPadding;
        if (iconY + iconSize > maxIconY) {
            iconSize = Math.max(minIconSize, maxIconY - iconY);
            iconX = x + (cardWidth - iconSize) / 2;
        }
        
        return mouseX >= iconX && mouseX <= iconX + iconSize &&
               mouseY >= iconY && mouseY <= iconY + iconSize;
    }
    
    /**
     * Renders tooltip for an item stack (should be called AFTER all cards are rendered).
     */
    public static void renderItemTooltip(GuiGraphics guiGraphics, ItemStack itemStack, int mouseX, int mouseY) {
        java.util.List<net.minecraft.network.chat.Component> tooltip = itemStack.getTooltipLines(
            net.minecraft.world.item.Item.TooltipContext.EMPTY,
            Minecraft.getInstance().player,
            net.minecraft.world.item.TooltipFlag.Default.NORMAL
        );
        
        guiGraphics.renderTooltip(
            Minecraft.getInstance().font,
            tooltip,
            itemStack.getTooltipImage(),
            mouseX, mouseY
        );
    }
    
    private static CardLayout getCachedCardLayout(int x, int y, int cardWidth, int cardHeight, float guiScale, int numButtons) {
        if (cachedLayout == null || 
            cachedX != x || cachedY != y || cachedWidth != cardWidth || cachedHeight != cardHeight || 
            cachedGuiScale != guiScale || cachedButtonCount != numButtons) {
            
            cachedLayout = new CardLayout(x, y, cardWidth, cardHeight, guiScale, numButtons);
            cachedX = x;
            cachedY = y;
            cachedWidth = cardWidth;
            cachedHeight = cardHeight;
            cachedGuiScale = guiScale;
            cachedButtonCount = numButtons;
        }
        
        return cachedLayout;
    }
}

