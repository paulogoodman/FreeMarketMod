package com.freemarket.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import com.freemarket.common.data.FreeMarketItem;
import com.freemarket.common.network.FreeMarketPacket;
import com.freemarket.common.network.PacketType;
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
        guiGraphics.fill(x, y, x + 2, y + height, 0x80404040); // 50% opacity
        guiGraphics.fill(x + width - 2, y, x + width, y + height, 0x80404040); // 50% opacity
        guiGraphics.fill(x, y + height - 2, x + width, y + height, 0x80404040); // 50% opacity
        
        // Render search box
        if (searchBox != null) {
            searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        
        // Draw category sidebar
        renderCategorySidebar(guiGraphics, mouseX, mouseY);
        
        // Get items to render based on selected category and search
        List<FreeMarketItem> itemsToRender = getItemsToRender();
        
        // Render the data grid
        renderDataGrid(guiGraphics, itemsToRender, mouseX, mouseY, partialTick);
        
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
    
    // Abstract method implementations
    
    @Override
    protected Component getSearchPlaceholder() {
        return Component.translatable("gui.FreeMarket.marketplace.search_placeholder");
    }
    
    @Override
    protected List<FreeMarketItem> getAllData() {
        return allItems;
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
        return data.stream()
            .filter(item -> item.getItemName().toLowerCase().contains(searchLower))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    @Override
    protected Map<ItemCategoryManager.Category, Integer> getCategoryCounts() {
        return ItemCategoryManager.getCategoryCounts(allItems);
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
                    
                    // Render using unified renderer with GUI scale and cooldown states
                    Minecraft client = Minecraft.getInstance();
                    float guiScale = (float) client.getWindow().getGuiScale();
                    
                    // Create button config for marketplace
                    CardButtonConfig config = CardButtonConfig.forMarketplace(
                        item.getBuyPrice(), item.getSellPrice(),
                        getCachedCanBuyState(item), getCachedCanSellState(item),
                        isBuyButtonInCooldown(item), isSellButtonInCooldown(item)
                    );
                    
                    unifiedRenderer.renderCard(guiGraphics, displayStack, config, null,
                                              itemX, itemY, calculatedItemWidth, cardHeight, 
                                              mouseX, mouseY, guiScale, 
                                              parentScreen != null && parentScreen.isAnyPopupVisible());
                }
                itemsRendered++;
            }
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
                
                // Check if this is the add item entry
                if (isAddItemEntry(currentItem)) {
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
                            FreeMarketPacket packet = FreeMarketPacket.withString(PacketType.MARKETPLACE_REMOVE_ITEM, currentItem.getGuid());
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
                    
                    // Set cooldown immediately to prevent spam clicking
                    long currentTime = System.currentTimeMillis();
                    buyButtonCooldowns.put(currentItem.getGuid(), currentTime + BUY_COOLDOWN_MS);
                    
                    // Send buy request to server via network packet
                    FreeMarketPacket packet = FreeMarketPacket.withString(PacketType.BUY_ITEM_REQUEST, currentItem.getGuid());
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
                    
                    // Update button states after buy operation
                    updateButtonStates();
                    
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
                    
                    // Set cooldown immediately to prevent spam clicking
                    long currentTime = System.currentTimeMillis();
                    sellButtonCooldowns.put(currentItem.getGuid(), currentTime + SELL_COOLDOWN_MS);
                    
                    // Send sell request to server via network packet
                    FreeMarketPacket packet = FreeMarketPacket.withString(PacketType.SELL_ITEM_REQUEST, currentItem.getGuid());
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
                    
                    // Update button states after sell operation
                    updateButtonStates();
                    
                    return true; // Consume the click
                }
                
                itemsRendered++;
            }
        }
        
        return false;
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
        if (cachedFilteredData == null || 
            lastFilteredCategory != selectedCategory ||
            !currentSearchText.equals(lastSearchText) ||
            (currentTime - lastDataCacheUpdate) > DATA_CACHE_DURATION) {
            
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
            
            cachedFilteredData = categoryFiltered;
            lastFilteredCategory = selectedCategory;
            lastSearchText = currentSearchText;
            lastDataCacheUpdate = currentTime;
        }
        
        return cachedFilteredData;
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
    
    
    public void setFocused(boolean focused) {
        if (searchBox != null) {
            searchBox.setFocused(focused);
        }
    }
    
    public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        // Narration support
    }
}

