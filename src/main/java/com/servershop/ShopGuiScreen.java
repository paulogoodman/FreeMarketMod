package com.servershop;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Semi-transparent dark overlay GUI for the ServerShop mod.
 * Opens with the O keybind and displays a dark overlay with conditional admin button and marketplace.
 */
public class ShopGuiScreen extends Screen {
    
    private Button plusButton;
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
        
        this.marketplaceContainer = new MarketplaceContainer(containerX, containerY, containerWidth, containerHeight, marketplaceItems);
        this.marketplaceContainer.init();
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
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
        
        // Wallet display is now handled inside the marketplace container
        
        // Draw admin mode status if enabled (smaller, top right)
        if (AdminModeHandler.isAdminMode()) {
            Component adminStatus = Component.translatable("gui.servershop.admin_mode");
            int adminWidth = this.font.width(adminStatus);
            int adminX = this.width - adminWidth - 20;
            guiGraphics.drawString(this.font, adminStatus, adminX, 20, 0x00FF00);
        }
        
        // Render marketplace container
        if (marketplaceContainer != null) {
            marketplaceContainer.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }
    
    /**
     * Draws the marketplace section with items for sale.
     */
    private void drawMarketplace(GuiGraphics guiGraphics) {
        int marketplaceY = 60;
        int marketplaceX = 50;
        
        // Draw marketplace title
        Component marketplaceTitle = Component.translatable("gui.servershop.marketplace.title");
        guiGraphics.drawString(this.font, marketplaceTitle, marketplaceX, marketplaceY, 0xFFFFFF);
        
        // Draw marketplace items
        int itemY = marketplaceY + 25;
        int maxItemsPerRow = 3;
        int itemSpacing = 120;
        int itemHeight = 80;
        
        for (int i = 0; i < marketplaceItems.size(); i++) {
            MarketplaceItem item = marketplaceItems.get(i);
            int itemX = marketplaceX + (i % maxItemsPerRow) * itemSpacing;
            int currentItemY = itemY + (i / maxItemsPerRow) * itemHeight;
            
            // Draw item background
            guiGraphics.fill(itemX - 2, currentItemY - 2, itemX + 110, currentItemY + 70, 0x80000000);
            guiGraphics.fill(itemX - 1, currentItemY - 1, itemX + 109, currentItemY + 69, 0x80404040);
            
            // Draw item icon
            guiGraphics.renderItem(item.getItemStack(), itemX, currentItemY);
            guiGraphics.renderItemDecorations(this.font, item.getItemStack(), itemX, currentItemY);
            
            // Draw item name
            String itemName = item.getItemName();
            if (itemName.length() > 10) {
                itemName = itemName.substring(0, 10) + "...";
            }
            guiGraphics.drawString(this.font, itemName, itemX, currentItemY + 18, 0xFFFFFF);
            
            // Draw buy price
            guiGraphics.drawString(this.font, "Buy: " + item.getBuyPrice(), itemX, currentItemY + 30, 0x00FF00);
            
            // Draw sell price
            guiGraphics.drawString(this.font, "Sell: " + item.getSellPrice(), itemX, currentItemY + 40, 0xFF6600);
            
            // Draw quantity
            guiGraphics.drawString(this.font, "Stock: " + item.getQuantity(), itemX, currentItemY + 50, 0xCCCCCC);
            
            // Draw buy/sell buttons (simplified as text for now)
            boolean canBuy = WalletHandler.hasEnoughMoney(item.getBuyPrice());
            boolean canSell = true; // Assume player has items to sell
            
            guiGraphics.drawString(this.font, "[Buy]", itemX + 60, currentItemY + 30, canBuy ? 0x00FF00 : 0x666666);
            guiGraphics.drawString(this.font, "[Sell]", itemX + 60, currentItemY + 40, canSell ? 0xFF6600 : 0x666666);
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
