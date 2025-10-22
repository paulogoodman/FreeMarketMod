package com.freemarket.client.gui;

/**
 * Configuration for buttons displayed on a card.
 * Immutable configuration object that determines which buttons to show and their states.
 */
public class CardButtonConfig {
    public final CardType type;
    
    // Button visibility
    public final boolean showBuy;
    public final boolean showSell;
    public final boolean showBid;
    
    // Prices
    public final long buyPrice;
    public final long sellPrice;
    public final long bidPrice;
    
    // Button states
    public final boolean canBuy;
    public final boolean canSell;
    public final boolean canBid;
    
    // Cooldown states
    public final boolean isBuyCooldown;
    public final boolean isSellCooldown;
    public final boolean isBidCooldown;
    
    // Auction-specific states
    public final boolean isOwnAuction;
    public final boolean isHighestBidder;
    public final boolean isExpired;
    
    private CardButtonConfig(CardType type, boolean showBuy, boolean showSell, boolean showBid,
                            long buyPrice, long sellPrice, long bidPrice,
                            boolean canBuy, boolean canSell, boolean canBid,
                            boolean isBuyCooldown, boolean isSellCooldown, boolean isBidCooldown,
                            boolean isOwnAuction, boolean isHighestBidder, boolean isExpired) {
        this.type = type;
        this.showBuy = showBuy;
        this.showSell = showSell;
        this.showBid = showBid;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.bidPrice = bidPrice;
        this.canBuy = canBuy;
        this.canSell = canSell;
        this.canBid = canBid;
        this.isBuyCooldown = isBuyCooldown;
        this.isSellCooldown = isSellCooldown;
        this.isBidCooldown = isBidCooldown;
        this.isOwnAuction = isOwnAuction;
        this.isHighestBidder = isHighestBidder;
        this.isExpired = isExpired;
    }
    
    /**
     * Creates a button configuration for marketplace cards.
     */
    public static CardButtonConfig forMarketplace(long buyPrice, long sellPrice,
                                                  boolean canBuy, boolean canSell,
                                                  boolean isBuyCooldown, boolean isSellCooldown) {
        return new CardButtonConfig(
            CardType.MARKETPLACE,
            true, true, false,  // show buy, show sell, don't show bid
            buyPrice, sellPrice, 0,
            canBuy, canSell, false,
            isBuyCooldown, isSellCooldown, false,
            false, false, false  // auction states not used
        );
    }
    
    /**
     * Creates a button configuration for auction cards.
     */
    public static CardButtonConfig forAuction(long currentBid, boolean canBid, boolean isBidCooldown,
                                             boolean isOwnAuction, boolean isHighestBidder, boolean isExpired) {
        return new CardButtonConfig(
            CardType.AUCTION,
            false, false, true,  // don't show buy/sell, show bid
            0, 0, currentBid,
            false, false, canBid,
            false, false, isBidCooldown,
            isOwnAuction, isHighestBidder, isExpired
        );
    }
    
    /**
     * Gets the number of buttons that will be displayed.
     */
    public int getButtonCount() {
        int count = 0;
        if (showBuy && buyPrice > 0) count++;
        if (showSell && sellPrice > 0) count++;
        if (showBid) count++;
        return count;
    }
}

