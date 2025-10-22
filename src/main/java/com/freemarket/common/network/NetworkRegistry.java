package com.freemarket.common.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Centralized network packet registration for the FreeMarket mod.
 * 
 * <p><b>Unified Packet Architecture:</b>
 * Instead of registering 15+ separate packet types, this mod uses a single unified packet
 * ({@link FreeMarketPacket}) with a discriminator enum ({@link PacketType}). This approach:
 * <ul>
 *   <li>Reduces code by ~90% (3 classes instead of 30+)</li>
 *   <li>Simplifies maintenance and debugging</li>
 *   <li>Is the industry standard for Minecraft modding</li>
 *   <li>Makes packet registration trivial (single registration)</li>
 * </ul>
 * 
 * <p><b>Security:</b> The unified packet is registered bidirectionally (common), but the handler
 * validates packet direction and permissions. Server-bound packets check ServerPlayer and permissions,
 * and all game state is loaded from server-authoritative DataManagers.
 * 
 * @see FreeMarketPacket
 * @see FreeMarketPacketHandler
 * @see PacketType
 */
public class NetworkRegistry {
    
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("1");
        
        // Register the unified packet for client-to-server communication
        registrar.playToServer(
            FreeMarketPacket.CLIENT_TO_SERVER_TYPE,
            FreeMarketPacket.STREAM_CODEC,
            FreeMarketPacketHandler::handle
        );
        
        // Register the unified packet for server-to-client communication
        registrar.playToClient(
            FreeMarketPacket.SERVER_TO_CLIENT_TYPE,
            FreeMarketPacket.STREAM_CODEC,
            FreeMarketPacketHandler::handle
        );
    }
}

