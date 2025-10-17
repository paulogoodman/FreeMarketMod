package com.freemarket.common.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.freemarket.FreeMarket;
import com.freemarket.common.data.FreeMarketItem;
import com.freemarket.server.handlers.ServerWalletHandler;
import com.freemarket.server.data.FreeMarketDataManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * Network handler for buy item operations.
 * Handles buy requests from clients and processes them on the server.
 */
public class BuyItemNetworkHandler {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("1");
        
        // Register buy item request packet (client to server)
        registrar.playToServer(
            BuyItemRequestPacket.TYPE,
            BuyItemRequestPacket.STREAM_CODEC,
            BuyItemNetworkHandler::handleBuyRequest
        );
        
        // Register buy item response packet (server to client)
        registrar.playToClient(
            BuyItemResponsePacket.TYPE,
            BuyItemResponsePacket.STREAM_CODEC,
            BuyItemNetworkHandler::handleBuyResponse
        );
    }

    /**
     * Handles buy item request packets on the server side.
     * Validates wallet balance, deducts money, and gives item to player.
     */
    public static void handleBuyRequest(BuyItemRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            String itemGuid = packet.itemGuid();
            ServerLevel level = player.serverLevel();
            
            // Load marketplace items to find the item
            List<FreeMarketItem> items = FreeMarketDataManager.loadFreeMarketItems(level);
            FreeMarketItem itemToBuy = null;
            
            for (FreeMarketItem item : items) {
                if (item.getGuid().equals(itemGuid)) {
                    itemToBuy = item;
                    break;
                }
            }
            
            if (itemToBuy == null) {
                // Item not found
                BuyItemResponsePacket response = new BuyItemResponsePacket(false, "Item not found", ServerWalletHandler.getPlayerMoney(player));
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, response);
                return;
            }
            
            // Check if player has enough money
            if (!ServerWalletHandler.hasEnoughMoney(player, itemToBuy.getBuyPrice())) {
                long currentBalance = ServerWalletHandler.getPlayerMoney(player);
                BuyItemResponsePacket response = new BuyItemResponsePacket(false, "Insufficient funds", currentBalance);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, response);
                return;
            }
            
            // Create item with component data
            ItemStack itemStack = com.freemarket.server.handlers.ServerItemHandler.createItemWithComponentData(
                itemToBuy.getItemStack(), itemToBuy.getComponentData(), level.getServer());
            
            // Try to add item to player inventory
            boolean addedToInventory = addItemToInventory(player, itemStack);
            if (!addedToInventory) {
                // Drop item at player's feet
                player.drop(itemStack, false);
            }
            
            // Deduct money from wallet
            boolean success = ServerWalletHandler.removeMoney(player, itemToBuy.getBuyPrice());
            if (!success) {
                // This shouldn't happen since we checked above, but just in case
                BuyItemResponsePacket response = new BuyItemResponsePacket(false, "Failed to deduct money", ServerWalletHandler.getPlayerMoney(player));
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, response);
                return;
            }
            
            // Send success response
            long newBalance = ServerWalletHandler.getPlayerMoney(player);
            BuyItemResponsePacket response = new BuyItemResponsePacket(true, "Purchase successful", newBalance);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, response);
            
            FreeMarket.LOGGER.info("Player {} bought {} for {} coins. New balance: {}", 
                player.getName().getString(), itemToBuy.getItemStack().getDisplayName().getString(), 
                itemToBuy.getBuyPrice(), newBalance);
        });
    }

    /**
     * Handles buy item response packets on the client side.
     * Updates wallet balance and shows success/error message.
     */
    public static void handleBuyResponse(BuyItemResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.player == null) {
                return;
            }
            
            var player = Objects.requireNonNull(minecraft.player); // Store reference to avoid repeated null checks
            
            // Update wallet cache
            com.freemarket.client.data.ClientWalletCache.updateBalance(
                player.getUUID().toString(), 
                packet.newBalance()
            );
            
            // Update GUI if it's open
            if (minecraft.screen instanceof com.freemarket.client.gui.FreeMarketGuiScreen freeMarketScreen) {
                freeMarketScreen.updateWalletBalanceAndRefreshButtons(packet.newBalance());
                
                if (packet.success()) {
                    // Play purchase sound on client side
                    player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.0F);
                } else {
                    FreeMarket.LOGGER.warn("Purchase failed: {}", packet.message());
                }
            }
        });
    }
    
    /**
     * Adds an item to the player's inventory.
     * Returns true if successful, false if inventory is full.
     */
    private static boolean addItemToInventory(ServerPlayer player, ItemStack itemToAdd) {
        var inventory = player.getInventory();
        int remainingToAdd = itemToAdd.getCount();
        
        // Only use main inventory slots (0-35) - avoid offhand (40), armor (36-39), and curio slots
        final int MAIN_INVENTORY_SIZE = 36; // 0-35: hotbar + main inventory
        
        // First pass: find all existing stacks of the same item in main inventory and sort by count (fewest first)
        java.util.List<java.util.Map.Entry<Integer, ItemStack>> existingStacks = new java.util.ArrayList<>();
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (!slotItem.isEmpty() && ItemStack.isSameItemSameComponents(slotItem, itemToAdd)) {
                existingStacks.add(new java.util.AbstractMap.SimpleEntry<>(i, slotItem));
            }
        }
        
        // Sort by count (ascending - fewest items first)
        existingStacks.sort((a, b) -> Integer.compare(a.getValue().getCount(), b.getValue().getCount()));
        
        // Add items to existing stacks starting with those that have fewest items
        for (var entry : existingStacks) {
            if (remainingToAdd <= 0) break;
            
            int slotIndex = entry.getKey();
            ItemStack slotItem = entry.getValue();
            int maxStackSize = slotItem.getMaxStackSize();
            int currentCount = slotItem.getCount();
            int canAdd = maxStackSize - currentCount;
            
            if (canAdd > 0) {
                int addToSlot = Math.min(remainingToAdd, canAdd);
                slotItem.grow(addToSlot);
                remainingToAdd -= addToSlot;
                inventory.setItem(slotIndex, slotItem);
            }
        }
        
        // If there are still items to add, try to find empty slots in main inventory only
        if (remainingToAdd > 0) {
            for (int i = 0; i < MAIN_INVENTORY_SIZE && remainingToAdd > 0; i++) {
                ItemStack slotItem = inventory.getItem(i);
                if (slotItem.isEmpty()) {
                    int addToSlot = Math.min(remainingToAdd, itemToAdd.getMaxStackSize());
                    ItemStack newStack = itemToAdd.copy();
                    newStack.setCount(addToSlot);
                    inventory.setItem(i, newStack);
                    remainingToAdd -= addToSlot;
                }
            }
        }
        
        return remainingToAdd == 0;
    }
}
