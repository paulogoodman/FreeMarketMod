package com.freemarket.server.events;

import com.freemarket.FreeMarket;
import com.freemarket.server.data.LeaderboardDataManager;
import com.freemarket.server.handlers.ServerWalletHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Event handler for syncing player balances to leaderboard on login/logout.
 */
@EventBusSubscriber(modid = FreeMarket.MODID)
public class LeaderboardEventHandler {
    
    /**
     * Called when a player logs in.
     * Updates their balance in the leaderboard.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            String uuid = player.getUUID().toString();
            String playerName = player.getName().getString();
            long balance = ServerWalletHandler.getPlayerMoney(player);
            
            LeaderboardDataManager.updatePlayerBalance(level, uuid, playerName, balance);
            FreeMarket.LOGGER.debug("Updated leaderboard for player {} on login", playerName);
        }
    }
    
    /**
     * Called when a player logs out.
     * Updates their balance in the leaderboard.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            String uuid = player.getUUID().toString();
            String playerName = player.getName().getString();
            long balance = ServerWalletHandler.getPlayerMoney(player);
            
            LeaderboardDataManager.updatePlayerBalance(level, uuid, playerName, balance);
            FreeMarket.LOGGER.debug("Updated leaderboard for player {} on logout", playerName);
        }
    }
}

