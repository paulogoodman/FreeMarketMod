package com.freemarket.server.events;

import com.freemarket.common.handlers.AdminModeHandler;
import com.freemarket.common.handlers.AuctionDebugModeHandler;
import com.freemarket.server.network.ServerAuctionSync;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Server-side event handlers for FreeMarket.
 * Handles player join events to synchronize admin mode, auction debug mode state, and auction data.
 */
public class ServerEventHandler {
    
    /**
     * Handles player login events to send admin mode, auction debug mode state, and auction data to newly connected players.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // Send current admin mode state to the newly connected player
            AdminModeHandler.sendAdminModeToPlayer(serverPlayer);
            
            // Send current auction debug mode state to the newly connected player
            AuctionDebugModeHandler.sendAuctionDebugModeToPlayer(serverPlayer);
            
            // Send current auction data to the newly connected player
            ServerAuctionSync.syncAuctionDataToPlayer(serverPlayer);
        }
    }
}
