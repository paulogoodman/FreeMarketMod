package com.freemarket.client.gui;

import com.freemarket.FreeMarket;
import com.freemarket.client.handlers.ClientWalletHandler;
import com.freemarket.common.data.PlayerAuction;
import com.freemarket.common.network.FreeMarketPacket;
import com.freemarket.common.network.PacketType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Popup overlay for placing bids on auctions.
 * Renders on top of the existing screen without replacing it.
 */
public class PlaceBidPopupOverlay extends PopupOverlay {
    
    private final PlayerAuction auction;
    private EditBox bidAmountBox;
    
    // Bid validation
    private long minimumBid;
    private long playerBalance;
    
    // UI Constants
    private static final int POPUP_WIDTH = 420;
    private static final int POPUP_HEIGHT = 320;
    private static final int HEADER_HEIGHT = 40;
    private static final int SECTION_SPACING = 8;
    
    public PlaceBidPopupOverlay(PlayerAuction auction) {
        super(0, 0, POPUP_WIDTH, POPUP_HEIGHT);
        this.auction = auction;
        
        // Calculate minimum bid using the same logic as server
        this.minimumBid = auction.getMinimumBid();
        this.playerBalance = ClientWalletHandler.getPlayerMoney();
    }
    
    @Override
    public void show() {
        super.show();
        
        // Calculate popup position (centered)
        int screenWidth = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
        
        // Update position to be centered
        this.x = (screenWidth - POPUP_WIDTH) / 2;
        this.y = (screenHeight - POPUP_HEIGHT) / 2;
        
        // Initialize input field
        initializeInputField();
    }
    
    private void initializeInputField() {
        Minecraft minecraft = Minecraft.getInstance();
        
        // Bid amount input
        this.bidAmountBox = new EditBox(
            minecraft.font,
            x + 30,
            y + POPUP_HEIGHT - 100,
            POPUP_WIDTH - 60,
            24,
            Component.literal("Enter bid amount")
        );
        this.bidAmountBox.setValue(String.valueOf(minimumBid));
        this.bidAmountBox.setFilter(text -> text.matches("\\d*")); // Only allow digits
        this.bidAmountBox.setMaxLength(10); // Limit to reasonable bid amounts
        this.bidAmountBox.setHint(Component.literal("Minimum: $" + minimumBid));
    }
    
    @Override
    protected Component getTitle() {
        return Component.literal("🎯 Place Bid");
    }
    
    @Override
    protected void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        
        // Draw header section
        drawRoundedRect(guiGraphics, x, y, POPUP_WIDTH, HEADER_HEIGHT, BACKGROUND_COLOR);
        
        // Draw header border
        guiGraphics.fill(x, y + HEADER_HEIGHT - 1, x + POPUP_WIDTH, y + HEADER_HEIGHT, BORDER_COLOR);
        
        // Draw title with icon
        Component title = Component.literal("🎯 Place Bid");
        int titleWidth = minecraft.font.width(title);
        int titleX = x + (POPUP_WIDTH - titleWidth) / 2;
        int titleY = y + (HEADER_HEIGHT - minecraft.font.lineHeight) / 2;
        guiGraphics.drawString(minecraft.font, title, titleX, titleY, TEXT_PRIMARY);
        
        // Draw content sections
        int contentY = y + HEADER_HEIGHT + SECTION_SPACING;
        drawAuctionInfo(guiGraphics, contentY);
        
        // Draw bid input section
        drawBidInputSection(guiGraphics);
        
        // Draw buttons
        renderButtons(guiGraphics, mouseX, mouseY);
    }
    
    private void drawAuctionInfo(GuiGraphics guiGraphics, int startY) {
        Minecraft minecraft = Minecraft.getInstance();
        int currentY = startY;
        int lineHeight = minecraft.font.lineHeight + 4;
        int sectionWidth = POPUP_WIDTH - 60;
        int sectionX = x + 30;
        
        // Section title
        Component sectionTitle = Component.literal("📋 Auction Details");
        guiGraphics.drawString(minecraft.font, sectionTitle, sectionX, currentY, ACCENT_COLOR);
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
        Minecraft minecraft = Minecraft.getInstance();
        
        // Card background
        guiGraphics.fill(x, y, x + width, y + 30, 0x1AFFFFFF);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + 29, BACKGROUND_COLOR);
        
        // Label
        guiGraphics.drawString(minecraft.font, label, x + 8, y + 8, TEXT_MUTED);
        
        // Value
        guiGraphics.drawString(minecraft.font, value, x + 8, y + 18, valueColor);
    }
    
    private void drawBidInputSection(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        int inputY = y + POPUP_HEIGHT - 100;
        int sectionX = x + 30;
        int sectionWidth = POPUP_WIDTH - 60;
        
        // Section title
        Component inputTitle = Component.literal("💵 Enter Your Bid");
        guiGraphics.drawString(minecraft.font, inputTitle, sectionX, inputY - 25, TEXT_PRIMARY);
        
        // Input field background
        guiGraphics.fill(sectionX, inputY - 2, sectionX + sectionWidth, inputY + 26, BORDER_COLOR);
        guiGraphics.fill(sectionX + 1, inputY - 1, sectionX + sectionWidth - 1, inputY + 25, SURFACE_COLOR);
        
        // Dollar sign prefix
        guiGraphics.drawString(minecraft.font, "$", sectionX + 8, inputY + 6, TEXT_SECONDARY);
    }
    
    private void renderButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        int buttonY = y + POPUP_HEIGHT - 50;
        
        // Place Bid button
        int placeBidButtonX = x + 30;
        int placeBidButtonWidth = 160;
        int placeBidButtonHeight = 28;
        
        boolean placeBidHovered = mouseX >= placeBidButtonX && mouseX <= placeBidButtonX + placeBidButtonWidth &&
                                 mouseY >= buttonY && mouseY <= buttonY + placeBidButtonHeight;
        
        int placeBidBgColor = placeBidHovered ? 0xCC4CAF50 : 0x994CAF50;
        guiGraphics.fill(placeBidButtonX, buttonY, placeBidButtonX + placeBidButtonWidth, buttonY + placeBidButtonHeight, placeBidBgColor);
        guiGraphics.fill(placeBidButtonX, buttonY, placeBidButtonX + placeBidButtonWidth, buttonY + 1, BORDER_COLOR);
        guiGraphics.fill(placeBidButtonX, buttonY, placeBidButtonX + 1, buttonY + placeBidButtonHeight, BORDER_COLOR);
        guiGraphics.fill(placeBidButtonX + placeBidButtonWidth - 1, buttonY, placeBidButtonX + placeBidButtonWidth, buttonY + placeBidButtonHeight, BORDER_COLOR);
        guiGraphics.fill(placeBidButtonX, buttonY + placeBidButtonHeight - 1, placeBidButtonX + placeBidButtonWidth, buttonY + placeBidButtonHeight, BORDER_COLOR);
        
        String placeBidText = "✓ Place Bid";
        int placeBidTextWidth = minecraft.font.width(placeBidText);
        int placeBidTextX = placeBidButtonX + (placeBidButtonWidth - placeBidTextWidth) / 2;
        int placeBidTextY = buttonY + (placeBidButtonHeight - minecraft.font.lineHeight) / 2;
        guiGraphics.drawString(minecraft.font, placeBidText, placeBidTextX, placeBidTextY, TEXT_PRIMARY);
        
        // Cancel button
        int cancelButtonX = x + 230;
        int cancelButtonWidth = 160;
        int cancelButtonHeight = 28;
        
        boolean cancelHovered = mouseX >= cancelButtonX && mouseX <= cancelButtonX + cancelButtonWidth &&
                               mouseY >= buttonY && mouseY <= buttonY + cancelButtonHeight;
        
        int cancelBgColor = cancelHovered ? 0xCC666666 : 0x99666666;
        guiGraphics.fill(cancelButtonX, buttonY, cancelButtonX + cancelButtonWidth, buttonY + cancelButtonHeight, cancelBgColor);
        guiGraphics.fill(cancelButtonX, buttonY, cancelButtonX + cancelButtonWidth, buttonY + 1, BORDER_COLOR);
        guiGraphics.fill(cancelButtonX, buttonY, cancelButtonX + 1, buttonY + cancelButtonHeight, BORDER_COLOR);
        guiGraphics.fill(cancelButtonX + cancelButtonWidth - 1, buttonY, cancelButtonX + cancelButtonWidth, buttonY + cancelButtonHeight, BORDER_COLOR);
        guiGraphics.fill(cancelButtonX, buttonY + cancelButtonHeight - 1, cancelButtonX + cancelButtonWidth, buttonY + cancelButtonHeight, BORDER_COLOR);
        
        String cancelText = "✗ Cancel";
        int cancelTextWidth = minecraft.font.width(cancelText);
        int cancelTextX = cancelButtonX + (cancelButtonWidth - cancelTextWidth) / 2;
        int cancelTextY = buttonY + (cancelButtonHeight - minecraft.font.lineHeight) / 2;
        guiGraphics.drawString(minecraft.font, cancelText, cancelTextX, cancelTextY, TEXT_PRIMARY);
    }
    
    @Override
    protected boolean handlePopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false; // Only left click
        
        // Handle input field click
        if (bidAmountBox != null && bidAmountBox.mouseClicked(mouseX, mouseY, button)) {
            bidAmountBox.setFocused(true);
            return true;
        }
        
        // Handle button clicks
        int buttonY = y + POPUP_HEIGHT - 50;
        
        // Place Bid button
        int placeBidButtonX = x + 30;
        int placeBidButtonWidth = 160;
        int placeBidButtonHeight = 28;
        
        if (mouseX >= placeBidButtonX && mouseX <= placeBidButtonX + placeBidButtonWidth &&
            mouseY >= buttonY && mouseY <= buttonY + placeBidButtonHeight) {
            placeBid();
            return true;
        }
        
        // Cancel button
        int cancelButtonX = x + 230;
        int cancelButtonWidth = 160;
        int cancelButtonHeight = 28;
        
        if (mouseX >= cancelButtonX && mouseX <= cancelButtonX + cancelButtonWidth &&
            mouseY >= buttonY && mouseY <= buttonY + cancelButtonHeight) {
            hide();
            return true;
        }
        
        return false;
    }
    
    @Override
    protected boolean handlePopupKeyPress(int keyCode, int scanCode, int modifiers) {
        // Handle input field key presses
        if (bidAmountBox != null && bidAmountBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        
        return false;
    }
    
    @Override
    protected boolean handlePopupCharTyped(char codePoint, int modifiers) {
        // Handle input field character typing
        if (bidAmountBox != null && bidAmountBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        
        return false;
    }
    
    private void placeBid() {
        String bidStr = bidAmountBox.getValue();
        
        // Validate input
        if (bidStr.isEmpty()) {
            setErrorMessage("Please enter a bid amount");
            return;
        }
        
        long bidAmount;
        try {
            bidAmount = Long.parseLong(bidStr);
        } catch (NumberFormatException e) {
            setErrorMessage("Invalid bid amount");
            return;
        }
        
        // Validate bid amount
        if (bidAmount < minimumBid) {
            if (auction.getCurrentBid() == auction.getStartingPrice()) {
                // First bid must be at least the starting price
                setErrorMessage("Bid must be at least the starting price of $" + auction.getStartingPrice());
            } else {
                // Subsequent bids must be higher than current bid
                setErrorMessage("Bid must be higher than the current bid of $" + auction.getCurrentBid());
            }
            return;
        }
        
        if (bidAmount > playerBalance) {
            setErrorMessage("Insufficient funds");
            return;
        }
        
        // Send bid packet to server
        String jsonData = String.format("{\"auctionId\":\"%s\",\"bidAmount\":%d}", auction.getAuctionId(), bidAmount);
        FreeMarketPacket packet = FreeMarketPacket.withJson(PacketType.AUCTION_BID, jsonData);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
        
        FreeMarket.LOGGER.info("Placed bid of ${} on auction {}", bidAmount, auction.getAuctionId());
        
        // Hide popup
        hide();
    }
}
