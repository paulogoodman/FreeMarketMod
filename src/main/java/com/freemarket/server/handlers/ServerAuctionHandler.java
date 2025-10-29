package com.freemarket.server.handlers;

import com.freemarket.FreeMarket;
import com.freemarket.common.data.PlayerAuction;
import com.freemarket.server.data.AuctionDataManager;
import com.freemarket.server.data.PendingReward;
import com.freemarket.server.data.PendingRewardsManager;
import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.freemarket.common.network.FreeMarketPacket;
import com.freemarket.common.network.PacketType;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-side auction handler for processing bids, creating auctions, and handling expiry.
 */
public class ServerAuctionHandler {
    
    private static boolean DEBUG_MODE = false; // Debug mode to allow bidding on own auctions
    
    /**
     * Creates a new auction from an inventory slot.
     * This method gets the actual item from the player's inventory to avoid component mismatch issues.
     * @return true if successful
     */
    public static boolean createAuctionFromSlot(ServerLevel level, ServerPlayer player, int slotIndex, 
                                                int quantity, long startingPrice, long durationMinutes) {
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
            
            // Get the actual item from the player's inventory at the specified slot
            Inventory inventory = player.getInventory();
            if (slotIndex < 0 || slotIndex >= inventory.getContainerSize()) {
                player.sendSystemMessage(Component.literal("Invalid inventory slot!"));
                return false;
            }
            
            ItemStack itemInSlot = inventory.getItem(slotIndex);
            if (itemInSlot.isEmpty()) {
                player.sendSystemMessage(Component.literal("No item in that slot!"));
                return false;
            }
            
            // Validate quantity
            if (quantity < 1 || quantity > itemInSlot.getCount()) {
                player.sendSystemMessage(Component.literal("Invalid quantity! You only have " + itemInSlot.getCount() + " of that item."));
                return false;
            }
            
            // Extract item ID and component data server-side from the ACTUAL item
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(itemInSlot.getItem()).toString();
            String componentData = com.freemarket.common.attachments.ItemComponentHandler.getComponentData(itemInSlot);
            
            // Remove the item from inventory
            ItemStack toRemove = itemInSlot.copy();
            toRemove.setCount(quantity);
            itemInSlot.shrink(quantity);
            inventory.setItem(slotIndex, itemInSlot.isEmpty() ? ItemStack.EMPTY : itemInSlot);
            
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
            
            // Save auction to NBT storage
            AuctionDataManager.addAuction(level, auction);
            
            player.sendSystemMessage(Component.literal("Auction created successfully!"));
            FreeMarket.LOGGER.info("{} created auction {} for {}", player.getName().getString(), auctionId, itemId);
            
            return true;
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to create auction: {}", e.getMessage(), e);
            player.sendSystemMessage(Component.literal("Failed to create auction!"));
            return false;
        }
    }
    
    /**
     * Creates a new auction (legacy method for backwards compatibility).
     * @deprecated Use {@link #createAuctionFromSlot} instead to avoid component mismatch issues.
     * @return true if successful
     */
    @Deprecated
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
            
            // Create ItemStack to validate and remove from inventory
            ItemStack itemStack = createItemStackFromId(itemId, componentData, quantity, level.getServer());
            if (itemStack == null) {
                player.sendSystemMessage(Component.literal("Invalid item!"));
                return false;
            }
            
            // Check if player has the item
            if (!hasItemInInventory(player, itemStack)) {
                player.sendSystemMessage(Component.literal("You don't have this item in your inventory!"));
                return false;
            }
            
            // Remove item from inventory
            boolean removed = removeItemFromInventory(player, itemStack);
            if (!removed) {
                player.sendSystemMessage(Component.literal("Failed to remove item from inventory!"));
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
            
            // Save auction to NBT storage
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
     * Data is loaded from NBT storage only when bidding.
     * @return true if successful
     */
    public static boolean placeBid(ServerLevel level, ServerPlayer player, String auctionId, long bidAmount) {
        try {
            // Load auctions from NBT storage (only when bidding)
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
            
            // Check if player is the seller (skip in debug mode)
            if (!DEBUG_MODE && auction.getSellerUuid().equals(player.getUUID().toString())) {
                player.sendSystemMessage(Component.literal("You cannot bid on your own auction!"));
                return false;
            }
            
            // Check if bid is high enough
            long minBid = auction.getMinimumBid();
            if (bidAmount < minBid) {
                if (auction.getCurrentBid() == auction.getStartingPrice()) {
                    // First bid must be at least the starting price
                    player.sendSystemMessage(Component.literal("Bid must be at least the starting price of $" + auction.getStartingPrice() + "!"));
                } else {
                    // Subsequent bids must be higher than current bid
                    player.sendSystemMessage(Component.literal("Bid must be higher than the current bid of $" + auction.getCurrentBid() + "!"));
                }
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
            
            // Save auction to NBT storage
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
     * Cancels an auction and returns the item to the seller.
     * @return true if successful
     */
    public static boolean cancelAuction(ServerLevel level, ServerPlayer player, String auctionId) {
        try {
            // Load auctions from NBT storage
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
            
            // Check if player is the seller (unless admin mode is enabled)
            boolean isAdminMode = player.hasPermissions(2); // OP Level 2
            boolean isOwnAuction = auction.getSellerUuid().equals(player.getUUID().toString());
            
            if (!isOwnAuction && !isAdminMode) {
                player.sendSystemMessage(Component.literal("You can only cancel your own auctions!"));
                return false;
            }
            
            // Check if expired
            if (auction.isExpired()) {
                player.sendSystemMessage(Component.literal("This auction has already expired!"));
                return false;
            }
            
            // Refund the current bidder if there is one
            if (auction.getBidderUuid() != null) {
                ServerPlayer bidder = level.getServer().getPlayerList().getPlayer(
                    java.util.UUID.fromString(auction.getBidderUuid())
                );
                if (bidder != null) {
                    ServerWalletHandler.addMoney(bidder, auction.getCurrentBid());
                    bidder.sendSystemMessage(Component.literal("The auction you bid on was cancelled. Your bid of $" + 
                        auction.getCurrentBid() + " has been refunded."));
                }
            }
            
            // Create ItemStack from auction data
            ItemStack itemStack = createItemStackFromId(auction.getItemId(), auction.getComponentData(), auction.getQuantity(), level.getServer());
            if (itemStack == null) {
                player.sendSystemMessage(Component.literal("Failed to restore item!"));
                return false;
            }
            
            // Return item to seller's inventory (or admin's if admin deleted)
            ServerPlayer seller = isOwnAuction ? player : level.getServer().getPlayerList().getPlayer(
                java.util.UUID.fromString(auction.getSellerUuid())
            );
            
            if (seller != null) {
                boolean added = seller.getInventory().add(itemStack);
                if (!added) {
                    // Try to drop the item if inventory is full
                    seller.drop(itemStack, false);
                    seller.sendSystemMessage(Component.literal("Auction cancelled! Item dropped because inventory is full."));
                } else {
                    seller.sendSystemMessage(Component.literal("Auction cancelled! Item returned to your inventory."));
                }
            } else {
                // Seller is offline
                if (isAdminMode && !isOwnAuction) {
                    // Admin deleted auction - store item as pending reward for seller
                    PendingReward reward = createItemReward(
                        auction.getSellerUuid(),
                        auction.getSellerName(),
                        itemStack,
                        "Auction cancelled by admin: " + auction.getItemId()
                    );
                    PendingRewardsManager.addPendingReward(level, reward);
                    
                    player.sendSystemMessage(Component.literal("Auction cancelled. Item stored for seller (offline)."));
                    FreeMarket.LOGGER.info("Admin {} cancelled auction for offline seller {}, stored item as pending reward", 
                        player.getName().getString(), auction.getSellerName());
                } else {
                    // Player cancelled their own auction but is offline - store as pending reward
                    PendingReward reward = createItemReward(
                        auction.getSellerUuid(),
                        auction.getSellerName(),
                        itemStack,
                        "Auction cancelled: " + auction.getItemId()
                    );
                    PendingRewardsManager.addPendingReward(level, reward);
                    
                    FreeMarket.LOGGER.info("Player {} cancelled their own auction while offline, stored item as pending reward", 
                        auction.getSellerName());
                }
            }
            
            // Remove auction from storage
            AuctionDataManager.removeAuction(level, auctionId);
            
            FreeMarket.LOGGER.info("Player {} {} auction {}", 
                player.getName().getString(), 
                isAdminMode && !isOwnAuction ? "admin-deleted" : "cancelled", 
                auctionId);
            
            return true;
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to cancel auction: {}", e.getMessage(), e);
            player.sendSystemMessage(Component.literal("Failed to cancel auction!"));
            return false;
        }
    }
    
    /**
     * Syncs auction data to all players.
     * Data is loaded from NBT storage only when syncing (render operations).
     */
    public static void syncAuctionsToAllPlayers(ServerLevel level) {
        try {
            // Load auctions from NBT storage (only when syncing for render)
            var auctions = AuctionDataManager.loadAuctions(level);
            FreeMarketPacket syncPacket = FreeMarketPacket.withJson(PacketType.AUCTION_SYNC, new GsonBuilder().create().toJson(auctions));
            net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(syncPacket);
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to sync auctions to players: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Processes expired auctions and sends expiry sync packets to clients.
     * Should be called periodically (e.g., every minute).
     * Data is loaded from NBT storage only when processing expired auctions.
     */
    public static void processExpiredAuctions(ServerLevel level) {
        try {
            // Load auctions from NBT storage
            var auctions = AuctionDataManager.loadAuctions(level);
            List<String> expiredAuctionIds = new ArrayList<>();
            
            // Check for expired auctions
            long currentTime = System.currentTimeMillis();
            for (PlayerAuction auction : auctions) {
                if (currentTime > auction.getExpiryTime()) {
                    expiredAuctionIds.add(auction.getAuctionId());
                }
            }
            
            // If we have expired auctions, send expiry sync packet to all clients
            if (!expiredAuctionIds.isEmpty()) {
                String jsonData = new GsonBuilder().create().toJson(expiredAuctionIds);
                FreeMarketPacket expiryPacket = FreeMarketPacket.withJson(PacketType.AUCTION_EXPIRY_SYNC, jsonData);
                net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(expiryPacket);
                
                FreeMarket.LOGGER.info("Sent expiry sync for {} expired auctions", expiredAuctionIds.size());
            }
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to process expired auctions: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handles expired auctions by processing refunds and item distribution.
     * This method should be called after processExpiredAuctions to actually complete the auctions.
     */
    public static void handleExpiredAuctions(ServerLevel level) {
        try {
            // Load and remove expired auctions from NBT storage
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
                    } else {
                        // Seller offline - store money for when they log in
                        PendingReward reward = new PendingReward(
                            auction.getSellerUuid(),
                            auction.getSellerName(),
                            auction.getCurrentBid(),
                            "Auction sold: " + auction.getItemId()
                        );
                        PendingRewardsManager.addPendingReward(level, reward);
                        FreeMarket.LOGGER.info("Seller {} is offline, stored ${} reward for login", auction.getSellerName(), auction.getCurrentBid());
                    }
                    
                    // Give item to winner
                    ItemStack itemStack = createItemFromAuction(auction, level.getServer());
                    if (winner != null) {
                        boolean added = addItemToInventory(winner, itemStack);
                        if (added) {
                            winner.sendSystemMessage(Component.literal("You won an auction! Item added to your inventory."));
                        } else {
                            winner.sendSystemMessage(Component.literal("You won an auction, but your inventory is full!"));
                            // Store item for later - player was online but inventory was full
                            PendingReward reward = createItemReward(
                                auction.getBidderUuid(),
                                auction.getBidderName(),
                                itemStack,
                                "Won auction: " + auction.getItemId()
                            );
                            PendingRewardsManager.addPendingReward(level, reward);
                            FreeMarket.LOGGER.info("Winner {} inventory full, stored item for later", auction.getBidderName());
                        }
                    } else {
                        // Winner offline - store item for when they log in
                        PendingReward reward = createItemReward(
                            auction.getBidderUuid(),
                            auction.getBidderName(),
                            itemStack,
                            "Won auction: " + auction.getItemId()
                        );
                        PendingRewardsManager.addPendingReward(level, reward);
                        FreeMarket.LOGGER.info("Winner {} is offline, stored item for login", auction.getBidderName());
                    }
                    
                    FreeMarket.LOGGER.info("Auction {} completed: {} sold to {} for ${}", 
                        auction.getAuctionId(), auction.getItemId(), auction.getBidderName(), auction.getCurrentBid());
                } else {
                    // No bids - return item to seller
                    ServerPlayer seller = level.getServer().getPlayerList().getPlayer(
                        java.util.UUID.fromString(auction.getSellerUuid())
                    );
                    
                    // Return item to seller
                    ItemStack itemStack = createItemFromAuction(auction, level.getServer());
                    if (seller != null) {
                        boolean added = addItemToInventory(seller, itemStack);
                        if (added) {
                            seller.sendSystemMessage(Component.literal("Your auction expired with no bids. Item returned to your inventory."));
                        } else {
                            seller.sendSystemMessage(Component.literal("Your auction expired with no bids, but your inventory is full!"));
                            // Store item for later - player was online but inventory was full
                            PendingReward reward = createItemReward(
                                auction.getSellerUuid(),
                                auction.getSellerName(),
                                itemStack,
                                "Auction expired (no bids): " + auction.getItemId()
                            );
                            PendingRewardsManager.addPendingReward(level, reward);
                            FreeMarket.LOGGER.info("Seller {} inventory full, stored item for later", auction.getSellerName());
                        }
                    } else {
                        // Seller offline - store item for when they log in
                        PendingReward reward = createItemReward(
                            auction.getSellerUuid(),
                            auction.getSellerName(),
                            itemStack,
                            "Auction expired (no bids): " + auction.getItemId()
                        );
                        PendingRewardsManager.addPendingReward(level, reward);
                        FreeMarket.LOGGER.info("Seller {} is offline, stored item for login", auction.getSellerName());
                    }
                    
                    FreeMarket.LOGGER.info("Auction {} expired with no bids", auction.getAuctionId());
                }
            }
            
            // Broadcast updated auction list to all players if any auctions expired
            if (!expiredAuctions.isEmpty()) {
                syncAuctionsToAllPlayers(level);
            }
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to handle expired auctions: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Creates an ItemStack from auction data.
     */
    private static ItemStack createItemFromAuction(PlayerAuction auction, net.minecraft.server.MinecraftServer server) {
        return createItemStackFromId(auction.getItemId(), auction.getComponentData(), auction.getQuantity(), server);
    }
    
    /**
     * Creates a pending reward with an item. Extracts item data to avoid registry reference issues.
     */
    private static PendingReward createItemReward(String uuid, String playerName, ItemStack itemStack, String reason) {
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
        String componentData = com.freemarket.common.attachments.ItemComponentHandler.getComponentData(itemStack);
        int count = itemStack.getCount();
        return new PendingReward(uuid, playerName, itemId, componentData, count, reason);
    }
    
    /**
     * Creates an ItemStack from item ID and component data using server-side registry access.
     */
    private static ItemStack createItemStackFromId(String itemId, String componentData, int quantity, net.minecraft.server.MinecraftServer server) {
        try {
            ResourceLocation itemLocation = ResourceLocation.parse(itemId);
            Item item = BuiltInRegistries.ITEM.get(itemLocation);
            
            if (item == null || item == BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("minecraft", "air"))) {
                return null;
            }
            
            ItemStack itemStack = new ItemStack(item, quantity);
            
            // Apply component data using ServerItemHandler for proper server-side registry access
            if (componentData != null && !componentData.trim().isEmpty() && !componentData.equals("{}")) {
                itemStack = ServerItemHandler.createItemWithComponentData(itemStack, componentData, server);
            }
            
            return itemStack;
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to create ItemStack from ID {}: {}", itemId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Checks if player has the specified item in their inventory.
     */
    private static boolean hasItemInInventory(ServerPlayer player, ItemStack itemToCheck) {
        Inventory inventory = player.getInventory();
        int totalCount = 0;
        
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (!slotItem.isEmpty() && ItemStack.isSameItemSameComponents(slotItem, itemToCheck)) {
                totalCount += slotItem.getCount();
            }
        }
        
        return totalCount >= itemToCheck.getCount();
    }
    
    /**
     * Removes the specified item from player's inventory.
     */
    private static boolean removeItemFromInventory(ServerPlayer player, ItemStack itemToRemove) {
        Inventory inventory = player.getInventory();
        int remainingToRemove = itemToRemove.getCount();
        
        // Find all matching stacks and sort by count (fewest first)
        List<java.util.Map.Entry<Integer, ItemStack>> matchingStacks = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (!slotItem.isEmpty() && ItemStack.isSameItemSameComponents(slotItem, itemToRemove)) {
                matchingStacks.add(new java.util.AbstractMap.SimpleEntry<>(i, slotItem));
            }
        }
        
        // Sort by count (ascending)
        matchingStacks.sort((a, b) -> Integer.compare(a.getValue().getCount(), b.getValue().getCount()));
        
        // Remove items
        for (var entry : matchingStacks) {
            if (remainingToRemove <= 0) break;
            
            int slotIndex = entry.getKey();
            ItemStack slotItem = entry.getValue();
            int removeFromSlot = Math.min(remainingToRemove, slotItem.getCount());
            slotItem.shrink(removeFromSlot);
            remainingToRemove -= removeFromSlot;
            
            inventory.setItem(slotIndex, slotItem.isEmpty() ? ItemStack.EMPTY : slotItem);
        }
        
        return remainingToRemove == 0;
    }
    
    /**
     * Adds an item to player's inventory.
     */
    private static boolean addItemToInventory(ServerPlayer player, ItemStack itemToAdd) {
        Inventory inventory = player.getInventory();
        int remainingToAdd = itemToAdd.getCount();
        
        // Only use main inventory slots (0-35)
        final int MAIN_INVENTORY_SIZE = 36;
        
        // Find existing stacks and sort by count (fewest first)
        List<java.util.Map.Entry<Integer, ItemStack>> existingStacks = new ArrayList<>();
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (!slotItem.isEmpty() && ItemStack.isSameItemSameComponents(slotItem, itemToAdd)) {
                existingStacks.add(new java.util.AbstractMap.SimpleEntry<>(i, slotItem));
            }
        }
        
        // Sort by count (ascending)
        existingStacks.sort((a, b) -> Integer.compare(a.getValue().getCount(), b.getValue().getCount()));
        
        // Add to existing stacks
        for (var entry : existingStacks) {
            if (remainingToAdd <= 0) break;
            
            int slotIndex = entry.getKey();
            ItemStack slotItem = entry.getValue();
            int maxStackSize = slotItem.getMaxStackSize();
            int currentCount = slotItem.getCount();
            int canAdd = maxStackSize - currentCount;
            
            if (canAdd > 0) {
                int addToSlot = Math.min(remainingToAdd, canAdd);
                slotItem.grow(addToSlot);
                remainingToAdd -= addToSlot;
                inventory.setItem(slotIndex, slotItem);
            }
        }
        
        // Add to empty slots if needed
        if (remainingToAdd > 0) {
            for (int i = 0; i < MAIN_INVENTORY_SIZE && remainingToAdd > 0; i++) {
                ItemStack slotItem = inventory.getItem(i);
                if (slotItem.isEmpty()) {
                    int addToSlot = Math.min(remainingToAdd, itemToAdd.getMaxStackSize());
                    ItemStack newStack = itemToAdd.copy();
                    newStack.setCount(addToSlot);
                    inventory.setItem(i, newStack);
                    remainingToAdd -= addToSlot;
                }
            }
        }
        
        return remainingToAdd == 0;
    }
    
    /**
     * Enables debug mode to allow bidding on own auctions.
     */
    public static void enableDebugMode() {
        DEBUG_MODE = true;
        FreeMarket.LOGGER.info("Auction debug mode enabled - players can bid on their own auctions");
    }
    
    /**
     * Disables debug mode.
     */
    public static void disableDebugMode() {
        DEBUG_MODE = false;
        FreeMarket.LOGGER.info("Auction debug mode disabled");
    }
    
    /**
     * Checks if debug mode is enabled.
     */
    public static boolean isDebugModeEnabled() {
        return DEBUG_MODE;
    }
}

