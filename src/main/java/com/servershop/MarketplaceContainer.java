package com.servershop;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A scrollable container for displaying marketplace items with search functionality.
 */
public class MarketplaceContainer implements Renderable {
    
    private final int x, y, width, height;
    private final List<MarketplaceItem> allItems;
    private List<MarketplaceItem> filteredItems;
    private EditBox searchBox;
    private int scrollOffset = 0;
    private int maxVisibleItems = 0;
    private int itemHeight = 60;
    private int itemsPerRow = 3;
    private int itemSpacing = 120;
    private boolean isClosing = false;
    private int hoveredEditButton = -1;
    private int hoveredCloseButton = -1;
    
    public MarketplaceContainer(int x, int y, int width, int height, List<MarketplaceItem> items) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.allItems = new ArrayList<>(items);
        this.filteredItems = new ArrayList<>(allItems);
        calculateMaxVisibleItems();
    }
    
    public void init() {
        // Create search box
        this.searchBox = new EditBox(
            net.minecraft.client.Minecraft.getInstance().font,
            x + 10, y + 15, width - 40, 20, // Moved down and made room for close button
            Component.translatable("gui.servershop.marketplace.search")
        );
        this.searchBox.setResponder(this::onSearchChanged);
    }
    
    private void calculateMaxVisibleItems() {
        int availableHeight = height - 50; // Account for search box and padding
        this.maxVisibleItems = (availableHeight / itemHeight) * itemsPerRow;
    }
    
    private void onSearchChanged(String searchText) {
        if (searchText.isEmpty()) {
            filteredItems = new ArrayList<>(allItems);
        } else {
            filteredItems = allItems.stream()
                .filter(item -> item.getItemName().toLowerCase().contains(searchText.toLowerCase()))
                .collect(Collectors.toList());
        }
        scrollOffset = 0; // Reset scroll when searching
    }
    
    public void addItem(MarketplaceItem item) {
        allItems.add(item);
        onSearchChanged(searchBox != null ? searchBox.getValue() : "");
    }
    
    public void removeItem(MarketplaceItem item) {
        allItems.remove(item);
        onSearchChanged(searchBox != null ? searchBox.getValue() : "");
    }
    
    public void scroll(int delta) {
        int maxScroll = Math.max(0, (filteredItems.size() + itemsPerRow - 1) / itemsPerRow - maxVisibleItems / itemsPerRow);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + delta));
    }
    
    public void scrollToTop() {
        scrollOffset = 0;
    }
    
    public void scrollToBottom() {
        int maxScroll = Math.max(0, (filteredItems.size() + itemsPerRow - 1) / itemsPerRow - maxVisibleItems / itemsPerRow);
        scrollOffset = maxScroll;
    }
    
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw modern container background with gradient effect
        guiGraphics.fill(x, y, x + width, y + height, 0xFF1E1E1E);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF2A2A2A);
        
        // Draw subtle border with rounded corners effect
        guiGraphics.fill(x, y, x + width, y + 2, 0xFF404040);
        guiGraphics.fill(x, y, x + 2, y + height, 0xFF404040);
        guiGraphics.fill(x + width - 2, y, x + width, y + height, 0xFF404040);
        guiGraphics.fill(x, y + height - 2, x + width, y + height, 0xFF404040);
        
        // Draw title with better styling
        Component title = Component.translatable("gui.servershop.marketplace.title");
        int titleWidth = net.minecraft.client.Minecraft.getInstance().font.width(title);
        int titleX = x + (width - titleWidth) / 2;
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, title, titleX, y + 8, 0xFFE0E0E0);
        
        // Render search box
        if (searchBox != null) {
            searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        
        // Draw modern close button
        renderCloseButton(guiGraphics, mouseX, mouseY);
        
        // Draw items with modern styling
        int startY = y + 50;
        int itemsRendered = 0;
        int maxItemsToRender = maxVisibleItems;
        
        for (int i = scrollOffset * itemsPerRow; i < filteredItems.size() && itemsRendered < maxItemsToRender; i += itemsPerRow) {
            for (int j = 0; j < itemsPerRow && i + j < filteredItems.size() && itemsRendered < maxItemsToRender; j++) {
                MarketplaceItem item = filteredItems.get(i + j);
                int itemX = x + 10 + j * itemSpacing;
                int itemY = startY + (itemsRendered / itemsPerRow) * itemHeight;
                
                renderModernItemCard(guiGraphics, item, itemX, itemY, mouseX, mouseY, i + j);
                itemsRendered++;
            }
        }
        
        // Draw scroll bar
        drawScrollBar(guiGraphics);
        
        // Draw item count
        Component countText = Component.translatable("gui.servershop.marketplace.count", filteredItems.size(), allItems.size());
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, countText, x + 10, y + height - 15, 0xCCCCCC);
    }
    
    private void renderCloseButton(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int closeButtonX = x + width - 30;
        int closeButtonY = y + 10;
        int closeButtonSize = 24;
        
        boolean isHovered = mouseX >= closeButtonX && mouseX <= closeButtonX + closeButtonSize &&
                           mouseY >= closeButtonY && mouseY <= closeButtonY + closeButtonSize;
        
        // Modern close button with hover effect
        if (isHovered) {
            // Hover state - brighter background
            guiGraphics.fill(closeButtonX, closeButtonY, closeButtonX + closeButtonSize, closeButtonY + closeButtonSize, 0xFF4A4A4A);
            guiGraphics.fill(closeButtonX + 1, closeButtonY + 1, closeButtonX + closeButtonSize - 1, closeButtonY + closeButtonSize - 1, 0xFF5A5A5A);
        } else {
            // Normal state
            guiGraphics.fill(closeButtonX, closeButtonY, closeButtonX + closeButtonSize, closeButtonY + closeButtonSize, 0xFF3A3A3A);
            guiGraphics.fill(closeButtonX + 1, closeButtonY + 1, closeButtonX + closeButtonSize - 1, closeButtonY + closeButtonSize - 1, 0xFF4A4A4A);
        }
        
        // Draw modern X icon
        int centerX = closeButtonX + closeButtonSize / 2;
        int centerY = closeButtonY + closeButtonSize / 2;
        int xColor = isHovered ? 0xFFFFFFFF : 0xFFCCCCCC;
        
        // Draw X lines
        guiGraphics.fill(centerX - 6, centerY - 1, centerX + 6, centerY + 1, xColor);
        guiGraphics.fill(centerX - 1, centerY - 6, centerX + 1, centerY + 6, xColor);
    }
    
    private void renderModernItemCard(GuiGraphics guiGraphics, MarketplaceItem item, int itemX, int itemY, int mouseX, int mouseY, int itemIndex) {
        // Modern card background with subtle shadow
        guiGraphics.fill(itemX - 3, itemY - 3, itemX + 115, itemY + 55, 0xFF1A1A1A);
        guiGraphics.fill(itemX - 2, itemY - 2, itemX + 114, itemY + 54, 0xFF2D2D2D);
        guiGraphics.fill(itemX - 1, itemY - 1, itemX + 113, itemY + 53, 0xFF3A3A3A);
        
        // Draw modern edit button
        renderModernEditButton(guiGraphics, itemX, itemY, mouseX, mouseY, itemIndex);
        
        // Draw item icon with better positioning
        guiGraphics.renderItem(item.getItemStack(), itemX + 2, itemY + 2);
        guiGraphics.renderItemDecorations(net.minecraft.client.Minecraft.getInstance().font, item.getItemStack(), itemX + 2, itemY + 2);
        
        // Draw item name with better styling
        String itemName = item.getItemName();
        if (itemName.length() > 12) {
            itemName = itemName.substring(0, 12) + "...";
        }
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, itemName, itemX + 20, itemY + 5, 0xFFE0E0E0);
        
        // Draw prices with better formatting
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, "Buy: $" + item.getBuyPrice(), itemX + 20, itemY + 18, 0xFF4CAF50);
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, "Sell: $" + item.getSellPrice(), itemX + 20, itemY + 28, 0xFFFF9800);
        
        // Draw modern action buttons
        renderActionButtons(guiGraphics, item, itemX, itemY, mouseX, mouseY);
    }
    
    private void renderModernEditButton(GuiGraphics guiGraphics, int itemX, int itemY, int mouseX, int mouseY, int itemIndex) {
        int editButtonX = itemX + 90;
        int editButtonY = itemY + 2;
        int editButtonSize = 20;
        
        boolean isHovered = mouseX >= editButtonX && mouseX <= editButtonX + editButtonSize &&
                           mouseY >= editButtonY && mouseY <= editButtonY + editButtonSize;
        
        if (isHovered) {
            hoveredEditButton = itemIndex;
            // Hover state
            guiGraphics.fill(editButtonX, editButtonY, editButtonX + editButtonSize, editButtonY + editButtonSize, 0xFF4A4A4A);
            guiGraphics.fill(editButtonX + 1, editButtonY + 1, editButtonX + editButtonSize - 1, editButtonY + editButtonSize - 1, 0xFF5A5A5A);
        } else {
            // Normal state
            guiGraphics.fill(editButtonX, editButtonY, editButtonX + editButtonSize, editButtonY + editButtonSize, 0xFF3A3A3A);
            guiGraphics.fill(editButtonX + 1, editButtonY + 1, editButtonX + editButtonSize - 1, editButtonY + editButtonSize - 1, 0xFF4A4A4A);
        }
        
        // Draw modern edit icon (pencil)
        int iconColor = isHovered ? 0xFFFFFFFF : 0xFFCCCCCC;
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, "✏", editButtonX + 5, editButtonY + 3, iconColor);
    }
    
    private void renderActionButtons(GuiGraphics guiGraphics, MarketplaceItem item, int itemX, int itemY, int mouseX, int mouseY) {
        boolean canBuy = WalletHandler.hasEnoughMoney(item.getBuyPrice());
        boolean canSell = true;
        
        // Buy button
        int buyButtonX = itemX + 20;
        int buyButtonY = itemY + 38;
        int buyButtonWidth = 35;
        int buyButtonHeight = 12;
        
        boolean buyHovered = mouseX >= buyButtonX && mouseX <= buyButtonX + buyButtonWidth &&
                            mouseY >= buyButtonY && mouseY <= buyButtonY + buyButtonHeight;
        
        if (canBuy) {
            int buyColor = buyHovered ? 0xFF66BB6A : 0xFF4CAF50;
            guiGraphics.fill(buyButtonX, buyButtonY, buyButtonX + buyButtonWidth, buyButtonY + buyButtonHeight, buyColor);
            guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, "Buy", buyButtonX + 10, buyButtonY + 2, 0xFFFFFFFF);
        } else {
            guiGraphics.fill(buyButtonX, buyButtonY, buyButtonX + buyButtonWidth, buyButtonY + buyButtonHeight, 0xFF666666);
            guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, "Buy", buyButtonX + 10, buyButtonY + 2, 0xFF999999);
        }
        
        // Sell button
        int sellButtonX = itemX + 60;
        int sellButtonY = itemY + 38;
        int sellButtonWidth = 35;
        int sellButtonHeight = 12;
        
        boolean sellHovered = mouseX >= sellButtonX && mouseX <= sellButtonX + sellButtonWidth &&
                             mouseY >= sellButtonY && mouseY <= sellButtonY + sellButtonHeight;
        
        if (canSell) {
            int sellColor = sellHovered ? 0xFFFFB74D : 0xFFFF9800;
            guiGraphics.fill(sellButtonX, sellButtonY, sellButtonX + sellButtonWidth, sellButtonY + sellButtonHeight, sellColor);
            guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, "Sell", sellButtonX + 10, sellButtonY + 2, 0xFFFFFFFF);
        } else {
            guiGraphics.fill(sellButtonX, sellButtonY, sellButtonX + sellButtonWidth, sellButtonY + sellButtonHeight, 0xFF666666);
            guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, "Sell", sellButtonX + 10, sellButtonY + 2, 0xFF999999);
        }
    }
    
    private void drawScrollBar(GuiGraphics guiGraphics) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) return; // No scrolling needed
        
        int scrollBarWidth = 8;
        int scrollBarX = x + width - scrollBarWidth - 2;
        int scrollBarY = y + 35;
        int scrollBarHeight = height - 50;
        
        // Draw scroll bar background
        guiGraphics.fill(scrollBarX, scrollBarY, scrollBarX + scrollBarWidth, scrollBarY + scrollBarHeight, 0x80000000);
        
        // Calculate thumb position and size
        int thumbHeight = Math.max(20, (scrollBarHeight * scrollBarHeight) / (maxScroll + scrollBarHeight));
        int thumbY = scrollBarY + (scrollBarHeight - thumbHeight) * scrollOffset / maxScroll;
        
        // Draw scroll thumb
        guiGraphics.fill(scrollBarX + 1, thumbY, scrollBarX + scrollBarWidth - 1, thumbY + thumbHeight, 0xFF808080);
    }
    
    public void scrollToPosition(int position) {
        int maxScroll = getMaxScroll();
        scrollOffset = Math.max(0, Math.min(maxScroll, position));
    }
    
    public int getScrollPosition() {
        return scrollOffset;
    }
    
    public int getMaxScrollPosition() {
        return getMaxScroll();
    }
    
    private int getMaxScroll() {
        return Math.max(0, (filteredItems.size() + itemsPerRow - 1) / itemsPerRow - maxVisibleItems / itemsPerRow);
    }
    
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchBox != null && searchBox.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        
        // Handle close button click
        int closeButtonX = x + width - 30;
        int closeButtonY = y + 10;
        int closeButtonSize = 24;
        
        if (mouseX >= closeButtonX && mouseX <= closeButtonX + closeButtonSize &&
            mouseY >= closeButtonY && mouseY <= closeButtonY + closeButtonSize) {
            isClosing = true;
            return true;
        }
        
        // Handle edit button clicks on items
        int startY = y + 50;
        int itemsRendered = 0;
        int maxItemsToRender = maxVisibleItems;
        
        for (int i = scrollOffset * itemsPerRow; i < filteredItems.size() && itemsRendered < maxItemsToRender; i += itemsPerRow) {
            for (int j = 0; j < itemsPerRow && i + j < filteredItems.size() && itemsRendered < maxItemsToRender; j++) {
                MarketplaceItem item = filteredItems.get(i + j);
                int itemX = x + 10 + j * itemSpacing;
                int itemY = startY + (itemsRendered / itemsPerRow) * itemHeight;
                
                // Check edit button click
                int editButtonX = itemX + 90;
                int editButtonY = itemY + 2;
                int editButtonSize = 20;
                
                if (mouseX >= editButtonX && mouseX <= editButtonX + editButtonSize &&
                    mouseY >= editButtonY && mouseY <= editButtonY + editButtonSize) {
                    // TODO: Open edit dialog for this item
                    System.out.println("Edit button clicked for: " + item.getItemName());
                    return true;
                }
                
                // Check buy/sell button clicks
                int buyButtonX = itemX + 20;
                int buyButtonY = itemY + 38;
                int buyButtonWidth = 35;
                int buyButtonHeight = 12;
                
                if (mouseX >= buyButtonX && mouseX <= buyButtonX + buyButtonWidth &&
                    mouseY >= buyButtonY && mouseY <= buyButtonY + buyButtonHeight) {
                    System.out.println("Buy button clicked for: " + item.getItemName());
                    return true;
                }
                
                int sellButtonX = itemX + 60;
                int sellButtonY = itemY + 38;
                int sellButtonWidth = 35;
                int sellButtonHeight = 12;
                
                if (mouseX >= sellButtonX && mouseX <= sellButtonX + sellButtonWidth &&
                    mouseY >= sellButtonY && mouseY <= sellButtonY + sellButtonHeight) {
                    System.out.println("Sell button clicked for: " + item.getItemName());
                    return true;
                }
                
                itemsRendered++;
            }
        }
        
        // Handle scroll bar clicks
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            int scrollBarWidth = 8;
            int scrollBarX = x + width - scrollBarWidth - 2;
            int scrollBarY = y + 35;
            int scrollBarHeight = height - 50;
            
            if (mouseX >= scrollBarX && mouseX <= scrollBarX + scrollBarWidth &&
                mouseY >= scrollBarY && mouseY <= scrollBarY + scrollBarHeight) {
                
                // Calculate new scroll position based on click
                double relativeY = (mouseY - scrollBarY) / scrollBarHeight;
                int newScroll = (int) (relativeY * maxScroll);
                scrollToPosition(newScroll);
                return true;
            }
        }
        
        // Check if click is within container bounds
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            return true;
        }
        
        return false;
    }
    
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return false;
    }
    
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox != null && searchBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return false;
    }
    
    public void setFocused(boolean focused) {
        if (searchBox != null) {
            searchBox.setFocused(focused);
        }
    }
    
    public boolean isFocused() {
        return searchBox != null && searchBox.isFocused();
    }
    
    public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        // Narration support
    }
    
    public boolean isClosing() {
        return isClosing;
    }
    
    public void resetClosing() {
        isClosing = false;
    }
}
