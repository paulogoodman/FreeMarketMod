package com.freemarket.client.gui;

import com.freemarket.client.data.ClientAuctionCache;
import com.freemarket.common.data.PlayerAuction;
import com.freemarket.common.network.AuctionRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * A container for displaying player auctions.
 * Currently shows a "Coming Soon" placeholder with auction infrastructure ready.
 */
public class PlayerAuctionContainer implements Renderable {
    
    private final int x, y, width, height;
    private final FreeMarketGuiScreen parentScreen;
    
    public PlayerAuctionContainer(int x, int y, int width, int height, FreeMarketGuiScreen parentScreen) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.parentScreen = parentScreen;
        
        // Request auction data from server
        requestAuctionData();
    }
    
    /**
     * Initializes the container.
     */
    public void init() {
        // Request fresh auction data
        requestAuctionData();
    }
    
    /**
     * Requests auction data from the server.
     */
    private void requestAuctionData() {
        AuctionRequestPacket packet = new AuctionRequestPacket();
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
    }
    
    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw modern container background with gradient effect (semi-transparent)
        guiGraphics.fill(x, y, x + width, y + height, 0x801E1E1E); // 50% opacity
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x802A2A2A); // 50% opacity
        
        // Draw subtle border
        guiGraphics.fill(x, y, x + width, y + 2, 0x80404040);
        guiGraphics.fill(x, y, x + 2, y + height, 0x80404040);
        guiGraphics.fill(x + width - 2, y, x + width, y + height, 0x80404040);
        guiGraphics.fill(x, y + height - 2, x + width, y + height, 0x80404040);
        
        // Draw title
        Component title = Component.literal("Player Auctions");
        int titleWidth = Minecraft.getInstance().font.width(title);
        int titleX = x + (width - titleWidth) / 2;
        int titleY = y + 10;
        guiGraphics.drawString(Minecraft.getInstance().font, title, titleX, titleY, 0xFFE0E0E0);
        
        // Get auction data
        List<PlayerAuction> auctions = ClientAuctionCache.getCachedAuctions();
        
        if (auctions.isEmpty()) {
            // Show "Coming Soon" message
            Component comingSoon = Component.literal("Coming Soon!");
            int comingSoonWidth = Minecraft.getInstance().font.width(comingSoon);
            int comingSoonX = x + (width - comingSoonWidth) / 2;
            int comingSoonY = y + (height / 2) - 20;
            guiGraphics.drawString(Minecraft.getInstance().font, comingSoon, comingSoonX, comingSoonY, 0xFF4CAF50);
            
            Component description = Component.literal("Player-to-player auctions will be available in a future update.");
            int descWidth = Minecraft.getInstance().font.width(description);
            int descX = x + (width - descWidth) / 2;
            int descY = comingSoonY + 20;
            guiGraphics.drawString(Minecraft.getInstance().font, description, descX, descY, 0xFF999999);
            
            Component features = Component.literal("Features: Bid on items, create auctions, automatic expiry handling");
            int featWidth = Minecraft.getInstance().font.width(features);
            int featX = x + (width - featWidth) / 2;
            int featY = descY + 15;
            guiGraphics.drawString(Minecraft.getInstance().font, features, featX, featY, 0xFF666666);
        } else {
            // TODO: Render auction items with bid buttons
            // For now, just show count
            Component count = Component.literal(auctions.size() + " active auctions");
            int countWidth = Minecraft.getInstance().font.width(count);
            int countX = x + (width - countWidth) / 2;
            int countY = y + (height / 2);
            guiGraphics.drawString(Minecraft.getInstance().font, count, countX, countY, 0xFFFFFFFF);
        }
    }
    
    /**
     * Handles mouse clicks.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // No clickable elements yet
        return false;
    }
    
    /**
     * Handles key presses.
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // No key handling needed
        return false;
    }
    
    /**
     * Handles character typing.
     */
    public boolean charTyped(char codePoint, int modifiers) {
        // No text input
        return false;
    }
    
    /**
     * Scrolls the auction list.
     */
    public void scroll(int delta) {
        // TODO: Implement scrolling when auction rendering is complete
    }
}

