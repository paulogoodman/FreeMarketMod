package com.freemarket.client.gui;

import com.freemarket.client.data.ClientAuctionCache;
import com.freemarket.client.data.ClientAuctionTimingCache;
import com.freemarket.client.handlers.ClientWalletHandler;
import com.freemarket.common.attachments.ItemComponentHandler;
import com.freemarket.common.data.PlayerAuction;
import com.freemarket.common.managers.ItemCategoryManager;
import com.freemarket.common.network.FreeMarketPacket;
import com.freemarket.common.network.PacketType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container for displaying player auctions with grid layout and bid functionality.
 */
public class PlayerAuctionContainer implements Renderable {
    
    private final int x, y, width, height;
    private final FreeMarketGuiScreen parentScreen;
    
    // Grid layout configuration - will be calculated responsively
    private int itemsPerRow = 3; // Default, will be overridden by calculateOptimalGridLayout()
    private int rowsOfItems = 3; // Default, will be overridden by calculateOptimalGridLayout()
    private int maxVisibleItems = 9; // Default, will be overridden by calculateOptimalGridLayout()
    private int itemSpacing = 150;
    private int itemHeight = 180;
    private int calculatedItemWidth = 130;
    
    // Scrolling
    private int scrollOffset = 0;
    
    // Cooldown tracking for bid button
    private final Map<String, Long> bidCooldowns = new HashMap<>();
    private static final long BID_COOLDOWN_MS = 1000; // 1 second cooldown
    
    // Periodic sync for expired auctions
    private long lastPeriodicSync = 0;
    private static final long PERIODIC_SYNC_INTERVAL_MS = 30000; // 30 seconds
    
    // Cached ItemStack rendering
    private final Map<String, ItemStack> itemStackCache = new HashMap<>();
    
    // Unified card renderer
    private final UnifiedItemCardRenderer unifiedRenderer = new UnifiedItemCardRenderer();
    
    // Search and category functionality
    private EditBox searchBox;
    private ItemCategoryManager.Category selectedCategory = ItemCategoryManager.Category.ALL;
    
    // Caching for auction data (similar to marketplace container)
    private List<PlayerAuction> cachedAllAuctions;
    private long lastAuctionDataUpdate = 0;
    private static final long AUCTION_DATA_CACHE_DURATION = 1000; // 1 second cache for auction data
    
    // Caching for filtered auctions
    private List<PlayerAuction> cachedAuctionsToRender;
    private ItemCategoryManager.Category lastFilteredCategory;
    private String lastSearchText;
    private long lastAuctionCacheUpdate = 0;
    private static final long AUCTION_CACHE_DURATION = 500; // 500ms cache
    
    // Caching for categories (similar to marketplace container)
    private List<ItemCategoryManager.Category> cachedCategories;
    private Map<ItemCategoryManager.Category, Integer> cachedCategoryCounts;
    private long lastCategoryCacheUpdate = 0;
    private static final long CATEGORY_CACHE_DURATION = 1000; // 1 second cache
    
    public PlayerAuctionContainer(int x, int y, int width, int height, FreeMarketGuiScreen parentScreen) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.parentScreen = parentScreen;
        
        // Request auction data from server
        requestAuctionData();
    }
    
    /**
     * Initializes the container with responsive dimensions.
     */
    public void init() {
        // Calculate responsive dimensions
        calculateResponsiveDimensions();
        
        // Create search box with proper spacing from title
        int searchWidth = (int)(width * 0.5); // 50% of container width
        int searchHeight = (int)(height * 0.05); // 5% of container height
        int searchX = x + (width - searchWidth) / 2; // Center horizontally
        int searchY = y + (int)(height * 0.08); // 8% from top (below title with space)
        
        this.searchBox = new EditBox(
            Minecraft.getInstance().font,
            searchX, searchY, searchWidth, searchHeight,
            Component.translatable("gui.FreeMarket.auction.search_placeholder")
        );
        this.searchBox.setResponder(this::onSearchChanged);
        this.searchBox.setMaxLength(50); // Set max length for search
        this.searchBox.setValue(""); // Clear any initial value
        
        // Initialize cache with current auction data
        initializeAuctionCache();
        
        // Request fresh auction data
        requestAuctionData();
    }
    
    /**
     * Calculates responsive grid layout based on GUI scale.
     * Identical to marketplace container implementation.
     */
    private void calculateResponsiveDimensions() {
        // Calculate grid layout based on GUI scale mapping
        calculateOptimalGridLayout();
        
        // Calculate available space for items - account for sidebar like marketplace
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
     * Identical to marketplace container implementation.
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
    
    /**
     * Requests auction data from the server.
     */
    private void requestAuctionData() {
        FreeMarketPacket packet = FreeMarketPacket.emptyRequest(PacketType.AUCTION_REQUEST);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
    }
    
    /**
     * Handles search text changes.
     */
    private void onSearchChanged(String searchText) {
        scrollOffset = 0; // Reset scroll when searching
    }
    
    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Check if we need periodic sync for expired auctions
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPeriodicSync > PERIODIC_SYNC_INTERVAL_MS) {
            performPeriodicSync();
            lastPeriodicSync = currentTime;
        }
        
        // Draw modern container background
        guiGraphics.fill(x, y, x + width, y + height, 0x801E1E1E);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x802A2A2A);
        
        // Draw border
        guiGraphics.fill(x, y, x + width, y + 2, 0x80404040);
        guiGraphics.fill(x, y, x + 2, y + height, 0x80404040);
        guiGraphics.fill(x + width - 2, y, x + width, y + height, 0x80404040);
        guiGraphics.fill(x, y + height - 2, x + width, y + height, 0x80404040);
        
        
        // Render search box
        if (searchBox != null) {
            searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        
        // Draw Create Auction button (top-right)
        renderCreateAuctionButton(guiGraphics, mouseX, mouseY);
        
        // Draw category sidebar
        renderCategorySidebar(guiGraphics, mouseX, mouseY);
        
        // Get filtered auction data (includes search and category filtering)
        List<PlayerAuction> auctionsToRender = getAuctionsToRender();
        
        if (auctionsToRender.isEmpty()) {
            // Show "No Active Auctions" message
            Component noAuctions = Component.literal("No Active Auctions");
            int noAuctionsWidth = Minecraft.getInstance().font.width(noAuctions);
            int noAuctionsX = x + (width - noAuctionsWidth) / 2;
            int noAuctionsY = y + (height / 2) - 20;
            guiGraphics.drawString(Minecraft.getInstance().font, noAuctions, noAuctionsX, noAuctionsY, 0xFF999999);
            
            Component description = Component.literal("Be the first to list an item for auction!");
            int descWidth = Minecraft.getInstance().font.width(description);
            int descX = x + (width - descWidth) / 2;
            int descY = noAuctionsY + 20;
            guiGraphics.drawString(Minecraft.getInstance().font, description, descX, descY, 0xFF666666);
        } else {
            // Render auction grid
            renderAuctionGrid(guiGraphics, auctionsToRender, mouseX, mouseY);
        }
    }
    
    /**
     * Renders the auction grid with scrolling support.
     */
    private void renderAuctionGrid(GuiGraphics guiGraphics, List<PlayerAuction> auctions, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        float guiScale = (float) minecraft.getWindow().getGuiScale();
        
        // Get player UUID for ownership checks
        String playerUuid = minecraft.player != null ? minecraft.player.getStringUUID() : null;
        long playerBalance = ClientWalletHandler.getPlayerMoney();
        
        // Calculate grid start position - account for sidebar like marketplace
        int startY = y + (int)(height * 0.15);
        int sidebarWidth = (int)(width * 0.2); // 20% of container width
        int sidebarMargin = (int)(width * 0.02); // 2% margin
        int startX = x + sidebarWidth + sidebarMargin; // Start after sidebar with consistent margin
        
        // Calculate visible range
        int startIndex = scrollOffset * itemsPerRow;
        int endIndex = Math.min(startIndex + maxVisibleItems, auctions.size());
        
        // Render visible auctions
        for (int i = startIndex; i < endIndex; i++) {
            PlayerAuction auction = auctions.get(i);
            
            // Calculate position in grid
            int gridIndex = i - startIndex;
            int row = gridIndex / itemsPerRow;
            int col = gridIndex % itemsPerRow;
            
            int cardX = startX + (col * itemSpacing);
            int cardY = startY + (row * itemHeight);
            
            // Get ItemStack for rendering
            ItemStack itemStack = getOrCreateItemStack(auction);
            
            // Check if player can afford minimum bid
            long minimumBid = auction.getMinimumBid();
            boolean canBid = playerBalance >= minimumBid;
            
            // Check cooldown
            boolean isCooldown = isBidCooldown(auction.getAuctionId());
            
            // Check ownership and bidder status
            boolean isOwnAuction = playerUuid != null && playerUuid.equals(auction.getSellerUuid());
            boolean isHighestBidder = playerUuid != null && playerUuid.equals(auction.getBidderUuid());
            boolean isExpired = ClientAuctionTimingCache.isExpired(auction.getAuctionId());
            
            // Create button config for auction
            CardButtonConfig config = CardButtonConfig.forAuction(
                auction.getCurrentBid(), canBid, isCooldown,
                isOwnAuction, isHighestBidder, isExpired
            );
            
            // Format auction info text
            String infoText = formatAuctionInfo(auction);
            
            // Render auction card using unified renderer
            int cardHeight = (int)(itemHeight * 0.9); // Use 90% of item height for card (leaving margin) - match market container
            unifiedRenderer.renderCard(guiGraphics, itemStack, config, infoText,
                                      cardX, cardY, calculatedItemWidth, 
                                      cardHeight,
                                      mouseX, mouseY, guiScale);
        }
    }
    
    /**
     * Formats auction information for display in the card.
     * Only shows Current/Starting Bid and Time Remaining as requested.
     */
    private String formatAuctionInfo(PlayerAuction auction) {
        String timeText = getTimeRemainingText(auction);
        
        // Format current price vs starting price
        String currentPriceText;
        if (auction.getCurrentBid() > auction.getStartingPrice()) {
            currentPriceText = String.format("Current Bid: $%d", auction.getCurrentBid());
        } else {
            currentPriceText = String.format("Starting Bid: $%d", auction.getStartingPrice());
        }
        
        // Format time remaining with proper label
        String timeLabelText = String.format("Time remaining: %s", timeText);
        
        return String.format("%s\n%s", 
            currentPriceText,
            timeLabelText
        );
    }
    
    /**
     * Gets time remaining text for display using optimized client-side timing.
     */
    private String getTimeRemainingText(PlayerAuction auction) {
        // Use cached timing data instead of calling System.currentTimeMillis() every frame
        long timeLeft = ClientAuctionTimingCache.getTimeRemaining(auction.getAuctionId());
        
        if (timeLeft <= 0) {
            return "Expired";
        }
        
        long seconds = timeLeft / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + "d " + (hours % 24) + "h";
        } else if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }
    
    /**
     * Gets or creates ItemStack from auction data with caching.
     */
    private ItemStack getOrCreateItemStack(PlayerAuction auction) {
        String cacheKey = auction.getAuctionId();
        
        if (itemStackCache.containsKey(cacheKey)) {
            return itemStackCache.get(cacheKey);
        }
        
        // Create ItemStack from item ID
        ResourceLocation itemId = ResourceLocation.parse(auction.getItemId());
        Item item = BuiltInRegistries.ITEM.get(itemId);
        ItemStack itemStack = new ItemStack(item, auction.getQuantity());
        
        // Apply component data if present
        String componentData = auction.getComponentData();
        if (componentData != null && !componentData.trim().isEmpty() && !componentData.equals("{}")) {
            Minecraft minecraft = Minecraft.getInstance();
            var singleplayerServer = minecraft.getSingleplayerServer();
            
            if (singleplayerServer != null) {
                // Use server-side handler
                itemStack = com.freemarket.server.handlers.ServerItemHandler.createItemWithComponentData(
                    itemStack, componentData, singleplayerServer);
            } else {
                // Fallback to client-side
                ItemComponentHandler.applyComponentData(itemStack, componentData);
            }
        }
        
        // Cache and return
        itemStackCache.put(cacheKey, itemStack);
        return itemStack;
    }
    
    /**
     * Checks if bid button is on cooldown.
     */
    private boolean isBidCooldown(String auctionId) {
        Long lastBidTime = bidCooldowns.get(auctionId);
        if (lastBidTime == null) {
            return false;
        }
        
        long timeSinceLastBid = System.currentTimeMillis() - lastBidTime;
        return timeSinceLastBid < BID_COOLDOWN_MS;
    }
    
    /**
     * Starts cooldown for a bid button.
     */
    private void startBidCooldown(String auctionId) {
        bidCooldowns.put(auctionId, System.currentTimeMillis());
    }
    
    /**
     * Performs periodic sync to check for expired auctions and clean up cache.
     */
    private void performPeriodicSync() {
        // Remove expired auctions from timing cache
        ClientAuctionTimingCache.removeExpiredAuctions();
        
        // Invalidate our cache to force refresh when new data arrives
        invalidateAuctionDataCache();
        
        // Request fresh auction data from server to ensure accuracy
        requestAuctionData();
    }
    
    /**
     * Renders the Create Auction button.
     */
    private void renderCreateAuctionButton(GuiGraphics guiGraphics, int mouseX, int mouseY) {
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
        guiGraphics.fill(buttonX, buttonY, buttonX + 1, buttonY + buttonHeight, 0xFF404040);
        guiGraphics.fill(buttonX + buttonWidth - 1, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, 0xFF404040);
        guiGraphics.fill(buttonX, buttonY + buttonHeight - 1, buttonX + buttonWidth, buttonY + buttonHeight, 0xFF404040);
        
        // Button text
        Minecraft minecraft = Minecraft.getInstance();
        String buttonText = "+ Create Auction";
        int textWidth = minecraft.font.width(buttonText);
        int textX = buttonX + (buttonWidth - textWidth) / 2;
        int textY = buttonY + (buttonHeight - minecraft.font.lineHeight) / 2;
        guiGraphics.drawString(minecraft.font, buttonText, textX, textY, 0xFFFFFFFF);
    }
    
    /**
     * Handles mouse clicks on auction cards.
     * Identical to marketplace container implementation.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false; // Only left click
        
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
            List<ItemCategoryManager.Category> categories = getCachedCategories();
            
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
        
        // Check Create Auction button
        int buttonWidth = 120;
        int buttonHeight = 20;
        int buttonX = x + width - buttonWidth - 10;
        int buttonY = y + 10;
        
        if (mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
            mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
            // Open create auction popup using overlay system
            parentScreen.showCreateAuctionPopup();
            return true;
        }
        
        Minecraft minecraft = Minecraft.getInstance();
        float guiScale = (float) minecraft.getWindow().getGuiScale();
        String playerUuid = minecraft.player != null ? minecraft.player.getStringUUID() : null;
        
        // Use cached auction data instead of calling ClientAuctionCache every time
        List<PlayerAuction> auctions = getCachedAuctionData();
        List<PlayerAuction> activeAuctions = new ArrayList<>();
        for (PlayerAuction auction : auctions) {
            if (!auction.isExpired()) {
                activeAuctions.add(auction);
            }
        }
        
        // Calculate grid start position - account for sidebar like marketplace
        int startY = y + (int)(height * 0.15);
        int sidebarWidth2 = (int)(width * 0.2); // 20% of container width
        int sidebarMargin = (int)(width * 0.02); // 2% margin
        int startX = x + sidebarWidth2 + sidebarMargin; // Start after sidebar with consistent margin
        
        // Calculate visible range
        int startIndex = scrollOffset * itemsPerRow;
        int endIndex = Math.min(startIndex + maxVisibleItems, activeAuctions.size());
        
        // Check each visible auction for click
        for (int i = startIndex; i < endIndex; i++) {
            PlayerAuction auction = activeAuctions.get(i);
            
            // Calculate position
            int gridIndex = i - startIndex;
            int row = gridIndex / itemsPerRow;
            int col = gridIndex % itemsPerRow;
            
            int cardX = startX + (col * itemSpacing);
            int cardY = startY + (row * itemHeight);
            int cardHeight = (int)(itemHeight * 0.9); // Use 90% of item height for card (leaving margin) - match market container
            
            // Check if button was clicked using unified renderer
            boolean isOwnAuction = playerUuid != null && playerUuid.equals(auction.getSellerUuid());
            boolean isHighestBidder = playerUuid != null && playerUuid.equals(auction.getBidderUuid());
            boolean isExpired = ClientAuctionTimingCache.isExpired(auction.getAuctionId());
            boolean isCooldown = isBidCooldown(auction.getAuctionId());
            
            long minimumBid = auction.getMinimumBid();
            long playerBalance = ClientWalletHandler.getPlayerMoney();
            boolean canBid = playerBalance >= minimumBid;
            
            // Create button config for click detection
            CardButtonConfig config = CardButtonConfig.forAuction(
                auction.getCurrentBid(), canBid, isCooldown,
                isOwnAuction, isHighestBidder, isExpired
            );
            
            ButtonType buttonClicked = UnifiedItemCardRenderer.checkButtonClick(
                cardX, cardY, calculatedItemWidth, cardHeight,
                (int)mouseX, (int)mouseY, guiScale, config
            );
            
            if (buttonClicked == ButtonType.BID) {
                // Check cooldown
                if (isBidCooldown(auction.getAuctionId())) {
                    return true; // Consume click but don't open popup
                }
                
                // Start cooldown
                startBidCooldown(auction.getAuctionId());
                
                // Open bid popup using overlay system
                parentScreen.showPlaceBidPopup(auction);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Handles key presses.
     * Forwards input to search box like marketplace container.
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return false;
    }
    
    /**
     * Handles character typing.
     * Forwards input to search box like marketplace container.
     */
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox != null && searchBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return false;
    }
    
    /**
     * Checks if the search box is focused.
     * Identical to marketplace container implementation.
     */
    public boolean isFocused() {
        return searchBox != null && searchBox.isFocused();
    }
    
    /**
     * Scrolls the auction list.
     */
    public void scroll(int delta) {
        // Use cached auction data instead of calling ClientAuctionCache every time
        int totalAuctions = getCachedAuctionData().size();
        int totalRows = (int) Math.ceil((double) totalAuctions / itemsPerRow);
        int maxScroll = Math.max(0, totalRows - rowsOfItems);
        
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + delta));
    }
    
    /**
     * Clears the ItemStack cache (call when auctions update).
     */
    public void clearCache() {
        itemStackCache.clear();
    }
    
    /**
     * Initializes the auction cache with current data.
     * Called during container initialization to populate cache immediately.
     */
    private void initializeAuctionCache() {
        // Initialize cache with current auction data if available
        if (ClientAuctionCache.hasCachedData()) {
            cachedAllAuctions = ClientAuctionCache.getCachedAuctions();
            lastAuctionDataUpdate = System.currentTimeMillis();
        }
    }
    
    /**
     * Invalidates the auction data cache to force refresh on next access.
     * Call this when new auction data arrives from the server.
     */
    public void invalidateAuctionDataCache() {
        cachedAllAuctions = null;
        cachedAuctionsToRender = null;
        cachedCategories = null;
        cachedCategoryCounts = null;
        lastAuctionDataUpdate = 0;
        lastAuctionCacheUpdate = 0;
        lastCategoryCacheUpdate = 0;
    }
    
    /**
     * Gets cached auction data, updating cache if needed.
     * Similar to marketplace container's approach.
     */
    private List<PlayerAuction> getCachedAuctionData() {
        long currentTime = System.currentTimeMillis();
        
        // Check if cache is valid
        if (cachedAllAuctions == null || 
            (currentTime - lastAuctionDataUpdate) > AUCTION_DATA_CACHE_DURATION) {
            
            // Update cache from ClientAuctionCache
            cachedAllAuctions = ClientAuctionCache.getCachedAuctions();
            lastAuctionDataUpdate = currentTime;
        }
        
        return cachedAllAuctions;
    }
    
    /**
     * Gets filtered auctions based on search text and category.
     */
    private List<PlayerAuction> getAuctionsToRender() {
        long currentTime = System.currentTimeMillis();
        String currentSearchText = searchBox != null ? searchBox.getValue() : "";
        
        // Check if cache is valid
        if (cachedAuctionsToRender == null || 
            lastFilteredCategory != selectedCategory ||
            !currentSearchText.equals(lastSearchText) ||
            (currentTime - lastAuctionCacheUpdate) > AUCTION_CACHE_DURATION) {
            
            // Get cached auction data instead of calling ClientAuctionCache every time
            List<PlayerAuction> allAuctions = getCachedAuctionData();
            List<PlayerAuction> activeAuctions = new ArrayList<>();
            for (PlayerAuction auction : allAuctions) {
                if (!ClientAuctionTimingCache.isExpired(auction.getAuctionId())) {
                    activeAuctions.add(auction);
                }
            }
            
            // Filter by category
            List<PlayerAuction> categoryFiltered = filterAuctionsByCategory(activeAuctions, selectedCategory);
            
            // Filter by search text
            if (!currentSearchText.isEmpty()) {
                String searchText = currentSearchText.toLowerCase();
                categoryFiltered = categoryFiltered.stream()
                    .filter(auction -> auction.getItemId().toLowerCase().contains(searchText))
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            }
            
            cachedAuctionsToRender = categoryFiltered;
            lastFilteredCategory = selectedCategory;
            lastSearchText = currentSearchText;
            lastAuctionCacheUpdate = currentTime;
        }
        
        return cachedAuctionsToRender;
    }
    
    /**
     * Filters a list of auctions by category.
     * Uses ItemCategoryManager.filterAuctionsByCategory for consistency.
     */
    private List<PlayerAuction> filterAuctionsByCategory(List<PlayerAuction> auctions, ItemCategoryManager.Category category) {
        return ItemCategoryManager.filterAuctionsByCategory(auctions, category);
    }
    
    /**
     * Renders the category sidebar.
     * Identical to marketplace container implementation.
     */
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
     * Gets cached categories, updating cache if needed
     */
    private List<ItemCategoryManager.Category> getCachedCategories() {
        long currentTime = System.currentTimeMillis();
        
        // Check if cache is valid
        if (cachedCategories == null || cachedCategoryCounts == null || 
            (currentTime - lastCategoryCacheUpdate) > CATEGORY_CACHE_DURATION) {
            
            // Get cached auction data instead of calling ClientAuctionCache every time
            List<PlayerAuction> allAuctions = getCachedAuctionData();
            List<PlayerAuction> activeAuctions = new ArrayList<>();
            for (PlayerAuction auction : allAuctions) {
                if (!ClientAuctionTimingCache.isExpired(auction.getAuctionId())) {
                    activeAuctions.add(auction);
                }
            }
            
            // Update cache
            List<ItemCategoryManager.Category> allCategories = ItemCategoryManager.getAllCategories();
            cachedCategoryCounts = ItemCategoryManager.getCategoryCountsForAuctions(activeAuctions);
            
            // Filter out categories with zero items
            cachedCategories = allCategories.stream()
                .filter(category -> cachedCategoryCounts.getOrDefault(category, 0) > 0)
                .collect(java.util.stream.Collectors.toList());
            
            lastCategoryCacheUpdate = currentTime;
        }
        
        return cachedCategories;
    }
}
