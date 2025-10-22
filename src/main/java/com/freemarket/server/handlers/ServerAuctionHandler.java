package com.freemarket.server.handlers;

import com.freemarket.FreeMarket;
import com.freemarket.common.attachments.ItemComponentHandler;
import com.freemarket.common.data.PlayerAuction;
import com.freemarket.server.data.AuctionDataManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Server-side auction handler for processing bids, creating auctions, and handling expiry.
 */
public class ServerAuctionHandler {
    
    private static final long MIN_BID_INCREMENT = 100; // Minimum bid increment
    
    /**
     * Creates a new auction.
     * @return true if successful
     */
    public static boolean createAuction(ServerLevel level, ServerPlayer player, String itemId, 
                                       String componentData, int quantity, long startingPrice, long durationMinutes) {
        try {
            // Validate inputs
            if (startingPrice < 0) {
                player.sendSystemMessage(Component.literal("Starting price must be positive!"));
                return false;
            }
            
            if (durationMinutes < 1 || durationMinutes > 10080) { // Max 1 week
                player.sendSystemMessage(Component.literal("Duration must be between 1 minute and 1 week!"));
                return false;
            }
            
            // Create auction object
            String auctionId = AuctionDataManager.generateAuctionId();
            long expiryTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000);
            
            PlayerAuction auction = new PlayerAuction(
                auctionId,
                itemId,
                componentData,
                quantity,
                startingPrice,
                startingPrice, // Current bid starts at starting price
                player.getUUID().toString(),
                player.getName().getString(),
                expiryTime,
                null, // No bidder yet
                null,
                System.currentTimeMillis()
            );
            
            // TODO: Remove item from player inventory (requires inventory management)
            // For now, we'll just create the auction
            
            // Save auction
            AuctionDataManager.addAuction(level, auction);
            
            player.sendSystemMessage(Component.literal("Auction created successfully!"));
            FreeMarket.LOGGER.info("Player {} created auction {} for {}", player.getName().getString(), auctionId, itemId);
            
            return true;
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to create auction: {}", e.getMessage(), e);
            player.sendSystemMessage(Component.literal("Failed to create auction!"));
            return false;
        }
    }
    
    /**
     * Places a bid on an auction.
     * @return true if successful
     */
    public static boolean placeBid(ServerLevel level, ServerPlayer player, String auctionId, long bidAmount) {
        try {
            // Load auctions
            List<PlayerAuction> auctions = AuctionDataManager.loadAuctions(level);
            PlayerAuction auction = null;
            
            // Find the auction
            for (PlayerAuction a : auctions) {
                if (a.getAuctionId().equals(auctionId)) {
                    auction = a;
                    break;
                }
            }
            
            if (auction == null) {
                player.sendSystemMessage(Component.literal("Auction not found!"));
                return false;
            }
            
            // Check if expired
            if (auction.isExpired()) {
                player.sendSystemMessage(Component.literal("This auction has expired!"));
                return false;
            }
            
            // Check if player is the seller
            if (auction.getSellerUuid().equals(player.getUUID().toString())) {
                player.sendSystemMessage(Component.literal("You cannot bid on your own auction!"));
                return false;
            }
            
            // Check if bid is high enough
            long minBid = auction.getCurrentBid() + MIN_BID_INCREMENT;
            if (bidAmount < minBid) {
                player.sendSystemMessage(Component.literal("Bid must be at least $" + minBid + "!"));
                return false;
            }
            
            // Check if player has enough money
            if (!ServerWalletHandler.hasEnoughMoney(player, bidAmount)) {
                player.sendSystemMessage(Component.literal("You don't have enough money!"));
                return false;
            }
            
            // Refund previous bidder if there was one
            if (auction.getBidderUuid() != null) {
                ServerPlayer previousBidder = level.getServer().getPlayerList().getPlayer(
                    java.util.UUID.fromString(auction.getBidderUuid())
                );
                if (previousBidder != null) {
                    ServerWalletHandler.addMoney(previousBidder, auction.getCurrentBid());
                    previousBidder.sendSystemMessage(Component.literal("You were outbid on an auction. Your bid of $" + 
                        auction.getCurrentBid() + " has been refunded."));
                }
            }
            
            // Deduct money from new bidder
            ServerWalletHandler.removeMoney(player, bidAmount);
            
            // Update auction
            auction.setCurrentBid(bidAmount);
            auction.setBidderUuid(player.getUUID().toString());
            auction.setBidderName(player.getName().getString());
            
            // Save auction
            AuctionDataManager.updateAuction(level, auction);
            
            player.sendSystemMessage(Component.literal("Bid placed successfully!"));
            FreeMarket.LOGGER.info("Player {} bid ${} on auction {}", player.getName().getString(), bidAmount, auctionId);
            
            return true;
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to place bid: {}", e.getMessage(), e);
            player.sendSystemMessage(Component.literal("Failed to place bid!"));
            return false;
        }
    }
    
    /**
     * Processes expired auctions.
     * Should be called periodically (e.g., every minute).
     */
    public static void processExpiredAuctions(ServerLevel level) {
        try {
            List<PlayerAuction> expiredAuctions = AuctionDataManager.removeExpiredAuctions(level);
            
            for (PlayerAuction auction : expiredAuctions) {
                if (auction.hasBids()) {
                    // Auction had bids - give item to winner, money to seller
                    ServerPlayer winner = level.getServer().getPlayerList().getPlayer(
                        java.util.UUID.fromString(auction.getBidderUuid())
                    );
                    ServerPlayer seller = level.getServer().getPlayerList().getPlayer(
                        java.util.UUID.fromString(auction.getSellerUuid())
                    );
                    
                    // Give money to seller
                    if (seller != null) {
                        ServerWalletHandler.addMoney(seller, auction.getCurrentBid());
                        seller.sendSystemMessage(Component.literal("Your auction sold for $" + auction.getCurrentBid() + "!"));
                    }
                    
                    // TODO: Give item to winner (requires inventory management)
                    if (winner != null) {
                        winner.sendSystemMessage(Component.literal("You won an auction for $" + auction.getCurrentBid() + "!"));
                    }
                    
                    FreeMarket.LOGGER.info("Auction {} completed: {} sold to {} for ${}", 
                        auction.getAuctionId(), auction.getItemId(), auction.getBidderName(), auction.getCurrentBid());
                } else {
                    // No bids - return item to seller
                    ServerPlayer seller = level.getServer().getPlayerList().getPlayer(
                        java.util.UUID.fromString(auction.getSellerUuid())
                    );
                    
                    // TODO: Return item to seller (requires inventory management)
                    if (seller != null) {
                        seller.sendSystemMessage(Component.literal("Your auction expired with no bids."));
                    }
                    
                    FreeMarket.LOGGER.info("Auction {} expired with no bids", auction.getAuctionId());
                }
            }
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to process expired auctions: {}", e.getMessage(), e);
        }
    }
}

