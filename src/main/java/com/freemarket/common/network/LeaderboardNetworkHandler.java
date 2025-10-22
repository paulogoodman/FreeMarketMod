package com.freemarket.common.network;

import com.freemarket.FreeMarket;
import com.freemarket.client.data.ClientLeaderboardCache;
import com.freemarket.server.data.LeaderboardDataManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Network handler for leaderboard synchronization.
 * Handles leaderboard requests and syncs leaderboard data to clients.
 */
public class LeaderboardNetworkHandler {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("1");
        
        // Register leaderboard request packet (client to server)
        registrar.playToServer(
            LeaderboardRequestPacket.TYPE,
            LeaderboardRequestPacket.STREAM_CODEC,
            LeaderboardNetworkHandler::handleLeaderboardRequest
        );
        
        // Register leaderboard sync packet (server to client)
        registrar.playToClient(
            LeaderboardSyncPacket.TYPE,
            LeaderboardSyncPacket.STREAM_CODEC,
            LeaderboardNetworkHandler::handleLeaderboardSync
        );
    }

    /**
     * Handles leaderboard request packets on the server side.
     * Sends the leaderboard data back to the client.
     */
    public static void handleLeaderboardRequest(LeaderboardRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            ServerLevel level = player.serverLevel();
            var leaderboardData = LeaderboardDataManager.loadLeaderboardData(level);
            LeaderboardSyncPacket syncPacket = new LeaderboardSyncPacket(leaderboardData);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, syncPacket);
            
            FreeMarket.LOGGER.debug("Sent leaderboard data to player {}", player.getName().getString());
        });
    }

    /**
     * Handles leaderboard sync packets on the client side.
     * Updates the client-side cached leaderboard data.
     */
    public static void handleLeaderboardSync(LeaderboardSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientLeaderboardCache.updateLeaderboard(packet.leaderboardData());
            FreeMarket.LOGGER.debug("Received leaderboard data with {} players", packet.leaderboardData().size());
        });
    }
}

