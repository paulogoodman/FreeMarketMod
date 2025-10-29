package com.freemarket.common.network;

/**
 * Enum defining all packet types used in the FreeMarket mod.
 * This discriminator-based approach reduces the need for separate packet classes.
 * 
 * <p><b>Naming Convention:</b>
 * - REQUEST: Client requests data from server
 * - SYNC: Server sends authoritative data to client
 * - RESPONSE: Server responds to client action
 * - ACTION: Client initiates an action on server
 */
public enum PacketType {
    // ===== CLIENT TO SERVER =====
    // Wallet operations
    WALLET_REQUEST,
    
    // Shop operations (buy/sell)
    BUY_ITEM_REQUEST,
    SELL_ITEM_REQUEST,
    
    // Auction operations
    AUCTION_REQUEST,
    AUCTION_BID,
    AUCTION_CREATE,
    AUCTION_CANCEL,
    
    // Leaderboard
    LEADERBOARD_REQUEST,
    
    // Marketplace admin operations
    MARKETPLACE_ADD_ITEM,
    MARKETPLACE_REMOVE_ITEM,
    
    // ===== SERVER TO CLIENT =====
    // Wallet sync
    WALLET_SYNC,
    
    // Shop responses
    BUY_ITEM_RESPONSE,
    SELL_ITEM_RESPONSE,
    
    // Auction sync
    AUCTION_SYNC,
    AUCTION_EXPIRY_SYNC,
    
    // Leaderboard sync
    LEADERBOARD_SYNC,
    
    // Marketplace sync
    MARKETPLACE_SYNC,
    
    // Admin mode
    ADMIN_MODE_SYNC,
    
    // Auction debug mode
    AUCTION_DEBUG_MODE_SYNC;
    
    /**
     * Returns true if this packet type is sent from client to server.
     */
    public boolean isClientToServer() {
        return switch (this) {
            case WALLET_REQUEST, BUY_ITEM_REQUEST, SELL_ITEM_REQUEST,
                 AUCTION_REQUEST, AUCTION_BID, AUCTION_CREATE, AUCTION_CANCEL,
                 LEADERBOARD_REQUEST, MARKETPLACE_ADD_ITEM, MARKETPLACE_REMOVE_ITEM -> true;
            default -> false;
        };
    }
    
    /**
     * Returns true if this packet type is sent from server to client.
     */
    public boolean isServerToClient() {
        return !isClientToServer();
    }
}

