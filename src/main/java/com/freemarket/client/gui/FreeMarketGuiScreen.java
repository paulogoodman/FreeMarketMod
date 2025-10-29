package com.freemarket.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

import com.freemarket.Config;
import com.freemarket.FreeMarket;
import com.freemarket.client.data.ClientFreeMarketDataManager;
import com.freemarket.client.data.ClientMarketplaceCache;
import com.freemarket.common.data.FreeMarketItem;
import com.freemarket.common.network.FreeMarketPacket;
import com.freemarket.common.network.PacketType;
import com.freemarket.client.handlers.ClientWalletHandler;
import com.freemarket.server.data.FreeMarketDataManager;

/**
 * Semi-transparent dark overlay GUI for the FreeMarket mod.
 * Opens with the O keybind and displays a dark overlay with conditional admin button and marketplace.
 */
public class FreeMarketGuiScreen extends Screen {
    
    /**
     * Enum for different screen types in the GUI
     */
    public enum ScreenType {
        MARKETPLACE,
        AUCTIONS,
        LEADERBOARD
    }
    
    private ScreenType currentScreen = ScreenType.MARKETPLACE;
    
    private List<FreeMarketItem> freeMarketItems;
    FreeMarketContainer freeMarketContainer;
    LeaderboardContainer leaderboardContainer;
    PlayerAuctionContainer auctionContainer;
    
    private PlaceBidPopupOverlay placeBidPopup;
    private CancelAuctionConfirmationPopup cancelAuctionPopup;
    
    // Cache wallet balance to avoid retrieving it every frame
    private long cachedBalance = 0;
    
    public FreeMarketGuiScreen() {
        super(Component.literal(Config.MARKETPLACE_NAME.get()));
        this.freeMarketItems = new ArrayList<>();
        // Don't load items here - let init() handle it with caching
    }
    
    private void loadFreeMarketItemsFromFile() {
        // First check if we have cached data from network sync
        if (ClientMarketplaceCache.hasCachedData()) {
            this.freeMarketItems = ClientMarketplaceCache.getCachedItems();
            return;
        }
        
        // Try to use server-side loading first (with SavedData attachments)
        Minecraft minecraft = Minecraft.getInstance();
        var singleplayerServer = minecraft.getSingleplayerServer();
        if (singleplayerServer != null) {
            try {
                ServerLevel serverLevel = singleplayerServer.getLevel(net.minecraft.world.level.Level.OVERWORLD);
                if (serverLevel != null) {
                    List<FreeMarketItem> serverItems = FreeMarketDataManager.loadFreeMarketItems(serverLevel);
                    this.freeMarketItems = serverItems;
                    return;
                }
            } catch (Exception e) {
                // Failed to load from server-side, will fall back to client-side
            }
        }
        
        // Fallback to client-side loading (for multiplayer or when server is not available)
        List<FreeMarketItem> loadedItems = ClientFreeMarketDataManager.loadFreeMarketItems();
        this.freeMarketItems = loadedItems;
    }
    
    /**
     * Shows the create auction popup overlay.
     */
    public void showCreateAuctionPopup() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new CreateAuctionPopupScreen(this));
        }
    }
    
    /**
     * Shows the place bid popup overlay for the given auction.
     */
    public void showPlaceBidPopup(com.freemarket.common.data.PlayerAuction auction) {
        if (placeBidPopup == null) {
            placeBidPopup = new PlaceBidPopupOverlay(auction);
        } else {
            // Update the auction data if popup already exists
            placeBidPopup = new PlaceBidPopupOverlay(auction);
        }
        placeBidPopup.show();
    }
    
    /**
     * Shows the cancel auction confirmation popup overlay for the given auction.
     */
    public void showCancelAuctionPopup(com.freemarket.common.data.PlayerAuction auction) {
        if (cancelAuctionPopup == null) {
            cancelAuctionPopup = new CancelAuctionConfirmationPopup(auction);
        } else {
            // Update the auction data if popup already exists
            cancelAuctionPopup = new CancelAuctionConfirmationPopup(auction);
        }
        cancelAuctionPopup.show();
    }
    
    /**
     * Hides all popup overlays.
     */
    public void hideAllPopups() {
        if (placeBidPopup != null) {
            placeBidPopup.hide();
        }
        if (cancelAuctionPopup != null) {
            cancelAuctionPopup.hide();
        }
    }
    
    
    /**
     * Checks if any popup overlay is currently visible.
     * @return true if any popup is visible, false otherwise
     */
    public boolean isAnyPopupVisible() {
        return (placeBidPopup != null && placeBidPopup.isVisible()) ||
               (cancelAuctionPopup != null && cancelAuctionPopup.isVisible());
    }
    
    /**
     * Gets the cached wallet balance for external access.
     * @return the cached balance
     */
    public long getCachedBalance() {
        return cachedBalance;
    }
    
    
    /**
     * Requests wallet balance from server (for multiplayer).
     * In singleplayer, this will use direct access; in multiplayer, it sends a network request.
     */
    private void requestWalletBalance() {
        try {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            var clientPlayer = minecraft.player;
            if (clientPlayer != null) {
                // In singleplayer, try to get the server player instead of client player
                var singleplayerServer = minecraft.getSingleplayerServer();
                if (singleplayerServer != null) {
                    // We're in singleplayer - get the server player directly
                    var serverPlayer = singleplayerServer.getPlayerList().getPlayer(clientPlayer.getUUID());
                    if (serverPlayer != null) {
                        long balance = com.freemarket.server.handlers.ServerWalletHandler.getPlayerMoney(serverPlayer);
                        cachedBalance = balance;
                        return;
                    }
                }
                
                // In multiplayer, request balance from server
                FreeMarketPacket packet = FreeMarketPacket.emptyRequest(PacketType.WALLET_REQUEST);
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
            }
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Could not request wallet balance: {}", e.getMessage());
        }
    }
    
    /**
     * Updates the wallet balance from network sync.
     * Called when receiving wallet balance from server.
     * Also updates button states since balance change affects buy/sell availability.
     */
    public void updateWalletBalance(long balance) {
        this.cachedBalance = balance;
        
        // Update button states when wallet balance is received from server
        if (freeMarketContainer != null) {
            freeMarketContainer.updateButtonStates();
        }
    }
    
    /**
     * Updates wallet balance and triggers button state update.
     * Should only be called when balance changes due to user actions (buy/sell).
     */
    public void updateWalletBalanceAndRefreshButtons(long balance) {
        this.cachedBalance = balance;
        
        // Update button states when wallet balance changes due to user actions
        if (freeMarketContainer != null) {
            freeMarketContainer.updateButtonStates();
        }
    }
    
    /**
     * Forces a refresh of the cached balance.
     * Call this after transactions to ensure the display is up-to-date.
     */
    public void refreshBalance() {
        cachedBalance = ClientWalletHandler.getPlayerMoney();
    }
    
    /**
     * Updates marketplace data from network sync.
     * Called when receiving marketplace data from server.
     */
    public void updateMarketplaceData(List<FreeMarketItem> items) {
        this.freeMarketItems = new ArrayList<>(items);
        
        // Update the marketplace container with new data
        if (freeMarketContainer != null) {
            freeMarketContainer.updateFreeMarketItems(freeMarketItems, true); // Preserve scroll position
        }
    }
    
    /**
     * Forces a refresh of the marketplace data.
     * Invalidates the cache and reloads from file.
     * @param preserveScrollPosition If true, preserves the current scroll position
     */
    public void refreshMarketplace(boolean preserveScrollPosition) {
        // First check if we have cached data from network sync
        if (ClientMarketplaceCache.hasCachedData()) {
            this.freeMarketItems = ClientMarketplaceCache.getCachedItems();
            
            // Update the marketplace container with new data
            if (freeMarketContainer != null) {
                freeMarketContainer.updateFreeMarketItems(freeMarketItems, preserveScrollPosition);
            }
            return;
        }
        
        // Try to use server-side loading first (with SavedData attachments)
        Minecraft minecraft = Minecraft.getInstance();
        var singleplayerServer = minecraft.getSingleplayerServer();
        if (singleplayerServer != null) {
            try {
                ServerLevel serverLevel = singleplayerServer.getLevel(net.minecraft.world.level.Level.OVERWORLD);
                if (serverLevel != null) {
                    List<FreeMarketItem> serverItems = FreeMarketDataManager.loadFreeMarketItems(serverLevel);
                    this.freeMarketItems = serverItems;
                    
                    // Update the marketplace container with new data
                    if (freeMarketContainer != null) {
                        freeMarketContainer.updateFreeMarketItems(freeMarketItems, preserveScrollPosition);
                    }
                    return;
                }
            } catch (Exception e) {
                // Failed to refresh from server-side, will fall back to client-side
            }
        }
        
        // Fallback to client-side loading
        ClientFreeMarketDataManager.invalidateCache();
        loadFreeMarketItemsFromFile();
        
        // Update the marketplace container with new data
        if (freeMarketContainer != null) {
            freeMarketContainer.updateFreeMarketItems(freeMarketItems, preserveScrollPosition);
        }
    }
    
    /**
     * Forces a refresh of the marketplace data (default behavior - resets scroll).
     * Invalidates the cache and reloads from file.
     */
    public void refreshMarketplace() {
        refreshMarketplace(false);
    }
    
    /**
     * Switches to a different screen type.
     * @param newScreen The screen type to switch to
     */
    public void switchScreen(ScreenType newScreen) {
        if (this.currentScreen != newScreen) {
            this.currentScreen = newScreen;
            // Recreate the appropriate container for the new screen
            createContainerForCurrentScreen();
            
            // Check for data refresh when switching to leaderboard
            if (newScreen == ScreenType.LEADERBOARD && leaderboardContainer != null) {
                leaderboardContainer.checkAndRefreshIfNeeded();
            }
        }
    }
    
    /**
     * Gets the current screen type.
     * @return The current screen type
     */
    public ScreenType getCurrentScreen() {
        return this.currentScreen;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Refresh marketplace items from file in case they were updated
        loadFreeMarketItemsFromFile();
        
        // Request wallet balance from server (for multiplayer)
        requestWalletBalance();
        
        // Pre-fetch auction data when opening the shop
        requestAuctionData();
        
        // Initialize popup overlays
        this.placeBidPopup = null; // Will be created when needed
        
        // Create the appropriate container based on current screen
        createContainerForCurrentScreen();
    }
    
    /**
     * Pre-fetches auction data from the server when opening the shop.
     */
    private void requestAuctionData() {
        FreeMarketPacket packet = FreeMarketPacket.emptyRequest(PacketType.AUCTION_REQUEST);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
    }
    
    /**
     * Creates the appropriate container based on the current screen type.
     */
    private void createContainerForCurrentScreen() {
        switch (currentScreen) {
            case MARKETPLACE:
                createMarketplaceContainer();
                if (freeMarketContainer != null) {
                    freeMarketContainer.updateButtonStates();
                }
                break;
            case AUCTIONS:
                createAuctionContainer();
                break;
            case LEADERBOARD:
                createLeaderboardContainer();
                break;
        }
    }
    
    /**
     * Creates or recreates the auction container with current screen dimensions.
     */
    private void createAuctionContainer() {
        // Use percentage-based sizing that scales automatically with Minecraft's width/height
        int containerWidth = (int)(width * 0.8); // 80% of screen width
        int containerHeight = (int)(height * 0.7); // 70% of screen height
        int containerX = (width - containerWidth) / 2; // Center horizontally
        int containerY = (height - containerHeight) / 2; // Center vertically
        
        this.auctionContainer = new PlayerAuctionContainer(containerX, containerY, containerWidth, containerHeight, this);
        this.auctionContainer.init();
    }
    
    /**
     * Creates or recreates the leaderboard container with current screen dimensions.
     */
    private void createLeaderboardContainer() {
        // Use percentage-based sizing that scales automatically with Minecraft's width/height
        int containerWidth = (int)(width * 0.8); // 80% of screen width
        int containerHeight = (int)(height * 0.7); // 70% of screen height
        int containerX = (width - containerWidth) / 2; // Center horizontally
        int containerY = (height - containerHeight) / 2; // Center vertically
        
        this.leaderboardContainer = new LeaderboardContainer(containerX, containerY, containerWidth, containerHeight, this);
        this.leaderboardContainer.init();
    }
    
    /**
     * Creates or recreates the marketplace container with current screen dimensions.
     * Uses Minecraft's built-in scaling - width and height automatically scale with window resizing.
     */
    private void createMarketplaceContainer() {
        // Use percentage-based sizing that scales automatically with Minecraft's width/height
        int containerWidth = (int)(width * 0.8); // 80% of screen width
        int containerHeight = (int)(height * 0.7); // 70% of screen height
        int containerX = (width - containerWidth) / 2; // Center horizontally
        int containerY = (height - containerHeight) / 2; // Center vertically
        
        this.freeMarketContainer = new FreeMarketContainer(containerX, containerY, containerWidth, containerHeight, freeMarketItems, this);
        this.freeMarketContainer.init();
    }
    
    @Override
    public void resize(@Nonnull net.minecraft.client.Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        // Recreate the appropriate container with new dimensions
        createContainerForCurrentScreen();
    }
    
    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Don't draw a full-screen overlay - let EMI/JEI panels show through
        // Only draw background behind our marketplace container
        
        // Call super.render() first to handle any background elements
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Draw wallet display in top right of screen
        renderWalletDisplay(guiGraphics);
        
        // Render tab navigation buttons
        renderTabButtons(guiGraphics, mouseX, mouseY);
        
        // Render the appropriate container based on current screen
        switch (currentScreen) {
            case MARKETPLACE:
                if (freeMarketContainer != null) {
                    freeMarketContainer.render(guiGraphics, mouseX, mouseY, partialTick);
                }
                break;
            case AUCTIONS:
                if (auctionContainer != null) {
                    auctionContainer.render(guiGraphics, mouseX, mouseY, partialTick);
                }
                break;
            case LEADERBOARD:
                if (leaderboardContainer != null) {
                    leaderboardContainer.render(guiGraphics, mouseX, mouseY, partialTick);
                }
                break;
        }
        
        // Render popup overlays on top of everything
        if (placeBidPopup != null && placeBidPopup.isVisible()) {
            placeBidPopup.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (cancelAuctionPopup != null && cancelAuctionPopup.isVisible()) {
            cancelAuctionPopup.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }
    
    /**
     * Renders the tab navigation buttons at the top of the container.
     */
    void renderTabButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Calculate container dimensions (same as createMarketplaceContainer)
        int containerWidth = (int)(width * 0.8);
        int containerHeight = (int)(height * 0.7);
        int containerX = (width - containerWidth) / 2;
        int containerY = (height - containerHeight) / 2;
        
        // Tab button dimensions - match container width exactly
        ScreenType[] screens = ScreenType.values();
        int numTabs = screens.length;
        int tabMargin = 4; // Spacing between tabs
        int totalTabArea = containerWidth; // Total width to use for tabs
        int tabWidth = (totalTabArea - (tabMargin * (numTabs - 1))) / numTabs;
        int tabHeight = 24;
        int tabY = containerY - tabHeight - 4; // Position above container
        
        // Render each tab button
        String[] tabLabels = {"Marketplace", "Auctions", "Leaderboard"};
        
        for (int i = 0; i < screens.length; i++) {
            int tabX = containerX + (i * (tabWidth + tabMargin));
            boolean isActive = currentScreen == screens[i];
            boolean isHovered = mouseX >= tabX && mouseX <= tabX + tabWidth && 
                               mouseY >= tabY && mouseY <= tabY + tabHeight;
            
            // Determine colors based on state
            int backgroundColor;
            int textColor;
            
            if (isActive) {
                // Active tab - bright color
                backgroundColor = 0xCC4CAF50; // Green
                textColor = 0xFFFFFFFF; // White
            } else if (isHovered) {
                // Hovered tab - lighter gray
                backgroundColor = 0xCC505050;
                textColor = 0xFFE0E0E0;
            } else {
                // Inactive tab - dark gray
                backgroundColor = 0xCC2A2A2A;
                textColor = 0xFF999999;
            }
            
            // Draw tab background
            guiGraphics.fill(tabX, tabY, tabX + tabWidth, tabY + tabHeight, backgroundColor);
            
            // Draw tab border
            guiGraphics.fill(tabX, tabY, tabX + tabWidth, tabY + 1, 0x80404040); // Top
            guiGraphics.fill(tabX, tabY + 1, tabX + 1, tabY + tabHeight - 1, 0x80404040); // Left
            guiGraphics.fill(tabX + tabWidth - 1, tabY + 1, tabX + tabWidth, tabY + tabHeight - 1, 0x80404040); // Right
            guiGraphics.fill(tabX, tabY + tabHeight - 1, tabX + tabWidth, tabY + tabHeight, 0x80404040); // Bottom
            
            // Draw tab label (centered)
            Component label = Component.literal(tabLabels[i]);
            int labelWidth = this.font.width(label);
            int labelX = tabX + (tabWidth - labelWidth) / 2;
            int labelY = tabY + (tabHeight - this.font.lineHeight) / 2;
            guiGraphics.drawString(this.font, label, labelX, labelY, textColor);
        }
    }
    
    void renderWalletDisplay(GuiGraphics guiGraphics) {
        // Draw wallet display in top right corner with percentage-based positioning
        long money = cachedBalance; // Use only cached balance - no polling
        String formattedMoney = "$" + formatPrice(money);
        
        // Create title and money components
        Component titleText = Component.literal("Balance:");
        Component walletText = Component.literal(formattedMoney);
        
        // Calculate text widths
        int titleWidth = this.font.width(titleText);
        int moneyWidth = this.font.width(walletText);
        int maxTextWidth = Math.max(titleWidth, moneyWidth);
        
        // Calculate background box dimensions using percentage-based scaling
        int paddingX = (int)(width * 0.01); // 1% of screen width for horizontal padding
        int paddingY = (int)(height * 0.008); // 0.8% of screen height for vertical padding
        
        // Width: text width plus horizontal padding (stretches to fit text)
        int backgroundWidth = maxTextWidth + (paddingX * 2);
        
        // Height: percentage-based, but ensure it fits both text lines with padding
        int minHeightForText = (paddingY * 2) + (this.font.lineHeight * 2) + 4; // 4px spacing between lines
        int backgroundHeight = Math.max((int)(height * 0.035), minHeightForText); // 3.5% of screen height or min required
        
        // Position in top-right corner using percentage-based positioning
        int widgetMarginX = (int)(width * 0.03); // 3% margin from edges
        int widgetMarginY = (int)(height * 0.03); // 3% margin from edges
        int backgroundX = width - backgroundWidth - widgetMarginX; // Right edge
        int backgroundY = widgetMarginY; // Top edge
        
        // Draw background box with semi-transparent colors (matching container)
        guiGraphics.fill(backgroundX, backgroundY, backgroundX + backgroundWidth, backgroundY + backgroundHeight, 0x801E1E1E); // 50% opacity
        guiGraphics.fill(backgroundX + 1, backgroundY + 1, backgroundX + backgroundWidth - 1, backgroundY + backgroundHeight - 1, 0x802A2A2A); // 50% opacity
        
        // Draw border
        guiGraphics.fill(backgroundX, backgroundY, backgroundX + backgroundWidth, backgroundY + 2, 0x80404040);
        guiGraphics.fill(backgroundX, backgroundY + 2, backgroundX + 2, backgroundY + backgroundHeight - 2, 0x80404040);
        guiGraphics.fill(backgroundX + backgroundWidth - 2, backgroundY + 2, backgroundX + backgroundWidth, backgroundY + backgroundHeight - 2, 0x80404040);
        guiGraphics.fill(backgroundX, backgroundY + backgroundHeight - 2, backgroundX + backgroundWidth, backgroundY + backgroundHeight, 0x80404040);
        
        // Calculate text positions (centered within background box)
        int titleX = backgroundX + (backgroundWidth - titleWidth) / 2;
        int moneyX = backgroundX + (backgroundWidth - moneyWidth) / 2;
        
        // Calculate vertical centering
        int titleY = backgroundY + paddingY;
        int moneyY = backgroundY + backgroundHeight - paddingY - this.font.lineHeight;
        
        // Draw title (centered horizontally and vertically)
        guiGraphics.drawString(this.font, titleText, titleX, titleY, 0xFFFFFFFF);
        
        // Draw wallet text (centered horizontally and vertically)
        guiGraphics.drawString(this.font, walletText, moneyX, moneyY, 0xFF4CAF50);
    }
    
    /**
     * Formats a price number to be shorter for display with intelligent decimal handling.
     * Only abbreviates when there are trailing zeros, otherwise shows full number.
     * Examples: 1000 -> 1K, 1001 -> 1001, 1100000 -> 1.1M, 1000001 -> 1000001
     */
    private String formatPrice(long price) {
        if (price < 1000) {
            return String.valueOf(price);
        } else if (price < 1000000) {
            // Thousands - only abbreviate if all trailing digits are zero
            if (price % 1000 == 0) {
                double thousands = price / 1000.0;
                if (thousands == Math.floor(thousands)) {
                    return String.format("%.0fK", thousands);
                } else {
                    return String.format("%.1fK", thousands);
                }
            } else {
                // Has non-zero trailing digits, show full number
                return String.valueOf(price);
            }
        } else if (price < 1000000000) {
            // Millions - only abbreviate if all trailing digits are zero
            if (price % 1000000 == 0) {
                double millions = price / 1000000.0;
                if (millions == Math.floor(millions)) {
                    return String.format("%.0fM", millions);
                } else {
                    return String.format("%.1fM", millions);
                }
            } else {
                // Has non-zero trailing digits, show full number
                return String.valueOf(price);
            }
        } else if (price < 1000000000000L) {
            // Billions - only abbreviate if all trailing digits are zero
            if (price % 1000000000 == 0) {
                double billions = price / 1000000000.0;
                if (billions == Math.floor(billions)) {
                    return String.format("%.0fB", billions);
                } else {
                    return String.format("%.1fB", billions);
                }
            } else {
                // Has non-zero trailing digits, show full number
                return String.valueOf(price);
            }
        } else {
            // Trillions - only abbreviate if all trailing digits are zero
            if (price % 1000000000000L == 0) {
                double trillions = price / 1000000000000.0;
                if (trillions == Math.floor(trillions)) {
                    return String.format("%.0fT", trillions);
                } else {
                    return String.format("%.1fT", trillions);
                }
            } else {
                // Has non-zero trailing digits, show full number
                return String.valueOf(price);
            }
        }
    }
    
    /**
     * Refreshes the GUI to update button visibility based on admin mode.
     * Call this when admin mode changes.
     */
    public void refreshAdminMode() {
        // Clear existing widgets
        this.clearWidgets();
        
        // Reinitialize to add/remove the plus button based on admin mode
        this.init();
    }
    
    @Override
    public boolean isPauseScreen() {
        return false; // Don't pause the game when GUI is open
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return true; // Close GUI when ESC is pressed
    }
    
        @Override
        public void onClose() {
            super.onClose();
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle popup overlay clicks first (highest priority)
        if (placeBidPopup != null && placeBidPopup.isVisible()) {
            if (placeBidPopup.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        
        if (cancelAuctionPopup != null && cancelAuctionPopup.isVisible()) {
            if (cancelAuctionPopup.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        
        // Don't process tab clicks or container clicks if popup is visible
        if (isAnyPopupVisible()) {
            return false; // Let popup handle all clicks
        }
        
        // Check if tab button was clicked
        if (handleTabClick(mouseX, mouseY)) {
            return true;
        }
        
        // Route to appropriate container
        switch (currentScreen) {
            case MARKETPLACE:
                if (freeMarketContainer != null && freeMarketContainer.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                break;
            case AUCTIONS:
                if (auctionContainer != null && auctionContainer.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                break;
            case LEADERBOARD:
                if (leaderboardContainer != null && leaderboardContainer.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                break;
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    /**
     * Handles clicks on tab buttons.
     * @return true if a tab was clicked
     */
    private boolean handleTabClick(double mouseX, double mouseY) {
        // Calculate container dimensions (same as createMarketplaceContainer)
        int containerWidth = (int)(width * 0.8);
        int containerHeight = (int)(height * 0.7);
        int containerX = (width - containerWidth) / 2;
        int containerY = (height - containerHeight) / 2;
        
        // Tab button dimensions - match container width exactly
        ScreenType[] screens = ScreenType.values();
        int numTabs = screens.length;
        int tabMargin = 4;
        int totalTabArea = containerWidth; // Total width to use for tabs
        int tabWidth = (totalTabArea - (tabMargin * (numTabs - 1))) / numTabs;
        int tabHeight = 24;
        int tabY = containerY - tabHeight - 4;
        
        // Check each tab button
        for (int i = 0; i < screens.length; i++) {
            int tabX = containerX + (i * (tabWidth + tabMargin));
            
            if (mouseX >= tabX && mouseX <= tabX + tabWidth && 
                mouseY >= tabY && mouseY <= tabY + tabHeight) {
                // Tab clicked - switch screen
                switchScreen(screens[i]);
                // Play click sound
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                }
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle popup overlay key presses first (highest priority)
        if (placeBidPopup != null && placeBidPopup.isVisible()) {
            if (placeBidPopup.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        
        if (cancelAuctionPopup != null && cancelAuctionPopup.isVisible()) {
            if (cancelAuctionPopup.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        
        // Route to appropriate container
        switch (currentScreen) {
            case MARKETPLACE:
                if (freeMarketContainer != null && freeMarketContainer.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
                break;
            case AUCTIONS:
                if (auctionContainer != null && auctionContainer.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
                break;
            case LEADERBOARD:
                if (leaderboardContainer != null && leaderboardContainer.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
                break;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // Handle popup overlay character typing first (highest priority)
        if (placeBidPopup != null && placeBidPopup.isVisible()) {
            if (placeBidPopup.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        
        if (cancelAuctionPopup != null && cancelAuctionPopup.isVisible()) {
            if (cancelAuctionPopup.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        
        // Route to appropriate container
        switch (currentScreen) {
            case MARKETPLACE:
                if (freeMarketContainer != null && freeMarketContainer.charTyped(codePoint, modifiers)) {
                    return true;
                }
                break;
            case AUCTIONS:
                if (auctionContainer != null && auctionContainer.charTyped(codePoint, modifiers)) {
                    return true;
                }
                break;
            case LEADERBOARD:
                if (leaderboardContainer != null && leaderboardContainer.charTyped(codePoint, modifiers)) {
                    return true;
                }
                break;
        }
        return super.charTyped(codePoint, modifiers);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        // Route to appropriate container
        switch (currentScreen) {
            case MARKETPLACE:
                if (freeMarketContainer != null) {
                    // Scroll by single row (same as auction container)
                    int scrollAmount = (int) -deltaY;
                    freeMarketContainer.scroll(scrollAmount);
                    return true;
                }
                break;
            case AUCTIONS:
                if (auctionContainer != null) {
                    // Scroll by single row (same as marketplace container)
                    int scrollAmount = (int) -deltaY;
                    auctionContainer.scroll(scrollAmount);
                    return true;
                }
                break;
            case LEADERBOARD:
                if (leaderboardContainer != null) {
                    int scrollAmount = (int) (-deltaY * 1); // Single row scrolling for leaderboard
                    leaderboardContainer.scroll(scrollAmount);
                    return true;
                }
                break;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }
}
