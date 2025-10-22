package com.freemarket.common.network;

import com.freemarket.FreeMarket;
import com.freemarket.client.data.ClientAuctionCache;
import com.freemarket.server.data.AuctionDataManager;
import com.freemarket.server.handlers.ServerAuctionHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Network handler for auction synchronization.
 */
public class AuctionNetworkHandler {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("1");
        
        // Register auction request packet (client to server)
        registrar.playToServer(
            AuctionRequestPacket.TYPE,
            AuctionRequestPacket.STREAM_CODEC,
            AuctionNetworkHandler::handleAuctionRequest
        );
        
        // Register auction bid packet (client to server)
        registrar.playToServer(
            AuctionBidPacket.TYPE,
            AuctionBidPacket.STREAM_CODEC,
            AuctionNetworkHandler::handleAuctionBid
        );
        
        // Register auction create packet (client to server)
        registrar.playToServer(
            AuctionCreatePacket.TYPE,
            AuctionCreatePacket.STREAM_CODEC,
            AuctionNetworkHandler::handleAuctionCreate
        );
        
        // Register auction sync packet (server to client)
        registrar.playToClient(
            AuctionSyncPacket.TYPE,
            AuctionSyncPacket.STREAM_CODEC,
            AuctionNetworkHandler::handleAuctionSync
        );
    }

    /**
     * Handles auction request packets on the server side.
     */
    public static void handleAuctionRequest(AuctionRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            ServerLevel level = player.serverLevel();
            var auctions = AuctionDataManager.loadAuctions(level);
            AuctionSyncPacket syncPacket = new AuctionSyncPacket(auctions);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, syncPacket);
            
            FreeMarket.LOGGER.debug("Sent auction data to player {}", player.getName().getString());
        });
    }

    /**
     * Handles auction bid packets on the server side.
     */
    public static void handleAuctionBid(AuctionBidPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            ServerLevel level = player.serverLevel();
            boolean success = ServerAuctionHandler.placeBid(level, player, packet.auctionId(), packet.bidAmount());
            
            if (success) {
                // Broadcast updated auction data to all players
                var auctions = AuctionDataManager.loadAuctions(level);
                AuctionSyncPacket syncPacket = new AuctionSyncPacket(auctions);
                net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(syncPacket);
            }
        });
    }

    /**
     * Handles auction create packets on the server side.
     */
    public static void handleAuctionCreate(AuctionCreatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            ServerLevel level = player.serverLevel();
            boolean success = ServerAuctionHandler.createAuction(level, player, packet.itemId(), 
                packet.componentData(), packet.quantity(), packet.startingPrice(), packet.durationMinutes());
            
            if (success) {
                // Broadcast updated auction data to all players
                var auctions = AuctionDataManager.loadAuctions(level);
                AuctionSyncPacket syncPacket = new AuctionSyncPacket(auctions);
                net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(syncPacket);
            }
        });
    }

    /**
     * Handles auction sync packets on the client side.
     */
    public static void handleAuctionSync(AuctionSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientAuctionCache.updateAuctions(packet.auctions());
            FreeMarket.LOGGER.debug("Received auction data with {} auctions", packet.auctions().size());
        });
    }
}

