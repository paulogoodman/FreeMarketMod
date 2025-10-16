package com.freemarket.server.events;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import com.freemarket.server.network.ServerMarketplaceSync;

/**
 * Server-side event handler for marketplace synchronization.
 */
public class ServerMarketplaceEventHandler {
    
    /**
     * Sends marketplace data to players when they join the server.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // Send marketplace data to the newly joined player
            ServerMarketplaceSync.syncMarketplaceData(serverPlayer.serverLevel());
        }
    }
}
