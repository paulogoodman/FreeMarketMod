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
        
        // Create the marketplace container with proper positioning
        int containerWidth = Math.min(600, this.width - 100);
        int containerHeight = Math.min(400, this.height - 150);
        int containerX = (this.width - containerWidth) / 2;
        int containerY = (this.height - containerHeight) / 2;
        
        this.marketplaceContainer = new MarketplaceContainer(containerX, containerY, containerWidth, containerHeight, marketplaceItems, this);
        this.marketplaceContainer.init();
    }
    
    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw a semi-transparent dark rectangle covering the entire screen
        int overlayAlpha = 150; // Semi-transparent (0-255)
        guiGraphics.fill(0, 0, this.width, this.height, (overlayAlpha << 24) | 0x000000);
        
        // Call super.render() first to handle any background elements
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Draw title at the top center (after super.render to ensure it's on top)
        int titleWidth = this.font.width(this.title);
        int titleX = (this.width - titleWidth) / 2;
        int titleY = 20;
        guiGraphics.drawString(this.font, this.title, titleX, titleY, 0xFFFFFF);
        
        // Draw wallet display in top right of screen
        renderWalletDisplay(guiGraphics);
        
        // Wallet display is now handled inside the marketplace container
        
        // Render marketplace container
        if (marketplaceContainer != null) {
            marketplaceContainer.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }
    
    private void renderWalletDisplay(GuiGraphics guiGraphics) {
        // Draw wallet display in top right of screen with background
        Component walletText = Component.translatable("gui.servershop.wallet", WalletHandler.getPlayerMoney());
        int walletWidth = this.font.width(walletText);
        int walletX = this.width - walletWidth - 20;
        int walletY = 20;
        
        // Draw background box behind wallet
        guiGraphics.fill(walletX - 5, walletY - 2, walletX + walletWidth + 5, walletY + 12, 0xFF1A1A1A);
        guiGraphics.fill(walletX - 4, walletY - 1, walletX + walletWidth + 4, walletY + 11, 0xFF2D2D2D);
        
        // Draw wallet text
        guiGraphics.drawString(this.font, walletText, walletX, walletY, 0xFF4CAF50);
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
