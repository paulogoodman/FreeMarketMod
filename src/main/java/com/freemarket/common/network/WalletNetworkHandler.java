package com.freemarket.common.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.freemarket.FreeMarket;
import com.freemarket.server.handlers.ServerWalletHandler;
import com.freemarket.client.data.ClientWalletCache;
import net.minecraft.server.level.ServerPlayer;

/**
 * Network handler for wallet synchronization.
 * Handles wallet requests and syncs wallet data to clients.
 */
public class WalletNetworkHandler {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("1");
        
        // Register wallet request packet (client to server)
        registrar.playToServer(
            WalletRequestPacket.TYPE,
            WalletRequestPacket.STREAM_CODEC,
            WalletNetworkHandler::handleWalletRequest
        );
        
        // Register wallet sync packet (server to client)
        registrar.playToClient(
            WalletSyncPacket.TYPE,
            WalletSyncPacket.STREAM_CODEC,
            WalletNetworkHandler::handleWalletSync
        );
    }

    /**
     * Handles wallet request packets on the server side.
     * Sends the player's current balance back to the client.
     */
    public static void handleWalletRequest(WalletRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            long balance = ServerWalletHandler.getPlayerMoney(player);
            WalletSyncPacket syncPacket = new WalletSyncPacket(player.getUUID().toString(), balance);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, syncPacket);
        });
    }

    /**
     * Handles wallet sync packets on the client side.
     * Updates the client-side cached wallet balance and refreshes GUI if open.
     */
    public static void handleWalletSync(WalletSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Update client-side wallet cache
            ClientWalletCache.updateBalance(packet.playerUuid(), packet.balance());
            
            // Update GUI if it's open
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.screen instanceof com.freemarket.client.gui.FreeMarketGuiScreen freeMarketScreen) {
                // Update the GUI's cached balance
                freeMarketScreen.updateWalletBalance(packet.balance());
                // Wallet sync received - no need to log every sync
            } else {
                // Wallet sync cached - no need to log every sync
            }
        });
    }
}
