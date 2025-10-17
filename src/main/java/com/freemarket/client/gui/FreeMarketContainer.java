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
import com.freemarket.client.handlers.ClientWalletHandler;
import com.freemarket.common.managers.ItemCategoryManager;
import com.freemarket.common.attachments.ItemComponentHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

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
    
    // Caching for button states to prevent flickering
    private final Map<String, Boolean> cachedCanBuyStates = new HashMap<>();
    private final Map<String, Boolean> cachedCanSellStates = new HashMap<>();
    
    // Caching for category filtering to prevent recalculation on every render
    private List<ItemCategoryManager.Category> cachedCategories;
    private Map<ItemCategoryManager.Category, Integer> cachedCategoryCounts;
    private long lastCategoryCacheUpdate = 0;
    private static final long CATEGORY_CACHE_DURATION = 1000; // 1 second cache
    
    // Caching for item filtering to prevent recalculation on every render
    private List<FreeMarketItem> cachedItemsToRender;
    private ItemCategoryManager.Category lastFilteredCategory;
    private String lastSearchText;
    private long lastItemCacheUpdate = 0;
    private static final long ITEM_CACHE_DURATION = 500; // 500ms cache for more responsive search
    // Item card renderer for proper GUI scaling
    private final ItemCardRenderer itemCardRenderer = new ItemCardRenderer();
    
    // GUI Scale to Grid Layout Mapping
    // Scale 1 = 5x5, Scale 2 = 4x4, Scale 3 = 3x3, Scale 4 = 2x2, Scale 5 = 1x1
    // This provides predictable layouts for each GUI scale setting
    
    private int scrollOffset = 0;
    private int maxVisibleItems; // Calculated dynamically based on available space
    private int itemHeight; // Calculated responsively based on card content
    private int itemsPerRow; // Calculated dynamically based on available width
    private int rowsOfItems; // Calculated dynamically based on available height
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
        // Calculate grid layout based on GUI scale mapping
        calculateOptimalGridLayout();
        
        // Calculate available space for items
        int sidebarWidth = (int)(width * 0.2); // 20% of container width
        int sidebarMargin = (int)(width * 0.02); // 2% margin
        int rightMargin = (int)(width * 0.02); // 2% margin
        int availableWidth = width - sidebarWidth - sidebarMargin - rightMargin;
        
        // Calculate card spacing based on grid size
        int cardMargin = Math.max(2, (int)(width * 0.005)); // Minimum 2px margin between cards
        int shadowOffset = Math.max(1, (int)(width * 0.002)); // Minimum 1px shadow offset
        
        // Calculate card width to fit the grid perfectly
        int totalSpacing = (itemsPerRow - 1) * (cardMargin + shadowOffset);
        int cardWidth = (availableWidth - totalSpacing) / itemsPerRow;
        
        // Ensure minimum card size for usability
        int minCardWidth = Math.max(40, (int)(width * 0.05)); // Minimum 5% of container width
        cardWidth = Math.max(cardWidth, minCardWidth);
        
        // Calculate card height based on available vertical space
        int availableHeight = height - (int)(height * 0.2); // Leave space for search box and margins
        int verticalMargin = Math.max(2, (int)(height * 0.005)); // Minimum 2px vertical margin
        int totalVerticalSpacing = (rowsOfItems - 1) * verticalMargin;
        int cardHeight = (availableHeight - totalVerticalSpacing) / rowsOfItems;
        
        // Ensure minimum card height for usability
        int minCardHeight = Math.max(30, (int)(height * 0.05)); // Minimum 5% of container height
        cardHeight = Math.max(cardHeight, minCardHeight);
        
        // Calculate item spacing
        this.itemSpacing = cardWidth + cardMargin + shadowOffset;
        this.itemHeight = cardHeight + verticalMargin;
        
        // Store the calculated dimensions for use in rendering
        this.calculatedItemWidth = cardWidth;
    }
    
    /**
     * Maps GUI scale to grid layout
     * Scale 1 = 5x5, Scale 2 = 4x4, Scale 3 = 3x3, Scale 4 = 2x2, Scale 5 = 1x1
     */
    private void calculateOptimalGridLayout() {
        Minecraft client = Minecraft.getInstance();
        float guiScale = (float) client.getWindow().getGuiScale();
        
        // Map GUI scale to grid size (inverse relationship)
        int gridSize;
        if (guiScale <= 1.0f) {
            gridSize = 5; // Scale 1 = 5x5 (most items)
        } else if (guiScale <= 2.0f) {
            gridSize = 4; // Scale 2 = 4x4
        } else if (guiScale <= 3.0f) {
            gridSize = 3; // Scale 3 = 3x3
        } else if (guiScale <= 4.0f) {
            gridSize = 2; // Scale 4 = 2x2
        } else {
            gridSize = 1; // Scale 5+ = 1x1 (largest cards)
        }
        
        // Set grid layout based on GUI scale
        this.itemsPerRow = gridSize;
        this.rowsOfItems = gridSize;
        this.maxVisibleItems = gridSize * gridSize;
        
        // Ensure we always show at least 1 item
        this.itemsPerRow = Math.max(1, this.itemsPerRow);
        this.rowsOfItems = Math.max(1, this.rowsOfItems);
        this.maxVisibleItems = Math.max(1, this.maxVisibleItems);
    }
    
    
    // Add a field to store the calculated item width
    private int calculatedItemWidth = 130;
    
    public void init() {
        // Recalculate responsive dimensions for current screen size
        calculateResponsiveDimensions();
        
        // Create search box with proper spacing from title
        int searchWidth = (int)(width * 0.5); // 50% of container width
        int searchHeight = (int)(height * 0.05); // 5% of container height
        int searchX = x + (width - searchWidth) / 2; // Center horizontally
        int searchY = y + (int)(height * 0.08); // 8% from top (below title with space)
        
        this.searchBox = new EditBox(
            net.minecraft.client.Minecraft.getInstance().font,
            searchX, searchY, searchWidth, searchHeight,
            Component.translatable("gui.FreeMarket.marketplace.search_placeholder")
        );
        this.searchBox.setResponder(this::onSearchChanged);
        this.searchBox.setMaxLength(50); // Set max length for search
        this.searchBox.setValue(""); // Clear any initial value
    }
    
    // calculateMaxVisibleItems is no longer needed - it's calculated in calculateResponsiveDimensions()
    
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
        updateButtonStates(); // Update button states when items change
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
        // Draw modern container background with gradient effect (semi-transparent)
        guiGraphics.fill(x, y, x + width, y + height, 0x801E1E1E); // 50% opacity
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x802A2A2A); // 50% opacity
        
        // Draw subtle border with rounded corners effect (semi-transparent)
        guiGraphics.fill(x, y, x + width, y + 2, 0x80404040); // 50% opacity
        guiGraphics.fill(x, y, x + 2, y + height, 0x80404040); // 50% opacity
        guiGraphics.fill(x + width - 2, y, x + width, y + height, 0x80404040); // 50% opacity
        guiGraphics.fill(x, y + height - 2, x + width, y + height, 0x80404040); // 50% opacity
        
        // Draw title (simple rendering with proper spacing from search bar)
        Component title = Component.literal(Config.MARKETPLACE_NAME.get());
        int titleWidth = net.minecraft.client.Minecraft.getInstance().font.width(title);
        int titleX = x + (width - titleWidth) / 2;
        int titleY = y + (int)(height * 0.02); // 2% from top (reduced to avoid search bar)
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
        
        // Draw items with percentage-based positioning (aligned with sidebar)
        int sidebarWidth = (int)(width * 0.2); // Match calculateResponsiveDimensions
        int sidebarMargin = (int)(width * 0.02); // Match calculateResponsiveDimensions
        int startY = y + (int)(height * 0.15); // 15% from top (matches sidebar start)
        int startX = x + sidebarWidth + sidebarMargin; // Start after sidebar with consistent margin
        int itemsRendered = 0;
        int maxItemsToRender = maxVisibleItems;
        
        for (int i = scrollOffset * itemsPerRow; i < itemsToRender.size() && itemsRendered < maxItemsToRender; i += itemsPerRow) {
            for (int j = 0; j < itemsPerRow && i + j < itemsToRender.size() && itemsRendered < maxItemsToRender; j++) {
                // Use the new ItemCardRenderer for proper GUI scaling
                FreeMarketItem item = itemsToRender.get(i + j);
                int itemX = startX + j * itemSpacing;
                int itemY = startY + (itemsRendered / itemsPerRow) * itemHeight;
                int cardHeight = (int)(itemHeight * 0.9); // Use 90% of item height for card (leaving margin)
                
                // Check if this is the special "add item" entry
                if (isAddItemEntry(item)) {
                    // Render special add item card with plus icon (no buy/sell buttons)
                    renderAddItemCard(guiGraphics, itemX, itemY, calculatedItemWidth, cardHeight, mouseX, mouseY);
                } else {
                    // Create item stack with the marketplace quantity for display
                    net.minecraft.world.item.ItemStack displayStack = createItemWithComponentData(item);
                    displayStack.setCount(item.getQuantity());
                    
                    // Render the modern item card using the new renderer with GUI scale and cooldown states
                    Minecraft client = Minecraft.getInstance();
                    float guiScale = (float) client.getWindow().getGuiScale();
                    
                    // Get button states
                    boolean canBuy = getCachedCanBuyState(item);
                    boolean canSell = getCachedCanSellState(item);
                    boolean isBuyCooldown = isBuyButtonInCooldown(item);
                    boolean isSellCooldown = isSellButtonInCooldown(item);
                    
                    itemCardRenderer.renderItemCard(guiGraphics, displayStack, itemX, itemY, 
                                                 calculatedItemWidth, cardHeight, 
                                                 mouseX, mouseY, guiScale,
                                                 canBuy, canSell, isBuyCooldown, isSellCooldown,
                                                 item.getBuyPrice(), item.getSellPrice());
                }
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
        // Use percentage-based sizing for sidebar with matching margins
        int sidebarWidth = (int)(width * 0.2); // 20% of container width
        int sidebarX = x + (int)(width * 0.02); // 2% margin from left (matches right margin)
        int sidebarY = y + (int)(height * 0.15); // 15% from top (below search box)
        int sidebarHeight = height - (int)(height * 0.2); // 80% of container height
        
        // Draw sidebar background (semi-transparent)
        guiGraphics.fill(sidebarX, sidebarY, sidebarX + sidebarWidth, sidebarY + sidebarHeight, 0x801A1A1A); // 50% opacity
        guiGraphics.fill(sidebarX + 1, sidebarY + 1, sidebarX + sidebarWidth - 1, sidebarY + sidebarHeight - 1, 0x802D2D2D); // 50% opacity
        
        // Draw sidebar title
        Component sidebarTitle = Component.literal("Categories");
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, sidebarTitle, sidebarX + GuiScalingHelper.responsiveWidth(5, 4, 8), sidebarY + GuiScalingHelper.responsiveHeight(5, 4, 8), 0xFFE0E0E0);
        
        // Get cached categories (filter out categories with zero items)
        List<ItemCategoryManager.Category> categories = getCachedCategories();
        
        int categoryY = sidebarY + GuiScalingHelper.responsiveHeight(20, 16, 28);
        int categoryHeight = GuiScalingHelper.responsiveHeight(16, 12, 22);
        
        for (int i = 0; i < categories.size(); i++) {
            ItemCategoryManager.Category category = categories.get(i);
            int currentCategoryY = categoryY + i * categoryHeight;
            
            boolean isSelected = category == selectedCategory;
            boolean isHovered = mouseX >= sidebarX && mouseX <= sidebarX + sidebarWidth &&
                               mouseY >= currentCategoryY && mouseY <= currentCategoryY + categoryHeight;
            
            // Update hover state
            
            // Draw category background (semi-transparent)
            if (isSelected) {
                guiGraphics.fill(sidebarX + GuiScalingHelper.responsiveWidth(2, 1, 3), currentCategoryY, sidebarX + sidebarWidth - GuiScalingHelper.responsiveWidth(2, 1, 3), currentCategoryY + categoryHeight, 0x804CAF50); // 50% opacity
                guiGraphics.fill(sidebarX + GuiScalingHelper.responsiveWidth(3, 2, 4), currentCategoryY + 1, sidebarX + sidebarWidth - GuiScalingHelper.responsiveWidth(3, 2, 4), currentCategoryY + categoryHeight - 1, 0x8066BB6A); // 50% opacity
            } else if (isHovered) {
                guiGraphics.fill(sidebarX + GuiScalingHelper.responsiveWidth(2, 1, 3), currentCategoryY, sidebarX + sidebarWidth - GuiScalingHelper.responsiveWidth(2, 1, 3), currentCategoryY + categoryHeight, 0x803A3A3A); // 50% opacity
                guiGraphics.fill(sidebarX + GuiScalingHelper.responsiveWidth(3, 2, 4), currentCategoryY + 1, sidebarX + sidebarWidth - GuiScalingHelper.responsiveWidth(3, 2, 4), currentCategoryY + categoryHeight - 1, 0x804A4A4A); // 50% opacity
            }
            
            // Draw category text (simple rendering)
            int textColor = isSelected ? 0xFFFFFFFF : 0xFFE0E0E0;
            String categoryText = category.getDisplayName();
            int count = cachedCategoryCounts.getOrDefault(category, 0);
            String displayText = categoryText + " (" + count + ")";
            
            // Truncate text if it's too long for the container
            int availableWidth = sidebarWidth - (int)(sidebarWidth * 0.1); // 10% total padding (5% each side)
            String truncatedText = truncateTextToWidth(displayText, availableWidth);
            
            guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, truncatedText, 
                sidebarX + (int)(sidebarWidth * 0.05), // 5% padding from sidebar edge
                currentCategoryY + (int)(categoryHeight * 0.2), textColor); // 20% from top of category
        }
    }
    
    /**
     * Truncates text to fit within the specified width, adding ellipsis if needed.
     * Examples: "Miscellaneous" -> "Miscellan..", "Tools" -> "Tools"
     */
    private String truncateTextToWidth(String text, int maxWidth) {
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
        long currentTime = System.currentTimeMillis();
        String currentSearchText = (searchBox != null) ? searchBox.getValue() : "";
        
        // Check if cache is valid
        if (cachedItemsToRender == null || 
            lastFilteredCategory != selectedCategory ||
            !currentSearchText.equals(lastSearchText) ||
            (currentTime - lastItemCacheUpdate) > ITEM_CACHE_DURATION) {
            
            // Update cache
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
            
            cachedItemsToRender = categoryFiltered;
            lastFilteredCategory = selectedCategory;
            lastSearchText = currentSearchText;
            lastItemCacheUpdate = currentTime;
        }
        
        return cachedItemsToRender;
    }
    
    
    
    /**
     * Renders the special "add item" card that looks like a marketplace item but with a big plus icon.
     */
    private void renderAddItemCard(GuiGraphics guiGraphics, int itemX, int itemY, int cardWidth, int cardHeight, int mouseX, int mouseY) {
        // Modern card background with gradient effect
        int backgroundColor = 0x801A1A1A; // 50% opacity
        int borderColor = 0x80404040; // 50% opacity
        
        // Draw card background
        guiGraphics.fill(itemX, itemY, itemX + cardWidth, itemY + cardHeight, backgroundColor);
        
        // Draw card border
        guiGraphics.fill(itemX, itemY, itemX + cardWidth, itemY + 2, borderColor); // Top
        guiGraphics.fill(itemX, itemY, itemX + 2, itemY + cardHeight, borderColor); // Left
        guiGraphics.fill(itemX + cardWidth - 2, itemY, itemX + cardWidth, itemY + cardHeight, borderColor); // Right
        guiGraphics.fill(itemX, itemY + cardHeight - 2, itemX + cardWidth, itemY + cardHeight, borderColor); // Bottom
        
        // Check if mouse is hovering over the add item card
        boolean isHovered = mouseX >= itemX && mouseX <= itemX + cardWidth &&
                           mouseY >= itemY && mouseY <= itemY + cardHeight;
        
        // Draw a big plus icon in the upper-center of the card
        int centerX = itemX + cardWidth / 2; // Center horizontally
        int centerY = itemY + cardHeight / 3; // Upper third of card (raised from center)
        int plusSize = Math.min(cardWidth, cardHeight) / 3; // Scale with card size
        int plusThickness = Math.max(2, plusSize / 10); // Thickness scales with size
        int plusColor = isHovered ? 0xFF4CAF50 : 0xFF66BB6A; // Green color, brighter on hover
        
        // Draw + lines (horizontal and vertical)
        guiGraphics.fill(centerX - plusSize/2, centerY - plusThickness, centerX + plusSize/2, centerY + plusThickness, plusColor);
        guiGraphics.fill(centerX - plusThickness, centerY - plusSize/2, centerX + plusThickness, centerY + plusSize/2, plusColor);
        
        // Draw "Add Item" text below the plus
        String addText = "Add Item";
        int textWidth = net.minecraft.client.Minecraft.getInstance().font.width(addText);
        int textX = itemX + (cardWidth - textWidth) / 2; // Center the text
        int textY = centerY + plusSize/2 + 10; // Below the plus
        int textColor = isHovered ? 0xFF4CAF50 : 0xFF66BB6A;
        
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, addText, textX, textY, textColor);
    }
    
    
    
    
    private void drawScrollBar(GuiGraphics guiGraphics) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) return; // No scrolling needed
        
        int scrollBarWidth = 8;
        int scrollBarX = x + width - scrollBarWidth - 2;
        int scrollBarY = y + 35;
        int scrollBarHeight = height - 50;
        
        // Draw scroll bar background (semi-transparent)
        guiGraphics.fill(scrollBarX, scrollBarY, scrollBarX + scrollBarWidth, scrollBarY + scrollBarHeight, 0x80000000); // 50% opacity
        
        // Calculate thumb position and size
        List<FreeMarketItem> itemsToRender = getItemsToRender();
        int thumbHeight = Math.max(20, (scrollBarHeight * scrollBarHeight) / (itemsToRender.size() * itemHeight / itemsPerRow + scrollBarHeight));
        int thumbY = scrollBarY + (scrollBarHeight - thumbHeight) * scrollOffset / maxScroll;
        
        // Draw scroll thumb (semi-transparent)
        guiGraphics.fill(scrollBarX + 1, thumbY, scrollBarX + scrollBarWidth - 1, thumbY + thumbHeight, 0x80808080); // 50% opacity
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
        // Use percentage-based sidebar dimensions for click detection (aligned with items)
        int sidebarWidth = (int)(width * 0.2); // 20% of container width
        int sidebarX = x + (int)(width * 0.02); // 2% margin from left (matches right margin)
        int sidebarY = y + (int)(height * 0.15); // 15% from top (matches items start)
        int sidebarHeight = height - (int)(height * 0.2); // 80% of container height
        
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
        // Use SAME calculations as rendering (lines 324-325) for consistency
        int sidebarMargin = (int)(width * 0.02); // Match calculateResponsiveDimensions
        int startY = y + (int)(height * 0.15); // 15% from top (matches sidebar start)
        int startX = x + sidebarWidth + sidebarMargin; // Start after sidebar with consistent margin
        int itemsRendered = 0;
        int maxItemsToRender = maxVisibleItems;
        List<FreeMarketItem> itemsToRender = getItemsToRender();
        
        for (int i = scrollOffset * itemsPerRow; i < itemsToRender.size() && itemsRendered < maxItemsToRender; i += itemsPerRow) {
            for (int j = 0; j < itemsPerRow && i + j < itemsToRender.size() && itemsRendered < maxItemsToRender; j++) {
                FreeMarketItem item = itemsToRender.get(i + j);
                int itemX = startX + j * itemSpacing;
                int itemY = startY + (itemsRendered / itemsPerRow) * itemHeight;
                int cardWidth = calculatedItemWidth;
                int cardHeight = (int)(itemHeight * 0.9); // Use 90% of item height for card (leaving margin)
                
                // Check if this is the add item entry
                if (isAddItemEntry(item)) {
                    // Handle click on add item card (use same dimensions as rendering)
                    if (mouseX >= itemX && mouseX <= itemX + cardWidth &&
                        mouseY >= itemY && mouseY <= itemY + cardHeight) {
                        // Open add item popup
                        if (parentScreen != null) {
                            net.minecraft.client.Minecraft.getInstance().setScreen(new AddItemPopupScreen(parentScreen));
                        }
                        return true;
                    }
                    // Skip buy/sell button checks for add item entry - continue to next item
                    itemsRendered++;
                    continue;
                }
                
                // Regular item card - check delete button and buy/sell buttons
                {
                    // Check delete button click (only if admin mode) - match ItemCardRenderer dimensions
                    if (AdminModeHandler.isAdminMode()) {
                        int deleteButtonSize = (int)(cardWidth * 0.12); // 12% of card width (match ItemCardRenderer)
                        int margin = 0; // No margin - match ItemCardRenderer
                        int deleteButtonX = itemX + cardWidth - deleteButtonSize - margin; // Right at the edge
                        int deleteButtonY = itemY + margin; // Top at the edge
                        
                        if (mouseX >= deleteButtonX && mouseX <= deleteButtonX + deleteButtonSize &&
                            mouseY >= deleteButtonY && mouseY <= deleteButtonY + deleteButtonSize) {
                            // Play note block sound for delete action
                            var player = Minecraft.getInstance().player;
                            if (player != null) {
                                player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.5f);
                            }
                            
                            // Send delete request to server via network packet
                            MarketplaceItemOperationPacket packet = MarketplaceItemOperationPacket.removeItem(item);
                            net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
                            
                            return true;
                        }
                    }
                }
                

                // Use the new ItemCardRenderer for buy button click detection
                // Use raw mouse coordinates like the highlighting does (which works correctly)
                Minecraft client = Minecraft.getInstance();
                float guiScale = (float) client.getWindow().getGuiScale();
                
                if (ItemCardRenderer.isBuyButtonClicked(itemX, itemY, cardWidth, cardHeight, (int)mouseX, (int)mouseY, guiScale, item.getBuyPrice())) {
                    // Check if button is enabled before processing
                    if (!getCachedCanBuyState(item)) {
                        return true; // Consume click but don't process - button is disabled
                    }
                    
                    // Check cooldown before processing
                    if (isBuyButtonInCooldown(item)) {
                        return true; // Consume click but don't process
                    }
                    
                    // Set cooldown immediately to prevent spam clicking
                    long currentTime = System.currentTimeMillis();
                    buyButtonCooldowns.put(item.getGuid(), currentTime + BUY_COOLDOWN_MS);
                    
                    // Send buy request to server via network packet
                    com.freemarket.common.network.BuyItemRequestPacket packet = new com.freemarket.common.network.BuyItemRequestPacket(item.getGuid());
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
                    
                    // Update button states after buy operation
                    updateButtonStates();
                    
                    return true; // Consume the click
                }
                
                // Use the new ItemCardRenderer for sell button click detection
                if (ItemCardRenderer.isSellButtonClicked(itemX, itemY, cardWidth, cardHeight, (int)mouseX, (int)mouseY, guiScale, item.getSellPrice())) {
                    // Check if button is enabled before processing
                    if (!getCachedCanSellState(item)) {
                        return true; // Consume click but don't process - button is disabled
                    }
                    
                    // Check cooldown before processing
                    if (isSellButtonInCooldown(item)) {
                        return true; // Consume click but don't process
                    }
                    
                    // Set cooldown immediately to prevent spam clicking
                    long currentTime = System.currentTimeMillis();
                    sellButtonCooldowns.put(item.getGuid(), currentTime + SELL_COOLDOWN_MS);
                    
                    // Send sell request to server via network packet
                    com.freemarket.common.network.SellItemRequestPacket packet = new com.freemarket.common.network.SellItemRequestPacket(item.getGuid());
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
                    
                    // Update button states after sell operation
                    updateButtonStates();
                    
                    return true; // Consume the click
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
     * Creates an ItemStack with component data applied from the marketplace item.
     * Always applies component data fresh for rendering to ensure visual effects work.
     */
    private ItemStack createItemWithComponentData(FreeMarketItem item) {
        ItemStack baseItemStack = item.getItemStack().copy();
        
        // Apply component data if present
        String componentData = item.getComponentData();
        
        if (componentData != null && !componentData.trim().isEmpty() && !componentData.equals("{}")) {
            // Try to use server-side processing for proper registry access
            Minecraft minecraft = Minecraft.getInstance();
            var singleplayerServer = minecraft.getSingleplayerServer();
            
            if (singleplayerServer != null) {
                // Use server-side handler with registry access
                return com.freemarket.server.handlers.ServerItemHandler.createItemWithComponentData(
                    baseItemStack, componentData, singleplayerServer);
            } else {
                // Fallback to client-side processing
                ItemComponentHandler.applyComponentData(baseItemStack, componentData);
                return baseItemStack;
            }
        }
        
        return baseItemStack;
    }
    
    /**
     * Gets the cached can buy state for an item.
     * Only updates when explicitly requested via updateButtonStates().
     */
    private boolean getCachedCanBuyState(FreeMarketItem item) {
        String itemGuid = item.getGuid();
        return cachedCanBuyStates.computeIfAbsent(itemGuid, guid -> canBuyItem(item));
    }
    
    /**
     * Gets the cached can sell state for an item.
     * Only updates when explicitly requested via updateButtonStates().
     */
    private boolean getCachedCanSellState(FreeMarketItem item) {
        String itemGuid = item.getGuid();
        return cachedCanSellStates.computeIfAbsent(itemGuid, guid -> canSellItem(item));
    }
    
    /**
     * Updates all button states. Should only be called when:
     * - GUI opens
     * - After buy/sell operations
     * - When wallet balance changes significantly
     */
    public void updateButtonStates() {
        cachedCanBuyStates.clear();
        cachedCanSellStates.clear();
        
        // Pre-calculate states for all items
        if (allItems != null) {
            for (FreeMarketItem item : allItems) {
                cachedCanBuyStates.put(item.getGuid(), canBuyItem(item));
                cachedCanSellStates.put(item.getGuid(), canSellItem(item));
            }
        }
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
        
        // Check if player has the item in inventory
        var inventory = playerForCheck.getInventory();
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
     * Checks if the player can buy the specified item (has enough money and buy price > 0).
     */
    private boolean canBuyItem(FreeMarketItem item) {
        // First check if buy price is greater than 0
        if (item.getBuyPrice() <= 0) {
            return false;
        }
        
        // Use the GUI's cached balance instead of calling ClientWalletHandler directly
        if (parentScreen != null) {
            long cachedBalance = parentScreen.getCachedBalance();
            return cachedBalance >= item.getBuyPrice();
        }
        
        // Fallback to ClientWalletHandler if no parent screen
        return ClientWalletHandler.hasEnoughMoney(item.getBuyPrice());
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
    
    /**
     * Gets cached categories, updating cache if needed
     */
    private List<ItemCategoryManager.Category> getCachedCategories() {
        long currentTime = System.currentTimeMillis();
        
        // Check if cache is valid
        if (cachedCategories == null || cachedCategoryCounts == null || 
            (currentTime - lastCategoryCacheUpdate) > CATEGORY_CACHE_DURATION) {
            
            // Update cache
            List<ItemCategoryManager.Category> allCategories = ItemCategoryManager.getAllCategories();
            cachedCategoryCounts = ItemCategoryManager.getCategoryCounts(allItems);
            
            // Filter out categories with zero items
            cachedCategories = allCategories.stream()
                .filter(category -> cachedCategoryCounts.getOrDefault(category, 0) > 0)
                .collect(java.util.stream.Collectors.toList());
            
            lastCategoryCacheUpdate = currentTime;
        }
        
        return cachedCategories;
    }
}

