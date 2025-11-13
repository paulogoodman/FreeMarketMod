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
import com.freemarket.server.network.ServerAuctionSync;
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
            
            String itemMarketListingId = packet.data();
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
            
            String itemMarketListingId = packet.data();
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
            if (minecraft.screen instanceof com.freemarket.client.gui.FreeMarketGuiScreen screen) {
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

