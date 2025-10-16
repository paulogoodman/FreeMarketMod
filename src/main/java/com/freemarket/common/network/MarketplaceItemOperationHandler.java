package com.freemarket.common.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.freemarket.FreeMarket;
import com.freemarket.common.data.FreeMarketItem;
import com.freemarket.server.data.FreeMarketDataManager;
import com.freemarket.server.network.ServerMarketplaceSync;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Network handler for marketplace item operations (add/remove).
 * Handles registration and processing of MarketplaceItemOperationPacket.
 */
public class MarketplaceItemOperationHandler {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("1");
        registrar.playToServer(
            MarketplaceItemOperationPacket.TYPE,
            MarketplaceItemOperationPacket.STREAM_CODEC,
            MarketplaceItemOperationHandler::handle
        );
    }

    /**
     * Handles marketplace item operation packets on the server side.
     * Processes add/remove operations and syncs changes to all clients.
     */
    public static void handle(MarketplaceItemOperationPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            // Check if player has permission (admin mode or OP)
            if (!player.hasPermissions(2)) {
                FreeMarket.LOGGER.warn("Player {} attempted marketplace operation without permission", player.getName().getString());
                return;
            }

            FreeMarketItem item = packet.toItem();
            if (item == null) {
                FreeMarket.LOGGER.error("Failed to deserialize item from marketplace operation packet");
                return;
            }

            String operation = packet.operation();
            ServerLevel level = player.serverLevel();

            if ("add".equals(operation)) {
                // Add item to marketplace
                List<FreeMarketItem> existingItems = FreeMarketDataManager.loadFreeMarketItems(level);
                existingItems.add(item);
                FreeMarketDataManager.saveFreeMarketItems(level, existingItems);
                
                FreeMarket.LOGGER.info("Player {} added item to marketplace: {}", 
                    player.getName().getString(), item.getItemStack().getDisplayName().getString());
                
                // Sync to all players
                ServerMarketplaceSync.syncToAllPlayers(level, existingItems);
                
            } else if ("remove".equals(operation)) {
                // Remove item from marketplace
                List<FreeMarketItem> existingItems = FreeMarketDataManager.loadFreeMarketItems(level);
                boolean removed = existingItems.removeIf(existingItem -> 
                    existingItem.getGuid().equals(item.getGuid())
                );
                
                if (removed) {
                    FreeMarketDataManager.saveFreeMarketItems(level, existingItems);
                    FreeMarket.LOGGER.info("Player {} removed item from marketplace: {}", 
                        player.getName().getString(), item.getItemStack().getDisplayName().getString());
                    
                    // Sync to all players
                    ServerMarketplaceSync.syncToAllPlayers(level, existingItems);
                } else {
                    FreeMarket.LOGGER.warn("Player {} attempted to remove non-existent item: {}", 
                        player.getName().getString(), item.getItemStack().getDisplayName().getString());
                }
            } else {
                FreeMarket.LOGGER.error("Unknown marketplace operation: {}", operation);
            }
        });
    }
}
