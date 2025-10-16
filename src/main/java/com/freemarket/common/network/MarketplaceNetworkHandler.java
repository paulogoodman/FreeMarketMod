package com.freemarket.common.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.freemarket.FreeMarket;
import com.freemarket.client.gui.FreeMarketGuiScreen;
import com.freemarket.client.data.ClientMarketplaceCache;
import com.freemarket.common.data.FreeMarketItem;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Network handler for marketplace data synchronization.
 * Handles registration and processing of MarketplaceSyncPacket.
 */
public class MarketplaceNetworkHandler {
    
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("1");
        registrar.playToClient(
            MarketplaceSyncPacket.TYPE,
            MarketplaceSyncPacket.STREAM_CODEC,
            MarketplaceNetworkHandler::handle
        );
    }
    
    /**
     * Handles the marketplace sync packet on the client side.
     * Updates the client-side marketplace data to match the server.
     */
    public static void handle(MarketplaceSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Convert JSON data to items and cache them
            List<FreeMarketItem> items = packet.toItems();
            ClientMarketplaceCache.updateCache(items);
            
            // Update GUI if it's open
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof FreeMarketGuiScreen freeMarketScreen) {
                freeMarketScreen.updateMarketplaceData(items);
                FreeMarket.LOGGER.info("Client received marketplace sync: {} items (GUI open)", items.size());
            } else {
                FreeMarket.LOGGER.info("Client received marketplace sync: {} items (GUI not open, cached)", items.size());
            }
        });
    }
}
