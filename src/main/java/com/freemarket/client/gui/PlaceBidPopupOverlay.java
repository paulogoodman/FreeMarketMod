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
    private static final int POPUP_HEIGHT = 340;
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
        
        // Bid amount input - positioned in the bid input section
        int inputY = y + POPUP_HEIGHT - 120;
        this.bidAmountBox = new EditBox(
            minecraft.font,
            x + 45,  // Offset for dollar sign
            inputY + 5,
            POPUP_WIDTH - 90,
            20,
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
        int sectionX = x + 30;
        
        // Section title
        Component sectionTitle = Component.literal("📋 Auction Details");
        guiGraphics.drawString(minecraft.font, sectionTitle, sectionX, currentY, ACCENT_COLOR);
        currentY += lineHeight + 6;
        
        // 2x2 Grid Layout
        int cardWidth = (POPUP_WIDTH - 60 - 10) / 2;  // Split width in half with 10px gap
        int cardHeight = 30;
        int gap = 10;
        
        // Top Left: Listed By
        drawInfoCard(guiGraphics, sectionX, currentY, cardWidth, "🏪 Listed by", auction.getSellerName(), TEXT_SECONDARY);
        
        // Top Right: Starting Price
        drawInfoCard(guiGraphics, sectionX + cardWidth + gap, currentY, cardWidth, "💰 Starting Price", "$" + auction.getStartingPrice(), SUCCESS_COLOR);
        
        currentY += cardHeight + gap;
        
        // Bottom Left: Last Bidder
        String bidderInfo = auction.getBidderName() != null && !auction.getBidderName().isEmpty() 
            ? auction.getBidderName() 
            : "No bids yet";
        drawInfoCard(guiGraphics, sectionX, currentY, cardWidth, "🎯 Last Bidder", bidderInfo, TEXT_SECONDARY);
        
        // Bottom Right: Current Bid
        drawInfoCard(guiGraphics, sectionX + cardWidth + gap, currentY, cardWidth, "🏆 Current Bid", "$" + auction.getCurrentBid(), WARNING_COLOR);
        
        currentY += cardHeight + gap;
        
        // Your Balance (full width below grid)
        String balanceText = "$" + playerBalance;
        int balanceColor = playerBalance >= minimumBid ? SUCCESS_COLOR : ERROR_COLOR;
        drawInfoCard(guiGraphics, sectionX, currentY, POPUP_WIDTH - 60, "💳 Your Balance", balanceText, balanceColor);
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
        int inputY = y + POPUP_HEIGHT - 120;
        int sectionX = x + 30;
        int sectionWidth = POPUP_WIDTH - 60;
        
        // Section title
        Component inputTitle = Component.literal("💵 Enter Your Bid");
        guiGraphics.drawString(minecraft.font, inputTitle, sectionX, inputY - 22, TEXT_PRIMARY);
        
        // Input field background
        guiGraphics.fill(sectionX, inputY, sectionX + sectionWidth, inputY + 30, BORDER_COLOR);
        guiGraphics.fill(sectionX + 1, inputY + 1, sectionX + sectionWidth - 1, inputY + 29, SURFACE_COLOR);
        
        // Dollar sign prefix
        guiGraphics.drawString(minecraft.font, "$", sectionX + 8, inputY + 8, TEXT_SECONDARY);
        
        // Render the EditBox widget
        if (bidAmountBox != null) {
            bidAmountBox.render(guiGraphics, (int)Minecraft.getInstance().mouseHandler.xpos(), (int)Minecraft.getInstance().mouseHandler.ypos(), 0);
        }
    }
    
    private void renderButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        int buttonY = y + POPUP_HEIGHT - 60;
        
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
        
        String placeBidText = "Place Bid";
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
        
        String cancelText = "Cancel";
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
        int buttonY = y + POPUP_HEIGHT - 60;
        
        // Place Bid button
        int placeBidButtonX = x + 30;
        int placeBidButtonWidth = 160;
        int placeBidButtonHeight = 28;
        
        if (mouseX >= placeBidButtonX && mouseX <= placeBidButtonX + placeBidButtonWidth &&
            mouseY >= buttonY && mouseY <= buttonY + placeBidButtonHeight) {
            // Play click sound
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
            }
            placeBid();
            return true;
        }
        
        // Cancel button
        int cancelButtonX = x + 230;
        int cancelButtonWidth = 160;
        int cancelButtonHeight = 28;
        
        if (mouseX >= cancelButtonX && mouseX <= cancelButtonX + cancelButtonWidth &&
            mouseY >= buttonY && mouseY <= buttonY + cancelButtonHeight) {
            // Play click sound
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
            }
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
