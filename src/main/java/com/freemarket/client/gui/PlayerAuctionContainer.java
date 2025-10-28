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
public class PlayerAuctionContainer extends BaseGridContainer<com.freemarket.common.data.PlayerAuction> {
    
    
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
    
    
    // Caching for auction data (similar to marketplace container)
    private List<PlayerAuction> cachedAllAuctions;
    private long lastAuctionDataUpdate = 0;
    private static final long AUCTION_DATA_CACHE_DURATION = 1000; // 1 second cache for auction data
    
    
    public PlayerAuctionContainer(int x, int y, int width, int height, FreeMarketGuiScreen parentScreen) {
        super(x, y, width, height, parentScreen);
        
        // Request auction data from server
        requestAuctionData();
    }
    
    
    
    /**
     * Requests auction data from the server.
     */
    private void requestAuctionData() {
        FreeMarketPacket packet = FreeMarketPacket.emptyRequest(PacketType.AUCTION_REQUEST);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
    }
    
    // Abstract method implementations
    
    @Override
    protected Component getSearchPlaceholder() {
        return Component.translatable("gui.FreeMarket.auction.search_placeholder");
    }
    
    @Override
    protected List<com.freemarket.common.data.PlayerAuction> getAllData() {
        return getCachedAuctionData();
    }
    
    @Override
    protected List<com.freemarket.common.data.PlayerAuction> filterByCategory(List<com.freemarket.common.data.PlayerAuction> data, ItemCategoryManager.Category category) {
        return ItemCategoryManager.filterAuctionsByCategory(data, category);
    }
    
    @Override
    protected List<com.freemarket.common.data.PlayerAuction> filterBySearch(List<com.freemarket.common.data.PlayerAuction> data, String searchText) {
        if (searchText.isEmpty()) {
            return data;
        }
        String searchLower = searchText.toLowerCase();
        return data.stream()
            .filter(auction -> auction.getItemId().toLowerCase().contains(searchLower))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    @Override
    protected Map<ItemCategoryManager.Category, Integer> getCategoryCounts() {
        List<com.freemarket.common.data.PlayerAuction> allAuctions = getCachedAuctionData();
        List<com.freemarket.common.data.PlayerAuction> activeAuctions = new ArrayList<>();
        for (com.freemarket.common.data.PlayerAuction auction : allAuctions) {
            if (!ClientAuctionTimingCache.isExpired(auction.getAuctionId())) {
                activeAuctions.add(auction);
            }
        }
        return ItemCategoryManager.getCategoryCountsForAuctions(activeAuctions);
    }
    
    @Override
    protected ItemCategoryManager.Category getItemCategory(com.freemarket.common.data.PlayerAuction auction) {
        // Create ItemStack from auction data
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
        
        return ItemCategoryManager.getCategoryForItem(itemStack);
    }
    
    @Override
    protected void renderDataGrid(GuiGraphics guiGraphics, List<com.freemarket.common.data.PlayerAuction> auctions, int mouseX, int mouseY, float partialTick) {
        // Filter to only active auctions
        List<com.freemarket.common.data.PlayerAuction> activeAuctions = new ArrayList<>();
        for (com.freemarket.common.data.PlayerAuction auction : auctions) {
            if (!ClientAuctionTimingCache.isExpired(auction.getAuctionId())) {
                activeAuctions.add(auction);
            }
        }
        
        // Render auction grid
        renderAuctionGrid(guiGraphics, activeAuctions, mouseX, mouseY);
    }
    
    @Override
    protected boolean handleDataClick(com.freemarket.common.data.PlayerAuction auction, double mouseX, double mouseY, int button) {
        // Handle bid button clicks
        Minecraft minecraft = Minecraft.getInstance();
        float guiScale = (float) minecraft.getWindow().getGuiScale();
        String playerUuid = minecraft.player != null ? minecraft.player.getStringUUID() : null;
        
        // Calculate grid start position - account for sidebar like marketplace
        int startY = y + (int)(height * 0.15);
        int sidebarWidth = (int)(width * 0.2); // 20% of container width
        int sidebarMargin = (int)(width * 0.02); // 2% margin
        int startX = x + sidebarWidth + sidebarMargin; // Start after sidebar with consistent margin
        
        // Get filtered auction data
        List<com.freemarket.common.data.PlayerAuction> auctions = getFilteredData();
        
        // Calculate visible range
        int startIndex = scrollOffset * itemsPerRow;
        int endIndex = Math.min(startIndex + maxVisibleItems, auctions.size());
        
        // Check each visible auction for click
        for (int i = startIndex; i < endIndex; i++) {
            com.freemarket.common.data.PlayerAuction currentAuction = auctions.get(i);
            
            // Calculate position
            int gridIndex = i - startIndex;
            int row = gridIndex / itemsPerRow;
            int col = gridIndex % itemsPerRow;
            
            int cardX = startX + (col * itemSpacing);
            int cardY = startY + (row * itemHeight);
            int cardHeight = (int)(itemHeight * 0.9); // Use 90% of item height for card (leaving margin) - match market container
            
            // Check if button was clicked using unified renderer
            boolean isOwnAuction = playerUuid != null && playerUuid.equals(currentAuction.getSellerUuid());
            boolean isHighestBidder = playerUuid != null && playerUuid.equals(currentAuction.getBidderUuid());
            boolean isExpired = ClientAuctionTimingCache.isExpired(currentAuction.getAuctionId());
            boolean isCooldown = isBidCooldown(currentAuction.getAuctionId());
            
            long minimumBid = currentAuction.getMinimumBid();
            long playerBalance = ClientWalletHandler.getPlayerMoney();
            boolean canBid = playerBalance >= minimumBid;
            
            // Create button config for click detection
            CardButtonConfig config = CardButtonConfig.forAuction(
                currentAuction.getCurrentBid(), canBid, isCooldown,
                isOwnAuction, isHighestBidder, isExpired
            );
            
            ButtonType buttonClicked = UnifiedItemCardRenderer.checkButtonClick(
                cardX, cardY, calculatedItemWidth, cardHeight,
                (int)mouseX, (int)mouseY, guiScale, config
            );
            
            if (buttonClicked == ButtonType.BID) {
                // Play click sound
                if (minecraft.player != null) {
                    minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                }
                
                // Check if player is the highest bidder - if so, just consume the click without opening popup
                if (isHighestBidder) {
                    return true;
                }
                
                // Check cooldown
                if (isBidCooldown(currentAuction.getAuctionId())) {
                    return true; // Consume click but don't open popup
                }
                
                // Start cooldown
                startBidCooldown(currentAuction.getAuctionId());
                
                // Open bid popup using overlay system
                parentScreen.showPlaceBidPopup(currentAuction);
                return true;
            } else if (buttonClicked == ButtonType.CANCEL_AUCTION) {
                // Play click sound
                if (minecraft.player != null) {
                    minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                }
                
                // Open cancel auction confirmation popup
                parentScreen.showCancelAuctionPopup(currentAuction);
                return true;
            }
            
            // Check delete button click (only if admin mode) - match ItemCardRenderer dimensions
            if (com.freemarket.common.handlers.AdminModeHandler.isAdminMode()) {
                int deleteButtonSize = (int)(calculatedItemWidth * 0.12); // 12% of card width (match ItemCardRenderer)
                int margin = 0; // No margin - match ItemCardRenderer
                int deleteButtonX = cardX + calculatedItemWidth - deleteButtonSize - margin; // Right at the edge
                int deleteButtonY = cardY + margin; // Top at the edge
                
                if (mouseX >= deleteButtonX && mouseX <= deleteButtonX + deleteButtonSize &&
                    mouseY >= deleteButtonY && mouseY <= deleteButtonY + deleteButtonSize) {
                    // Play note block sound for delete action
                    if (minecraft.player != null) {
                        minecraft.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.5f);
                    }
                    
                    // Send delete request to server via network packet (using AUCTION_CANCEL with admin privileges)
                    com.google.gson.JsonObject json = new com.google.gson.JsonObject();
                    json.addProperty("auctionId", currentAuction.getAuctionId());
                    com.freemarket.common.network.FreeMarketPacket packet = com.freemarket.common.network.FreeMarketPacket.withJson(
                        com.freemarket.common.network.PacketType.AUCTION_CANCEL, json.toString());
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
                    
                    return true;
                }
            }
        }
        
        return false;
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
        guiGraphics.fill(x, y + 2, x + 2, y + height - 2, 0x80404040);
        guiGraphics.fill(x + width - 2, y + 2, x + width, y + height - 2, 0x80404040);
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
        List<com.freemarket.common.data.PlayerAuction> auctionsToRender = getFilteredData();
        
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
            renderDataGrid(guiGraphics, auctionsToRender, mouseX, mouseY, partialTick);
        }
        
        // Draw auction count
        int actualAuctionCount = auctionsToRender.size();
        Component countText = Component.translatable("gui.FreeMarket.auction.count", actualAuctionCount, getCachedAuctionData().size());
        guiGraphics.drawString(Minecraft.getInstance().font, countText, x + GuiScalingHelper.responsiveWidth(10, 8, 15), y + height - GuiScalingHelper.responsiveHeight(15, 12, 20), 0xCCCCCC);
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
                                      mouseX, mouseY, guiScale,
                                      parentScreen != null && parentScreen.isAnyPopupVisible());
        }
    }
    
    /**
     * Formats auction information for display in the card.
     * Only shows Current/Starting Bid and Time Remaining as requested.
     */
    private String formatAuctionInfo(PlayerAuction auction) {
        String timeText = getTimeRemainingText(auction);
        
        // Format current price vs starting price
        // Show "Current Bid" if there's any bidder, even if the value equals starting price
        String currentPriceText;
        if (auction.hasBids()) {
            currentPriceText = String.format("Current Bid: $%d", auction.getCurrentBid());
        } else {
            currentPriceText = String.format("Starting Bid: $%d", auction.getStartingPrice());
        }
        
        // Format time remaining with clock emoji (placeholder until icon is added)
        String timeLabelText = String.format("🕐 %s", timeText);
        
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
        guiGraphics.fill(buttonX, buttonY + 1, buttonX + 1, buttonY + buttonHeight - 1, 0xFF404040);
        guiGraphics.fill(buttonX + buttonWidth - 1, buttonY + 1, buttonX + buttonWidth, buttonY + buttonHeight - 1, 0xFF404040);
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
        
        Minecraft minecraft = Minecraft.getInstance();
        
        // Block all clicks if a popup is visible (except search box to allow unfocusing)
        boolean popupVisible = parentScreen != null && parentScreen.isAnyPopupVisible();
        
        // Handle search box clicks first (always allow to enable unfocusing)
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
        
        // Block remaining clicks if popup is visible
        if (popupVisible) {
            return false; // Don't consume - let popup handle it
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
                    // Play click sound
                    if (minecraft.player != null) {
                        minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                    }
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
            // Play click sound
            if (minecraft.player != null) {
                minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
            }
            // Open create auction popup using overlay system
            parentScreen.showCreateAuctionPopup();
            return true;
        }
        
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
                // Play click sound
                if (minecraft.player != null) {
                    minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                }
                
                // Check if player is the highest bidder - if so, just consume the click without opening popup
                if (isHighestBidder) {
                    return true;
                }
                
                // Check cooldown
                if (isBidCooldown(auction.getAuctionId())) {
                    return true; // Consume click but don't open popup
                }
                
                // Start cooldown
                startBidCooldown(auction.getAuctionId());
                
                // Open bid popup using overlay system
                parentScreen.showPlaceBidPopup(auction);
                return true;
            } else if (buttonClicked == ButtonType.CANCEL_AUCTION) {
                // Play click sound
                if (minecraft.player != null) {
                    minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                }
                
                // Open cancel auction confirmation popup
                parentScreen.showCancelAuctionPopup(auction);
                return true;
            }
        }
        
        return false;
    }
    
    
    
    
    /**
     * Scrolls the auction list.
     */
    public void scroll(int delta) {
        super.scroll(delta);
    }
    
    /**
     * Clears the ItemStack cache (call when auctions update).
     */
    public void clearCache() {
        itemStackCache.clear();
    }
    
    
    /**
     * Invalidates the auction data cache to force refresh on next access.
     * Call this when new auction data arrives from the server.
     */
    public void invalidateAuctionDataCache() {
        cachedAllAuctions = null;
        invalidateDataCache();
        lastAuctionDataUpdate = 0;
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
}
