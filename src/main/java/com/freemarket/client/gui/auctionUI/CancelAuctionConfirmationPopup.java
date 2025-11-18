package com.freemarket.client.gui.auctionUI;

import com.freemarket.FreeMarket;
import com.freemarket.client.gui.commonUI.PopupOverlay;
import com.freemarket.common.data.PlayerAuction;
import com.freemarket.common.network.FreeMarketPacket;
import com.freemarket.common.network.PacketType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Confirmation popup for canceling an auction.
 * Renders on top of the existing screen without replacing it.
 */
public class CancelAuctionConfirmationPopup extends PopupOverlay {
    
    private final PlayerAuction auction;
    
    // UI Constants
    private static final int POPUP_WIDTH = 400;
    private static final int POPUP_HEIGHT = 220;
    
    public CancelAuctionConfirmationPopup(PlayerAuction auction) {
        super(0, 0, POPUP_WIDTH, POPUP_HEIGHT);
        this.auction = auction;
    }
    
    @Override
    public void show() {
        super.show();
        
        // Calculate popup position (centered)
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        
        // Update position to be centered
        this.x = (screenWidth - POPUP_WIDTH) / 2;
        this.y = (screenHeight - POPUP_HEIGHT) / 2;
    }
    
    @Override
    protected Component getTitle() {
        return Component.literal("⚠ Cancel Auction");
    }
    
    @Override
    protected void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw content sections (title is drawn by base class)
        int contentY = y + 60; // Start below the base class title
        drawConfirmationMessage(guiGraphics, contentY);
        
        // Draw buttons
        renderButtons(guiGraphics, mouseX, mouseY);
    }
    
    private void drawConfirmationMessage(GuiGraphics guiGraphics, int startY) {
        Minecraft minecraft = Minecraft.getInstance();
        int centerX = x + POPUP_WIDTH / 2;
        
        // Warning message
        String warningLine1 = "Are you sure you want to cancel this auction?";
        int warningWidth1 = minecraft.font.width(warningLine1);
        guiGraphics.drawString(minecraft.font, warningLine1, 
            centerX - warningWidth1 / 2, startY, TEXT_PRIMARY);
        
        startY += minecraft.font.lineHeight + 10;
        
        // Item info
        String itemInfo = "Item: " + getItemDisplayName();
        int itemWidth = minecraft.font.width(itemInfo);
        guiGraphics.drawString(minecraft.font, itemInfo,
            centerX - itemWidth / 2, startY, ACCENT_COLOR);
        
        startY += minecraft.font.lineHeight + 6;
        
        // Current bid info
        String bidInfo;
        if (auction.getBidderName() != null && !auction.getBidderName().isEmpty()) {
            bidInfo = "Current Bid: $" + auction.getCurrentBid() + " by " + auction.getBidderName();
        } else {
            bidInfo = "Starting Price: $" + auction.getStartingPrice() + " (No bids yet)";
        }
        int bidWidth = minecraft.font.width(bidInfo);
        guiGraphics.drawString(minecraft.font, bidInfo,
            centerX - bidWidth / 2, startY, TEXT_SECONDARY);
        
        startY += minecraft.font.lineHeight + 15;
        
        // Return message
        String returnMessage = "The item will be returned to your inventory.";
        int returnWidth = minecraft.font.width(returnMessage);
        guiGraphics.drawString(minecraft.font, returnMessage,
            centerX - returnWidth / 2, startY, SUCCESS_COLOR);
        
        // Refund message if there are bids
        if (auction.getBidderName() != null && !auction.getBidderName().isEmpty()) {
            startY += minecraft.font.lineHeight + 4;
            String refundMessage = "The bidder will be refunded $" + auction.getCurrentBid() + ".";
            int refundWidth = minecraft.font.width(refundMessage);
            guiGraphics.drawString(minecraft.font, refundMessage,
                centerX - refundWidth / 2, startY, TEXT_MUTED);
        }
    }
    
    private String getItemDisplayName() {
        // Extract simple name from item ID
        String itemId = auction.getItemId();
        if (itemId.contains(":")) {
            itemId = itemId.substring(itemId.lastIndexOf(":") + 1);
        }
        // Convert snake_case to Title Case
        String[] parts = itemId.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (result.length() > 0) result.append(" ");
            result.append(Character.toUpperCase(part.charAt(0)))
                  .append(part.substring(1).toLowerCase());
        }
        return result.toString() + (auction.getStackSize() > 1 ? " x" + auction.getStackSize() : "");
    }
    
    private void renderButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        int buttonY = y + POPUP_HEIGHT - 50;
        int buttonWidth = 150;
        int buttonHeight = 28;
        int buttonGap = 20;
        
        // Calculate button positions (centered)
        int totalWidth = (buttonWidth * 2) + buttonGap;
        int startX = x + (POPUP_WIDTH - totalWidth) / 2;
        
        // Keep Auction button (left, gray)
        int keepAuctionButtonX = startX;
        boolean keepAuctionHovered = mouseX >= keepAuctionButtonX && mouseX <= keepAuctionButtonX + buttonWidth &&
                                     mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        
        int keepAuctionBgColor = keepAuctionHovered ? 0xCC666666 : 0x99666666; // Gray
        guiGraphics.fill(keepAuctionButtonX, buttonY, keepAuctionButtonX + buttonWidth, buttonY + buttonHeight, keepAuctionBgColor);
        // Draw borders without corner overlap
        guiGraphics.fill(keepAuctionButtonX, buttonY, keepAuctionButtonX + buttonWidth, buttonY + 1, BORDER_COLOR); // Top
        guiGraphics.fill(keepAuctionButtonX, buttonY + 1, keepAuctionButtonX + 1, buttonY + buttonHeight, BORDER_COLOR); // Left
        guiGraphics.fill(keepAuctionButtonX + buttonWidth - 1, buttonY + 1, keepAuctionButtonX + buttonWidth, buttonY + buttonHeight, BORDER_COLOR); // Right
        guiGraphics.fill(keepAuctionButtonX + 1, buttonY + buttonHeight - 1, keepAuctionButtonX + buttonWidth - 1, buttonY + buttonHeight, BORDER_COLOR); // Bottom
        
        String keepAuctionText = "Keep Auction";
        int keepHorizontalPadding = calculateOnePercentPadding(buttonWidth);
        int keepVerticalPadding = calculateOnePercentPadding(buttonHeight);
        int keepAvailableWidth = Math.max(1, buttonWidth - (keepHorizontalPadding * 2));
        String keepAuctionDisplayText = minecraft.font.plainSubstrByWidth(keepAuctionText, keepAvailableWidth);
        int keepAuctionTextWidth = minecraft.font.width(keepAuctionDisplayText);
        int keepAuctionTextX = keepAuctionButtonX + keepHorizontalPadding +
                               Math.max(0, (keepAvailableWidth - keepAuctionTextWidth) / 2);
        int keepFontHeight = minecraft.font.lineHeight;
        int keepCenteredY = buttonY + (buttonHeight - keepFontHeight) / 2;
        int keepMinY = buttonY + keepVerticalPadding;
        int keepMaxY = buttonY + buttonHeight - keepVerticalPadding - keepFontHeight;
        int keepAuctionTextY = Math.max(keepMinY, Math.min(keepCenteredY, keepMaxY));
        guiGraphics.drawString(minecraft.font, keepAuctionDisplayText, keepAuctionTextX, keepAuctionTextY, TEXT_PRIMARY);
        
        // Cancel Auction button (right, red)
        int cancelAuctionButtonX = startX + buttonWidth + buttonGap;
        boolean cancelAuctionHovered = mouseX >= cancelAuctionButtonX && mouseX <= cancelAuctionButtonX + buttonWidth &&
                                       mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        
        int cancelAuctionBgColor = cancelAuctionHovered ? 0xCCE53935 : 0x99E53935; // Red
        guiGraphics.fill(cancelAuctionButtonX, buttonY, cancelAuctionButtonX + buttonWidth, buttonY + buttonHeight, cancelAuctionBgColor);
        // Draw borders without corner overlap
        guiGraphics.fill(cancelAuctionButtonX, buttonY, cancelAuctionButtonX + buttonWidth, buttonY + 1, BORDER_COLOR); // Top
        guiGraphics.fill(cancelAuctionButtonX, buttonY + 1, cancelAuctionButtonX + 1, buttonY + buttonHeight, BORDER_COLOR); // Left
        guiGraphics.fill(cancelAuctionButtonX + buttonWidth - 1, buttonY + 1, cancelAuctionButtonX + buttonWidth, buttonY + buttonHeight, BORDER_COLOR); // Right
        guiGraphics.fill(cancelAuctionButtonX + 1, buttonY + buttonHeight - 1, cancelAuctionButtonX + buttonWidth - 1, buttonY + buttonHeight, BORDER_COLOR); // Bottom
        
        String cancelAuctionText = "Cancel Auction";
        int cancelHorizontalPadding = calculateOnePercentPadding(buttonWidth);
        int cancelVerticalPadding = calculateOnePercentPadding(buttonHeight);
        int cancelAvailableWidth = Math.max(1, buttonWidth - (cancelHorizontalPadding * 2));
        String cancelAuctionDisplayText = minecraft.font.plainSubstrByWidth(cancelAuctionText, cancelAvailableWidth);
        int cancelAuctionTextWidth = minecraft.font.width(cancelAuctionDisplayText);
        int cancelAuctionTextX = cancelAuctionButtonX + cancelHorizontalPadding +
                                 Math.max(0, (cancelAvailableWidth - cancelAuctionTextWidth) / 2);
        int cancelFontHeight = minecraft.font.lineHeight;
        int cancelCenteredY = buttonY + (buttonHeight - cancelFontHeight) / 2;
        int cancelMinY = buttonY + cancelVerticalPadding;
        int cancelMaxY = buttonY + buttonHeight - cancelVerticalPadding - cancelFontHeight;
        int cancelAuctionTextY = Math.max(cancelMinY, Math.min(cancelCenteredY, cancelMaxY));
        guiGraphics.drawString(minecraft.font, cancelAuctionDisplayText, cancelAuctionTextX, cancelAuctionTextY, TEXT_PRIMARY);
    }
    
    @Override
    protected boolean handlePopupKeyPress(int keyCode, int scanCode, int modifiers) {
        // Handle ESC key to close popup
        if (keyCode == 256) { // ESC key
            hide();
            return true;
        }
        return false;
    }
    
    @Override
    protected boolean handlePopupCharTyped(char codePoint, int modifiers) {
        // No character input needed for confirmation popup
        return false;
    }
    
    @Override
    protected boolean handlePopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false; // Only left click
        
        int buttonY = y + POPUP_HEIGHT - 50;
        int buttonWidth = 150;
        int buttonHeight = 28;
        int buttonGap = 20;
        
        // Calculate button positions
        int totalWidth = (buttonWidth * 2) + buttonGap;
        int startX = x + (POPUP_WIDTH - totalWidth) / 2;
        
        // Keep Auction button (left)
        int keepAuctionButtonX = startX;
        if (mouseX >= keepAuctionButtonX && mouseX <= keepAuctionButtonX + buttonWidth &&
            mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
            // Play click sound
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
            }
            hide();
            return true;
        }
        
        // Cancel Auction button (right)
        int cancelAuctionButtonX = startX + buttonWidth + buttonGap;
        if (mouseX >= cancelAuctionButtonX && mouseX <= cancelAuctionButtonX + buttonWidth &&
            mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
            // Play click sound
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
            }
            cancelAuction();
            return true;
        }
        
        return false;
    }
    
    private void cancelAuction() {
        // Send cancel auction packet to server
        String jsonData = String.format("{\"auctionId\":\"%s\"}", auction.getAuctionId());
        FreeMarketPacket packet = FreeMarketPacket.withJson(PacketType.AUCTION_CANCEL, jsonData);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
        
        FreeMarket.LOGGER.info("Cancelled auction {}", auction.getAuctionId());
        
        // Hide popup
        hide();
    }

    private int calculateOnePercentPadding(int size) {
        return Math.max(1, Math.round(size * 0.01f));
    }
}

