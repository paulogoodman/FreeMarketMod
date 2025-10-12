package com.servershop.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

import com.servershop.ServerShop;
import com.servershop.client.data.ClientMarketplaceDataManager;
import com.servershop.common.data.MarketplaceItem;
import com.servershop.common.handlers.WalletHandler;
import com.servershop.server.data.MarketplaceDataManager;

/**
 * Semi-transparent dark overlay GUI for the ServerShop mod.
 * Opens with the O keybind and displays a dark overlay with conditional admin button and marketplace.
 */
public class ShopGuiScreen extends Screen {
    
    private List<MarketplaceItem> marketplaceItems;
    private MarketplaceContainer marketplaceContainer;
    
    // Cache wallet balance to avoid retrieving it every frame
    private long cachedBalance = 0;
    private long lastBalanceUpdate = 0;
    private static final long BALANCE_CACHE_DURATION = 1000; // Update every 1 second
    
    public ShopGuiScreen() {
        super(Component.translatable("gui.servershop.shop.title"));
        this.marketplaceItems = new ArrayList<>();
        // Don't load items here - let init() handle it with caching
    }
    
    private void loadMarketplaceItemsFromFile() {
        // Try to use server-side loading first (with SavedData attachments)
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSingleplayerServer() != null) {
            try {
                ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
                if (serverLevel != null) {
                    List<MarketplaceItem> serverItems = MarketplaceDataManager.loadMarketplaceItems(serverLevel);
                    this.marketplaceItems = serverItems;
                    return;
                }
            } catch (Exception e) {
                // Failed to load from server-side, will fall back to client-side
            }
        }
        
        // Fallback to client-side loading (for multiplayer or when server is not available)
        List<MarketplaceItem> loadedItems = ClientMarketplaceDataManager.loadMarketplaceItems();
        this.marketplaceItems = loadedItems;
    }
    
    /**
     * Gets the cached wallet balance, updating it only if the cache has expired.
     * This prevents excessive wallet balance retrieval during rendering.
     */
    private long getCachedBalance() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBalanceUpdate > BALANCE_CACHE_DURATION) {
            cachedBalance = WalletHandler.getPlayerMoney();
            lastBalanceUpdate = currentTime;
        }
        return cachedBalance;
    }
    
    /**
     * Forces a refresh of the cached balance.
     * Call this after transactions to ensure the display is up-to-date.
     */
    public void refreshBalance() {
        cachedBalance = WalletHandler.getPlayerMoney();
        lastBalanceUpdate = System.currentTimeMillis();
    }
    
    /**
     * Forces a refresh of the marketplace data.
     * Invalidates the cache and reloads from file.
     * @param preserveScrollPosition If true, preserves the current scroll position
     */
    public void refreshMarketplace(boolean preserveScrollPosition) {
        // Try to use server-side loading first (with SavedData attachments)
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSingleplayerServer() != null) {
            try {
                ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
                if (serverLevel != null) {
                    List<MarketplaceItem> serverItems = MarketplaceDataManager.loadMarketplaceItems(serverLevel);
                    this.marketplaceItems = serverItems;
                    
                    // Update the marketplace container with new data
                    if (marketplaceContainer != null) {
                        marketplaceContainer.updateMarketplaceItems(marketplaceItems, preserveScrollPosition);
                    }
                    return;
                }
            } catch (Exception e) {
                // Failed to refresh from server-side, will fall back to client-side
            }
        }
        
        // Fallback to client-side loading
        ClientMarketplaceDataManager.invalidateCache();
        loadMarketplaceItemsFromFile();
        
        // Update the marketplace container with new data
        if (marketplaceContainer != null) {
            marketplaceContainer.updateMarketplaceItems(marketplaceItems, preserveScrollPosition);
        }
    }
    
    /**
     * Forces a refresh of the marketplace data (default behavior - resets scroll).
     * Invalidates the cache and reloads from file.
     */
    public void refreshMarketplace() {
        refreshMarketplace(false);
    }
    
    
    @Override
    protected void init() {
        super.init();
        
        // Refresh marketplace items from file in case they were updated
        loadMarketplaceItemsFromFile();
        
        // Plus button is now handled inside the marketplace container
        
        // Create the marketplace container with responsive positioning
        int containerWidth = GuiScalingHelper.responsiveWidth(600, 400, 800);
        int containerHeight = GuiScalingHelper.responsiveHeight(450, 350, 650); // Increased height for better spacing
        int containerX = GuiScalingHelper.centerX(containerWidth);
        int containerY = GuiScalingHelper.centerY(containerHeight);
        
        this.marketplaceContainer = new MarketplaceContainer(containerX, containerY, containerWidth, containerHeight, marketplaceItems, this);
        this.marketplaceContainer.init();
    }
    
    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Don't draw a full-screen overlay - let EMI/JEI panels show through
        // Only draw background behind our marketplace container
        
        // Call super.render() first to handle any background elements
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Draw wallet display in top right of screen
        renderWalletDisplay(guiGraphics);
        
        // Render marketplace container (it will draw its own background)
        if (marketplaceContainer != null) {
            marketplaceContainer.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }
    
    private void renderWalletDisplay(GuiGraphics guiGraphics) {
        // Draw wallet display in top right of screen with background
        long money = getCachedBalance(); // Use cached balance instead of retrieving every frame
        String formattedMoney = String.format("$%,d", money);
        
        // Create title and money components
        Component titleText = Component.literal("Balance:");
        Component walletText = Component.literal(formattedMoney);
        
        // Calculate text widths
        int titleWidth = this.font.width(titleText);
        int moneyWidth = this.font.width(walletText);
        int maxTextWidth = Math.max(titleWidth, moneyWidth);
        
        // Calculate background box dimensions first
        int marginX = GuiScalingHelper.responsiveWidth(15, 10, 20);
        int marginY = GuiScalingHelper.responsiveHeight(8, 6, 12);
        int backgroundWidth = maxTextWidth + (marginX * 2);
        int backgroundHeight = GuiScalingHelper.responsiveHeight(40, 30, 50); // Fixed height for better centering
        
        // Position background box in top right
        int backgroundX = GuiScalingHelper.percentageX(0.85f) - backgroundWidth; // 85% from left, minus width
        int backgroundY = GuiScalingHelper.responsiveHeight(15, 10, 25);
        
        // Draw background box
        guiGraphics.fill(backgroundX, backgroundY, backgroundX + backgroundWidth, backgroundY + backgroundHeight, 0xFF1A1A1A);
        guiGraphics.fill(backgroundX + 1, backgroundY + 1, backgroundX + backgroundWidth - 1, backgroundY + backgroundHeight - 1, 0xFF2D2D2D);
        
        // Calculate text positions (centered within background box)
        int titleX = backgroundX + (backgroundWidth - titleWidth) / 2;
        int moneyX = backgroundX + (backgroundWidth - moneyWidth) / 2;
        
        // Calculate vertical centering
        int titleY = backgroundY + marginY;
        int moneyY = backgroundY + backgroundHeight - marginY - GuiScalingHelper.responsiveHeight(8, 6, 12);
        
        // Draw title (centered horizontally and vertically)
        guiGraphics.drawString(this.font, titleText, titleX, titleY, 0xFFFFFFFF);
        
        // Draw wallet text (centered horizontally and vertically)
        guiGraphics.drawString(this.font, walletText, moneyX, moneyY, 0xFF4CAF50);
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
        if (marketplaceContainer != null && marketplaceContainer.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (marketplaceContainer != null && marketplaceContainer.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (marketplaceContainer != null && marketplaceContainer.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (marketplaceContainer != null) {
            // Use smoother scrolling with smaller increments
            int scrollAmount = (int) (-deltaY * 2); // Multiply by 2 for smoother scrolling
            marketplaceContainer.scroll(scrollAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }
}
