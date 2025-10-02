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
            x + 10, y + 10, width - 20, 20,
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
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw container background
        guiGraphics.fill(x, y, x + width, y + height, 0x80000000);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x80404040);
        
        // Draw border
        guiGraphics.fill(x, y, x + width, y + 1, 0xFF808080);
        guiGraphics.fill(x, y, x + 1, y + height, 0xFF808080);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, 0xFF808080);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, 0xFF808080);
        
        // Draw title
        Component title = Component.translatable("gui.servershop.marketplace.title");
        int titleWidth = net.minecraft.client.Minecraft.getInstance().font.width(title);
        int titleX = x + (width - titleWidth) / 2;
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, title, titleX, y + 2, 0xFFFFFF);
        
        // Render search box
        if (searchBox != null) {
            searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        
        // Draw items
        int startY = y + 45; // Increased from 40 to give more space
        int itemsRendered = 0;
        int maxItemsToRender = maxVisibleItems;
        
        for (int i = scrollOffset * itemsPerRow; i < filteredItems.size() && itemsRendered < maxItemsToRender; i += itemsPerRow) {
            for (int j = 0; j < itemsPerRow && i + j < filteredItems.size() && itemsRendered < maxItemsToRender; j++) {
                MarketplaceItem item = filteredItems.get(i + j);
                int itemX = x + 10 + j * itemSpacing;
                int itemY = startY + (itemsRendered / itemsPerRow) * itemHeight;
                
                renderItemCard(guiGraphics, item, itemX, itemY, mouseX, mouseY);
                itemsRendered++;
            }
        }
        
        // Draw scroll bar
        drawScrollBar(guiGraphics);
        
        // Draw item count
        Component countText = Component.translatable("gui.servershop.marketplace.count", filteredItems.size(), allItems.size());
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, countText, x + 10, y + height - 15, 0xCCCCCC);
    }
    
    private void renderItemCard(GuiGraphics guiGraphics, MarketplaceItem item, int itemX, int itemY, int mouseX, int mouseY) {
        // Draw item background
        guiGraphics.fill(itemX - 2, itemY - 2, itemX + 110, itemY + 50, 0x80000000);
        guiGraphics.fill(itemX - 1, itemY - 1, itemX + 109, itemY + 49, 0x80404040);
        
        // Draw item icon
        guiGraphics.renderItem(item.getItemStack(), itemX, itemY);
        guiGraphics.renderItemDecorations(net.minecraft.client.Minecraft.getInstance().font, item.getItemStack(), itemX, itemY);
        
        // Draw item name
        String itemName = item.getItemName();
        if (itemName.length() > 10) {
            itemName = itemName.substring(0, 10) + "...";
        }
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, itemName, itemX, itemY + 18, 0xFFFFFF);
        
        // Draw buy price
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, "Buy: " + item.getBuyPrice(), itemX, itemY + 30, 0x00FF00);
        
        // Draw sell price
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, "Sell: " + item.getSellPrice(), itemX, itemY + 40, 0xFF6600);
        
        // Draw buy/sell buttons (simplified as text for now)
        boolean canBuy = WalletHandler.hasEnoughMoney(item.getBuyPrice());
        boolean canSell = true; // Assume player has items to sell
        
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, "[Buy]", itemX + 60, itemY + 30, canBuy ? 0x00FF00 : 0x666666);
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, "[Sell]", itemX + 60, itemY + 40, canSell ? 0xFF6600 : 0x666666);
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
            // Handle item clicks here if needed
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
}
