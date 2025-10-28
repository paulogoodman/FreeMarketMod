package com.freemarket.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

import com.freemarket.common.managers.ItemCategoryManager;

/**
 * Abstract base class for grid-based containers with search, category filtering, and scrolling.
 * Provides common functionality for marketplace and auction containers.
 */
public abstract class BaseGridContainer<T> implements Renderable {
    
    // Common fields
    protected final int x, y, width, height;
    protected final FreeMarketGuiScreen parentScreen;
    protected EditBox searchBox;
    protected ItemCategoryManager.Category selectedCategory = ItemCategoryManager.Category.ALL;
    protected int scrollOffset = 0;
    
    // Grid layout fields
    protected int itemsPerRow = 3; // Default, will be overridden by calculateOptimalGridLayout()
    protected int rowsOfItems = 3; // Default, will be overridden by calculateOptimalGridLayout()
    protected int maxVisibleItems = 9; // Default, will be overridden by calculateOptimalGridLayout()
    protected int itemSpacing = 150;
    protected int itemHeight = 180;
    protected int calculatedItemWidth = 130;
    
    // Caching for category filtering to prevent recalculation on every render
    protected List<ItemCategoryManager.Category> cachedCategories;
    protected Map<ItemCategoryManager.Category, Integer> cachedCategoryCounts;
    protected long lastCategoryCacheUpdate = 0;
    protected static final long CATEGORY_CACHE_DURATION = 1000; // 1 second cache
    
    // Caching for data filtering to prevent recalculation on every render
    protected List<T> cachedFilteredData;
    protected ItemCategoryManager.Category lastFilteredCategory;
    protected String lastSearchText;
    protected long lastDataCacheUpdate = 0;
    protected static final long DATA_CACHE_DURATION = 500; // 500ms cache for more responsive search
    
    public BaseGridContainer(int x, int y, int width, int height, FreeMarketGuiScreen parentScreen) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.parentScreen = parentScreen;
        
        // Calculate responsive dimensions that fit within the container
        calculateResponsiveDimensions();
    }
    
    /**
     * Initializes the container with responsive dimensions.
     */
    public void init() {
        // Calculate responsive dimensions for current screen size
        calculateResponsiveDimensions();
        
        // Create search box with proper spacing from title
        int searchWidth = (int)(width * 0.5); // 50% of container width
        int searchHeight = (int)(height * 0.05); // 5% of container height
        int searchX = x + (width - searchWidth) / 2; // Center horizontally
        int searchY = y + (int)(height * 0.08); // 8% from top (below title with space)
        
        this.searchBox = new EditBox(
            Minecraft.getInstance().font,
            searchX, searchY, searchWidth, searchHeight,
            getSearchPlaceholder()
        );
        this.searchBox.setResponder(this::onSearchChanged);
        this.searchBox.setMaxLength(50); // Set max length for search
        this.searchBox.setValue(""); // Clear any initial value
    }
    
    /**
     * Gets the search placeholder text for the specific container type.
     * Subclasses should override this to provide appropriate placeholder text.
     */
    protected abstract Component getSearchPlaceholder();
    
    /**
     * Handles search text changes.
     */
    private void onSearchChanged(String searchText) {
        scrollOffset = 0; // Reset scroll when searching
    }
    
    /**
     * Calculates responsive grid layout based on GUI scale.
     * Identical implementation from both containers.
     */
    protected void calculateResponsiveDimensions() {
        // Calculate grid layout based on GUI scale mapping
        calculateOptimalGridLayout();
        
        // Calculate available space for items - account for sidebar
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
     * Identical implementation from both containers.
     */
    protected void calculateOptimalGridLayout() {
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
    
    /**
     * Renders the category sidebar.
     * Identical implementation from both containers.
     */
    protected void renderCategorySidebar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
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
        guiGraphics.drawString(Minecraft.getInstance().font, sidebarTitle, 
            sidebarX + GuiScalingHelper.responsiveWidth(5, 4, 8), 
            sidebarY + GuiScalingHelper.responsiveHeight(5, 4, 8), 0xFFE0E0E0);
        
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
            int maxTextWidth = sidebarWidth - GuiScalingHelper.responsiveWidth(10, 8, 15);
            if (Minecraft.getInstance().font.width(displayText) > maxTextWidth) {
                while (Minecraft.getInstance().font.width(displayText + "...") > maxTextWidth && displayText.length() > 0) {
                    displayText = displayText.substring(0, displayText.length() - 1);
                }
                displayText += "...";
            }
            
            guiGraphics.drawString(Minecraft.getInstance().font, displayText, 
                sidebarX + GuiScalingHelper.responsiveWidth(5, 4, 8), 
                currentCategoryY, textColor);
        }
    }
    
    /**
     * Gets cached categories, updating cache if needed.
     * Identical implementation from both containers.
     */
    protected List<ItemCategoryManager.Category> getCachedCategories() {
        long currentTime = System.currentTimeMillis();
        
        // Check if cache is valid
        if (cachedCategories == null || cachedCategoryCounts == null || 
            (currentTime - lastCategoryCacheUpdate) > CATEGORY_CACHE_DURATION) {
            
            // Update cache
            List<ItemCategoryManager.Category> allCategories = ItemCategoryManager.getAllCategories();
            cachedCategoryCounts = getCategoryCounts();
            
            // Filter out categories with zero items
            cachedCategories = allCategories.stream()
                .filter(category -> cachedCategoryCounts.getOrDefault(category, 0) > 0)
                .collect(java.util.stream.Collectors.toList());
            
            lastCategoryCacheUpdate = currentTime;
        }
        
        return cachedCategories;
    }
    
    /**
     * Gets filtered data based on search text and category.
     * Identical implementation from both containers.
     */
    protected List<T> getFilteredData() {
        long currentTime = System.currentTimeMillis();
        String currentSearchText = searchBox != null ? searchBox.getValue() : "";
        
        // Check if cache is valid
        if (cachedFilteredData == null || 
            lastFilteredCategory != selectedCategory ||
            !currentSearchText.equals(lastSearchText) ||
            (currentTime - lastDataCacheUpdate) > DATA_CACHE_DURATION) {
            
            // Update cache
            // First filter by category
            List<T> categoryFiltered = filterByCategory(getAllData(), selectedCategory);
            
            // Then filter by search text
            if (!currentSearchText.isEmpty()) {
                categoryFiltered = filterBySearch(categoryFiltered, currentSearchText);
            }
            
            cachedFilteredData = categoryFiltered;
            lastFilteredCategory = selectedCategory;
            lastSearchText = currentSearchText;
            lastDataCacheUpdate = currentTime;
        }
        
        return cachedFilteredData;
    }
    
    /**
     * Scrolls the data list.
     * Identical implementation from both containers.
     */
    public void scroll(int delta) {
        int totalItems = getFilteredData().size();
        int totalRows = (int) Math.ceil((double) totalItems / itemsPerRow);
        int maxScroll = Math.max(0, totalRows - rowsOfItems);
        
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + delta));
    }
    
    /**
     * Handles mouse clicks on category sidebar.
     * Identical implementation from both containers.
     */
    protected boolean handleCategoryClick(double mouseX, double mouseY) {
        // Use percentage-based sidebar dimensions for click detection (aligned with items)
        int sidebarWidth = (int)(width * 0.2); // 20% of container width
        int sidebarX = x + (int)(width * 0.02); // 2% margin from left (matches right margin)
        int sidebarY = y + (int)(height * 0.15); // 15% from top (matches items start)
        int sidebarHeight = height - (int)(height * 0.2); // 80% of container height
        
        if (mouseX >= sidebarX && mouseX <= sidebarX + sidebarWidth &&
            mouseY >= sidebarY && mouseY <= sidebarY + sidebarHeight) {
            
            // Use the same filtered categories as rendering
            List<ItemCategoryManager.Category> categories = getCachedCategories();
            
            int categoryY = sidebarY + GuiScalingHelper.responsiveHeight(20, 16, 28);
            int categoryHeight = GuiScalingHelper.responsiveHeight(16, 12, 22);
            
            for (int i = 0; i < categories.size(); i++) {
                int currentCategoryY = categoryY + i * categoryHeight;
                
                if (mouseY >= currentCategoryY && mouseY <= currentCategoryY + categoryHeight) {
                    // Play click sound
                    net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
                    if (minecraft.player != null) {
                        minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                    }
                    selectedCategory = categories.get(i);
                    scrollOffset = 0; // Reset scroll when changing category
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Handles mouse clicks on search box.
     * Identical implementation from both containers.
     */
    protected boolean handleSearchBoxClick(double mouseX, double mouseY, int button) {
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
        return false;
    }
    
    /**
     * Handles key presses.
     * Forwards input to search box like both containers.
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return false;
    }
    
    /**
     * Handles character typing.
     * Forwards input to search box like both containers.
     */
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox != null && searchBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return false;
    }
    
    /**
     * Checks if the search box is focused.
     * Identical implementation from both containers.
     */
    public boolean isFocused() {
        return searchBox != null && searchBox.isFocused();
    }
    
    /**
     * Invalidates the data cache to force refresh on next access.
     * Call this when new data arrives from the server.
     */
    public void invalidateDataCache() {
        cachedFilteredData = null;
        cachedCategories = null;
        cachedCategoryCounts = null;
        lastDataCacheUpdate = 0;
        lastCategoryCacheUpdate = 0;
    }
    
    // Abstract methods that subclasses must implement
    
    /**
     * Gets all data items for this container.
     */
    protected abstract List<T> getAllData();
    
    /**
     * Filters data by category.
     */
    protected abstract List<T> filterByCategory(List<T> data, ItemCategoryManager.Category category);
    
    /**
     * Filters data by search text.
     */
    protected abstract List<T> filterBySearch(List<T> data, String searchText);
    
    /**
     * Gets category counts for the current data.
     */
    protected abstract Map<ItemCategoryManager.Category, Integer> getCategoryCounts();
    
    /**
     * Gets the category for a specific item.
     */
    protected abstract ItemCategoryManager.Category getItemCategory(T item);
    
    /**
     * Renders the data grid with the specific container's rendering logic.
     */
    protected abstract void renderDataGrid(GuiGraphics guiGraphics, List<T> data, int mouseX, int mouseY, float partialTick);
    
    /**
     * Handles clicks on data items.
     */
    protected abstract boolean handleDataClick(T item, double mouseX, double mouseY, int button);
}
