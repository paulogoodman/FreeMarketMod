package com.servershop.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

import com.servershop.ServerShop;
import com.servershop.client.data.ClientMarketplaceDataManager;
import com.servershop.common.data.MarketplaceItem;
import com.servershop.common.handlers.WalletHandler;

/**
 * Semi-transparent dark overlay GUI for the ServerShop mod.
 * Opens with the O keybind and displays a dark overlay with conditional admin button and marketplace.
 */
public class ShopGuiScreen extends Screen {
    
    private List<MarketplaceItem> marketplaceItems;
    private MarketplaceContainer marketplaceContainer;
    
    public ShopGuiScreen() {
        super(Component.translatable("gui.servershop.shop.title"));
        this.marketplaceItems = new ArrayList<>();
        loadMarketplaceItemsFromFile();
    }
    
    private void loadMarketplaceItemsFromFile() {
        // Load marketplace items from JSON file
        List<MarketplaceItem> loadedItems = ClientMarketplaceDataManager.loadMarketplaceItems();
        this.marketplaceItems = loadedItems;
        ServerShop.LOGGER.info("Loaded {} marketplace items from JSON file", loadedItems.size());
    }
    
    
    @Override
    protected void init() {
        super.init();
        
        // Refresh marketplace items from file in case they were updated
        loadMarketplaceItemsFromFile();
        
        // Plus button is now handled inside the marketplace container
        
        // Create the marketplace container with responsive positioning
        int containerWidth = GuiScalingHelper.responsiveWidth(600, 400, 800);
        int containerHeight = GuiScalingHelper.responsiveHeight(400, 300, 600);
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
        long money = WalletHandler.getPlayerMoney();
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
        
        /**
         * Refreshes the marketplace items from the JSON file.
         * Call this when returning from the add item popup.
         */
        public void refreshMarketplace() {
            loadMarketplaceItemsFromFile();
            if (marketplaceContainer != null) {
                // Update the container's items list
                marketplaceContainer.updateItems(marketplaceItems);
            }
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
