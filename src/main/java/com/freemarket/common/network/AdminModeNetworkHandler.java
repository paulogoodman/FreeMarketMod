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
        registrar.playToClient(
            AdminModeSyncPacket.TYPE,
            AdminModeSyncPacket.STREAM_CODEC,
            AdminModeNetworkHandler::handle
        );
    }
    
    /**
     * Handles the admin mode sync packet on the client side.
     * Updates the client-side admin mode state to match the server.
     */
    private static void handle(AdminModeSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Update client-side admin mode state
            AdminModeHandler.setAdminMode(packet.adminMode());
            
            FreeMarket.LOGGER.info("Client received admin mode sync: {}", packet.adminMode() ? "enabled" : "disabled");
        });
    }
}
