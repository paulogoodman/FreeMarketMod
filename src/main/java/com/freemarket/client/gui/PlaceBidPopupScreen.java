package com.freemarket.client.gui;

import com.freemarket.FreeMarket;
import com.freemarket.client.handlers.ClientWalletHandler;
import com.freemarket.common.data.PlayerAuction;
import com.freemarket.common.network.FreeMarketPacket;
import com.freemarket.common.network.PacketType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;

/**
 * Modern popup screen for placing bids on auctions with improved UI design.
 */
public class PlaceBidPopupScreen extends Screen {
    
    private final PlayerAuction auction;
    private final FreeMarketGuiScreen parentScreen;
    private EditBox bidAmountBox;
    private Button placeBidButton;
    private Button cancelButton;
    private String errorMessage = null;
    
    // Bid validation
    private long minimumBid;
    private long playerBalance;
    
    // UI Constants for modern design
    private static final int POPUP_WIDTH = 420;
    private static final int POPUP_HEIGHT = 320;
    private static final int HEADER_HEIGHT = 40;
    private static final int SECTION_SPACING = 8;
    private static final int BORDER_RADIUS = 6;
    
    // Color scheme
    private static final int BACKGROUND_COLOR = 0xFF1A1A1A;
    private static final int SURFACE_COLOR = 0xFF2D2D2D;
    private static final int BORDER_COLOR = 0xFF404040;
    private static final int ACCENT_COLOR = 0xFF4CAF50;
    private static final int ERROR_COLOR = 0xFFFF6B6B;
    private static final int SUCCESS_COLOR = 0xFF4CAF50;
    private static final int WARNING_COLOR = 0xFFFFB74D;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFB0B0B0;
    private static final int TEXT_MUTED = 0xFF808080;
    
    public PlaceBidPopupScreen(PlayerAuction auction, FreeMarketGuiScreen parent) {
        super(Component.literal("Place Bid"));
        this.auction = auction;
        this.parentScreen = parent;
        
        // Calculate minimum bid using the same logic as server
        this.minimumBid = auction.getMinimumBid();
        
        this.playerBalance = ClientWalletHandler.getPlayerMoney();
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Calculate popup dimensions (centered)
        int popupX = (this.width - POPUP_WIDTH) / 2;
        int popupY = (this.height - POPUP_HEIGHT) / 2;
        
        // Calculate component positions with proper spacing
        int inputY = popupY + POPUP_HEIGHT - 100; // Position near bottom
        int buttonY = popupY + POPUP_HEIGHT - 50;
        
        // Bid amount input with modern styling
        this.bidAmountBox = new EditBox(
            this.font,
            popupX + 30,
            inputY,
            POPUP_WIDTH - 60,
            24,
            Component.literal("Enter bid amount")
        );
        this.bidAmountBox.setValue(String.valueOf(minimumBid));
        this.bidAmountBox.setResponder(this::onBidAmountChanged);
        this.bidAmountBox.setFilter(text -> text.matches("\\d*")); // Only allow digits
        this.bidAmountBox.setMaxLength(10); // Limit to reasonable bid amounts
        this.bidAmountBox.setHint(Component.literal("Minimum: $" + minimumBid));
        this.addRenderableWidget(this.bidAmountBox);
        
        // Place Bid button with modern styling
        this.placeBidButton = Button.builder(
            Component.literal("✓ Place Bid"),
            button -> placeBid()
        ).bounds(
            popupX + 30,
            buttonY,
            160,
            28
        ).build();
        this.addRenderableWidget(this.placeBidButton);
        
        // Cancel button with modern styling
        this.cancelButton = Button.builder(
            Component.literal("✗ Cancel"),
            button -> onClose()
        ).bounds(
            popupX + 230,
            buttonY,
            160,
            28
        ).build();
        this.addRenderableWidget(this.cancelButton);
    }
    
    private void onBidAmountChanged(String bidStr) {
        errorMessage = null; // Clear error on input change
    }
    
    private void placeBid() {
        String bidStr = this.bidAmountBox.getValue();
        
        // Validate input
        if (bidStr.isEmpty()) {
            errorMessage = "Please enter a bid amount";
            return;
        }
        
        long bidAmount;
        try {
            bidAmount = Long.parseLong(bidStr);
        } catch (NumberFormatException e) {
            errorMessage = "Invalid bid amount";
            return;
        }
        
        // Validate bid amount
        if (bidAmount < minimumBid) {
            if (auction.getCurrentBid() == auction.getStartingPrice()) {
                // First bid must be at least the starting price
                errorMessage = "Bid must be at least the starting price of $" + auction.getStartingPrice();
            } else {
                // Subsequent bids must be higher than current bid
                errorMessage = "Bid must be higher than the current bid of $" + auction.getCurrentBid();
            }
            return;
        }
        
        if (bidAmount > playerBalance) {
            errorMessage = "Insufficient funds";
            return;
        }
        
        // Send bid packet to server
        String jsonData = String.format("{\"auctionId\":\"%s\",\"bidAmount\":%d}", auction.getAuctionId(), bidAmount);
        FreeMarketPacket packet = FreeMarketPacket.withJson(PacketType.AUCTION_BID, jsonData);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
        
        FreeMarket.LOGGER.info("Placed bid of ${} on auction {}", bidAmount, auction.getAuctionId());
        
        // Close popup and return to auction screen
        onClose();
    }
    
    /**
     * Renders only the background elements of the parent screen without widgets.
     * This prevents z-order issues where item cards render on top of popups.
     */
    private void renderParentBackground(GuiGraphics guiGraphics, float partialTick) {
        // Draw wallet display in top right of screen
        parentScreen.renderWalletDisplay(guiGraphics);
        
        // Render tab navigation buttons
        parentScreen.renderTabButtons(guiGraphics, -1, -1);
        
        // Render the appropriate container based on current screen (background only)
        switch (parentScreen.getCurrentScreen()) {
            case MARKETPLACE:
                if (parentScreen.freeMarketContainer != null) {
                    parentScreen.freeMarketContainer.render(guiGraphics, -1, -1, partialTick);
                }
                break;
            case AUCTIONS:
                if (parentScreen.auctionContainer != null) {
                    parentScreen.auctionContainer.render(guiGraphics, -1, -1, partialTick);
                }
                break;
            case LEADERBOARD:
                if (parentScreen.leaderboardContainer != null) {
                    parentScreen.leaderboardContainer.render(guiGraphics, -1, -1, partialTick);
                }
                break;
        }
    }
    
    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Calculate popup dimensions
        int popupX = (this.width - POPUP_WIDTH) / 2;
        int popupY = (this.height - POPUP_HEIGHT) / 2;
        
        // Render only the background elements of the parent screen (not widgets)
        if (parentScreen != null) {
            renderParentBackground(guiGraphics, partialTick);
        }
        
        // Draw backdrop overlay with blur effect
        guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);
        
        // Draw main popup background with rounded corners effect
        drawRoundedRect(guiGraphics, popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT, SURFACE_COLOR);
        
        // Draw header section
        drawRoundedRect(guiGraphics, popupX, popupY, POPUP_WIDTH, HEADER_HEIGHT, BACKGROUND_COLOR);
        
        // Draw header border
        guiGraphics.fill(popupX, popupY + HEADER_HEIGHT - 1, popupX + POPUP_WIDTH, popupY + HEADER_HEIGHT, BORDER_COLOR);
        
        // Draw title with icon
        Component title = Component.literal("🎯 Place Bid");
        int titleWidth = this.font.width(title);
        int titleX = popupX + (POPUP_WIDTH - titleWidth) / 2;
        int titleY = popupY + (HEADER_HEIGHT - this.font.lineHeight) / 2;
        guiGraphics.drawString(this.font, title, titleX, titleY, TEXT_PRIMARY);
        
        // Draw content sections
        int contentY = popupY + HEADER_HEIGHT + SECTION_SPACING;
        drawAuctionInfo(guiGraphics, popupX, contentY);
        
        // Draw bid input section
        drawBidInputSection(guiGraphics, popupX, popupY);
        
        // Draw error message if present
        if (errorMessage != null) {
            drawErrorMessage(guiGraphics, popupX, popupY);
        }
        
        // Call super.render() to draw widgets
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private void drawRoundedRect(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        // Draw main rectangle
        guiGraphics.fill(x + BORDER_RADIUS, y, x + width - BORDER_RADIUS, y + height, color);
        guiGraphics.fill(x, y + BORDER_RADIUS, x + width, y + height - BORDER_RADIUS, color);
        
        // Draw rounded corners (simplified)
        guiGraphics.fill(x + BORDER_RADIUS, y + BORDER_RADIUS, x + width - BORDER_RADIUS, y + height - BORDER_RADIUS, color);
    }
    
    private void drawAuctionInfo(GuiGraphics guiGraphics, int popupX, int startY) {
        int currentY = startY;
        int lineHeight = this.font.lineHeight + 4;
        int sectionWidth = POPUP_WIDTH - 60;
        int sectionX = popupX + 30;
        
        // Section title
        Component sectionTitle = Component.literal("📋 Auction Details");
        guiGraphics.drawString(this.font, sectionTitle, sectionX, currentY, ACCENT_COLOR);
        currentY += lineHeight + 4;
        
        // Draw info cards
        drawInfoCard(guiGraphics, sectionX, currentY, sectionWidth, "💰 Starting Price", "$" + auction.getStartingPrice(), SUCCESS_COLOR);
        currentY += 35;
        
        drawInfoCard(guiGraphics, sectionX, currentY, sectionWidth, "🏆 Current Bid", "$" + auction.getCurrentBid(), WARNING_COLOR);
        currentY += 35;
        
        // Bidder info
        String bidderInfo = auction.getBidderName() != null && !auction.getBidderName().isEmpty() 
            ? "👤 " + auction.getBidderName() 
            : "❌ No bids yet";
        drawInfoCard(guiGraphics, sectionX, currentY, sectionWidth, "🎯 Last Bidder", bidderInfo, TEXT_SECONDARY);
        currentY += 35;
        
        // Seller info
        drawInfoCard(guiGraphics, sectionX, currentY, sectionWidth, "🏪 Listed by", auction.getSellerName(), TEXT_SECONDARY);
        currentY += 35;
        
        // Minimum bid requirement
        String minBidText = auction.getCurrentBid() == auction.getStartingPrice() 
            ? "$" + auction.getStartingPrice() + " (starting price)"
            : "$" + (auction.getCurrentBid() + 1) + " (must exceed current bid)";
        drawInfoCard(guiGraphics, sectionX, currentY, sectionWidth, "⚡ Minimum Bid", minBidText, ACCENT_COLOR);
        currentY += 35;
        
        // Player balance with status indicator
        String balanceStatus = playerBalance >= minimumBid ? "✅ Sufficient" : "❌ Insufficient";
        int balanceColor = playerBalance >= minimumBid ? SUCCESS_COLOR : ERROR_COLOR;
        drawInfoCard(guiGraphics, sectionX, currentY, sectionWidth, "💳 Your Balance", "$" + playerBalance + " - " + balanceStatus, balanceColor);
    }
    
    private void drawInfoCard(GuiGraphics guiGraphics, int x, int y, int width, String label, String value, int valueColor) {
        // Card background
        guiGraphics.fill(x, y, x + width, y + 30, 0x1AFFFFFF);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + 29, BACKGROUND_COLOR);
        
        // Label
        guiGraphics.drawString(this.font, label, x + 8, y + 8, TEXT_MUTED);
        
        // Value
        guiGraphics.drawString(this.font, value, x + 8, y + 18, valueColor);
    }
    
    private void drawBidInputSection(GuiGraphics guiGraphics, int popupX, int popupY) {
        int inputY = popupY + POPUP_HEIGHT - 100;
        int sectionX = popupX + 30;
        int sectionWidth = POPUP_WIDTH - 60;
        
        // Section title
        Component inputTitle = Component.literal("💵 Enter Your Bid");
        guiGraphics.drawString(this.font, inputTitle, sectionX, inputY - 25, TEXT_PRIMARY);
        
        // Input field background
        guiGraphics.fill(sectionX, inputY - 2, sectionX + sectionWidth, inputY + 26, BORDER_COLOR);
        guiGraphics.fill(sectionX + 1, inputY - 1, sectionX + sectionWidth - 1, inputY + 25, SURFACE_COLOR);
        
        // Dollar sign prefix
        guiGraphics.drawString(this.font, "$", sectionX + 8, inputY + 6, TEXT_SECONDARY);
    }
    
    private void drawErrorMessage(GuiGraphics guiGraphics, int popupX, int popupY) {
        int errorY = popupY + POPUP_HEIGHT - 25;
        int errorWidth = this.font.width(errorMessage);
        int errorX = popupX + (POPUP_WIDTH - errorWidth) / 2;
        
        // Error background
        guiGraphics.fill(errorX - 8, errorY - 2, errorX + errorWidth + 8, errorY + this.font.lineHeight + 2, 0x4DFF6B6B);
        guiGraphics.fill(errorX - 7, errorY - 1, errorX + errorWidth + 7, errorY + this.font.lineHeight + 1, BACKGROUND_COLOR);
        
        // Error text
        guiGraphics.drawString(this.font, "⚠ " + errorMessage, errorX, errorY, ERROR_COLOR);
    }
    
    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parentScreen);
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        return false; // Don't pause game in multiplayer
    }
}

