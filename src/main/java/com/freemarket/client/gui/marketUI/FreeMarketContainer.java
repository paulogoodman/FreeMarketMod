package com.freemarket.client.gui.marketUI;

import com.freemarket.FreeMarket;
import com.freemarket.client.handlers.ClientWalletHandler;
import com.freemarket.client.gui.commonUI.BaseGridContainer;
import com.freemarket.client.gui.commonUI.ButtonType;
import com.freemarket.client.gui.commonUI.CardButtonConfig;
import com.freemarket.client.gui.commonUI.FreeMarketGuiScreen;
import com.freemarket.client.gui.commonUI.GuiScalingHelper;
import com.freemarket.common.attachments.ItemComponentHandler;
import com.freemarket.common.data.FreeMarketItem;
import com.freemarket.common.handlers.AdminModeHandler;
import com.freemarket.common.managers.ItemCategoryManager;
import com.freemarket.common.network.FreeMarketPacket;
import com.freemarket.common.network.PacketType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A scrollable container for displaying free market items with search functionality.
 */
public class FreeMarketContainer extends BaseGridContainer<FreeMarketItem> {
    
    private List<FreeMarketItem> allItems;
    
    // Caching for processed items with component data
    private final Map<String, ItemStack> processedItemCache = new HashMap<>();
    
    // Caching for button states to prevent flickering
    private final Map<String, Boolean> cachedCanBuyStates = new HashMap<>();
    private final Map<String, Boolean> cachedCanSellStates = new HashMap<>();
    
    // Unified card renderer for proper GUI scaling
    private final UnifiedItemCardRenderer unifiedRenderer = new UnifiedItemCardRenderer();
    
    // Buy button state tracking - per item
    private final java.util.Map<String, Long> buyButtonCooldowns = new java.util.HashMap<>();
    private static final long BUY_COOLDOWN_MS = 250; // 250ms cooldown
    
    // Sell button state tracking - per item
    private final java.util.Map<String, Long> sellButtonCooldowns = new java.util.HashMap<>();
    private static final long SELL_COOLDOWN_MS = 250; // 250ms cooldown

    // Cached inventory counts (per tick) to avoid repeated shulker parsing
    private Map<ItemSignature, Integer> inventoryCountCache = new HashMap<>();
    
    public FreeMarketContainer(int x, int y, int width, int height, List<FreeMarketItem> items, FreeMarketGuiScreen parentScreen) {
        super(x, y, width, height, parentScreen);
        this.allItems = new ArrayList<>(items);
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
        // Invalidate caches to ensure new data (including order) is used
        invalidateDataCache();
        clearProcessedItemCache();
        updateButtonStates(); // Update button states when items change
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
    
    
    
    
    public void addItem(FreeMarketItem item) {
        allItems.add(item);
        invalidateDataCache();
    }
    
    public void removeItem(FreeMarketItem item) {
        allItems.remove(item);
        invalidateDataCache();
    }
    
    public void updateItems(List<FreeMarketItem> newItems) {
        allItems.clear();
        allItems.addAll(newItems);
        // Clear cache when items are updated
        clearProcessedItemCache();
        updateButtonStates(); // Update button states when items change
        invalidateDataCache();
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
        guiGraphics.fill(x, y + 2, x + 2, y + height - 2, 0x80404040); // 50% opacity
        guiGraphics.fill(x + width - 2, y + 2, x + width, y + height - 2, 0x80404040); // 50% opacity
        guiGraphics.fill(x, y + height - 2, x + width, y + height, 0x80404040); // 50% opacity
        
        // Render search box
        if (searchBox != null) {
            searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        
        // Draw Add Item button (top-right) - only visible in admin mode
        if (com.freemarket.common.handlers.AdminModeHandler.isAdminMode() && (searchBox == null || searchBox.getValue().isEmpty())) {
            renderAddItemButton(guiGraphics, mouseX, mouseY);
        }
        
        // Draw category sidebar
        renderCategorySidebar(guiGraphics, mouseX, mouseY);
        
        // Get items to render based on selected category and search
        List<FreeMarketItem> itemsToRender = getItemsToRender();
        
        // Render the data grid
        renderDataGrid(guiGraphics, itemsToRender, mouseX, mouseY, partialTick);
        
        // Draw scroll bar
        drawScrollBar(guiGraphics);
        
        // Draw item count
        int actualItemCount = itemsToRender.size();
        Component countText = Component.translatable("gui.FreeMarket.marketplace.count", actualItemCount, allItems.size());
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, countText, x + GuiScalingHelper.responsiveWidth(10, 8, 15), y + height - GuiScalingHelper.responsiveHeight(15, 12, 20), 0xCCCCCC);
    }
    
    // Abstract method implementations
    
    @Override
    protected Component getSearchPlaceholder() {
        return Component.translatable("gui.FreeMarket.marketplace.search_placeholder");
    }
    
    @Override
    protected List<FreeMarketItem> getAllData() {
        // Return all items without sorting - sorting happens after filtering in getFilteredData()
        return new ArrayList<>(allItems);
    }
    
    /**
     * Orders a list of items by order attribute first, then alphabetically.
     * This is called after filtering to ensure correct display order.
     */
    private List<FreeMarketItem> orderItems(List<FreeMarketItem> items) {
        if (items.isEmpty()) {
            return items;
        }
        
        // Cache extracted item names to avoid repeated extraction during sorting
        java.util.Map<FreeMarketItem, String> nameCache = new java.util.HashMap<>(items.size());
        for (FreeMarketItem item : items) {
            String itemId = item.getItemStack().getItem().toString();
            nameCache.put(item, extractItemName(itemId));
        }
        
        // Create a new list to avoid modifying the input
        List<FreeMarketItem> ordered = new ArrayList<>(items);
        
        // Sort by order attribute first (lower numbers appear first)
        // If order is the same, sort alphabetically by item name
        ordered.sort((a, b) -> {
            // First compare by order (fast integer comparison)
            int orderA = a.getOrder();
            int orderB = b.getOrder();
            int orderCompare = Integer.compare(orderA, orderB);
            if (orderCompare != 0) {
                return orderCompare;
            }
            // If order is the same, compare alphabetically by item name (use cached names)
            String nameA = nameCache.get(a);
            String nameB = nameCache.get(b);
            if (nameA == null) nameA = "";
            if (nameB == null) nameB = "";
            return nameA.compareToIgnoreCase(nameB);
        });
        
        return ordered;
    }
    
    /**
     * Extracts the item name from an item ID (e.g., "minecraft:dirt" -> "dirt").
     * Returns the full ID if no colon is found.
     * Optimized to minimize string allocations.
     */
    private String extractItemName(String itemId) {
        int colonIndex = itemId.indexOf(':');
        if (colonIndex >= 0 && colonIndex < itemId.length() - 1) {
            return itemId.substring(colonIndex + 1);
        }
        return itemId;
    }
    
    @Override
    protected List<FreeMarketItem> getFilteredData() {
        long currentTime = System.currentTimeMillis();
        String currentSearchText = searchBox != null ? searchBox.getValue() : "";
        
        // Check if cache is valid
        if (cachedFilteredData == null || 
            lastFilteredCategory != selectedCategory ||
            !currentSearchText.equals(lastSearchText) ||
            (currentTime - lastDataCacheUpdate) > DATA_CACHE_DURATION) {
            
            // First filter by category
            List<FreeMarketItem> categoryFiltered = filterByCategory(getAllData(), selectedCategory);
            
            // Then filter by search text
            if (!currentSearchText.isEmpty()) {
                categoryFiltered = filterBySearch(categoryFiltered, currentSearchText);
            }
            
            // Finally, ORDER the filtered results by order attribute, then alphabetically
            // This ensures items are displayed in the correct order within each category/search result
            categoryFiltered = orderItems(categoryFiltered);
            
            cachedFilteredData = categoryFiltered;
            lastFilteredCategory = selectedCategory;
            lastSearchText = currentSearchText;
            lastDataCacheUpdate = currentTime;
        }
        
        return cachedFilteredData;
    }
    
    @Override
    protected List<FreeMarketItem> filterByCategory(List<FreeMarketItem> data, ItemCategoryManager.Category category) {
        return ItemCategoryManager.filterItemsByCategory(data, category);
    }
    
    @Override
    protected List<FreeMarketItem> filterBySearch(List<FreeMarketItem> data, String searchText) {
        if (searchText.isEmpty()) {
            return data;
        }
        String searchLower = searchText.toLowerCase();
        // Use Collectors.toList() to ensure order is preserved from the input stream
        return data.stream()
            .filter(item -> item.getItemName().toLowerCase().contains(searchLower))
            .collect(java.util.stream.Collectors.toList());
    }
    
    @Override
    protected Map<ItemCategoryManager.Category, Integer> getCategoryCounts() {
        // Use getAllData() to ensure we're counting sorted items
        return ItemCategoryManager.getCategoryCounts(getAllData());
    }
    
    @Override
    protected ItemCategoryManager.Category getItemCategory(FreeMarketItem item) {
        return ItemCategoryManager.getCategoryForItem(item.getItemStack());
    }
    
    @Override
    protected void renderDataGrid(GuiGraphics guiGraphics, List<FreeMarketItem> itemsToRender, int mouseX, int mouseY, float partialTick) {
        // Use getItemsToRender to include admin add item
        itemsToRender = getItemsToRender();
        // Draw items with percentage-based positioning (aligned with sidebar)
        int sidebarWidth = (int)(width * 0.2); // Match calculateResponsiveDimensions
        int sidebarMargin = (int)(width * 0.02); // Match calculateResponsiveDimensions
        int startY = y + (int)(height * 0.15); // 15% from top (matches sidebar start)
        int startX = x + sidebarWidth + sidebarMargin; // Start after sidebar with consistent margin
        int itemsRendered = 0;
        int maxItemsToRender = maxVisibleItems;
        
        // Track tooltip to render last (after all cards)
        ItemStack tooltipStack = null;
        
        for (int i = scrollOffset * itemsPerRow; i < itemsToRender.size() && itemsRendered < maxItemsToRender; i += itemsPerRow) {
            for (int j = 0; j < itemsPerRow && i + j < itemsToRender.size() && itemsRendered < maxItemsToRender; j++) {
                // Use the new ItemCardRenderer for proper GUI scaling
                FreeMarketItem item = itemsToRender.get(i + j);
                int itemX = startX + j * itemSpacing;
                int itemY = startY + (itemsRendered / itemsPerRow) * itemHeight;
                int cardHeight = (int)(itemHeight * 0.9); // Use 90% of item height for card (leaving margin)
                
                // Create item stack with the marketplace stack size for display
                net.minecraft.world.item.ItemStack displayStack = createItemWithComponentData(item);
                displayStack.setCount(item.getStackSize());
                
                // Render using unified renderer with GUI scale and cooldown states
                Minecraft client = Minecraft.getInstance();
                float guiScale = (float) client.getWindow().getGuiScale();
                
                // Create button config for marketplace
                CardButtonConfig config = CardButtonConfig.forMarketplace(
                    item.getBuyPrice(), item.getSellPrice(),
                    getCachedCanBuyState(item), getCachedCanSellState(item),
                    isBuyButtonInCooldown(item), isSellButtonInCooldown(item)
                );
                
                ItemStack cardTooltip = unifiedRenderer.renderCard(guiGraphics, displayStack, config, null,
                                          itemX, itemY, calculatedItemWidth, cardHeight, 
                                          mouseX, mouseY, guiScale, 
                                          parentScreen != null && parentScreen.isAnyPopupVisible());
                
                // Collect tooltip for deferred rendering
                if (cardTooltip != null) {
                    tooltipStack = cardTooltip;
                }
                
                itemsRendered++;
            }
        }
        
        // Render tooltip AFTER all cards (so it appears on top)
        if (tooltipStack != null) {
            UnifiedItemCardRenderer.renderItemTooltip(guiGraphics, tooltipStack, mouseX, mouseY);
        }
    }
    
    @Override
    protected boolean handleDataClick(FreeMarketItem item, double mouseX, double mouseY, int button) {
        // Handle edit button clicks on items
        // Use SAME calculations as rendering (lines 324-325) for consistency
        int sidebarMargin = (int)(width * 0.02); // Match calculateResponsiveDimensions
        int sidebarWidth = (int)(width * 0.2); // 20% of container width
        int startY = y + (int)(height * 0.15); // 15% from top (matches sidebar start)
        int startX = x + sidebarWidth + sidebarMargin; // Start after sidebar with consistent margin
        int itemsRendered = 0;
        int maxItemsToRender = maxVisibleItems;
        List<FreeMarketItem> itemsToRender = getItemsToRender();
        
        for (int i = scrollOffset * itemsPerRow; i < itemsToRender.size() && itemsRendered < maxItemsToRender; i += itemsPerRow) {
            for (int j = 0; j < itemsPerRow && i + j < itemsToRender.size() && itemsRendered < maxItemsToRender; j++) {
                FreeMarketItem currentItem = itemsToRender.get(i + j);
                int itemX = startX + j * itemSpacing;
                int itemY = startY + (itemsRendered / itemsPerRow) * itemHeight;
                int cardWidth = calculatedItemWidth;
                int cardHeight = (int)(itemHeight * 0.9); // Use 90% of item height for card (leaving margin)
                
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
                            FreeMarketPacket packet = FreeMarketPacket.withString(PacketType.MARKETPLACE_REMOVE_ITEM, currentItem.getMarketListingId());
                            net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
                            
                            return true;
                        }
                    }
                }
                

                // Use unified renderer for button click detection
                Minecraft client = Minecraft.getInstance();
                float guiScale = (float) client.getWindow().getGuiScale();
                
                // Create button config for click detection
                CardButtonConfig config = CardButtonConfig.forMarketplace(
                    currentItem.getBuyPrice(), currentItem.getSellPrice(),
                    getCachedCanBuyState(currentItem), getCachedCanSellState(currentItem),
                    isBuyButtonInCooldown(currentItem), isSellButtonInCooldown(currentItem)
                );
                
                ButtonType buttonClicked = UnifiedItemCardRenderer.checkButtonClick(
                    itemX, itemY, cardWidth, cardHeight, 
                    (int)mouseX, (int)mouseY, guiScale, config
                );
                
                if (buttonClicked == ButtonType.BUY) {
                    // Check if button is enabled before processing
                    if (!getCachedCanBuyState(currentItem)) {
                        return true; // Consume click but don't process - button is disabled
                    }
                    
                    // Check cooldown before processing
                    if (isBuyButtonInCooldown(currentItem)) {
                        return true; // Consume click but don't process
                    }
                    
                    if (parentScreen != null) {
                        parentScreen.showBuyConfirmationPopup(currentItem, this);
                    }
                    return true; // Consume the click
                } else if (buttonClicked == ButtonType.SELL) {
                    // Check if button is enabled before processing
                    if (!getCachedCanSellState(currentItem)) {
                        return true; // Consume click but don't process - button is disabled
                    }
                    
                    // Check cooldown before processing
                    if (isSellButtonInCooldown(currentItem)) {
                        return true; // Consume click but don't process
                    }
                    
                    if (parentScreen != null) {
                        parentScreen.showSellConfirmationPopup(currentItem, this);
                    }
                    return true; // Consume the click
                }
                
                itemsRendered++;
            }
        }
        
        return false;
    }
    
    private List<FreeMarketItem> getItemsToRender() {
        // Use the unified filtering + ordering pipeline
        return getFilteredData();
    }
    
    
    
    /**
     * Renders the Add Item button in the top-right of the container.
     */
    private void renderAddItemButton(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int buttonWidth = 120;
        int buttonHeight = 20;
        int buttonX = x + width - buttonWidth - 10;
        int buttonY = y + 10;
        
        boolean isHovered = mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                           mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        
        // Button background
        int bgColor = isHovered ? 0xCC4CAF50 : 0x994CAF50; // Green
        guiGraphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, bgColor);
        
        // Button border
        guiGraphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + 1, 0xFF404040);
        guiGraphics.fill(buttonX, buttonY + 1, buttonX + 1, buttonY + buttonHeight - 1, 0xFF404040);
        guiGraphics.fill(buttonX + buttonWidth - 1, buttonY + 1, buttonX + buttonWidth, buttonY + buttonHeight - 1, 0xFF404040);
        guiGraphics.fill(buttonX, buttonY + buttonHeight - 1, buttonX + buttonWidth, buttonY + buttonHeight, 0xFF404040);
        
        // Button text
        Minecraft minecraft = Minecraft.getInstance();
        String buttonText = "+ Add Item";
        int textWidth = minecraft.font.width(buttonText);
        int textX = buttonX + (buttonWidth - textWidth) / 2;
        int textY = buttonY + (buttonHeight - minecraft.font.lineHeight) / 2;
        guiGraphics.drawString(minecraft.font, buttonText, textX, textY, 0xFFFFFFFF);
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
    
    protected int getMaxScroll() {
        List<FreeMarketItem> itemsToRender = getItemsToRender();
        return Math.max(0, (itemsToRender.size() + itemsPerRow - 1) / itemsPerRow - maxVisibleItems / itemsPerRow);
    }
    
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Block all clicks if a popup is visible (except search box to allow unfocusing)
        boolean popupVisible = parentScreen != null && parentScreen.isAnyPopupVisible();
        
        // Handle search box clicks first (always allow to enable unfocusing)
        if (handleSearchBoxClick(mouseX, mouseY, button)) {
                return true;
        }
        
        // Block remaining clicks if popup is visible
        if (popupVisible) {
            return false; // Don't consume - let popup handle it
        }
        
        // Handle Add Item button clicks (top-right) - only in admin mode
        if (AdminModeHandler.isAdminMode() && (searchBox == null || searchBox.getValue().isEmpty())) {
            int buttonWidth = 120;
            int buttonHeight = 20;
            int buttonX = x + width - buttonWidth - 10;
            int buttonY = y + 10;
            
            if (mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
                // Play click sound
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                }
                // Open add item popup
                if (parentScreen != null) {
                    net.minecraft.client.Minecraft.getInstance().setScreen(new AddItemPopupScreen(parentScreen));
                }
                return true;
            }
        }
        
        // Handle category sidebar clicks
        if (handleCategoryClick(mouseX, mouseY)) {
                    return true;
        }
        
        // Handle data clicks
        List<FreeMarketItem> itemsToRender = getItemsToRender();
        for (FreeMarketItem item : itemsToRender) {
            if (handleDataClick(item, mouseX, mouseY, button)) {
                return true;
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
        String itemMarketListingId = item.getMarketListingId();
        return cachedCanBuyStates.computeIfAbsent(itemMarketListingId, marketListingId -> canBuyItem(item));
    }
    
    /**
     * Gets the cached can sell state for an item.
     * Only updates when explicitly requested via updateButtonStates().
     */
    private boolean getCachedCanSellState(FreeMarketItem item) {
        String itemMarketListingId = item.getMarketListingId();
        return cachedCanSellStates.computeIfAbsent(itemMarketListingId, marketListingId -> canSellItem(item));
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
        refreshInventorySnapshot();
        
        // Pre-calculate states for all items
        if (allItems != null) {
            for (FreeMarketItem item : allItems) {
                cachedCanBuyStates.put(item.getMarketListingId(), canBuyItem(item));
                cachedCanSellStates.put(item.getMarketListingId(), canSellItem(item));
            }
        }
    }

    /**
     * Calculates the maximum number of marketplace orders the player can buy for the given item.
     */
    public int calculateMaxBuyable(FreeMarketItem item) {
        if (item == null) {
            return 0;
        }

        long pricePerOrder = item.getBuyPrice();
        if (pricePerOrder <= 0) {
            return 0;
        }

        long balance = parentScreen != null
            ? parentScreen.getCachedBalance()
            : ClientWalletHandler.getPlayerMoney();

        if (balance <= 0) {
            return 0;
        }

        long maxOrders = balance / pricePerOrder;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, maxOrders));
    }

    /**
     * Counts how many matching items the player currently has in their inventory.
     */
    public int getPlayerInventoryCount(FreeMarketItem item) {
        if (item == null) {
            return 0;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player playerForCheck = resolvePlayerForInventoryChecks(minecraft);
        if (playerForCheck == null) {
            return 0;
        }

        ItemStack itemToCheck = item.getItemStack().copy();

        String componentData = item.getComponentData();
        if (componentData != null && !componentData.trim().isEmpty() && !componentData.equals("{}")) {
            var singleplayerServer = minecraft.getSingleplayerServer();
            if (singleplayerServer != null) {
                itemToCheck = com.freemarket.server.handlers.ServerItemHandler.createItemWithComponentData(
                    itemToCheck, componentData, singleplayerServer);
            } else {
                ItemComponentHandler.applyComponentData(itemToCheck, componentData);
            }
        }

        Map<ItemSignature, Integer> counts = getInventoryCounts();
        return counts.getOrDefault(ItemSignature.of(itemToCheck), 0);
    }
    
    /**
     * Checks if an item stack is a shulker box (client-side).
     */
    private boolean isShulkerBox(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(net.minecraft.world.item.Items.SHULKER_BOX) || 
               stack.is(net.minecraft.world.item.Items.WHITE_SHULKER_BOX) || 
               stack.is(net.minecraft.world.item.Items.ORANGE_SHULKER_BOX) ||
               stack.is(net.minecraft.world.item.Items.MAGENTA_SHULKER_BOX) || 
               stack.is(net.minecraft.world.item.Items.LIGHT_BLUE_SHULKER_BOX) ||
               stack.is(net.minecraft.world.item.Items.YELLOW_SHULKER_BOX) || 
               stack.is(net.minecraft.world.item.Items.LIME_SHULKER_BOX) ||
               stack.is(net.minecraft.world.item.Items.PINK_SHULKER_BOX) || 
               stack.is(net.minecraft.world.item.Items.GRAY_SHULKER_BOX) ||
               stack.is(net.minecraft.world.item.Items.LIGHT_GRAY_SHULKER_BOX) || 
               stack.is(net.minecraft.world.item.Items.CYAN_SHULKER_BOX) ||
               stack.is(net.minecraft.world.item.Items.PURPLE_SHULKER_BOX) || 
               stack.is(net.minecraft.world.item.Items.BLUE_SHULKER_BOX) ||
               stack.is(net.minecraft.world.item.Items.BROWN_SHULKER_BOX) || 
               stack.is(net.minecraft.world.item.Items.GREEN_SHULKER_BOX) ||
               stack.is(net.minecraft.world.item.Items.RED_SHULKER_BOX) || 
               stack.is(net.minecraft.world.item.Items.BLACK_SHULKER_BOX);
    }
    
    private Map<ItemSignature, Integer> getInventoryCounts() {
        if (inventoryCountCache.isEmpty()) {
            refreshInventorySnapshot();
        }
        return inventoryCountCache;
    }
    
    /**
     * Refreshes the cached inventory snapshot.
     * Should be called when opening market/auction or before confirmations.
     */
    public void refreshInventorySnapshot() {
        Minecraft minecraft = Minecraft.getInstance();
        Player playerForCheck = resolvePlayerForInventoryChecks(minecraft);
        if (playerForCheck == null) {
            inventoryCountCache = new HashMap<>();
            return;
        }
        inventoryCountCache = buildInventoryCountMap(playerForCheck, minecraft);
    }
    
    private Player resolvePlayerForInventoryChecks(Minecraft minecraft) {
        Player clientPlayer = minecraft.player;
        if (clientPlayer == null) {
            return null;
        }
        var singleplayerServer = minecraft.getSingleplayerServer();
        if (singleplayerServer != null) {
            var serverPlayer = singleplayerServer.getPlayerList().getPlayer(clientPlayer.getUUID());
            if (serverPlayer != null) {
                return serverPlayer;
            }
        }
        return clientPlayer;
    }
    
    private Map<ItemSignature, Integer> buildInventoryCountMap(Player player, Minecraft minecraft) {
        Map<ItemSignature, Integer> counts = new HashMap<>();
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (slotItem.isEmpty()) {
                continue;
            }
            addStackToCounts(counts, slotItem);
            if (isShulkerBox(slotItem)) {
                addShulkerContentsToCounts(counts, slotItem, minecraft);
            }
        }
        return counts;
    }
    
    private void addStackToCounts(Map<ItemSignature, Integer> counts, ItemStack stack) {
        ItemSignature key = ItemSignature.of(stack);
        counts.merge(key, stack.getCount(), Integer::sum);
    }
    
    private void addShulkerContentsToCounts(Map<ItemSignature, Integer> counts, ItemStack shulkerBox, Minecraft minecraft) {
        List<ItemStack> contents = readShulkerContents(shulkerBox, minecraft);
        for (ItemStack content : contents) {
            if (!content.isEmpty()) {
                addStackToCounts(counts, content);
            }
        }
    }
    
    private List<ItemStack> readShulkerContents(ItemStack shulkerBox, Minecraft minecraft) {
        Optional<List<ItemStack>> fromComponent = readFromContainerComponent(shulkerBox);
        if (fromComponent.isPresent()) {
            return fromComponent.get();
        }
        return readFromLegacyBlockEntity(shulkerBox, minecraft);
    }
    
    private Optional<List<ItemStack>> readFromContainerComponent(ItemStack shulkerBox) {
        ItemContainerContents containerContents = shulkerBox.get(DataComponents.CONTAINER);
        if (containerContents == null) {
            return Optional.empty();
        }
        NonNullList<ItemStack> temp = NonNullList.withSize(27, ItemStack.EMPTY);
        containerContents.copyInto(temp);
        List<ItemStack> decodedItems = new ArrayList<>();
        temp.stream().filter(stack -> !stack.isEmpty()).forEach(stack -> decodedItems.add(stack.copy()));
        if (FreeMarket.LOGGER.isDebugEnabled()) {
            FreeMarket.LOGGER.debug("Client snapshot read {} items from shulker via container component", decodedItems.size());
        }
        return Optional.of(decodedItems);
    }
    
    private List<ItemStack> readFromLegacyBlockEntity(ItemStack shulkerBox, Minecraft minecraft) {
        List<ItemStack> contents = new ArrayList<>();
        if (!shulkerBox.has(DataComponents.BLOCK_ENTITY_DATA)) {
            return contents;
        }
        try {
            var blockEntityData = shulkerBox.get(DataComponents.BLOCK_ENTITY_DATA);
            if (blockEntityData == null) {
                return contents;
            }
            var tag = blockEntityData.copyTag();
            if (tag == null || !tag.contains("Items", 9)) {
                return contents;
            }
            var itemsList = tag.getList("Items", 10);
            net.minecraft.core.RegistryAccess registryAccess = getRegistryAccess(minecraft);
            if (registryAccess == null) {
                return contents;
            }
            for (int i = 0; i < itemsList.size(); i++) {
                var itemTag = itemsList.getCompound(i);
                ItemStack shulkerItem = ItemStack.parseOptional(registryAccess, itemTag);
                if (shulkerItem != null && !shulkerItem.isEmpty()) {
                    contents.add(shulkerItem);
                }
            }
            if (FreeMarket.LOGGER.isDebugEnabled()) {
                FreeMarket.LOGGER.debug("Client snapshot read {} items from shulker via legacy block entity data", contents.size());
            }
        } catch (Exception e) {
            FreeMarket.LOGGER.warn("Failed to read shulker box contents on client", e);
        }
        return contents;
    }
    
    private net.minecraft.core.RegistryAccess getRegistryAccess(Minecraft minecraft) {
        var singleplayerServer = minecraft.getSingleplayerServer();
        if (singleplayerServer != null) {
            return singleplayerServer.registryAccess();
        }
        if (minecraft.level != null) {
            return minecraft.level.registryAccess();
        }
        return null;
    }

    /**
     * Starts the cooldown timer for the buy button of the specified item.
     */
    public void startBuyCooldown(FreeMarketItem item) {
        if (item == null) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        buyButtonCooldowns.put(item.getMarketListingId(), currentTime + BUY_COOLDOWN_MS);
    }

    /**
     * Starts the cooldown timer for the sell button of the specified item.
     */
    public void startSellCooldown(FreeMarketItem item) {
        if (item == null) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        sellButtonCooldowns.put(item.getMarketListingId(), currentTime + SELL_COOLDOWN_MS);
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
        Long cooldownEnd = buyButtonCooldowns.get(item.getMarketListingId());
        return cooldownEnd != null && currentTime < cooldownEnd;
    }
    
    
    /**
     * Checks if sell button is in cooldown for an item.
     */
    private boolean isSellButtonInCooldown(FreeMarketItem item) {
        long currentTime = System.currentTimeMillis();
        Long cooldownEnd = sellButtonCooldowns.get(item.getMarketListingId());
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
        
        int perOrder = Math.max(1, item.getStackSize());
        int totalCount = getPlayerInventoryCount(item);
        return totalCount >= perOrder;
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
    
    /**
     * Signature for identifying unique item stacks (item + components) irrespective of count.
     */
    private static final class ItemSignature {
        private final ItemStack stack;
        private final ResourceLocation itemId;
        
        private ItemSignature(ItemStack stack, ResourceLocation itemId) {
            this.stack = stack;
            this.itemId = itemId;
        }
        
        static ItemSignature of(ItemStack stack) {
            ItemStack copy = stack.copy();
            copy.setCount(1);
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(copy.getItem());
            return new ItemSignature(copy, id);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ItemSignature other)) return false;
            return Objects.equals(itemId, other.itemId) && ItemStack.isSameItemSameComponents(this.stack, other.stack);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(itemId, stack.getComponents());
        }
    }
    
    
    public void setFocused(boolean focused) {
        if (searchBox != null) {
            searchBox.setFocused(focused);
        }
    }
    
    public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        // Narration support
    }
}

