package com.freemarket.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import com.freemarket.Config;
import com.freemarket.common.data.FreeMarketItem;
import com.freemarket.common.network.MarketplaceItemOperationPacket;
import com.freemarket.common.handlers.AdminModeHandler;
import com.freemarket.common.handlers.WalletHandler;
import com.freemarket.common.managers.ItemCategoryManager;
import com.freemarket.common.attachments.ItemComponentHandler;
import com.freemarket.client.data.ClientFreeMarketDataManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;

/**
 * A scrollable container for displaying free market items with search functionality.
 */
public class FreeMarketContainer implements Renderable {
    
    private final int x, y, width, height;
    private List<FreeMarketItem> allItems;
    private final FreeMarketGuiScreen parentScreen;
    private EditBox searchBox;
    
    // Caching for processed items with component data
    private final Map<String, ItemStack> processedItemCache = new HashMap<>();
    private int scrollOffset = 0;
    private int maxVisibleItems = 0;
    private int itemHeight; // Will be calculated responsively
    private int itemsPerRow = 3;
    private int itemSpacing; // Will be calculated responsively
    private ItemCategoryManager.Category selectedCategory = ItemCategoryManager.Category.ALL;
    
    // Buy button state tracking - per item
    private final java.util.Map<String, Long> buyButtonCooldowns = new java.util.HashMap<>();
    private static final long BUY_COOLDOWN_MS = 250; // 250ms cooldown
    
    // Sell button state tracking - per item
    private final java.util.Map<String, Long> sellButtonCooldowns = new java.util.HashMap<>();
    private static final long SELL_COOLDOWN_MS = 250; // 250ms cooldown
    
    public FreeMarketContainer(int x, int y, int width, int height, List<FreeMarketItem> items, FreeMarketGuiScreen parentScreen) {
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
    
    /**
     * Updates the marketplace items list.
     * Called when the marketplace data is refreshed.
     */
    /**
     * Updates the marketplace items list and optionally preserves scroll position.
     * @param newItems The new list of marketplace items
     * @param preserveScrollPosition If true, preserves the current scroll position
     */
    public void updateFreeMarketItems(List<FreeMarketItem> newItems, boolean preserveScrollPosition) {
        this.allItems = new ArrayList<>(newItems);
        if (!preserveScrollPosition) {
            // Reset scroll position when items change (default behavior)
            this.scrollOffset = 0;
        }
        // If preserveScrollPosition is true, keep the current scrollOffset
    }
    
    /**
     * Updates the marketplace items list (default behavior - resets scroll).
     * @param newItems The new list of marketplace items
     */
    public void updateFreeMarketItems(List<FreeMarketItem> newItems) {
        updateFreeMarketItems(newItems, false);
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
        // Create search box with centered positioning and proper spacing from title
        int searchWidth = GuiScalingHelper.responsiveWidth(300, 250, 400); // Fixed width for centering
        int searchHeight = GuiScalingHelper.responsiveHeight(20, 16, 26);
        int searchX = x + (width - searchWidth) / 2; // Center horizontally
        int searchY = y + GuiScalingHelper.responsiveHeight(30, 25, 35); // Position well above sidebar
        
        this.searchBox = new EditBox(
            net.minecraft.client.Minecraft.getInstance().font,
            searchX, searchY, searchWidth, searchHeight,
            Component.translatable("gui.FreeMarket.marketplace.search_placeholder")
        );
        this.searchBox.setResponder(this::onSearchChanged);
        this.searchBox.setMaxLength(50); // Set max length for search
        this.searchBox.setValue(""); // Clear any initial value
    }
    
    private void calculateMaxVisibleItems() {
        int availableHeight = height - GuiScalingHelper.responsiveHeight(65, 55, 85); // Account for title, search box, and padding
        this.maxVisibleItems = (availableHeight / itemHeight) * itemsPerRow;
    }
    
    private void onSearchChanged(String searchText) {
        scrollOffset = 0; // Reset scroll when searching
    }
    
    public void addItem(FreeMarketItem item) {
        allItems.add(item);
        onSearchChanged(searchBox != null ? searchBox.getValue() : "");
    }
    
    public void removeItem(FreeMarketItem item) {
        allItems.remove(item);
        onSearchChanged(searchBox != null ? searchBox.getValue() : "");
    }
    
    public void updateItems(List<FreeMarketItem> newItems) {
        allItems.clear();
        allItems.addAll(newItems);
        // Clear cache when items are updated
        clearProcessedItemCache();
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
        Component title = Component.literal(Config.MARKETPLACE_NAME.get());
        int titleWidth = net.minecraft.client.Minecraft.getInstance().font.width(title);
        int titleX = x + (width - titleWidth) / 2;
        int titleY = y + GuiScalingHelper.responsiveHeight(15, 12, 20);
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, title, titleX, titleY, 0xFFE0E0E0);
        
        // Render search box
        if (searchBox != null) {
            searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        
        // Wallet display is now handled in the main screen
        
        // Add button is now handled as a special marketplace item
        
        // Draw category sidebar
        renderCategorySidebar(guiGraphics, mouseX, mouseY);
        
        // Get items to render based on selected category and search
        List<FreeMarketItem> itemsToRender = getItemsToRender();
        
        // Draw items with modern styling (adjusted for sidebar)
        int sidebarWidth = GuiScalingHelper.responsiveWidth(120, 100, 150);
        int startY = y + GuiScalingHelper.responsiveHeight(65, 55, 85); // Align with category sidebar
        int startX = x + sidebarWidth + GuiScalingHelper.responsiveWidth(20, 15, 30); // Start after sidebar
        int itemsRendered = 0;
        int maxItemsToRender = maxVisibleItems;
        
        for (int i = scrollOffset * itemsPerRow; i < itemsToRender.size() && itemsRendered < maxItemsToRender; i += itemsPerRow) {
            for (int j = 0; j < itemsPerRow && i + j < itemsToRender.size() && itemsRendered < maxItemsToRender; j++) {
                FreeMarketItem item = itemsToRender.get(i + j);
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
        Component countText = Component.translatable("gui.FreeMarket.marketplace.count", actualItemCount, allItems.size());
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, countText, x + GuiScalingHelper.responsiveWidth(10, 8, 15), y + height - GuiScalingHelper.responsiveHeight(15, 12, 20), 0xCCCCCC);
    }
    
    
    
    private void renderCategorySidebar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int sidebarWidth = GuiScalingHelper.responsiveWidth(120, 100, 150);
        int sidebarX = x + GuiScalingHelper.responsiveWidth(10, 8, 15);
        int sidebarY = y + GuiScalingHelper.responsiveHeight(65, 55, 85); // More margin from search box
        int sidebarHeight = height - GuiScalingHelper.responsiveHeight(95, 75, 115);
        
        // Draw sidebar background
        guiGraphics.fill(sidebarX, sidebarY, sidebarX + sidebarWidth, sidebarY + sidebarHeight, 0xFF1A1A1A);
        guiGraphics.fill(sidebarX + 1, sidebarY + 1, sidebarX + sidebarWidth - 1, sidebarY + sidebarHeight - 1, 0xFF2D2D2D);
        
        // Draw sidebar title
        Component sidebarTitle = Component.literal("Categories");
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, sidebarTitle, sidebarX + GuiScalingHelper.responsiveWidth(5, 4, 8), sidebarY + GuiScalingHelper.responsiveHeight(5, 4, 8), 0xFFE0E0E0);
        
        // Draw categories (filter out categories with zero items)
        List<ItemCategoryManager.Category> allCategories = ItemCategoryManager.getAllCategories();
        Map<ItemCategoryManager.Category, Integer> categoryCounts = ItemCategoryManager.getCategoryCounts(allItems);
        
        // Filter out categories with zero items
        List<ItemCategoryManager.Category> categories = allCategories.stream()
            .filter(category -> categoryCounts.getOrDefault(category, 0) > 0)
            .collect(java.util.stream.Collectors.toList());
        
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
    private FreeMarketItem createAddItemEntry() {
        // Create a dummy item stack (we won't actually use it for rendering)
        net.minecraft.world.item.ItemStack dummyStack = net.minecraft.world.item.Items.AIR.getDefaultInstance();
        return new FreeMarketItem(dummyStack, 0, 0, 0, "admin", "ADD_ITEM_SPECIAL");
    }
    
    /**
     * Checks if a marketplace item is the special "add item" entry.
     */
    private boolean isAddItemEntry(FreeMarketItem item) {
        return "ADD_ITEM_SPECIAL".equals(item.getGuid());
    }
    
    private List<FreeMarketItem> getItemsToRender() {
        // First filter by category
        List<FreeMarketItem> categoryFiltered = ItemCategoryManager.filterItemsByCategory(allItems, selectedCategory);
        
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
            FreeMarketItem addItem = createAddItemEntry();
            categoryFiltered.add(addItem);
        }
        
        return categoryFiltered;
    }
    
    private void renderModernItemCard(GuiGraphics guiGraphics, FreeMarketItem item, int itemX, int itemY, int mouseX, int mouseY, int itemIndex) {
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
        // Create display stack with component data applied for visual effects (glint, trim, etc.)
        net.minecraft.world.item.ItemStack displayStack = createItemWithComponentData(item);
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
        
        // Draw action buttons
        renderActionButtons(guiGraphics, item, itemX, itemY, mouseX, mouseY);
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
    
    private void renderActionButtons(GuiGraphics guiGraphics, FreeMarketItem item, int itemX, int itemY, int mouseX, int mouseY) {
        boolean canBuy = canBuyItem(item);
        boolean isBuyCooldown = isBuyButtonInCooldown(item);
        
        // Buy button
        int buyButtonX = itemX + GuiScalingHelper.responsiveWidth(5, 4, 8);
        int buyButtonY = itemY + GuiScalingHelper.responsiveHeight(50, 40, 65);
        int buyButtonWidth = calculatedItemWidth - (GuiScalingHelper.responsiveWidth(5, 4, 8) * 2);
        int buyButtonHeight = GuiScalingHelper.responsiveHeight(12, 10, 16);
        
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
            buyText = "Buy: $" + formatPrice(item.getBuyPrice());
        } else {
            // Cannot buy (insufficient funds)
            buyColor = 0xFF666666;
            buyText = "Buy: $" + formatPrice(item.getBuyPrice());
        }
        
        // Draw buy button background
        guiGraphics.fill(buyButtonX, buyButtonY, buyButtonX + buyButtonWidth, buyButtonY + buyButtonHeight, buyColor);
        
        // Draw text with appropriate color
        int textColor = isBuyCooldown ? 0xFFFFFFFF : (canBuy ? 0xFFFFFFFF : 0xFF999999);
        int textX = buyButtonX + (buyButtonWidth - net.minecraft.client.Minecraft.getInstance().font.width(buyText)) / 2;
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, buyText, textX, buyButtonY + GuiScalingHelper.responsiveHeight(2, 1, 3), textColor);
        
        // Sell button
        int sellButtonX = itemX + GuiScalingHelper.responsiveWidth(5, 4, 8);
        int sellButtonY = buyButtonY + buyButtonHeight + GuiScalingHelper.responsiveHeight(4, 3, 6);
        int sellButtonWidth = buyButtonWidth;
        int sellButtonHeight = buyButtonHeight;
        
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
            sellText = "Sell: $" + formatPrice(item.getSellPrice());
        } else {
            // Cannot sell (don't have item)
            sellColor = 0xFF666666;
            sellText = "Sell: $" + formatPrice(item.getSellPrice());
        }
        
        // Draw sell button background
        guiGraphics.fill(sellButtonX, sellButtonY, sellButtonX + sellButtonWidth, sellButtonY + sellButtonHeight, sellColor);
        
        // Draw text with appropriate color
        int sellTextColor = isSellCooldown ? 0xFFFFFFFF : (canSellItem ? 0xFFFFFFFF : 0xFF999999);
        int sellTextX = sellButtonX + (sellButtonWidth - net.minecraft.client.Minecraft.getInstance().font.width(sellText)) / 2;
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, sellText, sellTextX, sellButtonY + GuiScalingHelper.responsiveHeight(2, 1, 3), sellTextColor);
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
        List<FreeMarketItem> itemsToRender = getItemsToRender();
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
        List<FreeMarketItem> itemsToRender = getItemsToRender();
        return Math.max(0, (itemsToRender.size() + itemsPerRow - 1) / itemsPerRow - maxVisibleItems / itemsPerRow);
    }
    
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle search box clicks first
        if (searchBox != null) {
            if (searchBox.mouseClicked(mouseX, mouseY, button)) {
                searchBox.setFocused(true);
                return true;
            }
            // If click is within search box bounds, focus it
            if (mouseX >= searchBox.getX() && mouseX <= searchBox.getX() + searchBox.getWidth() &&
                mouseY >= searchBox.getY() && mouseY <= searchBox.getY() + searchBox.getHeight()) {
                searchBox.setFocused(true);
                return true;
            }
        }
        
        // Handle category sidebar clicks
        int sidebarWidth = GuiScalingHelper.responsiveWidth(120, 100, 150);
        int sidebarX = x + GuiScalingHelper.responsiveWidth(10, 8, 15);
        int sidebarY = y + GuiScalingHelper.responsiveHeight(65, 55, 85); // Match new sidebar position
        int sidebarHeight = height - GuiScalingHelper.responsiveHeight(95, 75, 115);
        
        if (mouseX >= sidebarX && mouseX <= sidebarX + sidebarWidth &&
            mouseY >= sidebarY && mouseY <= sidebarY + sidebarHeight) {
            
            // Use the same filtered categories as rendering
            List<ItemCategoryManager.Category> allCategories = ItemCategoryManager.getAllCategories();
            Map<ItemCategoryManager.Category, Integer> categoryCounts = ItemCategoryManager.getCategoryCounts(allItems);
            
            // Filter out categories with zero items
            List<ItemCategoryManager.Category> categories = allCategories.stream()
                .filter(category -> categoryCounts.getOrDefault(category, 0) > 0)
                .collect(java.util.stream.Collectors.toList());
            
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
        List<FreeMarketItem> itemsToRender = getItemsToRender();
        
        for (int i = scrollOffset * itemsPerRow; i < itemsToRender.size() && itemsRendered < maxItemsToRender; i += itemsPerRow) {
            for (int j = 0; j < itemsPerRow && i + j < itemsToRender.size() && itemsRendered < maxItemsToRender; j++) {
                FreeMarketItem item = itemsToRender.get(i + j);
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
                        
                        if (mouseX >= deleteButtonX - 2.0 && mouseX <= deleteButtonX + deleteButtonSize + 2.0 &&
                            mouseY >= deleteButtonY - 2.0 && mouseY <= deleteButtonY + deleteButtonSize + 2.0) {
                            // Send delete request to server via network packet
                            MarketplaceItemOperationPacket packet = MarketplaceItemOperationPacket.removeItem(item);
                            net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
                            
                            return true;
                        }
                    }
                }
                

                // Buy button (top) - with floating point tolerance
                int buyButtonX = itemX + GuiScalingHelper.responsiveWidth(5, 4, 8);
                int buyButtonY = itemY + GuiScalingHelper.responsiveHeight(50, 40, 65);
                int buyButtonWidth = calculatedItemWidth - (GuiScalingHelper.responsiveWidth(5, 4, 8) * 2);
                int buyButtonHeight = GuiScalingHelper.responsiveHeight(12, 10, 16);
                
                if (mouseX >= buyButtonX - 2.0 && mouseX <= buyButtonX + buyButtonWidth + 2.0 &&
                    mouseY >= buyButtonY - 2.0 && mouseY <= buyButtonY + buyButtonHeight + 2.0) {
                    
                    // Handle buy button click
                    if (buyItem(item)) {
                        // Success - item was bought
                        return true;
                    }
                    return true; // Still return true to consume the click
                }
                
                // Sell button (bottom) - with floating point tolerance
                int sellButtonX = itemX + GuiScalingHelper.responsiveWidth(5, 4, 8);
                int sellButtonY = buyButtonY + buyButtonHeight + GuiScalingHelper.responsiveHeight(4, 3, 6);
                int sellButtonWidth = buyButtonWidth;
                int sellButtonHeight = buyButtonHeight;
                
                if (mouseX >= sellButtonX - 2.0 && mouseX <= sellButtonX + sellButtonWidth + 2.0 &&
                    mouseY >= sellButtonY - 2.0 && mouseY <= sellButtonY + sellButtonHeight + 2.0) {
                    
                    // Handle sell button click
                    if (sellItem(item)) {
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
        
        // If click is outside search box, unfocus it
        if (searchBox != null && searchBox.isFocused()) {
            searchBox.setFocused(false);
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
    private boolean buyItem(FreeMarketItem item) {
        // Check if button is in cooldown first
        if (isBuyButtonInCooldown(item)) {
            return false; // Don't process transaction during cooldown
        }
        
        // Check if player has enough money
        if (!WalletHandler.hasEnoughMoney(item.getBuyPrice())) {
            // TODO: Show error message to player
            return false;
        }
        
        // Set cooldown for this specific item (only after validation passes)
        long currentTime = System.currentTimeMillis();
        buyButtonCooldowns.put(item.getGuid(), currentTime + BUY_COOLDOWN_MS);
        
        // Get the player
        Minecraft minecraft = Minecraft.getInstance();
        Player clientPlayer = minecraft.player;
        if (clientPlayer == null) {
            return false;
        }
        
        // Create item with component data applied
        ItemStack itemToGive = createItemWithComponentData(item);
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
        
        // Play purchase sound effect
        clientPlayer.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.0F);
        
        // Refresh wallet display and marketplace (preserve scroll position)
        if (parentScreen != null) {
            parentScreen.refreshBalance(); // Refresh cached balance
            parentScreen.refreshMarketplace(true); // Preserve scroll position
        }
        
        return true;
    }
    
    /**
     * Creates an ItemStack with component data applied from the marketplace item.
     * Uses caching to avoid reprocessing component data every frame.
     */
    private ItemStack createItemWithComponentData(FreeMarketItem item) {
        // Create cache key from item properties
        String cacheKey = item.getGuid() + "_" + item.getComponentData().hashCode();
        
        // Check if we already have this item processed
        if (processedItemCache.containsKey(cacheKey)) {
            return processedItemCache.get(cacheKey).copy();
        }
        
        ItemStack baseItemStack = item.getItemStack().copy();
        
        // Apply component data if present
        String componentData = item.getComponentData();
        
        if (componentData != null && !componentData.trim().isEmpty() && !componentData.equals("{}")) {
            // Try to use server-side processing for proper registry access
            Minecraft minecraft = Minecraft.getInstance();
            var singleplayerServer = minecraft.getSingleplayerServer();
            
            if (singleplayerServer != null) {
                // Use server-side handler with registry access
                ItemStack result = com.freemarket.server.handlers.ServerItemHandler.createItemWithComponentData(
                    baseItemStack, componentData, singleplayerServer);
                // Cache the result
                processedItemCache.put(cacheKey, result.copy());
                return result;
            } else {
                // Fallback to client-side processing
                ItemComponentHandler.applyComponentData(baseItemStack, componentData);
                // Cache the result
                processedItemCache.put(cacheKey, baseItemStack.copy());
                return baseItemStack;
            }
        }
        
        // Cache the base item stack
        processedItemCache.put(cacheKey, baseItemStack.copy());
        return baseItemStack;
    }
    
    /**
     * Clears the processed item cache. Should be called when marketplace data changes.
     */
    public void clearProcessedItemCache() {
        processedItemCache.clear();
    }
    
    /**
     * Checks if buy button is in cooldown for an item.
     */
    private boolean isBuyButtonInCooldown(FreeMarketItem item) {
        long currentTime = System.currentTimeMillis();
        Long cooldownEnd = buyButtonCooldowns.get(item.getGuid());
        return cooldownEnd != null && currentTime < cooldownEnd;
    }
    
    /**
     * Handles selling an item to the marketplace.
     * Validates inventory, removes item, and adds money to wallet.
     */
    private boolean sellItem(FreeMarketItem item) {
        // Check if button is in cooldown first
        if (isSellButtonInCooldown(item)) {
            return false; // Don't process transaction during cooldown
        }
        
        // Get the player
        Minecraft minecraft = Minecraft.getInstance();
        Player clientPlayer = minecraft.player;
        if (clientPlayer == null) {
            System.out.println("SELL: No client player found");
            return false;
        }
        
        // Check if player has the item in inventory - use server player for persistence
        // Create item with component data applied to ensure proper matching
        ItemStack itemToSell = createItemWithComponentData(item);
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
        
        // Set cooldown for this specific item (only after validation passes)
        long currentTime = System.currentTimeMillis();
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
        
        // Play sell sound effect (lower pitch note block)
        clientPlayer.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 0.5F);
        
        // Refresh wallet display and marketplace (preserve scroll position)
        if (parentScreen != null) {
            parentScreen.refreshBalance(); // Refresh cached balance
            parentScreen.refreshMarketplace(true); // Preserve scroll position
        }
        
        return true;
    }
    
    /**
     * Checks if the player has the specified item in their inventory.
     * Checks total count across all stacks, not individual stack counts.
     */
    private boolean hasItemInInventory(Player player, ItemStack itemToCheck) {
        var inventory = player.getInventory();
        int totalCount = 0;
        
        // Count all matching items across the entire inventory
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (!slotItem.isEmpty() && ItemStack.isSameItemSameComponents(slotItem, itemToCheck)) {
                totalCount += slotItem.getCount();
            }
        }
        
        return totalCount >= itemToCheck.getCount();
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
     * Only uses main inventory slots (0-35) to avoid offhand, armor, and curio slots.
     * Prioritizes adding to stacks with the fewest items.
     * @param player the player to add items to
     * @param itemToAdd the item to add
     * @return true if all items were added, false if some had to be dropped
     */
    private boolean addItemToInventory(Player player, ItemStack itemToAdd) {
        var inventory = player.getInventory();
        int remainingToAdd = itemToAdd.getCount();
        
        // Only use main inventory slots (0-35) - avoid offhand (40), armor (36-39), and curio slots
        final int MAIN_INVENTORY_SIZE = 36; // 0-35: hotbar + main inventory
        
        // First pass: find all existing stacks of the same item in main inventory and sort by count (fewest first)
        java.util.List<java.util.Map.Entry<Integer, ItemStack>> existingStacks = new java.util.ArrayList<>();
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
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
        
        // If there are still items to add, try to find empty slots in main inventory only
        if (remainingToAdd > 0) {
            for (int i = 0; i < MAIN_INVENTORY_SIZE && remainingToAdd > 0; i++) {
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
    private boolean isSellButtonInCooldown(FreeMarketItem item) {
        long currentTime = System.currentTimeMillis();
        Long cooldownEnd = sellButtonCooldowns.get(item.getGuid());
        boolean inCooldown = cooldownEnd != null && currentTime < cooldownEnd;
        
        return inCooldown;
    }
    
    /**
     * Checks if the player can sell the specified item (has it in inventory and sell price > 0).
     */
    private boolean canSellItem(FreeMarketItem item) {
        // First check if sell price is greater than 0
        if (item.getSellPrice() <= 0) {
            return false;
        }
        
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
        
        // Create item with component data applied to ensure proper matching
        ItemStack itemToCheck = item.getItemStack().copy();
        
        // Apply component data if present (same logic as createItemWithComponentData)
        String componentData = item.getComponentData();
        if (componentData != null && !componentData.trim().isEmpty() && !componentData.equals("{}")) {
            // Try to use server-side processing for proper registry access
            if (singleplayerServer != null) {
                // Use server-side handler with registry access
                itemToCheck = com.freemarket.server.handlers.ServerItemHandler.createItemWithComponentData(
                    itemToCheck, componentData, singleplayerServer);
            } else {
                // Fallback to client-side processing
                ItemComponentHandler.applyComponentData(itemToCheck, componentData);
            }
        }
        
        return hasItemInInventory(playerForCheck, itemToCheck);
    }
    
    /**
     * Checks if the player can buy the specified item (has enough money and buy price > 0).
     */
    private boolean canBuyItem(FreeMarketItem item) {
        // First check if buy price is greater than 0
        if (item.getBuyPrice() <= 0) {
            return false;
        }
        
        // Then check if player has enough money
        return WalletHandler.hasEnoughMoney(item.getBuyPrice());
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
