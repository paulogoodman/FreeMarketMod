package com.freemarket.server.events;

import com.freemarket.common.handlers.AdminModeHandler;
import com.freemarket.common.handlers.AuctionDebugModeHandler;
import com.freemarket.server.network.ServerAuctionSync;
import com.freemarket.server.data.ConfigFolderManager;
import net.minecraft.network.chat.Component;
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
            
            // Warn OPs if config folder is too large
            if (serverPlayer.hasPermissions(2) && ConfigFolderManager.isConfigFolderTooLarge()) {
                Component warning = Component.literal("§c[FreeMarket] WARNING: Config folder size exceeds 100 MB!");
                serverPlayer.sendSystemMessage(warning);
                Component suggestion = Component.literal("§eUse §6/freemarket admin clear_configs §eto clean up old JSON files.");
                serverPlayer.sendSystemMessage(suggestion);
            }
        }
    }
}
