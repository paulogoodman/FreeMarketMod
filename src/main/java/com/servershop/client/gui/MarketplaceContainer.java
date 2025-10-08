package com.servershop.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


import com.servershop.common.data.MarketplaceItem;
import com.servershop.common.handlers.AdminModeHandler;
import com.servershop.common.handlers.WalletHandler;
import com.servershop.common.managers.ItemCategoryManager;
import com.servershop.client.data.ClientMarketplaceDataManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A scrollable container for displaying marketplace items with search functionality.
 */
public class MarketplaceContainer implements Renderable {
    
    private final int x, y, width, height;
    private final List<MarketplaceItem> allItems;
    private final ShopGuiScreen parentScreen;
    private EditBox searchBox;
    private int scrollOffset = 0;
    private int maxVisibleItems = 0;
    private int itemHeight = 60;
    private int itemsPerRow = 3;
    private int itemSpacing = 120;
    private ItemCategoryManager.Category selectedCategory = ItemCategoryManager.Category.ALL;
    
    // Buy button state tracking - per item
    private final java.util.Map<String, Long> buyButtonCooldowns = new java.util.HashMap<>();
    private static final long BUY_COOLDOWN_MS = 250; // 0.25 second cooldown
    
    // Sell button state tracking - per item
    private final java.util.Map<String, Long> sellButtonCooldowns = new java.util.HashMap<>();
    private static final long SELL_COOLDOWN_MS = 250; // 0.25 second cooldown
    
    public MarketplaceContainer(int x, int y, int width, int height, List<MarketplaceItem> items, ShopGuiScreen parentScreen) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.allItems = new ArrayList<>(items);
        this.parentScreen = parentScreen;
        calculateMaxVisibleItems();
    }
    
    public void init() {
        // Create search box
        this.searchBox = new EditBox(
            net.minecraft.client.Minecraft.getInstance().font,
            x + 10, y + 25, width - 40, 20, // Added extra margin from top
            Component.translatable("gui.servershop.marketplace.search")
        );
        this.searchBox.setResponder(this::onSearchChanged);
    }
    
    private void calculateMaxVisibleItems() {
        int availableHeight = height - 50; // Account for search box and padding
        this.maxVisibleItems = (availableHeight / itemHeight) * itemsPerRow;
    }
    
    private void onSearchChanged(String searchText) {
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
    
    public void updateItems(List<MarketplaceItem> newItems) {
        allItems.clear();
        allItems.addAll(newItems);
        onSearchChanged(searchBox != null ? searchBox.getValue() : "");
    }
    
    public void scroll(int delta) {
        scrollOffset = Math.max(0, Math.min(getMaxScroll(), scrollOffset + delta));
    }
    
    public void scrollToTop() {
        scrollOffset = 0;
    }
    
    public void scrollToBottom() {
        scrollOffset = getMaxScroll();
    }
    
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw modern container background with gradient effect
        guiGraphics.fill(x, y, x + width, y + height, 0xFF1E1E1E);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF2A2A2A);
        
        // Draw subtle border with rounded corners effect
        guiGraphics.fill(x, y, x + width, y + 2, 0xFF404040);
        guiGraphics.fill(x, y, x + 2, y + height, 0xFF404040);
        guiGraphics.fill(x + width - 2, y, x + width, y + height, 0xFF404040);
        guiGraphics.fill(x, y + height - 2, x + width, y + height, 0xFF404040);
        
        // Draw title with better styling and extra margin
        Component title = Component.translatable("gui.servershop.marketplace.title");
        int titleWidth = net.minecraft.client.Minecraft.getInstance().font.width(title);
        int titleX = x + (width - titleWidth) / 2;
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, title, titleX, y + 15, 0xFFE0E0E0);
        
        // Render search box
        if (searchBox != null) {
            searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        
        // Wallet display is now handled in the main screen
        
        // Draw add button in top right (only if admin mode)
        if (AdminModeHandler.isAdminMode()) {
            renderAddButton(guiGraphics, mouseX, mouseY);
        }
        
        // Draw category sidebar
        renderCategorySidebar(guiGraphics, mouseX, mouseY);
        
        // Get items to render based on selected category and search
        List<MarketplaceItem> itemsToRender = getItemsToRender();
        
        // Draw items with modern styling (adjusted for sidebar)
        int sidebarWidth = 120;
        int startY = y + 60;
        int startX = x + sidebarWidth + 20; // Start after sidebar
        int itemsRendered = 0;
        int maxItemsToRender = maxVisibleItems;
        
        for (int i = scrollOffset * itemsPerRow; i < itemsToRender.size() && itemsRendered < maxItemsToRender; i += itemsPerRow) {
            for (int j = 0; j < itemsPerRow && i + j < itemsToRender.size() && itemsRendered < maxItemsToRender; j++) {
                MarketplaceItem item = itemsToRender.get(i + j);
                int itemX = startX + j * itemSpacing;
                int itemY = startY + (itemsRendered / itemsPerRow) * itemHeight;
                
                renderModernItemCard(guiGraphics, item, itemX, itemY, mouseX, mouseY, i + j);
                itemsRendered++;
            }
        }
        
        // Draw scroll bar
        drawScrollBar(guiGraphics);
        
        // Draw item count
        Component countText = Component.translatable("gui.servershop.marketplace.count", itemsToRender.size(), allItems.size());
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, countText, x + 10, y + height - 15, 0xCCCCCC);
    }
    
    
    private void renderAddButton(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int addButtonX = x + width - 30;
        int addButtonY = y + 20;
        int addButtonSize = 24;
        
        boolean isHovered = mouseX >= addButtonX && mouseX <= addButtonX + addButtonSize &&
                           mouseY >= addButtonY && mouseY <= addButtonY + addButtonSize;
        
        // Modern add button with hover effect
        if (isHovered) {
            // Hover state - brighter background
            guiGraphics.fill(addButtonX, addButtonY, addButtonX + addButtonSize, addButtonY + addButtonSize, 0xFF4A4A4A);
            guiGraphics.fill(addButtonX + 1, addButtonY + 1, addButtonX + addButtonSize - 1, addButtonY + addButtonSize - 1, 0xFF5A5A5A);
        } else {
            // Normal state
            guiGraphics.fill(addButtonX, addButtonY, addButtonX + addButtonSize, addButtonY + addButtonSize, 0xFF3A3A3A);
            guiGraphics.fill(addButtonX + 1, addButtonY + 1, addButtonX + addButtonSize - 1, addButtonY + addButtonSize - 1, 0xFF4A4A4A);
        }
        
        // Draw modern + icon
        int centerX = addButtonX + addButtonSize / 2;
        int centerY = addButtonY + addButtonSize / 2;
        int plusColor = isHovered ? 0xFFFFFFFF : 0xFFCCCCCC;
        
        // Draw + lines
        guiGraphics.fill(centerX - 6, centerY - 1, centerX + 6, centerY + 1, plusColor);
        guiGraphics.fill(centerX - 1, centerY - 6, centerX + 1, centerY + 6, plusColor);
    }
    
    private void renderCategorySidebar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int sidebarWidth = 120;
        int sidebarX = x + 10;
        int sidebarY = y + 50;
        int sidebarHeight = height - 80;
        
        // Draw sidebar background
        guiGraphics.fill(sidebarX, sidebarY, sidebarX + sidebarWidth, sidebarY + sidebarHeight, 0xFF1A1A1A);
        guiGraphics.fill(sidebarX + 1, sidebarY + 1, sidebarX + sidebarWidth - 1, sidebarY + sidebarHeight - 1, 0xFF2D2D2D);
        
        // Draw sidebar title
        Component sidebarTitle = Component.literal("Categories");
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, sidebarTitle, sidebarX + 5, sidebarY + 5, 0xFFE0E0E0);
        
        // Draw categories
        List<ItemCategoryManager.Category> categories = ItemCategoryManager.getAllCategories();
        Map<ItemCategoryManager.Category, Integer> categoryCounts = ItemCategoryManager.getCategoryCounts(allItems);
        
        int categoryY = sidebarY + 20;
        int categoryHeight = 16;
        
        for (int i = 0; i < categories.size(); i++) {
            ItemCategoryManager.Category category = categories.get(i);
            int currentCategoryY = categoryY + i * categoryHeight;
            
            boolean isSelected = category == selectedCategory;
            boolean isHovered = mouseX >= sidebarX && mouseX <= sidebarX + sidebarWidth &&
                               mouseY >= currentCategoryY && mouseY <= currentCategoryY + categoryHeight;
            
            // Update hover state
            
            // Draw category background
            if (isSelected) {
                guiGraphics.fill(sidebarX + 2, currentCategoryY, sidebarX + sidebarWidth - 2, currentCategoryY + categoryHeight, 0xFF4CAF50);
                guiGraphics.fill(sidebarX + 3, currentCategoryY + 1, sidebarX + sidebarWidth - 3, currentCategoryY + categoryHeight - 1, 0xFF66BB6A);
            } else if (isHovered) {
                guiGraphics.fill(sidebarX + 2, currentCategoryY, sidebarX + sidebarWidth - 2, currentCategoryY + categoryHeight, 0xFF3A3A3A);
                guiGraphics.fill(sidebarX + 3, currentCategoryY + 1, sidebarX + sidebarWidth - 3, currentCategoryY + categoryHeight - 1, 0xFF4A4A4A);
            }
            
            // Draw category text
            int textColor = isSelected ? 0xFFFFFFFF : 0xFFE0E0E0;
            String categoryText = category.getDisplayName();
            int count = categoryCounts.getOrDefault(category, 0);
            String displayText = categoryText + " (" + count + ")";
            
            guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, displayText, sidebarX + 5, currentCategoryY + 3, textColor);
        }
    }
    
    private List<MarketplaceItem> getItemsToRender() {
        // First filter by category
        List<MarketplaceItem> categoryFiltered = ItemCategoryManager.filterItemsByCategory(allItems, selectedCategory);
        
        // Then filter by search text
        if (searchBox != null && !searchBox.getValue().isEmpty()) {
            String searchText = searchBox.getValue().toLowerCase();
            return categoryFiltered.stream()
                .filter(item -> item.getItemName().toLowerCase().contains(searchText))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        }
        
        return categoryFiltered;
    }
    
    private void renderModernItemCard(GuiGraphics guiGraphics, MarketplaceItem item, int itemX, int itemY, int mouseX, int mouseY, int itemIndex) {
        // Modern card background with subtle shadow
        guiGraphics.fill(itemX - 3, itemY - 3, itemX + 115, itemY + 55, 0xFF1A1A1A);
        guiGraphics.fill(itemX - 2, itemY - 2, itemX + 114, itemY + 54, 0xFF2D2D2D);
        guiGraphics.fill(itemX - 1, itemY - 1, itemX + 113, itemY + 53, 0xFF3A3A3A);
        
            // Draw modern delete button (only if admin mode)
            if (AdminModeHandler.isAdminMode()) {
                renderModernDeleteButton(guiGraphics, itemX, itemY, mouseX, mouseY, itemIndex);
            }
        
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
    
    private void renderModernDeleteButton(GuiGraphics guiGraphics, int itemX, int itemY, int mouseX, int mouseY, int itemIndex) {
        int deleteButtonX = itemX + 90;
        int deleteButtonY = itemY + 2;
        int deleteButtonSize = 20;
        
        boolean isHovered = mouseX >= deleteButtonX && mouseX <= deleteButtonX + deleteButtonSize &&
                           mouseY >= deleteButtonY && mouseY <= deleteButtonY + deleteButtonSize;
        
        // Draw trash can icon directly (no background square)
        int iconColor = isHovered ? 0xFFFF4444 : 0xFFCC6666; // Red color for delete
        int iconX = deleteButtonX + deleteButtonSize - 8; // Right-align the icon
        int iconY = deleteButtonY + 2;
        
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, "🗑", iconX, iconY, iconColor);
    }
    
    private void renderActionButtons(GuiGraphics guiGraphics, MarketplaceItem item, int itemX, int itemY, int mouseX, int mouseY) {
        boolean canBuy = WalletHandler.hasEnoughMoney(item.getBuyPrice());
        boolean isBuyCooldown = isBuyButtonInCooldown(item);
        
        // Buy button
        int buyButtonX = itemX + 20;
        int buyButtonY = itemY + 38;
        int buyButtonWidth = 35;
        int buyButtonHeight = 12;
        
        boolean buyHovered = mouseX >= buyButtonX && mouseX <= buyButtonX + buyButtonWidth &&
                            mouseY >= buyButtonY && mouseY <= buyButtonY + buyButtonHeight;
        
        // Determine buy button state and color
        int buyColor;
        String buyText;
        
        if (isBuyCooldown) {
            // In cooldown
            buyColor = 0xFF9E9E9E; // Gray for cooldown
            buyText = "🔄";
        } else if (canBuy) {
            // Can buy
            buyColor = buyHovered ? 0xFF66BB6A : 0xFF4CAF50;
            buyText = "Buy";
        } else {
            // Cannot buy (insufficient funds)
            buyColor = 0xFF666666;
            buyText = "Buy";
        }
        
        guiGraphics.fill(buyButtonX, buyButtonY, buyButtonX + buyButtonWidth, buyButtonY + buyButtonHeight, buyColor);
        
        // Draw text with appropriate color
        int textColor = isBuyCooldown ? 0xFFFFFFFF : (canBuy ? 0xFFFFFFFF : 0xFF999999);
        int textX = buyButtonX + (buyButtonWidth - net.minecraft.client.Minecraft.getInstance().font.width(buyText)) / 2;
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, buyText, textX, buyButtonY + 2, textColor);
        
        // Sell button
        int sellButtonX = itemX + 60;
        int sellButtonY = itemY + 38;
        int sellButtonWidth = 35;
        int sellButtonHeight = 12;
        
        boolean sellHovered = mouseX >= sellButtonX && mouseX <= sellButtonX + sellButtonWidth &&
                             mouseY >= sellButtonY && mouseY <= sellButtonY + sellButtonHeight;
        
        boolean isSellCooldown = isSellButtonInCooldown(item);
        boolean canSellItem = canSellItem(item);
        
        // Determine sell button state and color
        int sellColor;
        String sellText;
        
        if (isSellCooldown) {
            // In cooldown
            sellColor = 0xFF9E9E9E; // Gray for cooldown
            sellText = "🔄";
        } else if (canSellItem) {
            // Can sell
            sellColor = sellHovered ? 0xFFFFB74D : 0xFFFF9800;
            sellText = "Sell";
        } else {
            // Cannot sell (don't have item)
            sellColor = 0xFF666666;
            sellText = "Sell";
        }
        
        guiGraphics.fill(sellButtonX, sellButtonY, sellButtonX + sellButtonWidth, sellButtonY + sellButtonHeight, sellColor);
        
        // Draw text with appropriate color
        int sellTextColor = isSellCooldown ? 0xFFFFFFFF : (canSellItem ? 0xFFFFFFFF : 0xFF999999);
        int sellTextX = sellButtonX + (sellButtonWidth - net.minecraft.client.Minecraft.getInstance().font.width(sellText)) / 2;
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, sellText, sellTextX, sellButtonY + 2, sellTextColor);
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
        List<MarketplaceItem> itemsToRender = getItemsToRender();
        int thumbHeight = Math.max(20, (scrollBarHeight * scrollBarHeight) / (itemsToRender.size() * itemHeight / itemsPerRow + scrollBarHeight));
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
        List<MarketplaceItem> itemsToRender = getItemsToRender();
        return Math.max(0, (itemsToRender.size() + itemsPerRow - 1) / itemsPerRow - maxVisibleItems / itemsPerRow);
    }
    
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchBox != null && searchBox.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        
        // Handle add button click (only if admin mode)
        if (AdminModeHandler.isAdminMode()) {
            int addButtonX = x + width - 30;
            int addButtonY = y + 20;
            int addButtonSize = 24;
            
            if (mouseX >= addButtonX && mouseX <= addButtonX + addButtonSize &&
                mouseY >= addButtonY && mouseY <= addButtonY + addButtonSize) {
                // Open add item popup
                if (parentScreen != null) {
                    net.minecraft.client.Minecraft.getInstance().setScreen(new AddItemPopupScreen(parentScreen));
                }
                return true;
            }
        }
        
        // Handle category sidebar clicks
        int sidebarWidth = 120;
        int sidebarX = x + 10;
        int sidebarY = y + 50;
        int sidebarHeight = height - 80;
        
        if (mouseX >= sidebarX && mouseX <= sidebarX + sidebarWidth &&
            mouseY >= sidebarY && mouseY <= sidebarY + sidebarHeight) {
            
            List<ItemCategoryManager.Category> categories = ItemCategoryManager.getAllCategories();
            int categoryY = sidebarY + 20;
            int categoryHeight = 16;
            
            for (int i = 0; i < categories.size(); i++) {
                int currentCategoryY = categoryY + i * categoryHeight;
                
                if (mouseY >= currentCategoryY && mouseY <= currentCategoryY + categoryHeight) {
                    selectedCategory = categories.get(i);
                    scrollOffset = 0; // Reset scroll when changing category
                    return true;
                }
            }
        }
        
        // Handle edit button clicks on items
        int startY = y + 60;
        int startX = x + sidebarWidth + 20; // Start after sidebar
        int itemsRendered = 0;
        int maxItemsToRender = maxVisibleItems;
        List<MarketplaceItem> itemsToRender = getItemsToRender();
        
        for (int i = scrollOffset * itemsPerRow; i < itemsToRender.size() && itemsRendered < maxItemsToRender; i += itemsPerRow) {
            for (int j = 0; j < itemsPerRow && i + j < itemsToRender.size() && itemsRendered < maxItemsToRender; j++) {
                MarketplaceItem item = itemsToRender.get(i + j);
                int itemX = startX + j * itemSpacing;
                int itemY = startY + (itemsRendered / itemsPerRow) * itemHeight;
                
                    // Check delete button click (only if admin mode)
                    if (AdminModeHandler.isAdminMode()) {
                        int deleteButtonX = itemX + 90;
                        int deleteButtonY = itemY + 2;
                        int deleteButtonSize = 20;
                        
                        if (mouseX >= deleteButtonX && mouseX <= deleteButtonX + deleteButtonSize &&
                            mouseY >= deleteButtonY && mouseY <= deleteButtonY + deleteButtonSize) {
                            // Delete item from marketplace
                            ClientMarketplaceDataManager.removeMarketplaceItem(item);
                            
                            // Refresh the marketplace display
                            if (parentScreen != null) {
                                parentScreen.refreshMarketplace();
                            }
                            
                            return true;
                        }
                    }
                
                // Check buy/sell button clicks
                int buyButtonX = itemX + 20;
                int buyButtonY = itemY + 38;
                int buyButtonWidth = 35;
                int buyButtonHeight = 12;
                
                if (mouseX >= buyButtonX && mouseX <= buyButtonX + buyButtonWidth &&
                    mouseY >= buyButtonY && mouseY <= buyButtonY + buyButtonHeight) {
                    // Handle buy button click (only if not in cooldown)
                    if (!isBuyButtonInCooldown(item) && buyItem(item)) {
                        // Success - item was bought
                        return true;
                    }
                    return true; // Still return true to consume the click
                }
                
                int sellButtonX = itemX + 60;
                int sellButtonY = itemY + 38;
                int sellButtonWidth = 35;
                int sellButtonHeight = 12;
                
                if (mouseX >= sellButtonX && mouseX <= sellButtonX + sellButtonWidth &&
                    mouseY >= sellButtonY && mouseY <= sellButtonY + sellButtonHeight) {
                    // Handle sell button click (only if not in cooldown)
                    if (!isSellButtonInCooldown(item) && sellItem(item)) {
                        // Success - item was sold
                        return true;
                    }
                    return true; // Still return true to consume the click
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
    
    /**
     * Handles buying an item from the marketplace.
     * Validates balance, deducts money, and spawns item in inventory or on ground.
     */
    private boolean buyItem(MarketplaceItem item) {
        // Check cooldown for this specific item
        if (isBuyButtonInCooldown(item)) {
            return false; // Still in cooldown
        }
        
        // Check if player has enough money
        if (!WalletHandler.hasEnoughMoney(item.getBuyPrice())) {
            // TODO: Show error message to player
            return false;
        }
        
        // Set cooldown for this specific item
        long currentTime = System.currentTimeMillis();
        buyButtonCooldowns.put(item.getGuid(), currentTime + BUY_COOLDOWN_MS);
        
        // Get the player
        Minecraft minecraft = Minecraft.getInstance();
        Player clientPlayer = minecraft.player;
        if (clientPlayer == null) {
            return false;
        }
        
        // Try to add item to inventory first - use server player for persistence
        ItemStack itemToGive = item.getItemStack().copy();
        Player playerForInventory = clientPlayer;
        
        // In singleplayer, use server player for inventory operations to ensure persistence
        var singleplayerServer = minecraft.getSingleplayerServer();
        if (singleplayerServer != null) {
            var serverPlayer = singleplayerServer.getPlayerList().getPlayer(clientPlayer.getUUID());
            if (serverPlayer != null) {
                playerForInventory = serverPlayer;
            }
        }
        
        boolean addedToInventory = addItemToInventory(playerForInventory, itemToGive);
        
        if (!addedToInventory) {
            // Drop the item at player's feet
            playerForInventory.drop(itemToGive, false);
        }
        
        // Deduct money from wallet - use server player if available
        Player playerForMoney = clientPlayer;
        if (singleplayerServer != null) {
            // In singleplayer, get the server player for money operations
            var serverPlayer = singleplayerServer.getPlayerList().getPlayer(clientPlayer.getUUID());
            if (serverPlayer != null) {
                playerForMoney = serverPlayer;
            }
        }
        
        WalletHandler.removeMoney(playerForMoney, item.getBuyPrice());
        
        // Refresh wallet display
        if (parentScreen != null) {
            parentScreen.refreshMarketplace();
        }
        
        return true;
    }
    
    /**
     * Checks if buy button is in cooldown for an item.
     */
    private boolean isBuyButtonInCooldown(MarketplaceItem item) {
        long currentTime = System.currentTimeMillis();
        Long cooldownEnd = buyButtonCooldowns.get(item.getGuid());
        return cooldownEnd != null && currentTime < cooldownEnd;
    }
    
    /**
     * Handles selling an item to the marketplace.
     * Validates inventory, removes item, and adds money to wallet.
     */
    private boolean sellItem(MarketplaceItem item) {
        // Check cooldown for this specific item
        long currentTime = System.currentTimeMillis();
        Long cooldownEnd = sellButtonCooldowns.get(item.getGuid());
        if (cooldownEnd != null && currentTime < cooldownEnd) {
            return false; // Still in cooldown
        }
        
        // Get the player
        Minecraft minecraft = Minecraft.getInstance();
        Player clientPlayer = minecraft.player;
        if (clientPlayer == null) {
            return false;
        }
        
        // Check if player has the item in inventory - use server player for persistence
        ItemStack itemToSell = item.getItemStack().copy();
        Player playerForInventory = clientPlayer;
        
        // In singleplayer, use server player for inventory operations to ensure persistence
        var singleplayerServer = minecraft.getSingleplayerServer();
        if (singleplayerServer != null) {
            var serverPlayer = singleplayerServer.getPlayerList().getPlayer(clientPlayer.getUUID());
            if (serverPlayer != null) {
                playerForInventory = serverPlayer;
            }
        }
        
        if (!hasItemInInventory(playerForInventory, itemToSell)) {
            // TODO: Show error message to player
            return false;
        }
        
        // Set cooldown for this specific item
        sellButtonCooldowns.put(item.getGuid(), currentTime + SELL_COOLDOWN_MS);
        
        // Remove item from inventory
        removeItemFromInventory(playerForInventory, itemToSell);
        
        // Add money to wallet - use server player if available
        Player playerForMoney = clientPlayer;
        if (singleplayerServer != null) {
            // In singleplayer, get the server player for money operations
            var serverPlayer = singleplayerServer.getPlayerList().getPlayer(clientPlayer.getUUID());
            if (serverPlayer != null) {
                playerForMoney = serverPlayer;
            }
        }
        
        WalletHandler.addMoney(playerForMoney, item.getSellPrice());
        
        // Refresh wallet display
        if (parentScreen != null) {
            parentScreen.refreshMarketplace();
        }
        
        return true;
    }
    
    /**
     * Checks if the player has the specified item in their inventory.
     */
    private boolean hasItemInInventory(Player player, ItemStack itemToCheck) {
        var inventory = player.getInventory();
        
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (!slotItem.isEmpty() && ItemStack.isSameItemSameComponents(slotItem, itemToCheck)) {
                return slotItem.getCount() >= itemToCheck.getCount();
            }
        }
        
        return false;
    }
    
    /**
     * Removes the specified item from the player's inventory.
     * Prioritizes removing from stacks with the fewest items.
     */
    private void removeItemFromInventory(Player player, ItemStack itemToRemove) {
        var inventory = player.getInventory();
        int remainingToRemove = itemToRemove.getCount();
        
        // First pass: find all matching stacks and sort by count (fewest first)
        java.util.List<java.util.Map.Entry<Integer, ItemStack>> matchingStacks = new java.util.ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (!slotItem.isEmpty() && ItemStack.isSameItemSameComponents(slotItem, itemToRemove)) {
                matchingStacks.add(new java.util.AbstractMap.SimpleEntry<>(i, slotItem));
            }
        }
        
        // Sort by count (ascending - fewest items first)
        matchingStacks.sort((a, b) -> Integer.compare(a.getValue().getCount(), b.getValue().getCount()));
        
        // Remove items starting from stacks with fewest items
        for (var entry : matchingStacks) {
            if (remainingToRemove <= 0) break;
            
            int slotIndex = entry.getKey();
            ItemStack slotItem = entry.getValue();
            int removeFromSlot = Math.min(remainingToRemove, slotItem.getCount());
            slotItem.shrink(removeFromSlot);
            remainingToRemove -= removeFromSlot;
            
            // Update the slot
            inventory.setItem(slotIndex, slotItem.isEmpty() ? ItemStack.EMPTY : slotItem);
        }
    }
    
    /**
     * Adds the specified item to the player's inventory.
     * Prioritizes adding to stacks with the fewest items.
     * @param player the player to add items to
     * @param itemToAdd the item to add
     * @return true if all items were added, false if some had to be dropped
     */
    private boolean addItemToInventory(Player player, ItemStack itemToAdd) {
        var inventory = player.getInventory();
        int remainingToAdd = itemToAdd.getCount();
        
        // First pass: find all existing stacks of the same item and sort by count (fewest first)
        java.util.List<java.util.Map.Entry<Integer, ItemStack>> existingStacks = new java.util.ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (!slotItem.isEmpty() && ItemStack.isSameItemSameComponents(slotItem, itemToAdd)) {
                existingStacks.add(new java.util.AbstractMap.SimpleEntry<>(i, slotItem));
            }
        }
        
        // Sort by count (ascending - fewest items first)
        existingStacks.sort((a, b) -> Integer.compare(a.getValue().getCount(), b.getValue().getCount()));
        
        // Add items to existing stacks starting with those that have fewest items
        for (var entry : existingStacks) {
            if (remainingToAdd <= 0) break;
            
            int slotIndex = entry.getKey();
            ItemStack slotItem = entry.getValue();
            int maxStackSize = slotItem.getMaxStackSize();
            int currentCount = slotItem.getCount();
            int canAdd = maxStackSize - currentCount;
            
            if (canAdd > 0) {
                int addToSlot = Math.min(remainingToAdd, canAdd);
                slotItem.grow(addToSlot);
                remainingToAdd -= addToSlot;
                inventory.setItem(slotIndex, slotItem);
            }
        }
        
        // If there are still items to add, try to find empty slots
        if (remainingToAdd > 0) {
            for (int i = 0; i < inventory.getContainerSize() && remainingToAdd > 0; i++) {
                ItemStack slotItem = inventory.getItem(i);
                if (slotItem.isEmpty()) {
                    int addToSlot = Math.min(remainingToAdd, itemToAdd.getMaxStackSize());
                    ItemStack newStack = itemToAdd.copy();
                    newStack.setCount(addToSlot);
                    inventory.setItem(i, newStack);
                    remainingToAdd -= addToSlot;
                }
            }
        }
        
        // Return true if all items were added, false if some remain
        return remainingToAdd == 0;
    }
    
    /**
     * Checks if sell button is in cooldown for an item.
     */
    private boolean isSellButtonInCooldown(MarketplaceItem item) {
        long currentTime = System.currentTimeMillis();
        Long cooldownEnd = sellButtonCooldowns.get(item.getGuid());
        return cooldownEnd != null && currentTime < cooldownEnd;
    }
    
    /**
     * Checks if the player can sell the specified item (has it in inventory).
     */
    private boolean canSellItem(MarketplaceItem item) {
        Minecraft minecraft = Minecraft.getInstance();
        Player clientPlayer = minecraft.player;
        if (clientPlayer == null) {
            return false;
        }
        
        // Use server player for inventory check to ensure consistency
        Player playerForCheck = clientPlayer;
        var singleplayerServer = minecraft.getSingleplayerServer();
        if (singleplayerServer != null) {
            var serverPlayer = singleplayerServer.getPlayerList().getPlayer(clientPlayer.getUUID());
            if (serverPlayer != null) {
                playerForCheck = serverPlayer;
            }
        }
        
        return hasItemInInventory(playerForCheck, item.getItemStack());
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
