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
    private int itemHeight; // Will be calculated responsively
    private int itemsPerRow = 3;
    private int itemSpacing; // Will be calculated responsively
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
        
        // Calculate responsive dimensions that fit within the container
        calculateResponsiveDimensions();
        
        calculateMaxVisibleItems();
    }
    
    private void calculateResponsiveDimensions() {
        // Calculate available space for items
        int sidebarWidth = GuiScalingHelper.responsiveWidth(120, 100, 150);
        int sidebarMargin = GuiScalingHelper.responsiveWidth(20, 15, 30);
        int rightMargin = GuiScalingHelper.responsiveWidth(20, 15, 30);
        
        // Available width for items = container width - sidebar - margins
        int availableWidth = width - sidebarWidth - sidebarMargin - rightMargin;
        
        // Calculate maximum item width that fits in the available space
        int maxItemWidth = (availableWidth - (itemsPerRow - 1) * GuiScalingHelper.responsiveWidth(10, 8, 15)) / itemsPerRow;
        
        // Ensure minimum and maximum constraints
        int minItemWidth = GuiScalingHelper.responsiveWidth(110, 90, 130);
        int maxItemWidthConstraint = GuiScalingHelper.responsiveWidth(160, 140, 180);
        
        // Use the smaller of calculated max width or constraint
        int finalItemWidth = Math.min(maxItemWidth, maxItemWidthConstraint);
        finalItemWidth = Math.max(finalItemWidth, minItemWidth);
        
        // Calculate item spacing based on available space
        int totalItemWidth = finalItemWidth * itemsPerRow;
        int remainingSpace = availableWidth - totalItemWidth;
        this.itemSpacing = finalItemWidth + (remainingSpace / Math.max(1, itemsPerRow - 1));
        
        // Set item height responsively
        this.itemHeight = GuiScalingHelper.responsiveHeight(90, 70, 120);
        
        // Store the calculated item width for use in rendering
        this.calculatedItemWidth = finalItemWidth;
    }
    
    // Add a field to store the calculated item width
    private int calculatedItemWidth = 130;
    
    public void init() {
        // Create search box with responsive positioning
        int searchMargin = GuiScalingHelper.responsiveWidth(10, 8, 15);
        int searchTopMargin = GuiScalingHelper.responsiveHeight(25, 20, 35);
        int searchHeight = GuiScalingHelper.responsiveHeight(20, 16, 26);
        
        this.searchBox = new EditBox(
            net.minecraft.client.Minecraft.getInstance().font,
            x + searchMargin, y + searchTopMargin, width - GuiScalingHelper.responsiveWidth(40, 30, 50), searchHeight,
            Component.translatable("gui.servershop.marketplace.search")
        );
        this.searchBox.setResponder(this::onSearchChanged);
    }
    
    private void calculateMaxVisibleItems() {
        int availableHeight = height - GuiScalingHelper.responsiveHeight(50, 40, 70); // Account for search box and padding
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
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, title, titleX, y + GuiScalingHelper.responsiveHeight(15, 12, 20), 0xFFE0E0E0);
        
        // Render search box
        if (searchBox != null) {
            searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        
        // Wallet display is now handled in the main screen
        
        // Add button is now handled as a special marketplace item
        
        // Draw category sidebar
        renderCategorySidebar(guiGraphics, mouseX, mouseY);
        
        // Get items to render based on selected category and search
        List<MarketplaceItem> itemsToRender = getItemsToRender();
        
        // Draw items with modern styling (adjusted for sidebar)
        int sidebarWidth = GuiScalingHelper.responsiveWidth(120, 100, 150);
        int startY = y + GuiScalingHelper.responsiveHeight(60, 50, 80);
        int startX = x + sidebarWidth + GuiScalingHelper.responsiveWidth(20, 15, 30); // Start after sidebar
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
        
        // Draw item count (exclude add item from count)
        int actualItemCount = itemsToRender.size();
        if (AdminModeHandler.isAdminMode() && (searchBox == null || searchBox.getValue().isEmpty())) {
            actualItemCount--; // Subtract 1 for the add item
        }
        Component countText = Component.translatable("gui.servershop.marketplace.count", actualItemCount, allItems.size());
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, countText, x + GuiScalingHelper.responsiveWidth(10, 8, 15), y + height - GuiScalingHelper.responsiveHeight(15, 12, 20), 0xCCCCCC);
    }
    
    
    
    private void renderCategorySidebar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int sidebarWidth = GuiScalingHelper.responsiveWidth(120, 100, 150);
        int sidebarX = x + GuiScalingHelper.responsiveWidth(10, 8, 15);
        int sidebarY = y + GuiScalingHelper.responsiveHeight(50, 40, 70);
        int sidebarHeight = height - GuiScalingHelper.responsiveHeight(80, 60, 100);
        
        // Draw sidebar background
        guiGraphics.fill(sidebarX, sidebarY, sidebarX + sidebarWidth, sidebarY + sidebarHeight, 0xFF1A1A1A);
        guiGraphics.fill(sidebarX + 1, sidebarY + 1, sidebarX + sidebarWidth - 1, sidebarY + sidebarHeight - 1, 0xFF2D2D2D);
        
        // Draw sidebar title
        Component sidebarTitle = Component.literal("Categories");
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, sidebarTitle, sidebarX + GuiScalingHelper.responsiveWidth(5, 4, 8), sidebarY + GuiScalingHelper.responsiveHeight(5, 4, 8), 0xFFE0E0E0);
        
        // Draw categories
        List<ItemCategoryManager.Category> categories = ItemCategoryManager.getAllCategories();
        Map<ItemCategoryManager.Category, Integer> categoryCounts = ItemCategoryManager.getCategoryCounts(allItems);
        
        int categoryY = sidebarY + GuiScalingHelper.responsiveHeight(20, 16, 28);
        int categoryHeight = GuiScalingHelper.responsiveHeight(16, 12, 22);
        
        for (int i = 0; i < categories.size(); i++) {
            ItemCategoryManager.Category category = categories.get(i);
            int currentCategoryY = categoryY + i * categoryHeight;
            
            boolean isSelected = category == selectedCategory;
            boolean isHovered = mouseX >= sidebarX && mouseX <= sidebarX + sidebarWidth &&
                               mouseY >= currentCategoryY && mouseY <= currentCategoryY + categoryHeight;
            
            // Update hover state
            
            // Draw category background
            if (isSelected) {
                guiGraphics.fill(sidebarX + GuiScalingHelper.responsiveWidth(2, 1, 3), currentCategoryY, sidebarX + sidebarWidth - GuiScalingHelper.responsiveWidth(2, 1, 3), currentCategoryY + categoryHeight, 0xFF4CAF50);
                guiGraphics.fill(sidebarX + GuiScalingHelper.responsiveWidth(3, 2, 4), currentCategoryY + 1, sidebarX + sidebarWidth - GuiScalingHelper.responsiveWidth(3, 2, 4), currentCategoryY + categoryHeight - 1, 0xFF66BB6A);
            } else if (isHovered) {
                guiGraphics.fill(sidebarX + GuiScalingHelper.responsiveWidth(2, 1, 3), currentCategoryY, sidebarX + sidebarWidth - GuiScalingHelper.responsiveWidth(2, 1, 3), currentCategoryY + categoryHeight, 0xFF3A3A3A);
                guiGraphics.fill(sidebarX + GuiScalingHelper.responsiveWidth(3, 2, 4), currentCategoryY + 1, sidebarX + sidebarWidth - GuiScalingHelper.responsiveWidth(3, 2, 4), currentCategoryY + categoryHeight - 1, 0xFF4A4A4A);
            }
            
            // Draw category text
            int textColor = isSelected ? 0xFFFFFFFF : 0xFFE0E0E0;
            String categoryText = category.getDisplayName();
            int count = categoryCounts.getOrDefault(category, 0);
            String displayText = categoryText + " (" + count + ")";
            
            guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, displayText, sidebarX + GuiScalingHelper.responsiveWidth(5, 4, 8), currentCategoryY + GuiScalingHelper.responsiveHeight(3, 2, 5), textColor);
        }
    }
    
    /**
     * Creates a special marketplace item entry for adding new items.
     * This item has a special GUID that identifies it as the add button.
     */
    private MarketplaceItem createAddItemEntry() {
        // Create a dummy item stack (we won't actually use it for rendering)
        net.minecraft.world.item.ItemStack dummyStack = net.minecraft.world.item.Items.AIR.getDefaultInstance();
        return new MarketplaceItem(dummyStack, 0, 0, 0, "admin", "ADD_ITEM_SPECIAL");
    }
    
    /**
     * Checks if a marketplace item is the special "add item" entry.
     */
    private boolean isAddItemEntry(MarketplaceItem item) {
        return "ADD_ITEM_SPECIAL".equals(item.getGuid());
    }
    
    private List<MarketplaceItem> getItemsToRender() {
        // First filter by category
        List<MarketplaceItem> categoryFiltered = ItemCategoryManager.filterItemsByCategory(allItems, selectedCategory);
        
        // Then filter by search text
        if (searchBox != null && !searchBox.getValue().isEmpty()) {
            String searchText = searchBox.getValue().toLowerCase();
            categoryFiltered = categoryFiltered.stream()
                .filter(item -> item.getItemName().toLowerCase().contains(searchText))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        }
        
        // Add special "add item" entry if in admin mode and not searching
        if (AdminModeHandler.isAdminMode() && (searchBox == null || searchBox.getValue().isEmpty())) {
            // Create a special marketplace item for adding new items
            MarketplaceItem addItem = createAddItemEntry();
            categoryFiltered.add(addItem);
        }
        
        return categoryFiltered;
    }
    
    private void renderModernItemCard(GuiGraphics guiGraphics, MarketplaceItem item, int itemX, int itemY, int mouseX, int mouseY, int itemIndex) {
        // Check if this is the special add item entry
        if (isAddItemEntry(item)) {
            renderAddItemCard(guiGraphics, itemX, itemY, mouseX, mouseY);
            return;
        }
        
        // Calculate responsive card dimensions using calculated width
        int cardWidth = calculatedItemWidth;
        int cardHeight = GuiScalingHelper.responsiveHeight(85, 70, 100);
        int shadowOffset = GuiScalingHelper.responsiveWidth(3, 2, 4);
        
        // Modern card background with subtle shadow
        guiGraphics.fill(itemX - shadowOffset, itemY - shadowOffset, itemX + cardWidth, itemY + cardHeight, 0xFF1A1A1A);
        guiGraphics.fill(itemX - shadowOffset + 1, itemY - shadowOffset + 1, itemX + cardWidth - 1, itemY + cardHeight - 1, 0xFF2D2D2D);
        guiGraphics.fill(itemX - shadowOffset + 2, itemY - shadowOffset + 2, itemX + cardWidth - 2, itemY + cardHeight - 2, 0xFF3A3A3A);
        
        // Draw modern delete button (only if admin mode)
        if (AdminModeHandler.isAdminMode()) {
            renderModernDeleteButton(guiGraphics, itemX, itemY, mouseX, mouseY, itemIndex);
        }
        
        // Create item stack with the marketplace quantity for display
        net.minecraft.world.item.ItemStack displayStack = item.getItemStack().copy();
        displayStack.setCount(item.getQuantity());
        
        // Calculate centered position for item stack
        int itemStackSize = GuiScalingHelper.responsiveWidth(28, 22, 35);
        int itemStackX = itemX + (cardWidth - itemStackSize) / 2; // Center horizontally
        int itemStackY = itemY + GuiScalingHelper.responsiveHeight(5, 4, 8); // Start near top
        
        // Draw item icon with quantity overlay
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(itemStackX, itemStackY, 0);
        float scale = (float) itemStackSize / 16f; // Scale to fit the responsive size
        guiGraphics.pose().scale(scale, scale, 1.0f);
        guiGraphics.renderItem(displayStack, 0, 0);
        guiGraphics.renderItemDecorations(net.minecraft.client.Minecraft.getInstance().font, displayStack, 0, 0);
        guiGraphics.pose().popPose();
        
        // Check if mouse is hovering over the item stack for tooltip
        if (mouseX >= itemStackX && mouseX <= itemStackX + itemStackSize &&
            mouseY >= itemStackY && mouseY <= itemStackY + itemStackSize) {
            // Render item tooltip
            renderItemTooltip(guiGraphics, displayStack, mouseX, mouseY);
        }
        
        // Draw clickable price buttons
        renderClickablePriceButtons(guiGraphics, item, itemX, itemY, mouseX, mouseY);
    }
    
    /**
     * Renders the item tooltip when hovering over an item card.
     */
    private void renderItemTooltip(GuiGraphics guiGraphics, net.minecraft.world.item.ItemStack itemStack, int mouseX, int mouseY) {
        // Get the item's tooltip components
        java.util.List<net.minecraft.network.chat.Component> tooltip = itemStack.getTooltipLines(
            net.minecraft.world.item.Item.TooltipContext.EMPTY,
            net.minecraft.client.Minecraft.getInstance().player,
            net.minecraft.world.item.TooltipFlag.Default.NORMAL
        );
        
        // Render the tooltip
        guiGraphics.renderTooltip(
            net.minecraft.client.Minecraft.getInstance().font,
            tooltip,
            itemStack.getTooltipImage(),
            mouseX, mouseY
        );
    }
    
    /**
     * Renders the special "add item" card that looks like a marketplace item but with a big plus icon.
     */
    private void renderAddItemCard(GuiGraphics guiGraphics, int itemX, int itemY, int mouseX, int mouseY) {
        // Calculate responsive card dimensions using calculated width
        int cardWidth = calculatedItemWidth;
        int cardHeight = GuiScalingHelper.responsiveHeight(85, 70, 100);
        int shadowOffset = GuiScalingHelper.responsiveWidth(3, 2, 4);
        
        // Modern card background with subtle shadow
        guiGraphics.fill(itemX - shadowOffset, itemY - shadowOffset, itemX + cardWidth, itemY + cardHeight, 0xFF1A1A1A);
        guiGraphics.fill(itemX - shadowOffset + 1, itemY - shadowOffset + 1, itemX + cardWidth - 1, itemY + cardHeight - 1, 0xFF2D2D2D);
        guiGraphics.fill(itemX - shadowOffset + 2, itemY - shadowOffset + 2, itemX + cardWidth - 2, itemY + cardHeight - 2, 0xFF3A3A3A);
        
        // Check if mouse is hovering over the add item card
        boolean isHovered = mouseX >= itemX - shadowOffset && mouseX <= itemX + cardWidth &&
                           mouseY >= itemY - shadowOffset && mouseY <= itemY + cardHeight;
        
        // Draw a big plus icon in the center of the card
        int centerX = itemX + cardWidth / 2; // Center of the card
        int centerY = itemY + GuiScalingHelper.responsiveHeight(20, 16, 28);
        int plusSize = GuiScalingHelper.responsiveWidth(20, 16, 26);
        int plusColor = isHovered ? 0xFF4CAF50 : 0xFF66BB6A; // Green color, brighter on hover
        
        // Draw + lines (horizontal and vertical)
        guiGraphics.fill(centerX - plusSize/2, centerY - GuiScalingHelper.responsiveHeight(2, 1, 3), centerX + plusSize/2, centerY + GuiScalingHelper.responsiveHeight(2, 1, 3), plusColor);
        guiGraphics.fill(centerX - GuiScalingHelper.responsiveWidth(2, 1, 3), centerY - plusSize/2, centerX + GuiScalingHelper.responsiveWidth(2, 1, 3), centerY + plusSize/2, plusColor);
        
        // Draw "Add Item" text below the plus
        String addText = "Add Item";
        int textWidth = net.minecraft.client.Minecraft.getInstance().font.width(addText);
        int textX = itemX + (cardWidth - textWidth) / 2; // Center the text
        int textY = itemY + GuiScalingHelper.responsiveHeight(32, 26, 40);
        int textColor = isHovered ? 0xFF4CAF50 : 0xFF66BB6A;
        
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, addText, textX, textY, textColor);
    }
    
    private void renderModernDeleteButton(GuiGraphics guiGraphics, int itemX, int itemY, int mouseX, int mouseY, int itemIndex) {
        int deleteButtonX = itemX + calculatedItemWidth - GuiScalingHelper.responsiveWidth(25, 20, 30);
        int deleteButtonY = itemY + GuiScalingHelper.responsiveHeight(2, 1, 3);
        int deleteButtonSize = GuiScalingHelper.responsiveWidth(20, 16, 26);
        
        boolean isHovered = mouseX >= deleteButtonX && mouseX <= deleteButtonX + deleteButtonSize &&
                           mouseY >= deleteButtonY && mouseY <= deleteButtonY + deleteButtonSize;
        
        // Draw trash can icon directly (no background square)
        int iconColor = isHovered ? 0xFFFF4444 : 0xFFCC6666; // Red color for delete
        int iconX = deleteButtonX + deleteButtonSize - GuiScalingHelper.responsiveWidth(8, 6, 10); // Right-align the icon
        int iconY = deleteButtonY + GuiScalingHelper.responsiveHeight(2, 1, 3);
        
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, "🗑", iconX, iconY, iconColor);
    }
    
    private void renderClickablePriceButtons(GuiGraphics guiGraphics, MarketplaceItem item, int itemX, int itemY, int mouseX, int mouseY) {
        boolean canBuy = WalletHandler.hasEnoughMoney(item.getBuyPrice());
        boolean isBuyCooldown = isBuyButtonInCooldown(item);
        boolean canSellItem = canSellItem(item);
        boolean isSellCooldown = isSellButtonInCooldown(item);
        
        int cardWidth = calculatedItemWidth;
        int margin = GuiScalingHelper.responsiveWidth(5, 4, 8);
        int buttonHeight = GuiScalingHelper.responsiveHeight(12, 10, 16);
        int buttonSpacing = GuiScalingHelper.responsiveHeight(2, 1, 3);
        
        // Calculate button dimensions (fill width with margins)
        int buttonWidth = cardWidth - (margin * 2);
        int buttonStartY = itemY + GuiScalingHelper.responsiveHeight(50, 40, 65);
        
        // Buy button (top)
        int buyButtonX = itemX + margin;
        int buyButtonY = buttonStartY;
        
        boolean buyHovered = mouseX >= buyButtonX && mouseX <= buyButtonX + buttonWidth &&
                            mouseY >= buyButtonY && mouseY <= buyButtonY + buttonHeight;
        
        // Determine buy button state and color
        int buyColor;
        String displayBuyText;
        
        if (isBuyCooldown) {
            // In cooldown
            buyColor = 0xFF9E9E9E; // Gray for cooldown
            displayBuyText = "🔄";
        } else if (canBuy) {
            // Can buy
            buyColor = buyHovered ? 0xFF66BB6A : 0xFF4CAF50;
            displayBuyText = "Buy: $" + formatPrice(item.getBuyPrice());
        } else {
            // Cannot buy (insufficient funds)
            buyColor = 0xFF666666;
            displayBuyText = "Buy: $" + formatPrice(item.getBuyPrice());
        }
        
        // Draw buy button background
        guiGraphics.fill(buyButtonX, buyButtonY, buyButtonX + buttonWidth, buyButtonY + buttonHeight, buyColor);
        
        // Draw buy text (centered)
        int buyTextColor = (isBuyCooldown || canBuy) ? 0xFFFFFFFF : 0xFF999999;
        int buyTextX = buyButtonX + (buttonWidth - net.minecraft.client.Minecraft.getInstance().font.width(displayBuyText)) / 2;
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, displayBuyText, buyTextX, buyButtonY + GuiScalingHelper.responsiveHeight(2, 1, 3), buyTextColor);
        
        // Sell button (bottom)
        int sellButtonX = itemX + margin;
        int sellButtonY = buttonStartY + buttonHeight + buttonSpacing;
        
        boolean sellHovered = mouseX >= sellButtonX && mouseX <= sellButtonX + buttonWidth &&
                             mouseY >= sellButtonY && mouseY <= sellButtonY + buttonHeight;
        
        // Determine sell button state and color
        int sellColor;
        String displaySellText;
        
        if (isSellCooldown) {
            // In cooldown
            sellColor = 0xFF9E9E9E; // Gray for cooldown
            displaySellText = "🔄";
        } else if (canSellItem) {
            // Can sell
            sellColor = sellHovered ? 0xFFFFB74D : 0xFFFF9800;
            displaySellText = "Sell: $" + formatPrice(item.getSellPrice());
        } else {
            // Cannot sell (don't have item)
            sellColor = 0xFF666666;
            displaySellText = "Sell: $" + formatPrice(item.getSellPrice());
        }
        
        // Draw sell button background
        guiGraphics.fill(sellButtonX, sellButtonY, sellButtonX + buttonWidth, sellButtonY + buttonHeight, sellColor);
        
        // Draw sell text (centered)
        int sellTextColor = (isSellCooldown || canSellItem) ? 0xFFFFFFFF : 0xFF999999;
        int sellTextX = sellButtonX + (buttonWidth - net.minecraft.client.Minecraft.getInstance().font.width(displaySellText)) / 2;
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, displaySellText, sellTextX, sellButtonY + GuiScalingHelper.responsiveHeight(2, 1, 3), sellTextColor);
    }
    
    /**
     * Formats a price number to be shorter for display with intelligent decimal handling.
     * Examples: 1000 -> 1K, 10001 -> 10K, 10500 -> 10.5K, 1000000 -> 1M
     */
    private String formatPrice(long price) {
        if (price < 1000) {
            return String.valueOf(price);
        } else if (price < 1000000) {
            // Thousands
            double thousands = price / 1000.0;
            if (thousands == Math.floor(thousands)) {
                // No decimal needed (e.g., 1000 -> 1K, 10000 -> 10K)
                return String.format("%.0fK", thousands);
            } else if (thousands < 10) {
                // Show 1 decimal for small numbers (e.g., 1500 -> 1.5K)
                return String.format("%.1fK", thousands);
            } else {
                // Show 1 decimal for larger numbers (e.g., 10500 -> 10.5K)
                return String.format("%.1fK", thousands);
            }
        } else if (price < 1000000000) {
            // Millions
            double millions = price / 1000000.0;
            if (millions == Math.floor(millions)) {
                return String.format("%.0fM", millions);
            } else if (millions < 10) {
                return String.format("%.1fM", millions);
            } else {
                return String.format("%.1fM", millions);
            }
        } else if (price < 1000000000000L) {
            // Billions
            double billions = price / 1000000000.0;
            if (billions == Math.floor(billions)) {
                return String.format("%.0fB", billions);
            } else if (billions < 10) {
                return String.format("%.1fB", billions);
            } else {
                return String.format("%.1fB", billions);
            }
        } else {
            // Trillions
            double trillions = price / 1000000000000.0;
            if (trillions == Math.floor(trillions)) {
                return String.format("%.0fT", trillions);
            } else if (trillions < 10) {
                return String.format("%.1fT", trillions);
            } else {
                return String.format("%.1fT", trillions);
            }
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
        
        // Handle category sidebar clicks
        int sidebarWidth = GuiScalingHelper.responsiveWidth(120, 100, 150);
        int sidebarX = x + GuiScalingHelper.responsiveWidth(10, 8, 15);
        int sidebarY = y + GuiScalingHelper.responsiveHeight(50, 40, 70);
        int sidebarHeight = height - GuiScalingHelper.responsiveHeight(80, 60, 100);
        
        if (mouseX >= sidebarX && mouseX <= sidebarX + sidebarWidth &&
            mouseY >= sidebarY && mouseY <= sidebarY + sidebarHeight) {
            
            List<ItemCategoryManager.Category> categories = ItemCategoryManager.getAllCategories();
            int categoryY = sidebarY + GuiScalingHelper.responsiveHeight(20, 16, 28);
            int categoryHeight = GuiScalingHelper.responsiveHeight(16, 12, 22);
            
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
        int startY = y + GuiScalingHelper.responsiveHeight(60, 50, 80);
        int startX = x + sidebarWidth + GuiScalingHelper.responsiveWidth(20, 15, 30); // Start after sidebar
        int itemsRendered = 0;
        int maxItemsToRender = maxVisibleItems;
        List<MarketplaceItem> itemsToRender = getItemsToRender();
        
        for (int i = scrollOffset * itemsPerRow; i < itemsToRender.size() && itemsRendered < maxItemsToRender; i += itemsPerRow) {
            for (int j = 0; j < itemsPerRow && i + j < itemsToRender.size() && itemsRendered < maxItemsToRender; j++) {
                MarketplaceItem item = itemsToRender.get(i + j);
                int itemX = startX + j * itemSpacing;
                int itemY = startY + (itemsRendered / itemsPerRow) * itemHeight;
                
                // Check if this is the add item entry
                if (isAddItemEntry(item)) {
                    // Handle click on add item card
                    if (mouseX >= itemX - GuiScalingHelper.responsiveWidth(3, 2, 4) && mouseX <= itemX + calculatedItemWidth &&
                        mouseY >= itemY - GuiScalingHelper.responsiveWidth(3, 2, 4) && mouseY <= itemY + GuiScalingHelper.responsiveHeight(85, 70, 100)) {
                        // Open add item popup
                        if (parentScreen != null) {
                            net.minecraft.client.Minecraft.getInstance().setScreen(new AddItemPopupScreen(parentScreen));
                        }
                        return true;
                    }
                } else {
                    // Check delete button click (only if admin mode)
                if (AdminModeHandler.isAdminMode()) {
                        int deleteButtonX = itemX + calculatedItemWidth - GuiScalingHelper.responsiveWidth(25, 20, 30);
                        int deleteButtonY = itemY + GuiScalingHelper.responsiveHeight(2, 1, 3);
                        int deleteButtonSize = GuiScalingHelper.responsiveWidth(20, 16, 26);
                        
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
                
                    // Check buy/sell button clicks (now vertically stacked)
                    int margin = GuiScalingHelper.responsiveWidth(5, 4, 8);
                    int buttonHeight = GuiScalingHelper.responsiveHeight(12, 10, 16);
                    int buttonSpacing = GuiScalingHelper.responsiveHeight(2, 1, 3);
                    int buttonWidth = calculatedItemWidth - (margin * 2);
                    int buttonStartY = itemY + GuiScalingHelper.responsiveHeight(50, 40, 65);
                    
                    // Buy button (top)
                    int buyButtonX = itemX + margin;
                    int buyButtonY = buttonStartY;
                    
                    if (mouseX >= buyButtonX && mouseX <= buyButtonX + buttonWidth &&
                        mouseY >= buyButtonY && mouseY <= buyButtonY + buttonHeight) {
                        // Handle buy button click (only if not in cooldown)
                        if (!isBuyButtonInCooldown(item) && buyItem(item)) {
                            // Success - item was bought
                    return true;
                        }
                        return true; // Still return true to consume the click
                    }
                    
                    // Sell button (bottom)
                    int sellButtonX = itemX + margin;
                    int sellButtonY = buttonStartY + buttonHeight + buttonSpacing;
                    
                    if (mouseX >= sellButtonX && mouseX <= sellButtonX + buttonWidth &&
                        mouseY >= sellButtonY && mouseY <= sellButtonY + buttonHeight) {
                        // Handle sell button click (only if not in cooldown)
                        if (!isSellButtonInCooldown(item) && sellItem(item)) {
                            // Success - item was sold
                    return true;
                        }
                        return true; // Still return true to consume the click
                    }
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
