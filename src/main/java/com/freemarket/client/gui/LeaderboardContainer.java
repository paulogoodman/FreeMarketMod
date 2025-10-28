package com.freemarket.client.gui;

import com.freemarket.client.data.ClientLeaderboardCache;
import com.freemarket.common.data.PlayerBalanceData;
import com.freemarket.common.network.FreeMarketPacket;
import com.freemarket.common.network.PacketType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * A scrollable container for displaying the player balance leaderboard.
 */
public class LeaderboardContainer implements Renderable {
    
    private final int x, y, width, height;
    private final FreeMarketGuiScreen parentScreen;
    
    private int scrollOffset = 0;
    private int maxVisibleRows;
    private int rowHeight = 24;
    private static final int TOP_PLAYERS_LIMIT = 50;
    
    // Auto-refresh timer
    private long lastRefreshTime = 0;
    private static final long REFRESH_INTERVAL = 30000; // 30 seconds
    
    public LeaderboardContainer(int x, int y, int width, int height, FreeMarketGuiScreen parentScreen) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.parentScreen = parentScreen;
        
        // Calculate how many rows can fit
        int availableHeight = height - 60; // Leave space for title and margins
        this.maxVisibleRows = availableHeight / rowHeight;
        
        // Request leaderboard data from server
        requestLeaderboardData();
    }
    
    /**
     * Initializes the container.
     */
    public void init() {
        // Request fresh leaderboard data
        requestLeaderboardData();
    }
    
    /**
     * Requests leaderboard data from the server.
     */
    private void requestLeaderboardData() {
        FreeMarketPacket packet = FreeMarketPacket.emptyRequest(PacketType.LEADERBOARD_REQUEST);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
        lastRefreshTime = System.currentTimeMillis();
    }
    
    /**
     * Checks if leaderboard data should be refreshed and requests if needed.
     * This should be called when switching to the leaderboard tab, not in render.
     */
    public void checkAndRefreshIfNeeded() {
        if (System.currentTimeMillis() - lastRefreshTime > REFRESH_INTERVAL) {
            requestLeaderboardData();
        }
    }
    
    /**
     * Scrolls the leaderboard.
     * @param delta the scroll amount
     */
    public void scroll(int delta) {
        List<PlayerBalanceData> leaderboard = ClientLeaderboardCache.getTopPlayers(TOP_PLAYERS_LIMIT);
        int maxScroll = Math.max(0, leaderboard.size() - maxVisibleRows);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + delta));
    }
    
    /**
     * Scrolls to a specific position in the leaderboard.
     * @param position the scroll position to jump to
     */
    public void scrollToPosition(int position) {
        List<PlayerBalanceData> leaderboard = ClientLeaderboardCache.getTopPlayers(TOP_PLAYERS_LIMIT);
        int maxScroll = Math.max(0, leaderboard.size() - maxVisibleRows);
        scrollOffset = Math.max(0, Math.min(maxScroll, position));
    }
    
    /**
     * Gets the current scroll position.
     * @return the current scroll position
     */
    public int getScrollPosition() {
        return scrollOffset;
    }
    
    /**
     * Gets the maximum scroll position.
     * @return the maximum scroll position
     */
    public int getMaxScrollPosition() {
        List<PlayerBalanceData> leaderboard = ClientLeaderboardCache.getTopPlayers(TOP_PLAYERS_LIMIT);
        return Math.max(0, leaderboard.size() - maxVisibleRows);
    }
    
    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Note: Auto-refresh moved to event-based system to avoid per-frame network calls
        
        // Draw modern container background with gradient effect (semi-transparent)
        guiGraphics.fill(x, y, x + width, y + height, 0x801E1E1E); // 50% opacity
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x802A2A2A); // 50% opacity
        
        // Draw subtle border
        guiGraphics.fill(x, y, x + width, y + 2, 0x80404040);
        guiGraphics.fill(x, y + 2, x + 2, y + height - 2, 0x80404040);
        guiGraphics.fill(x + width - 2, y + 2, x + width, y + height - 2, 0x80404040);
        guiGraphics.fill(x, y + height - 2, x + width, y + height, 0x80404040);
        
        
        // Draw column headers
        int headerY = y + 35;
        int rankX = x + 20;
        int nameX = x + 80;
        int balanceX = x + width - 150;
        
        guiGraphics.drawString(Minecraft.getInstance().font, "Rank", rankX, headerY, 0xFFAAAAAA);
        guiGraphics.drawString(Minecraft.getInstance().font, "Player", nameX, headerY, 0xFFAAAAAA);
        guiGraphics.drawString(Minecraft.getInstance().font, "Balance", balanceX, headerY, 0xFFAAAAAA);
        
        // Draw separator line
        int separatorY = headerY + 12;
        guiGraphics.fill(x + 10, separatorY, x + width - 10, separatorY + 1, 0x80FFFFFF);
        
        // Get leaderboard data
        List<PlayerBalanceData> leaderboard = ClientLeaderboardCache.getTopPlayers(TOP_PLAYERS_LIMIT);
        
        if (leaderboard.isEmpty()) {
            // Show "No data" message
            Component noDataMsg = Component.literal("No leaderboard data available");
            int msgWidth = Minecraft.getInstance().font.width(noDataMsg);
            int msgX = x + (width - msgWidth) / 2;
            int msgY = y + (height / 2);
            guiGraphics.drawString(Minecraft.getInstance().font, noDataMsg, msgX, msgY, 0xFF999999);
            return;
        }
        
        // Draw leaderboard entries
        int startY = separatorY + 10;
        int currentY = startY;
        
        for (int i = scrollOffset; i < leaderboard.size() && i < scrollOffset + maxVisibleRows; i++) {
            PlayerBalanceData player = leaderboard.get(i);
            int rank = i + 1;
            
            // Determine rank color
            int rankColor;
            if (rank == 1) {
                rankColor = 0xFFFFD700; // Gold
            } else if (rank == 2) {
                rankColor = 0xFFC0C0C0; // Silver
            } else if (rank == 3) {
                rankColor = 0xFFCD7F32; // Bronze
            } else {
                rankColor = 0xFFE0E0E0; // White
            }
            
            // Draw rank
            String rankText = "#" + rank;
            guiGraphics.drawString(Minecraft.getInstance().font, rankText, rankX, currentY, rankColor);
            
            // Draw player name
            String playerName = player.getPlayerName();
            if (playerName.length() > 20) {
                playerName = playerName.substring(0, 17) + "...";
            }
            guiGraphics.drawString(Minecraft.getInstance().font, playerName, nameX, currentY, 0xFFFFFFFF);
            
            // Draw balance
            String balanceText = "$" + formatPrice(player.getBalance());
            guiGraphics.drawString(Minecraft.getInstance().font, balanceText, balanceX, currentY, 0xFF4CAF50);
            
            currentY += rowHeight;
        }
        
        // Draw scroll bar if needed
        if (leaderboard.size() > maxVisibleRows) {
            drawScrollBar(guiGraphics, leaderboard.size());
        }
        
        // Draw player count
        Component countText = Component.literal("Showing " + Math.min(maxVisibleRows, leaderboard.size()) + " of " + leaderboard.size() + " players");
        int countX = x + 10;
        int countY = y + height - 20;
        guiGraphics.drawString(Minecraft.getInstance().font, countText, countX, countY, 0xFF999999);
    }
    
    /**
     * Draws the scroll bar.
     */
    private void drawScrollBar(GuiGraphics guiGraphics, int totalPlayers) {
        int maxScroll = Math.max(1, totalPlayers - maxVisibleRows);
        if (maxScroll <= 0) return; // No scrolling needed
        
        int scrollBarWidth = 8; // Match FreeMarketContainer scroll bar width
        int scrollBarX = x + width - scrollBarWidth - 2; // Match FreeMarketContainer positioning
        int scrollBarY = y + 50;
        int scrollBarHeight = height - 80;
        
        // Draw scroll bar background (semi-transparent)
        guiGraphics.fill(scrollBarX, scrollBarY, scrollBarX + scrollBarWidth, scrollBarY + scrollBarHeight, 0x80000000);
        
        // Calculate scroll bar thumb position and size
        int thumbHeight = Math.max(20, (maxVisibleRows * scrollBarHeight) / totalPlayers);
        int thumbY = scrollBarY + (scrollBarHeight - thumbHeight) * scrollOffset / maxScroll;
        
        // Draw scroll bar thumb (semi-transparent)
        guiGraphics.fill(scrollBarX + 1, thumbY, scrollBarX + scrollBarWidth - 1, thumbY + thumbHeight, 0x80808080);
    }
    
    /**
     * Formats a price number to be shorter for display.
     */
    private String formatPrice(long price) {
        if (price < 1000) {
            return String.valueOf(price);
        } else if (price < 1000000) {
            if (price % 1000 == 0) {
                double thousands = price / 1000.0;
                if (thousands == Math.floor(thousands)) {
                    return String.format("%.0fK", thousands);
                } else {
                    return String.format("%.1fK", thousands);
                }
            } else {
                return String.valueOf(price);
            }
        } else if (price < 1000000000) {
            if (price % 1000000 == 0) {
                double millions = price / 1000000.0;
                if (millions == Math.floor(millions)) {
                    return String.format("%.0fM", millions);
                } else {
                    return String.format("%.1fM", millions);
                }
            } else {
                return String.valueOf(price);
            }
        } else if (price < 1000000000000L) {
            if (price % 1000000000 == 0) {
                double billions = price / 1000000000.0;
                if (billions == Math.floor(billions)) {
                    return String.format("%.0fB", billions);
                } else {
                    return String.format("%.1fB", billions);
                }
            } else {
                return String.valueOf(price);
            }
        } else {
            if (price % 1000000000000L == 0) {
                double trillions = price / 1000000000000.0;
                if (trillions == Math.floor(trillions)) {
                    return String.format("%.0fT", trillions);
                } else {
                    return String.format("%.1fT", trillions);
                }
            } else {
                return String.valueOf(price);
            }
        }
    }
    
    /**
     * Handles mouse clicks.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Block clicks if popup is visible
        if (parentScreen != null && parentScreen.isAnyPopupVisible()) {
            return false; // Don't consume - let popup handle it
        }
        
        // Handle scroll bar clicks
        List<PlayerBalanceData> leaderboard = ClientLeaderboardCache.getTopPlayers(TOP_PLAYERS_LIMIT);
        if (leaderboard.size() > maxVisibleRows) {
            int scrollBarWidth = 8; // Match FreeMarketContainer scroll bar width
            int scrollBarX = x + width - scrollBarWidth - 2; // Match FreeMarketContainer positioning
            int scrollBarY = y + 50;
            int scrollBarHeight = height - 80;
            
            if (mouseX >= scrollBarX && mouseX <= scrollBarX + scrollBarWidth &&
                mouseY >= scrollBarY && mouseY <= scrollBarY + scrollBarHeight) {
                
                // Calculate new scroll position based on click
                int maxScroll = Math.max(1, leaderboard.size() - maxVisibleRows);
                double relativeY = (mouseY - scrollBarY) / scrollBarHeight;
                int newScroll = (int) (relativeY * maxScroll);
                scrollToPosition(newScroll);
                return true;
            }
        }
        
        // No other clickable elements in leaderboard
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
        // No text input in leaderboard
        return false;
    }
}

