package com.freemarket.server.events;

import com.freemarket.FreeMarket;
import com.freemarket.server.handlers.ServerAuctionHandler;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Periodic task that checks for and processes expired auctions.
 * Runs every minute to minimize server load.
 */
@EventBusSubscriber(modid = FreeMarket.MODID)
public class AuctionExpiryTask {
    
    private static final long CHECK_INTERVAL = 60000; // 1 minute in milliseconds
    private static long lastCheckTime = 0;
    
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long currentTime = System.currentTimeMillis();
        
        // Check if enough time has passed since last check
        if (currentTime - lastCheckTime < CHECK_INTERVAL) {
            return;
        }
        
        lastCheckTime = currentTime;
        
        // Process expired auctions for all loaded levels
        try {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                // First sync expired auctions to clients
                ServerAuctionHandler.processExpiredAuctions(level);
                // Then handle the actual refunds and item distribution
                ServerAuctionHandler.handleExpiredAuctions(level);
            }
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to run auction expiry task: {}", e.getMessage(), e);
        }
    }
}

