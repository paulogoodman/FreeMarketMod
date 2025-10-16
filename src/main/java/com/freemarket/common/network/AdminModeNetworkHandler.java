package com.freemarket.common.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.freemarket.FreeMarket;
import com.freemarket.common.handlers.AdminModeHandler;

/**
 * Network handler for admin mode synchronization.
 * Handles registration and processing of AdminModeSyncPacket.
 */
public class AdminModeNetworkHandler {
    
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("1");
        
        // Register admin mode sync packet
        registrar.playToClient(
            AdminModeSyncPacket.TYPE,
            AdminModeSyncPacket.STREAM_CODEC,
            AdminModeNetworkHandler::handleAdminMode
        );
        
        // Register marketplace sync packet
        registrar.playToClient(
            MarketplaceSyncPacket.TYPE,
            MarketplaceSyncPacket.STREAM_CODEC,
            MarketplaceNetworkHandler::handle
        );
        
        // Register marketplace item operation packet
        registrar.playToServer(
            MarketplaceItemOperationPacket.TYPE,
            MarketplaceItemOperationPacket.STREAM_CODEC,
            MarketplaceItemOperationHandler::handle
        );
        
        // Register wallet request packet
        registrar.playToServer(
            WalletRequestPacket.TYPE,
            WalletRequestPacket.STREAM_CODEC,
            WalletNetworkHandler::handleWalletRequest
        );
        
        // Register wallet sync packet
        registrar.playToClient(
            WalletSyncPacket.TYPE,
            WalletSyncPacket.STREAM_CODEC,
            WalletNetworkHandler::handleWalletSync
        );
        
        // Register buy item request packet
        registrar.playToServer(
            BuyItemRequestPacket.TYPE,
            BuyItemRequestPacket.STREAM_CODEC,
            BuyItemNetworkHandler::handleBuyRequest
        );
        
        // Register buy item response packet
        registrar.playToClient(
            BuyItemResponsePacket.TYPE,
            BuyItemResponsePacket.STREAM_CODEC,
            BuyItemNetworkHandler::handleBuyResponse
        );
    }
    
    /**
     * Handles the admin mode sync packet on the client side.
     * Updates the client-side admin mode state to match the server.
     */
    private static void handleAdminMode(AdminModeSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Update client-side admin mode state
            AdminModeHandler.setAdminMode(packet.adminMode());
            
            FreeMarket.LOGGER.info("Client received admin mode sync: {}", packet.adminMode() ? "enabled" : "disabled");
        });
    }
}
