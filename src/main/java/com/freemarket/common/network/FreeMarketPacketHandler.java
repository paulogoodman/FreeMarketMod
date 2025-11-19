package com.freemarket.common.network;

import com.freemarket.FreeMarket;
import com.freemarket.client.data.*;
import com.freemarket.common.data.FreeMarketItem;
import com.freemarket.common.data.FreeMarketItemDTO;
import com.freemarket.common.data.PlayerAuction;
import com.freemarket.common.data.PlayerBalanceData;
import com.freemarket.common.handlers.AdminModeHandler;
import com.freemarket.server.data.*;
import com.freemarket.server.handlers.*;
import com.freemarket.server.network.ServerAuctionSync;
import com.freemarket.server.network.ServerMarketplaceSync;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

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
            case INVENTORY_SPACE_REQUEST -> handleInventorySpaceRequest(packet, context);
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
            case INVENTORY_SPACE_RESPONSE -> handleInventorySpaceResponse(packet, context);
            case AUCTION_SYNC -> handleAuctionSync(packet, context);
            case AUCTION_EXPIRY_SYNC -> handleAuctionExpirySync(packet, context);
            case LEADERBOARD_SYNC -> handleLeaderboardSync(packet, context);
            case MARKETPLACE_SYNC -> handleMarketplaceSync(packet, context);
            case ADMIN_MODE_SYNC -> handleAdminModeSync(packet, context);
            case AUCTION_DEBUG_MODE_SYNC -> handleAuctionDebugModeSync(packet, context);
            
            // Chunk handling (client-side reassembly)
            case CHUNK_START, CHUNK_DATA, CHUNK_END -> handleChunk(packet, context);
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

            String packetData = packet.data();
            String parsedListingId = packetData;
            int quantity = 1;

            try {
                JsonObject json = JsonParser.parseString(packetData).getAsJsonObject();
                parsedListingId = json.get("marketListingId").getAsString();
                if (json.has("quantity")) {
                    quantity = Math.max(1, json.get("quantity").getAsInt());
                }
            } catch (Exception e) {
                parsedListingId = packetData;
            }

            final String itemMarketListingId = parsedListingId;

            if (quantity <= 0) {
                sendOperationResponse(player, PacketType.BUY_ITEM_RESPONSE, false, "Invalid quantity");
                return;
            }

            ServerLevel level = player.serverLevel();

            // SECURITY: Load item from server DataManager (server-authoritative)
            List<FreeMarketItem> items = FreeMarketDataManager.loadFreeMarketItems(level);
            FreeMarketItem itemToBuy = items.stream()
                .filter(item -> item.getMarketListingId().equals(itemMarketListingId))
                .findFirst()
                .orElse(null);

            if (itemToBuy == null) {
                sendOperationResponse(player, PacketType.BUY_ITEM_RESPONSE, false, "Item not found");
                return;
            }

            long pricePerOrder = itemToBuy.getBuyPrice();
            if (pricePerOrder <= 0) {
                sendOperationResponse(player, PacketType.BUY_ITEM_RESPONSE, false, "Invalid item price");
                return;
            }

            long totalCost;
            try {
                totalCost = Math.multiplyExact(pricePerOrder, (long) quantity);
            } catch (ArithmeticException ex) {
                sendOperationResponse(player, PacketType.BUY_ITEM_RESPONSE, false, "Requested quantity too large");
                return;
            }

            // SECURITY: Validate wallet balance server-side
            if (!ServerWalletHandler.hasEnoughMoney(player, totalCost)) {
                sendOperationResponse(player, PacketType.BUY_ITEM_RESPONSE, false, "Insufficient funds");
                return;
            }

            // Create item template for orders
            ItemStack itemStackTemplate = ServerItemHandler.createItemWithComponentData(
                itemToBuy.getItemStack(), itemToBuy.getComponentData(), level.getServer());
            
            // Calculate total items needed (quantity * stack size per order)
            int totalItemsNeeded = quantity * Math.max(1, itemToBuy.getStackSize());
            
            // Check inventory space
            InventorySpaceResult spaceResult = calculateInventorySpace(player, itemStackTemplate, totalItemsNeeded);
            
            // Check if we have enough space
            if (spaceResult.totalSpace() < totalItemsNeeded) {
                sendOperationResponse(player, PacketType.BUY_ITEM_RESPONSE, false, 
                    "Insufficient inventory space. Need " + totalItemsNeeded + " items, have space for " + spaceResult.totalSpace());
                return;
            }

            // Add items to inventory (will use shulker boxes if needed)
            for (int i = 0; i < quantity; i++) {
                ItemStack orderStack = itemStackTemplate.copy();
                orderStack.setCount(itemToBuy.getStackSize());
                if (!addItemToInventory(player, orderStack)) {
                    // If we can't add, drop it (shouldn't happen if space check passed)
                    player.drop(orderStack, false);
                }
            }

            // Deduct money (server-authoritative price)
            ServerWalletHandler.removeMoney(player, totalCost);
            
            // Build success message with inventory info
            String successMessage = "Purchase successful";
            if (spaceResult.willUseShulkers()) {
                successMessage += ". Items placed in inventory and shulker boxes";
            } else {
                successMessage += ". Items placed in inventory";
            }

            sendOperationResponse(player, PacketType.BUY_ITEM_RESPONSE, true, successMessage);
        });
    }

    private static void handleSellItemRequest(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            String packetData = packet.data();
            String parsedListingId = packetData;
            int quantity = 1;

            try {
                JsonObject json = JsonParser.parseString(packetData).getAsJsonObject();
                parsedListingId = json.get("marketListingId").getAsString();
                if (json.has("quantity")) {
                    quantity = Math.max(1, json.get("quantity").getAsInt());
                }
            } catch (Exception e) {
                parsedListingId = packetData;
            }

            final String itemMarketListingId = parsedListingId;

            if (quantity <= 0) {
                sendOperationResponse(player, PacketType.SELL_ITEM_RESPONSE, false, "Invalid quantity");
                return;
            }

            ServerLevel level = player.serverLevel();

            // SECURITY: Load item from server DataManager
            List<FreeMarketItem> items = FreeMarketDataManager.loadFreeMarketItems(level);
            FreeMarketItem itemToSell = items.stream()
                .filter(item -> item.getMarketListingId().equals(itemMarketListingId))
                .findFirst()
                .orElse(null);

            if (itemToSell == null) {
                sendOperationResponse(player, PacketType.SELL_ITEM_RESPONSE, false, "Item not found");
                return;
            }

            long pricePerOrder = itemToSell.getSellPrice();
            if (pricePerOrder <= 0) {
                sendOperationResponse(player, PacketType.SELL_ITEM_RESPONSE, false, "Item cannot be sold");
                return;
            }

            ItemStack itemStackTemplate = ServerItemHandler.createItemWithComponentData(
                itemToSell.getItemStack(), itemToSell.getComponentData(), level.getServer());

            int perOrderCount = Math.max(1, itemStackTemplate.getCount());
            long requiredTotalLong;
            try {
                requiredTotalLong = Math.multiplyExact((long) perOrderCount, (long) quantity);
            } catch (ArithmeticException ex) {
                sendOperationResponse(player, PacketType.SELL_ITEM_RESPONSE, false, "Requested quantity too large");
                return;
            }

            if (requiredTotalLong > Integer.MAX_VALUE) {
                sendOperationResponse(player, PacketType.SELL_ITEM_RESPONSE, false, "Requested quantity too large");
                return;
            }

            ItemStack requiredStack = itemStackTemplate.copy();
            requiredStack.setCount((int) requiredTotalLong);

            if (!hasItemInInventory(player, requiredStack)) {
                sendOperationResponse(player, PacketType.SELL_ITEM_RESPONSE, false, "You don't have this item");
                return;
            }

            if (!removeItemFromInventory(player, requiredStack)) {
                sendOperationResponse(player, PacketType.SELL_ITEM_RESPONSE, false, "Failed to remove item");
                return;
            }

            long totalPayout;
            try {
                totalPayout = Math.multiplyExact(pricePerOrder, (long) quantity);
            } catch (ArithmeticException ex) {
                sendOperationResponse(player, PacketType.SELL_ITEM_RESPONSE, false, "Requested quantity too large");
                return;
            }

            // Add money (server-authoritative price)
            ServerWalletHandler.addMoney(player, totalPayout);

            sendOperationResponse(player, PacketType.SELL_ITEM_RESPONSE, true, "Sale successful");
        });
    }

    private static void handleInventorySpaceRequest(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            String listingId = null;
            try {
                JsonObject json = JsonParser.parseString(packet.data()).getAsJsonObject();
                if (json.has("marketListingId")) {
                    listingId = json.get("marketListingId").getAsString();
                }
            } catch (Exception ignored) {
                // Fall through to fallback handling below
            }

            if (listingId == null || listingId.isEmpty()) {
                listingId = packet.data();
            }

            if (listingId == null || listingId.isEmpty()) {
                sendInventorySpaceResponse(player, new InventorySpaceResponse("", 0, 0, 0, 0, 1, System.currentTimeMillis()));
                return;
            }

            final String targetListingId = listingId;

            ServerLevel level = player.serverLevel();
            List<FreeMarketItem> items = FreeMarketDataManager.loadFreeMarketItems(level);
            FreeMarketItem requestedItem = items.stream()
                .filter(item -> targetListingId.equals(item.getMarketListingId()))
                .findFirst()
                .orElse(null);

            if (requestedItem == null) {
                sendInventorySpaceResponse(player, new InventorySpaceResponse(targetListingId, 0, 0, 0, 0, 1, System.currentTimeMillis()));
                return;
            }

            ItemStack template = ServerItemHandler.createItemWithComponentData(
                requestedItem.getItemStack(), requestedItem.getComponentData(), level.getServer());
            int itemsPerOrder = Math.max(1, requestedItem.getStackSize());

            InventorySpaceResult result = calculateInventorySpace(player, template, Integer.MAX_VALUE / 4);
            int maxOrders = itemsPerOrder <= 0 ? 0 : result.totalSpace() / itemsPerOrder;

            InventorySpaceResponse response = new InventorySpaceResponse(
                targetListingId,
                Math.max(0, maxOrders),
                Math.max(0, result.totalSpace()),
                Math.max(0, result.mainInventorySpace()),
                Math.max(0, result.shulkerSpace()),
                itemsPerOrder,
                System.currentTimeMillis()
            );

            sendInventorySpaceResponse(player, response);
        });
    }
    
    private static void handleAuctionRequest(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            
            ServerLevel level = player.serverLevel();
            var auctions = AuctionDataManager.loadAuctions(level);
            
            // Convert PlayerAuction objects to DTOs for serialization
            List<com.freemarket.common.data.FreeMarketAuctionDTO> dtos = auctions.stream()
                .map(com.freemarket.common.data.FreeMarketAuctionDTO::new)
                .collect(java.util.stream.Collectors.toList());
            
            String jsonData = GSON.toJson(dtos);
            
            PacketChunking.sendToPlayerWithChunking(player, PacketType.AUCTION_SYNC, jsonData);
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
                // Broadcast to all players using reliable sync
                FreeMarket.LOGGER.info("Player {} placed bid, syncing auctions to all players", player.getName().getString());
                ServerAuctionSync.syncAuctionData(level);
            }
        });
    }
    
    private static void handleAuctionCreate(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            
            JsonObject json = JsonParser.parseString(packet.data()).getAsJsonObject();
            int slotIndex = json.get("slotIndex").getAsInt();
            int quantity = json.get("quantity").getAsInt();
            long startingPrice = json.get("startingPrice").getAsLong();
            long durationMinutes = json.get("durationMinutes").getAsLong();
            
            ServerLevel level = player.serverLevel();
            boolean success = ServerAuctionHandler.createAuctionFromSlot(level, player, slotIndex, 
                quantity, startingPrice, durationMinutes);
            
            if (success) {
                // Broadcast to all players using reliable sync
                FreeMarket.LOGGER.info("Player {} created auction, syncing to all players", player.getName().getString());
                ServerAuctionSync.syncAuctionData(level);
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
                // Broadcast updated auction list to all players using reliable sync
                FreeMarket.LOGGER.info("Player {} cancelled auction, syncing to all players", player.getName().getString());
                ServerAuctionSync.syncAuctionData(level);
            }
        });
    }
    
    private static void handleLeaderboardRequest(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            
            ServerLevel level = player.serverLevel();
            var leaderboardData = LeaderboardDataManager.loadLeaderboardData(level);
            String jsonData = GSON.toJson(leaderboardData);
            
            com.freemarket.common.network.PacketChunking.sendToPlayerWithChunking(
                player, PacketType.LEADERBOARD_SYNC, jsonData);
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
                // Support both old "quantity" and new "stackSize" for backward compatibility
                int stackSize = jsonData.has("stackSize") ? jsonData.get("stackSize").getAsInt() : 
                               (jsonData.has("quantity") ? jsonData.get("quantity").getAsInt() : 1);
                
                // Create ItemStack from itemId with the correct stack size
                ItemStack itemStack = createItemStackFromId(itemId, componentData, stackSize);
                
                // Create FreeMarketItem (totalStockAvailable is null by default, not yet implemented)
                FreeMarketItem item = new FreeMarketItem(
                    itemStack,
                    buyPrice,
                    sellPrice,
                    stackSize,
                    null, // totalStockAvailable - not yet implemented
                    null, // market listing ID will be generated
                    componentData,
                    Integer.MAX_VALUE // order - default to last position
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
            
            String itemMarketListingId = packet.data();
            ServerLevel level = player.serverLevel();
            List<FreeMarketItem> items = FreeMarketDataManager.loadFreeMarketItems(level);
            
            boolean removed = items.removeIf(item -> item.getMarketListingId().equals(itemMarketListingId));
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
            if (minecraft.screen instanceof com.freemarket.client.gui.commonUI.FreeMarketGuiScreen screen) {
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

    private static void handleInventorySpaceResponse(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            InventorySpaceResponse response = GSON.fromJson(packet.data(), InventorySpaceResponse.class);
            if (response == null || response.marketListingId() == null || response.marketListingId().isEmpty()) {
                return;
            }
            ClientInventorySpaceCache.update(
                response.marketListingId(),
                response.maxOrders(),
                response.totalItems(),
                response.mainInventorySpace(),
                response.shulkerSpace(),
                response.itemsPerOrder(),
                response.timestamp()
            );
        });
    }
    
    private static void handleOperationResponse(FreeMarketPacket packet, IPayloadContext context, float pitch) {
        OperationResponse response = GSON.fromJson(packet.data(), OperationResponse.class);
        
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        
        var player = Objects.requireNonNull(minecraft.player);
        ClientWalletCache.updateBalance(player.getUUID().toString(), response.newBalance);
        
        if (minecraft.screen instanceof com.freemarket.client.gui.commonUI.FreeMarketGuiScreen screen) {
            screen.updateWalletBalanceAndRefreshButtons(response.newBalance);
            
            if (response.success) {
                player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, pitch);
            }
        }
    }
    
    private static void handleAuctionSync(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Deserialize DTOs from JSON
            List<com.freemarket.common.data.FreeMarketAuctionDTO> dtos = GSON.fromJson(packet.data(), 
                com.google.gson.reflect.TypeToken.getParameterized(List.class, com.freemarket.common.data.FreeMarketAuctionDTO.class).getType());
            
            // Convert DTOs to PlayerAuction objects
            List<PlayerAuction> auctions = dtos.stream()
                .map(com.freemarket.common.data.FreeMarketAuctionDTO::toPlayerAuction)
                .collect(java.util.stream.Collectors.toList());
            
            FreeMarket.LOGGER.info("Client received {} auctions from server", auctions.size());
            
            // Update the cache first - this ensures data is available before invalidating container cache
            ClientAuctionCache.updateAuctions(auctions);
            
            // Invalidate the auction container's cache so it picks up the new data
            // This will cause the container to refresh from ClientAuctionCache on next render
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof com.freemarket.client.gui.commonUI.FreeMarketGuiScreen screen) {
                screen.invalidateAuctionContainerCache();
            }
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
                        dto.getStackSize(),
                        dto.getTotalStockAvailable(),
                        dto.getMarketListingId(),
                        dto.getComponentData(),
                        dto.getOrder() // Include order field
                    );
                })
                .collect(java.util.stream.Collectors.toList());
            
            ClientMarketplaceCache.updateCache(items);
            
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof com.freemarket.client.gui.commonUI.FreeMarketGuiScreen screen) {
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
    
    // ===== CHUNK HANDLING =====
    
    /**
     * Per-player chunk reassembly storage.
     * Key: player UUID, Value: ChunkReassemblyState
     */
    private static final java.util.Map<java.util.UUID, ChunkReassemblyState> chunkStorage = new java.util.concurrent.ConcurrentHashMap<>();
    
    /**
     * Handles chunk packets and reassembles them into complete payloads.
     */
    private static void handleChunk(FreeMarketPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            java.util.UUID playerId;
            if (context.player() instanceof ServerPlayer player) {
                playerId = player.getUUID();
            } else {
                // Client-side: use a single client ID (chunks are per-connection)
                playerId = java.util.UUID.nameUUIDFromBytes("client".getBytes());
            }
            
            processChunk(packet, playerId, context);
        });
    }
    
    /**
     * Processes a chunk packet and reassembles the complete payload when all chunks are received.
     */
    private static void processChunk(FreeMarketPacket packet, java.util.UUID playerId, IPayloadContext originalContext) {
        try {
            JsonObject chunkJson = JsonParser.parseString(packet.data()).getAsJsonObject();
            PacketType chunkType = packet.packetType();
            
            ChunkReassemblyState state = chunkStorage.computeIfAbsent(playerId, k -> new ChunkReassemblyState());
            state.context = originalContext; // Store context for when we reassemble
            
            if (chunkType == PacketType.CHUNK_START) {
                // Initialize reassembly state
                String originalTypeName = chunkJson.get("originalType").getAsString();
                state.originalType = PacketType.valueOf(originalTypeName);
                state.totalChunks = chunkJson.get("totalChunks").getAsInt();
                state.chunks = new String[state.totalChunks];
                state.receivedChunks = 0;
                
                int chunkIndex = chunkJson.get("chunkIndex").getAsInt();
                String chunkData = chunkJson.get("data").getAsString();
                state.chunks[chunkIndex] = chunkData;
                state.receivedChunks++;
                
                FreeMarket.LOGGER.debug("Started receiving chunked packet: {} chunks for type {}", 
                    state.totalChunks, state.originalType);
            } else if (chunkType == PacketType.CHUNK_DATA) {
                int chunkIndex = chunkJson.get("chunkIndex").getAsInt();
                String chunkData = chunkJson.get("data").getAsString();
                
                if (state.chunks == null || chunkIndex >= state.chunks.length) {
                    FreeMarket.LOGGER.error("Received chunk {} out of order or invalid", chunkIndex);
                    chunkStorage.remove(playerId);
                    return;
                }
                
                state.chunks[chunkIndex] = chunkData;
                state.receivedChunks++;
            } else if (chunkType == PacketType.CHUNK_END) {
                int chunkIndex = chunkJson.get("chunkIndex").getAsInt();
                String chunkData = chunkJson.get("data").getAsString();
                
                if (state.chunks == null || chunkIndex >= state.chunks.length) {
                    FreeMarket.LOGGER.error("Received final chunk {} out of order or invalid", chunkIndex);
                    chunkStorage.remove(playerId);
                    return;
                }
                
                state.chunks[chunkIndex] = chunkData;
                state.receivedChunks++;
                
                // Check if all chunks received
                if (state.receivedChunks == state.totalChunks) {
                    // Reassemble complete payload
                    StringBuilder completePayload = new StringBuilder();
                    for (String chunk : state.chunks) {
                        if (chunk != null) {
                            completePayload.append(chunk);
                        }
                    }
                    
                    // Process the complete payload as if it were the original packet type
                    FreeMarketPacket completePacket = FreeMarketPacket.withJson(state.originalType, completePayload.toString());
                    
                    // Route to appropriate handler with the stored context
                    handle(completePacket, state.context);
                    
                    // Clean up
                    chunkStorage.remove(playerId);
                    
                    FreeMarket.LOGGER.debug("Successfully reassembled chunked packet: {} chunks for type {}", 
                        state.totalChunks, state.originalType);
                } else {
                    FreeMarket.LOGGER.warn("Received final chunk but missing {} chunks", 
                        state.totalChunks - state.receivedChunks);
                }
            }
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Error processing chunk packet: {}", e.getMessage(), e);
            chunkStorage.remove(playerId);
        }
    }
    
    /**
     * Internal class to track chunk reassembly state per player.
     */
    private static class ChunkReassemblyState {
        PacketType originalType;
        int totalChunks;
        String[] chunks;
        int receivedChunks;
        IPayloadContext context;
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

    private static void sendInventorySpaceResponse(ServerPlayer player, InventorySpaceResponse response) {
        String jsonData = GSON.toJson(response);
        PacketDistributor.sendToPlayer(player, FreeMarketPacket.withJson(PacketType.INVENTORY_SPACE_RESPONSE, jsonData));
    }
    
    /**
     * Checks if an item stack is a shulker box.
     */
    private static boolean isShulkerBox(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(Items.SHULKER_BOX) || 
               stack.is(Items.WHITE_SHULKER_BOX) || stack.is(Items.ORANGE_SHULKER_BOX) ||
               stack.is(Items.MAGENTA_SHULKER_BOX) || stack.is(Items.LIGHT_BLUE_SHULKER_BOX) ||
               stack.is(Items.YELLOW_SHULKER_BOX) || stack.is(Items.LIME_SHULKER_BOX) ||
               stack.is(Items.PINK_SHULKER_BOX) || stack.is(Items.GRAY_SHULKER_BOX) ||
               stack.is(Items.LIGHT_GRAY_SHULKER_BOX) || stack.is(Items.CYAN_SHULKER_BOX) ||
               stack.is(Items.PURPLE_SHULKER_BOX) || stack.is(Items.BLUE_SHULKER_BOX) ||
               stack.is(Items.BROWN_SHULKER_BOX) || stack.is(Items.GREEN_SHULKER_BOX) ||
               stack.is(Items.RED_SHULKER_BOX) || stack.is(Items.BLACK_SHULKER_BOX);
    }
    
    /**
     * Container wrapper for shulker box items that reads/writes directly from BlockEntityData.
     */
    private static class ShulkerBoxContainerWrapper implements Container {
        private final ItemStack shulkerBox;
        private final ServerPlayer player;
        private final ItemStack[] items = new ItemStack[27];
        private boolean modified = false;
        
        public ShulkerBoxContainerWrapper(ItemStack shulkerBox, ServerPlayer player) {
            this.shulkerBox = shulkerBox;
            this.player = player;
            // Initialize all slots to empty
            for (int i = 0; i < 27; i++) {
                items[i] = ItemStack.EMPTY;
            }
            // Load items from BlockEntityData
            loadItems();
        }
        
        private void loadItems() {
            if (loadItemsFromContainerComponent()) {
                return;
            }
            loadItemsFromLegacyData();
        }
        
        private boolean loadItemsFromContainerComponent() {
            ItemContainerContents containerContents = shulkerBox.get(DataComponents.CONTAINER);
            if (containerContents == null) {
                return false;
            }
            NonNullList<ItemStack> temp = NonNullList.withSize(27, ItemStack.EMPTY);
            containerContents.copyInto(temp);
            for (int i = 0; i < temp.size(); i++) {
                items[i] = temp.get(i);
            }
            if (FreeMarket.LOGGER.isDebugEnabled()) {
                FreeMarket.LOGGER.debug("Server loaded {} shulker items via container component", temp.stream().filter(stack -> !stack.isEmpty()).count());
            }
            return true;
        }
        
        private void loadItemsFromLegacyData() {
            if (!shulkerBox.has(DataComponents.BLOCK_ENTITY_DATA)) {
                return;
            }
            var blockEntityData = shulkerBox.get(DataComponents.BLOCK_ENTITY_DATA);
            if (blockEntityData == null) {
                return;
            }
            try {
                CompoundTag tag = blockEntityData.copyTag();
                if (tag != null && tag.contains("Items", 9)) {
                    var itemsList = tag.getList("Items", 10);
                    int listSize = itemsList.size();
                    var registryAccess = player.server.registryAccess();
                    for (int i = 0; i < listSize; i++) {
                        CompoundTag itemTag = itemsList.getCompound(i);
                        byte slot = itemTag.getByte("Slot");
                        if (slot >= 0 && slot < 27) {
                            ItemStack parsed = ItemStack.parseOptional(registryAccess, itemTag);
                            if (parsed != null) {
                                items[slot] = parsed;
                            }
                        }
                    }
                    if (FreeMarket.LOGGER.isDebugEnabled()) {
                        FreeMarket.LOGGER.debug("Server loaded {} shulker items via legacy block entity data", listSize);
                    }
                }
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to load shulker box items: {}", e.getMessage());
            }
        }
        
        public void saveItems() {
            if (!modified) return;
            
            NonNullList<ItemStack> temp = NonNullList.withSize(27, ItemStack.EMPTY);
            for (int i = 0; i < 27; i++) {
                temp.set(i, items[i]);
            }
            
            List<ItemStack> copies = new ArrayList<>(temp.size());
            for (ItemStack stack : temp) {
                copies.add(stack == null ? ItemStack.EMPTY : stack.copy());
            }
            shulkerBox.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(copies));
            if (FreeMarket.LOGGER.isDebugEnabled()) {
                FreeMarket.LOGGER.debug("Server wrote {} shulker items to container component", copies.stream().filter(stack -> !stack.isEmpty()).count());
            }
        }
        
        @Override
        public int getContainerSize() { return 27; }
        
        @Override
        public boolean isEmpty() {
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) return false;
            }
            return true;
        }
        
        @Override
        public ItemStack getItem(int slot) {
            return slot >= 0 && slot < 27 ? items[slot] : ItemStack.EMPTY;
        }
        
        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (slot < 0 || slot >= 27) return ItemStack.EMPTY;
            ItemStack stack = items[slot];
            if (stack.isEmpty()) return ItemStack.EMPTY;
            
            ItemStack result = stack.split(amount);
            if (stack.isEmpty()) {
                items[slot] = ItemStack.EMPTY;
            }
            modified = true;
            return result;
        }
        
        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            if (slot < 0 || slot >= 27) return ItemStack.EMPTY;
            ItemStack stack = items[slot];
            items[slot] = ItemStack.EMPTY;
            modified = true;
            return stack;
        }
        
        @Override
        public void setItem(int slot, @javax.annotation.Nonnull ItemStack stack) {
            if (slot >= 0 && slot < 27) {
                items[slot] = stack == null ? ItemStack.EMPTY : stack;
                modified = true;
            }
        }
        
        @Override
        public void setChanged() { modified = true; }
        
        @Override
        public boolean stillValid(@javax.annotation.Nonnull net.minecraft.world.entity.player.Player player) { return true; }
        
        @Override
        public void clearContent() {
            for (int i = 0; i < 27; i++) {
                items[i] = ItemStack.EMPTY;
            }
            modified = true;
        }
    }
    
    /**
     * Gets the container from a shulker box item stack.
     */
    private static Container getShulkerBoxContainer(ItemStack shulkerBox, ServerPlayer player) {
        if (!isShulkerBox(shulkerBox) || shulkerBox.isEmpty()) {
            return null;
        }
        return new ShulkerBoxContainerWrapper(shulkerBox, player);
    }
    
    /**
     * Saves the shulker box container data back to the item stack.
     */
    private static void saveShulkerBoxContainer(ItemStack shulkerBox, Container container, ServerPlayer player) {
        if (!isShulkerBox(shulkerBox) || !(container instanceof ShulkerBoxContainerWrapper wrapper)) {
            return;
        }
        wrapper.saveItems();
    }
    
    /**
     * Adds an item to a shulker box container.
     */
    private static int addItemToShulkerBox(Container shulkerContainer, ItemStack itemToAdd) {
        if (shulkerContainer == null) return 0;
        
        int remainingToAdd = itemToAdd.getCount();
        final int SHULKER_SIZE = 27;
        
        // First, try to add to existing stacks
        java.util.List<java.util.Map.Entry<Integer, ItemStack>> existingStacks = new java.util.ArrayList<>();
        for (int i = 0; i < SHULKER_SIZE; i++) {
            ItemStack slotItem = shulkerContainer.getItem(i);
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
                shulkerContainer.setItem(slotIndex, slotItem);
            }
        }
        
        // Then, try empty slots
        if (remainingToAdd > 0) {
            for (int i = 0; i < SHULKER_SIZE && remainingToAdd > 0; i++) {
                ItemStack slotItem = shulkerContainer.getItem(i);
                if (slotItem.isEmpty()) {
                    int addToSlot = Math.min(remainingToAdd, itemToAdd.getMaxStackSize());
                    ItemStack newStack = itemToAdd.copy();
                    newStack.setCount(addToSlot);
                    shulkerContainer.setItem(i, newStack);
                    remainingToAdd -= addToSlot;
                }
            }
        }
        
        return itemToAdd.getCount() - remainingToAdd;
    }
    
    /**
     * Checks if a shulker box container has the specified item.
     */
    private static int getItemCountInShulkerBox(Container shulkerContainer, ItemStack itemToCheck) {
        if (shulkerContainer == null) return 0;
        
        int totalCount = 0;
        for (int i = 0; i < shulkerContainer.getContainerSize(); i++) {
            ItemStack slotItem = shulkerContainer.getItem(i);
            if (!slotItem.isEmpty() && ItemStack.isSameItemSameComponents(slotItem, itemToCheck)) {
                totalCount += slotItem.getCount();
            }
        }
        return totalCount;
    }
    
    /**
     * Removes items from a shulker box container.
     */
    private static int removeItemFromShulkerBox(Container shulkerContainer, ItemStack itemToRemove) {
        if (shulkerContainer == null) return 0;
        
        int remainingToRemove = itemToRemove.getCount();
        
        java.util.List<java.util.Map.Entry<Integer, ItemStack>> matchingStacks = new java.util.ArrayList<>();
        for (int i = 0; i < shulkerContainer.getContainerSize(); i++) {
            ItemStack slotItem = shulkerContainer.getItem(i);
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
            shulkerContainer.setItem(slotIndex, slotItem.isEmpty() ? ItemStack.EMPTY : slotItem);
        }
        
        return itemToRemove.getCount() - remainingToRemove;
    }
    
    /**
     * Calculates how many items can fit in the player's inventory (including shulker boxes).
     * Returns a record with main inventory space and whether shulker boxes will be used.
     * 
     * PERFORMANCE: Only parses shulker boxes if main inventory is insufficient.
     * Early exits when enough space is found.
     */
    private static InventorySpaceResult calculateInventorySpace(ServerPlayer player, ItemStack itemTemplate, int quantity) {
        var inventory = player.getInventory();
        final int MAIN_INVENTORY_SIZE = 36;
        
        int stackSize = itemTemplate.getMaxStackSize();
        boolean isStackable = stackSize > 1;
        
        // Calculate space in main inventory
        int mainInventorySpace = 0;
        int emptySlots = 0;
        
        // Count existing matching stacks and empty slots
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (slotItem.isEmpty()) {
                emptySlots++;
                if (isStackable) {
                    mainInventorySpace += stackSize;
                } else {
                    mainInventorySpace += 1;
                }
            } else if (isStackable && ItemStack.isSameItemSameComponents(slotItem, itemTemplate)) {
                mainInventorySpace += (stackSize - slotItem.getCount());
            }
        }
        
        // If not stackable, each item needs its own slot
        if (!isStackable) {
            mainInventorySpace = emptySlots;
        }
        
        // Early exit: if main inventory has enough space, don't check shulker boxes
        if (mainInventorySpace >= quantity) {
            return new InventorySpaceResult(mainInventorySpace, 0, mainInventorySpace, false);
        }
        
        boolean willUseShulkers = true;
        int shulkerSpace = 0;
        int neededFromShulkers = quantity - mainInventorySpace;
        
        // Only check shulker boxes if we need more space
        // Early exit when we've found enough space
        for (int i = 0; i < MAIN_INVENTORY_SIZE && shulkerSpace < neededFromShulkers; i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (isShulkerBox(slotItem)) {
                // Create a copy to avoid modifying the original
                ItemStack shulkerBoxCopy = slotItem.copy();
                Container shulkerContainer = getShulkerBoxContainer(shulkerBoxCopy, player);
                if (shulkerContainer != null) {
                    int shulkerEmptySlots = 0;
                    int shulkerMatchingSpace = 0;
                    
                    // Only iterate through shulker slots if we still need space
                    for (int j = 0; j < shulkerContainer.getContainerSize() && shulkerMatchingSpace < neededFromShulkers; j++) {
                        ItemStack shulkerSlot = shulkerContainer.getItem(j);
                        if (shulkerSlot.isEmpty()) {
                            shulkerEmptySlots++;
                            if (isStackable) {
                                shulkerMatchingSpace += stackSize;
                            } else {
                                shulkerMatchingSpace += 1;
                            }
                        } else if (isStackable && ItemStack.isSameItemSameComponents(shulkerSlot, itemTemplate)) {
                            shulkerMatchingSpace += (stackSize - shulkerSlot.getCount());
                        }
                    }
                    
                    if (!isStackable) {
                        shulkerMatchingSpace = shulkerEmptySlots;
                    }
                    
                    shulkerSpace += shulkerMatchingSpace;
                }
            }
        }
        
        int totalSpace = mainInventorySpace + shulkerSpace;
        return new InventorySpaceResult(mainInventorySpace, shulkerSpace, totalSpace, willUseShulkers);
    }
    
    private record InventorySpaceResult(int mainInventorySpace, int shulkerSpace, int totalSpace, boolean willUseShulkers) {}
    
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
        
        // If still remaining, try to add to shulker boxes
        // PERFORMANCE: Early exit when all items are added
        if (remainingToAdd > 0) {
            for (int i = 0; i < MAIN_INVENTORY_SIZE && remainingToAdd > 0; i++) {
                ItemStack slotItem = inventory.getItem(i);
                if (isShulkerBox(slotItem)) {
                    Container shulkerContainer = getShulkerBoxContainer(slotItem, player);
                    if (shulkerContainer != null) {
                        // Create a copy with component data preserved
                        // The copy() method preserves all NBT data including components
                        ItemStack remainingStack = itemToAdd.copy();
                        remainingStack.setCount(remainingToAdd);
                        // Component data is automatically preserved by copy()
                        int added = addItemToShulkerBox(shulkerContainer, remainingStack);
                        if (added > 0) {
                            saveShulkerBoxContainer(slotItem, shulkerContainer, player);
                            // Update the inventory slot - ItemStack is modified in place via applyComponents
                            inventory.setItem(i, slotItem);
                            remainingToAdd -= added;
                            // Early exit if all items added
                            if (remainingToAdd <= 0) break;
                        }
                    }
                }
            }
        }
        
        return remainingToAdd == 0;
    }
    
    private static boolean hasItemInInventory(ServerPlayer player, ItemStack itemToCheck) {
        var inventory = player.getInventory();
        int totalCount = 0;
        
        // Check main inventory first (priority)
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (!slotItem.isEmpty() && ItemStack.isSameItemSameComponents(slotItem, itemToCheck)) {
                totalCount += slotItem.getCount();
            }
        }
        
        // If we have enough, return early
        if (totalCount >= itemToCheck.getCount()) {
            return true;
        }
        
        // Check shulker boxes if needed
        // PERFORMANCE: Early exit when we have enough items
        final int MAIN_INVENTORY_SIZE = 36;
        int needed = itemToCheck.getCount() - totalCount;
        for (int i = 0; i < MAIN_INVENTORY_SIZE && needed > 0; i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (isShulkerBox(slotItem)) {
                // Create a copy to avoid modifying the original (read-only check)
                ItemStack shulkerBoxCopy = slotItem.copy();
                Container shulkerContainer = getShulkerBoxContainer(shulkerBoxCopy, player);
                if (shulkerContainer != null) {
                    int shulkerCount = getItemCountInShulkerBox(shulkerContainer, itemToCheck);
                    totalCount += shulkerCount;
                    needed -= shulkerCount;
                    // Early exit when we have enough
                    if (totalCount >= itemToCheck.getCount()) {
                        return true;
                    }
                }
            }
        }
        
        return totalCount >= itemToCheck.getCount();
    }
    
    private static boolean removeItemFromInventory(ServerPlayer player, ItemStack itemToRemove) {
        var inventory = player.getInventory();
        int remainingToRemove = itemToRemove.getCount();
        
        // First, remove from main inventory (priority)
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
        
        // If still need more, remove from shulker boxes
        // PERFORMANCE: Early exit when all items removed
        if (remainingToRemove > 0) {
            final int MAIN_INVENTORY_SIZE = 36;
            for (int i = 0; i < MAIN_INVENTORY_SIZE && remainingToRemove > 0; i++) {
                ItemStack slotItem = inventory.getItem(i);
                if (isShulkerBox(slotItem)) {
                    Container shulkerContainer = getShulkerBoxContainer(slotItem, player);
                    if (shulkerContainer != null) {
                        // PERFORMANCE: Only copy item stack once, reuse for remaining count
                        ItemStack remainingStack = itemToRemove.copy();
                        remainingStack.setCount(remainingToRemove);
                        int removed = removeItemFromShulkerBox(shulkerContainer, remainingStack);
                        if (removed > 0) {
                            saveShulkerBoxContainer(slotItem, shulkerContainer, player);
                            // Update the inventory slot - ItemStack is modified in place via applyComponents
                            inventory.setItem(i, slotItem);
                            remainingToRemove -= removed;
                            // Early exit if all items removed
                            if (remainingToRemove <= 0) break;
                        }
                    }
                }
            }
        }
        
        return remainingToRemove == 0;
    }
    
    // ===== DATA CLASSES =====
    
    private record WalletData(String playerUuid, long balance) {}
    private record InventorySpaceResponse(
        String marketListingId,
        int maxOrders,
        int totalItems,
        int mainInventorySpace,
        int shulkerSpace,
        int itemsPerOrder,
        long timestamp
    ) {}
    private record OperationResponse(boolean success, String message, long newBalance) {}
}

