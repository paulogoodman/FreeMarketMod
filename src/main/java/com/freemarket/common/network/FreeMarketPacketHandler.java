package com.freemarket.common.network;

import com.freemarket.FreeMarket;
import com.freemarket.client.data.*;
import com.freemarket.common.data.FreeMarketItem;
import com.freemarket.common.data.FreeMarketItemDTO;
import com.freemarket.common.data.PlayerAuction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import com.freemarket.common.data.PlayerBalanceData;
import com.freemarket.common.handlers.AdminModeHandler;
import com.freemarket.server.data.*;
import com.freemarket.server.handlers.*;
import com.freemarket.server.network.ServerMarketplaceSync;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Objects;

/**
 * Unified packet handler for all FreeMarket network communication.
 * Uses a switch statement to route packets based on their {@link PacketType} discriminator.
 * 
 * <p><b>Security Principles:</b>
 * <ul>
 *   <li>All server-bound packets validate {@code instanceof ServerPlayer}</li>
 *   <li>Admin operations check {@code player.hasPermissions(2)}</li>
 *   <li>Game state (prices, balances, inventory) always loaded from server DataManagers</li>
 *   <li>Client packet data used only as identifiers/parameters, never trusted for game logic</li>
 * </ul>
 */
public class FreeMarketPacketHandler {
    
    private static final Gson GSON = new GsonBuilder().create();
    
    /**
     * Main packet handling method. Routes packets based on type discriminator.
     */
    public static void handle(FreeMarketPacket packet, IPayloadContext context) {
        PacketType type = packet.packetType();
        
        // Route to appropriate handler based on packet type
        switch (type) {
            // Client to Server
            case WALLET_REQUEST -> handleWalletRequest(packet, context);
            case BUY_ITEM_REQUEST -> handleBuyItemRequest(packet, context);
            case SELL_ITEM_REQUEST -> handleSellItemRequest(packet, context);
            case AUCTION_REQUEST -> handleAuctionRequest(packet, context);
            case AUCTION_BID -> handleAuctionBid(packet, context);
            case AUCTION_CREATE -> handleAuctionCreate(packet, context);
            case AUCTION_CANCEL -> handleAuctionCancel(packet, context);
            case LEADERBOARD_REQUEST -> handleLeaderboardRequest(packet, context);
            case MARKETPLACE_ADD_ITEM -> handleMarketplaceAddItem(packet, context);
            case MARKETPLACE_REMOVE_ITEM -> handleMarketplaceRemoveItem(packet, context);
            
            // Server to Client
            case WALLET_SYNC -> handleWalletSync(packet, context);
            case BUY_ITEM_RESPONSE -> handleBuyItemResponse(packet, context);
            case SELL_ITEM_RESPONSE -> handleSellItemResponse(packet, context);
            case AUCTION_SYNC -> handleAuctionSync(packet, context);
            case AUCTION_EXPIRY_SYNC -> handleAuctionExpirySync(packet, context);
            case LEADERBOARD_SYNC -> handleLeaderboardSync(packet, context);
            case MARKETPLACE_SYNC -> handleMarketplaceSync(packet, context);
            case ADMIN_MODE_SYNC -> handleAdminModeSync(packet, context);
            case AUCTION_DEBUG_MODE_SYNC -> handleAuctionDebugModeSync(packet, context);
        }
    }
    
    // ===== CLIENT TO SERVER HANDLERS =====
    
    private static void handleWalletRequest(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            
            long balance = ServerWalletHandler.getPlayerMoney(player);
            String jsonData = GSON.toJson(new WalletData(player.getUUID().toString(), balance));
            
            PacketDistributor.sendToPlayer(player, FreeMarketPacket.withJson(PacketType.WALLET_SYNC, jsonData));
        });
    }
    
    private static void handleBuyItemRequest(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            
            String itemGuid = packet.data();
            ServerLevel level = player.serverLevel();
            
            // SECURITY: Load item from server DataManager (server-authoritative)
            List<FreeMarketItem> items = FreeMarketDataManager.loadFreeMarketItems(level);
            FreeMarketItem itemToBuy = items.stream()
                .filter(item -> item.getGuid().equals(itemGuid))
                .findFirst()
                .orElse(null);
            
            if (itemToBuy == null) {
                sendOperationResponse(player, PacketType.BUY_ITEM_RESPONSE, false, "Item not found");
                return;
            }
            
            // SECURITY: Validate wallet balance server-side
            if (!ServerWalletHandler.hasEnoughMoney(player, itemToBuy.getBuyPrice())) {
                sendOperationResponse(player, PacketType.BUY_ITEM_RESPONSE, false, "Insufficient funds");
                return;
            }
            
            // Create item and add to inventory
            ItemStack itemStack = ServerItemHandler.createItemWithComponentData(
                itemToBuy.getItemStack(), itemToBuy.getComponentData(), level.getServer());
            
            if (!addItemToInventory(player, itemStack)) {
                player.drop(itemStack, false); // Drop if inventory full
            }
            
            // Deduct money (server-authoritative price)
            ServerWalletHandler.removeMoney(player, itemToBuy.getBuyPrice());
            
            sendOperationResponse(player, PacketType.BUY_ITEM_RESPONSE, true, "Purchase successful");
        });
    }
    
    private static void handleSellItemRequest(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            
            String itemGuid = packet.data();
            ServerLevel level = player.serverLevel();
            
            // SECURITY: Load item from server DataManager
            List<FreeMarketItem> items = FreeMarketDataManager.loadFreeMarketItems(level);
            FreeMarketItem itemToSell = items.stream()
                .filter(item -> item.getGuid().equals(itemGuid))
                .findFirst()
                .orElse(null);
            
            if (itemToSell == null) {
                sendOperationResponse(player, PacketType.SELL_ITEM_RESPONSE, false, "Item not found");
                return;
            }
            
            ItemStack itemStack = ServerItemHandler.createItemWithComponentData(
                itemToSell.getItemStack(), itemToSell.getComponentData(), level.getServer());
            
            if (!hasItemInInventory(player, itemStack)) {
                sendOperationResponse(player, PacketType.SELL_ITEM_RESPONSE, false, "You don't have this item");
                return;
            }
            
            if (!removeItemFromInventory(player, itemStack)) {
                sendOperationResponse(player, PacketType.SELL_ITEM_RESPONSE, false, "Failed to remove item");
                return;
            }
            
            // Add money (server-authoritative price)
            ServerWalletHandler.addMoney(player, itemToSell.getSellPrice());
            
            sendOperationResponse(player, PacketType.SELL_ITEM_RESPONSE, true, "Sale successful");
        });
    }
    
    private static void handleAuctionRequest(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            
            ServerLevel level = player.serverLevel();
            var auctions = AuctionDataManager.loadAuctions(level);
            String jsonData = GSON.toJson(auctions);
            
            PacketDistributor.sendToPlayer(player, FreeMarketPacket.withJson(PacketType.AUCTION_SYNC, jsonData));
        });
    }
    
    private static void handleAuctionBid(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            
            JsonObject json = JsonParser.parseString(packet.data()).getAsJsonObject();
            String auctionId = json.get("auctionId").getAsString();
            long bidAmount = json.get("bidAmount").getAsLong();
            
            ServerLevel level = player.serverLevel();
            boolean success = ServerAuctionHandler.placeBid(level, player, auctionId, bidAmount);
            
            if (success) {
                // Broadcast to all players
                var auctions = AuctionDataManager.loadAuctions(level);
                PacketDistributor.sendToAllPlayers(
                    FreeMarketPacket.withJson(PacketType.AUCTION_SYNC, GSON.toJson(auctions)));
            }
        });
    }
    
    private static void handleAuctionCreate(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            
            JsonObject json = JsonParser.parseString(packet.data()).getAsJsonObject();
            String itemId = json.get("itemId").getAsString();
            String componentData = json.get("componentData").getAsString();
            int quantity = json.get("quantity").getAsInt();
            long startingPrice = json.get("startingPrice").getAsLong();
            long durationMinutes = json.get("durationMinutes").getAsLong();
            
            ServerLevel level = player.serverLevel();
            boolean success = ServerAuctionHandler.createAuction(level, player, itemId, 
                componentData, quantity, startingPrice, durationMinutes);
            
            if (success) {
                var auctions = AuctionDataManager.loadAuctions(level);
                PacketDistributor.sendToAllPlayers(
                    FreeMarketPacket.withJson(PacketType.AUCTION_SYNC, GSON.toJson(auctions)));
            }
        });
    }
    
    private static void handleAuctionCancel(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            
            JsonObject json = JsonParser.parseString(packet.data()).getAsJsonObject();
            String auctionId = json.get("auctionId").getAsString();
            
            ServerLevel level = player.serverLevel();
            boolean success = ServerAuctionHandler.cancelAuction(level, player, auctionId);
            
            if (success) {
                // Broadcast updated auction list to all players
                var auctions = AuctionDataManager.loadAuctions(level);
                PacketDistributor.sendToAllPlayers(
                    FreeMarketPacket.withJson(PacketType.AUCTION_SYNC, GSON.toJson(auctions)));
            }
        });
    }
    
    private static void handleLeaderboardRequest(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            
            ServerLevel level = player.serverLevel();
            var leaderboardData = LeaderboardDataManager.loadLeaderboardData(level);
            String jsonData = GSON.toJson(leaderboardData);
            
            PacketDistributor.sendToPlayer(player, FreeMarketPacket.withJson(PacketType.LEADERBOARD_SYNC, jsonData));
        });
    }
    
    private static void handleMarketplaceAddItem(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            
            // SECURITY: Check admin permissions
            if (!player.hasPermissions(2)) {
                FreeMarket.LOGGER.warn("Player {} attempted marketplace operation without permission", 
                    player.getName().getString());
                return;
            }
            
            try {
                // Parse the JSON data from the client
                JsonObject jsonData = GSON.fromJson(packet.data(), JsonObject.class);
                String itemId = jsonData.get("itemId").getAsString();
                String componentData = jsonData.get("componentData").getAsString();
                long buyPrice = jsonData.get("buyPrice").getAsLong();
                long sellPrice = jsonData.get("sellPrice").getAsLong();
                int quantity = jsonData.get("quantity").getAsInt();
                
                // Create ItemStack from itemId with the correct count
                ItemStack itemStack = createItemStackFromId(itemId, componentData, quantity);
                
                // Create FreeMarketItem
                FreeMarketItem item = new FreeMarketItem(
                    itemStack,
                    buyPrice,
                    sellPrice,
                    quantity,
                    player.getName().getString(),
                    null, // GUID will be generated
                    componentData
                );
                
                ServerLevel level = player.serverLevel();
                List<FreeMarketItem> items = FreeMarketDataManager.loadFreeMarketItems(level);
                items.add(item);
                FreeMarketDataManager.saveFreeMarketItems(level, items);
                
                // Sync to all players
                ServerMarketplaceSync.syncToAllPlayers(level, items);
                    
            } catch (Exception e) {
                FreeMarket.LOGGER.error("Failed to add marketplace item: {}", e.getMessage(), e);
            }
        });
    }
    
    private static void handleMarketplaceRemoveItem(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            
            // SECURITY: Check admin permissions
            if (!player.hasPermissions(2)) {
                FreeMarket.LOGGER.warn("Player {} attempted marketplace operation without permission", 
                    player.getName().getString());
                return;
            }
            
            String itemGuid = packet.data();
            ServerLevel level = player.serverLevel();
            List<FreeMarketItem> items = FreeMarketDataManager.loadFreeMarketItems(level);
            
            boolean removed = items.removeIf(item -> item.getGuid().equals(itemGuid));
            if (removed) {
                FreeMarketDataManager.saveFreeMarketItems(level, items);
                ServerMarketplaceSync.syncToAllPlayers(level, items);
            }
        });
    }
    
    // ===== SERVER TO CLIENT HANDLERS =====
    
    private static void handleWalletSync(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            WalletData data = GSON.fromJson(packet.data(), WalletData.class);
            ClientWalletCache.updateBalance(data.playerUuid, data.balance);
            
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof com.freemarket.client.gui.FreeMarketGuiScreen screen) {
                screen.updateWalletBalance(data.balance);
            }
        });
    }
    
    private static void handleBuyItemResponse(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            handleOperationResponse(packet, context, 1.0F);
        });
    }
    
    private static void handleSellItemResponse(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            handleOperationResponse(packet, context, 0.5F);
        });
    }
    
    private static void handleOperationResponse(FreeMarketPacket packet, IPayloadContext context, float pitch) {
        OperationResponse response = GSON.fromJson(packet.data(), OperationResponse.class);
        
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        
        var player = Objects.requireNonNull(minecraft.player);
        ClientWalletCache.updateBalance(player.getUUID().toString(), response.newBalance);
        
        if (minecraft.screen instanceof com.freemarket.client.gui.FreeMarketGuiScreen screen) {
            screen.updateWalletBalanceAndRefreshButtons(response.newBalance);
            
            if (response.success) {
                player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, pitch);
            }
        }
    }
    
    private static void handleAuctionSync(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            List<PlayerAuction> auctions = GSON.fromJson(packet.data(), 
                com.google.gson.reflect.TypeToken.getParameterized(List.class, PlayerAuction.class).getType());
            ClientAuctionCache.updateAuctions(auctions);
        });
    }
    
    private static void handleAuctionExpirySync(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Parse expired auction IDs from server
            List<String> expiredAuctionIds = GSON.fromJson(packet.data(), 
                com.google.gson.reflect.TypeToken.getParameterized(List.class, String.class).getType());
            
            // Remove expired auctions from timing cache
            for (String auctionId : expiredAuctionIds) {
                ClientAuctionTimingCache.removeAuction(auctionId);
            }
            
            // Also remove from main auction cache
            List<PlayerAuction> currentAuctions = ClientAuctionCache.getCachedAuctions();
            currentAuctions.removeIf(auction -> expiredAuctionIds.contains(auction.getAuctionId()));
            ClientAuctionCache.updateAuctions(currentAuctions);
        });
    }
    
    private static void handleLeaderboardSync(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            List<PlayerBalanceData> data = GSON.fromJson(packet.data(),
                com.google.gson.reflect.TypeToken.getParameterized(List.class, PlayerBalanceData.class).getType());
            ClientLeaderboardCache.updateLeaderboard(data);
        });
    }
    
    private static void handleMarketplaceSync(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            List<FreeMarketItemDTO> dtos = GSON.fromJson(packet.data(),
                com.google.gson.reflect.TypeToken.getParameterized(List.class, FreeMarketItemDTO.class).getType());
            
            // Convert DTOs back to FreeMarketItem objects for client use
            List<FreeMarketItem> items = dtos.stream()
                .map(dto -> {
                    // Recreate ItemStack from itemId
                    ItemStack itemStack = createItemStackFromId(dto.getItemId(), dto.getComponentData());
                    return new FreeMarketItem(
                        itemStack,
                        dto.getBuyPrice(),
                        dto.getSellPrice(),
                        dto.getQuantity(),
                        dto.getSeller(),
                        dto.getGuid(),
                        dto.getComponentData()
                    );
                })
                .collect(java.util.stream.Collectors.toList());
            
            ClientMarketplaceCache.updateCache(items);
            
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof com.freemarket.client.gui.FreeMarketGuiScreen screen) {
                screen.updateMarketplaceData(items);
            }
        });
    }
    
    private static void handleAdminModeSync(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            boolean adminMode = Boolean.parseBoolean(packet.data());
            AdminModeHandler.setAdminMode(adminMode);
        });
    }
    
    private static void handleAuctionDebugModeSync(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            boolean auctionDebugMode = Boolean.parseBoolean(packet.data());
            com.freemarket.common.handlers.AuctionDebugModeHandler.setAuctionDebugMode(auctionDebugMode);
        });
    }
    
    // ===== HELPER METHODS =====
    
    /**
     * Creates an ItemStack from an item ID string.
     * Used for client-side reconstruction of items from DTOs.
     */
    private static ItemStack createItemStackFromId(String itemId, String componentData) {
        return createItemStackFromId(itemId, componentData, 1);
    }
    
    /**
     * Creates an ItemStack from an item ID string with a specified count.
     * Used for server-side marketplace item creation.
     */
    private static ItemStack createItemStackFromId(String itemId, String componentData, int count) {
        try {
            ResourceLocation resourceLocation = ResourceLocation.parse(itemId);
            var item = BuiltInRegistries.ITEM.get(resourceLocation);
            
            if (item == null) {
                FreeMarket.LOGGER.warn("Unknown item ID: {}", itemId);
                return net.minecraft.world.item.Items.AIR.getDefaultInstance();
            }
            
            ItemStack itemStack = new ItemStack(item, count);
            
            // Apply component data if present
            if (componentData != null && !componentData.trim().isEmpty() && !componentData.equals("{}")) {
                com.freemarket.common.attachments.ItemComponentHandler.applyComponentData(itemStack, componentData);
            }
            
            return itemStack;
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to create ItemStack from ID {}: {}", itemId, e.getMessage());
            return net.minecraft.world.item.Items.AIR.getDefaultInstance();
        }
    }
    
    private static void sendOperationResponse(ServerPlayer player, PacketType type, boolean success, String message) {
        long balance = ServerWalletHandler.getPlayerMoney(player);
        String jsonData = GSON.toJson(new OperationResponse(success, message, balance));
        PacketDistributor.sendToPlayer(player, FreeMarketPacket.withJson(type, jsonData));
    }
    
    private static boolean addItemToInventory(ServerPlayer player, ItemStack itemToAdd) {
        // Implementation from ShopPacketHandler
        var inventory = player.getInventory();
        int remainingToAdd = itemToAdd.getCount();
        final int MAIN_INVENTORY_SIZE = 36;
        
        java.util.List<java.util.Map.Entry<Integer, ItemStack>> existingStacks = new java.util.ArrayList<>();
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (!slotItem.isEmpty() && ItemStack.isSameItemSameComponents(slotItem, itemToAdd)) {
                existingStacks.add(new java.util.AbstractMap.SimpleEntry<>(i, slotItem));
            }
        }
        
        existingStacks.sort((a, b) -> Integer.compare(a.getValue().getCount(), b.getValue().getCount()));
        
        for (var entry : existingStacks) {
            if (remainingToAdd <= 0) break;
            int slotIndex = entry.getKey();
            ItemStack slotItem = entry.getValue();
            int canAdd = slotItem.getMaxStackSize() - slotItem.getCount();
            
            if (canAdd > 0) {
                int addToSlot = Math.min(remainingToAdd, canAdd);
                slotItem.grow(addToSlot);
                remainingToAdd -= addToSlot;
                inventory.setItem(slotIndex, slotItem);
            }
        }
        
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
    
    private static boolean hasItemInInventory(ServerPlayer player, ItemStack itemToCheck) {
        var inventory = player.getInventory();
        int totalCount = 0;
        
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (!slotItem.isEmpty() && ItemStack.isSameItemSameComponents(slotItem, itemToCheck)) {
                totalCount += slotItem.getCount();
            }
        }
        
        return totalCount >= itemToCheck.getCount();
    }
    
    private static boolean removeItemFromInventory(ServerPlayer player, ItemStack itemToRemove) {
        var inventory = player.getInventory();
        int remainingToRemove = itemToRemove.getCount();
        
        java.util.List<java.util.Map.Entry<Integer, ItemStack>> matchingStacks = new java.util.ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (!slotItem.isEmpty() && ItemStack.isSameItemSameComponents(slotItem, itemToRemove)) {
                matchingStacks.add(new java.util.AbstractMap.SimpleEntry<>(i, slotItem));
            }
        }
        
        matchingStacks.sort((a, b) -> Integer.compare(a.getValue().getCount(), b.getValue().getCount()));
        
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
    
    // ===== DATA CLASSES =====
    
    private record WalletData(String playerUuid, long balance) {}
    private record OperationResponse(boolean success, String message, long newBalance) {}
}

