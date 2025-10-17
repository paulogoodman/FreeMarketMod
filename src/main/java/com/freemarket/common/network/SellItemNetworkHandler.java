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
 * Network handler for sell item operations.
 * Handles sell requests from clients and processes them on the server.
 */
public class SellItemNetworkHandler {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("1");
        
        // Register sell item request packet (client to server)
        registrar.playToServer(
            SellItemRequestPacket.TYPE,
            SellItemRequestPacket.STREAM_CODEC,
            SellItemNetworkHandler::handleSellRequest
        );
        
        // Register sell item response packet (server to client)
        registrar.playToClient(
            SellItemResponsePacket.TYPE,
            SellItemResponsePacket.STREAM_CODEC,
            SellItemNetworkHandler::handleSellResponse
        );
    }

    /**
     * Handles sell item request packets on the server side.
     * Validates inventory, removes item, and adds money to wallet.
     */
    public static void handleSellRequest(SellItemRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            String itemGuid = packet.itemGuid();
            ServerLevel level = player.serverLevel();
            
            // Load marketplace items to find the item
            List<FreeMarketItem> items = FreeMarketDataManager.loadFreeMarketItems(level);
            FreeMarketItem itemToSell = null;
            
            for (FreeMarketItem item : items) {
                if (item.getGuid().equals(itemGuid)) {
                    itemToSell = item;
                    break;
                }
            }
            
            if (itemToSell == null) {
                // Item not found
                SellItemResponsePacket response = new SellItemResponsePacket(false, "Item not found", ServerWalletHandler.getPlayerMoney(player));
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, response);
                return;
            }
            
            // Create item with component data to ensure proper matching
            ItemStack itemStack = com.freemarket.server.handlers.ServerItemHandler.createItemWithComponentData(
                itemToSell.getItemStack(), itemToSell.getComponentData(), level.getServer());
            
            // Check if player has the item in inventory
            if (!hasItemInInventory(player, itemStack)) {
                SellItemResponsePacket response = new SellItemResponsePacket(false, "You don't have this item", ServerWalletHandler.getPlayerMoney(player));
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, response);
                return;
            }
            
            // Remove item from inventory
            boolean removed = removeItemFromInventory(player, itemStack);
            if (!removed) {
                SellItemResponsePacket response = new SellItemResponsePacket(false, "Failed to remove item from inventory", ServerWalletHandler.getPlayerMoney(player));
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, response);
                return;
            }
            
            // Add money to wallet
            ServerWalletHandler.addMoney(player, itemToSell.getSellPrice());
            
            // Send success response
            long newBalance = ServerWalletHandler.getPlayerMoney(player);
            SellItemResponsePacket response = new SellItemResponsePacket(true, "Sale successful", newBalance);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, response);
            
            FreeMarket.LOGGER.info("Player {} sold {} for {} coins. New balance: {}", 
                player.getName().getString(), itemToSell.getItemStack().getDisplayName().getString(), 
                itemToSell.getSellPrice(), newBalance);
        });
    }

    /**
     * Handles sell item response packets on the client side.
     * Updates wallet balance and shows success/error message.
     */
    public static void handleSellResponse(SellItemResponsePacket packet, IPayloadContext context) {
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
                    // Play sell sound on client side
                    player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 0.5F);
                } else {
                    FreeMarket.LOGGER.warn("Sale failed: {}", packet.message());
                }
            }
        });
    }
    
    /**
     * Checks if the player has the specified item in their inventory.
     * Checks total count across all stacks, not individual stack counts.
     */
    private static boolean hasItemInInventory(ServerPlayer player, ItemStack itemToCheck) {
        var inventory = player.getInventory();
        int totalCount = 0;
        
        // Count all matching items across the entire inventory
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (!slotItem.isEmpty() && ItemStack.isSameItemSameComponents(slotItem, itemToCheck)) {
                totalCount += slotItem.getCount();
            }
        }
        
        return totalCount >= itemToCheck.getCount();
    }
    
    /**
     * Removes the specified item from the player's inventory.
     * Prioritizes removing from stacks with the fewest items.
     * Returns true if successful, false if item not found.
     */
    private static boolean removeItemFromInventory(ServerPlayer player, ItemStack itemToRemove) {
        var inventory = player.getInventory();
        int remainingToRemove = itemToRemove.getCount();
        
        // First pass: find all matching stacks and sort by count (fewest first)
        java.util.List<java.util.Map.Entry<Integer, ItemStack>> matchingStacks = new java.util.ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (!slotItem.isEmpty() && ItemStack.isSameItemSameComponents(slotItem, itemToRemove)) {
                matchingStacks.add(new java.util.AbstractMap.SimpleEntry<>(i, slotItem));
            }
        }
        
        // Sort by count (ascending - fewest items first)
        matchingStacks.sort((a, b) -> Integer.compare(a.getValue().getCount(), b.getValue().getCount()));
        
        // Remove items starting from stacks with fewest items
        for (var entry : matchingStacks) {
            if (remainingToRemove <= 0) break;
            
            int slotIndex = entry.getKey();
            ItemStack slotItem = entry.getValue();
            int removeFromSlot = Math.min(remainingToRemove, slotItem.getCount());
            slotItem.shrink(removeFromSlot);
            remainingToRemove -= removeFromSlot;
            
            // Update the slot
            inventory.setItem(slotIndex, slotItem.isEmpty() ? ItemStack.EMPTY : slotItem);
        }
        
        return remainingToRemove == 0; // Return true if we removed all required items
    }
    
}
