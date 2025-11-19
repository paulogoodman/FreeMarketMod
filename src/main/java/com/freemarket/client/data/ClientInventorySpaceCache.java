package com.freemarket.client.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches server-verified inventory space calculations per marketplace listing.
 * Used by the buy confirmation popup to keep quantity limits in sync with the
 * player's actual inventory (including shulker boxes).
 */
public final class ClientInventorySpaceCache {

    private static final Map<String, InventorySpaceInfo> CACHE = new ConcurrentHashMap<>();

    private ClientInventorySpaceCache() {
        // Utility class
    }

    /**
     * Stores or updates the inventory space info for a market listing.
     *
     * @param marketListingId    listing ID, required
     * @param maxOrders          number of complete orders that can fit
     * @param totalItems         total item count capacity for the listing
     * @param mainInventorySpace number of items that fit directly in inventory
     * @param shulkerSpace       number of items that fit inside shulkers
     * @param itemsPerOrder      items consumed per order
     * @param timestamp          server timestamp when calculation was made
     */
    public static void update(String marketListingId,
                              int maxOrders,
                              int totalItems,
                              int mainInventorySpace,
                              int shulkerSpace,
                              int itemsPerOrder,
                              long timestamp) {
        if (marketListingId == null || marketListingId.isEmpty()) {
            return;
        }
        CACHE.put(marketListingId, new InventorySpaceInfo(
            marketListingId,
            maxOrders,
            totalItems,
            mainInventorySpace,
            shulkerSpace,
            itemsPerOrder,
            timestamp
        ));
    }

    /**
     * Retrieves cached inventory space info for a listing.
     *
     * @param marketListingId listing ID
     * @return cached info or null if none
     */
    public static InventorySpaceInfo get(String marketListingId) {
        if (marketListingId == null) {
            return null;
        }
        return CACHE.get(marketListingId);
    }

    /**
     * Removes cached data for a specific listing.
     *
     * @param marketListingId listing ID to invalidate
     */
    public static void invalidate(String marketListingId) {
        if (marketListingId == null) {
            return;
        }
        CACHE.remove(marketListingId);
    }

    /**
     * Clears all cached entries.
     */
    public static void clear() {
        CACHE.clear();
    }

    /**
     * Record representing server verified inventory capacity information.
     *
     * @param marketListingId    listing identifier
     * @param maxOrders          max complete orders available
     * @param totalItems         total items that can fit (direct + shulker)
     * @param mainInventorySpace capacity in the main inventory
     * @param shulkerSpace       capacity provided by shulker boxes
     * @param itemsPerOrder      items required per order
     * @param timestamp          when the calculation was performed (server time)
     */
    public record InventorySpaceInfo(
        String marketListingId,
        int maxOrders,
        int totalItems,
        int mainInventorySpace,
        int shulkerSpace,
        int itemsPerOrder,
        long timestamp
    ) {}
}

