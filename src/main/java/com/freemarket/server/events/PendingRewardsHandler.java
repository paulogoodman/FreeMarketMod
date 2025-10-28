package com.freemarket.server.events;

import com.freemarket.FreeMarket;
import com.freemarket.server.data.PendingReward;
import com.freemarket.server.data.PendingRewardsManager;
import com.freemarket.server.handlers.ServerWalletHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

/**
 * Event handler for notifying players about mail when they log in.
 * Players must manually claim their mail using /freemarket mail claim
 */
@EventBusSubscriber(modid = FreeMarket.MODID)
public class PendingRewardsHandler {
    
    /**
     * Called when a player logs in.
     * Notifies them if they have mail waiting to be claimed.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            String playerUuid = player.getUUID().toString();
            
            // Get all pending rewards for this player
            List<PendingReward> rewards = PendingRewardsManager.getPlayerRewards(level, playerUuid);
            
            if (rewards.isEmpty()) {
                return; // No mail
            }
            
            FreeMarket.LOGGER.info("Found {} mail items for player {}", rewards.size(), player.getName().getString());
            
            // Notify player they have mail
            int itemCount = 0;
            int moneyCount = 0;
            for (PendingReward reward : rewards) {
                if (reward.hasItem()) itemCount++;
                if (reward.hasMoney()) moneyCount++;
            }
            
            StringBuilder notification = new StringBuilder("§6✉ You have mail waiting!");
            notification.append("\n§7You have ");
            if (moneyCount > 0 && itemCount > 0) {
                notification.append(moneyCount).append(" money delivery").append(moneyCount > 1 ? "s" : "")
                           .append(" and ").append(itemCount).append(" item").append(itemCount > 1 ? "s" : "");
            } else if (moneyCount > 0) {
                notification.append(moneyCount).append(" money delivery").append(moneyCount > 1 ? "s" : "");
            } else if (itemCount > 0) {
                notification.append(itemCount).append(" item").append(itemCount > 1 ? "s" : "");
            }
            
            notification.append(" in your mailbox.");
            notification.append("\n§eUse §6/fm mail claim §eto retrieve your items!");
            
            player.sendSystemMessage(Component.literal(notification.toString()));
        }
    }
    
    /**
     * Claims all pending rewards for a player (called from the mail claim command).
     * @param player The player claiming their mail
     * @return true if mail was claimed, false if no mail to claim
     */
    public static boolean claimMail(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        String playerUuid = player.getUUID().toString();
        
        // Get all pending rewards for this player
        List<PendingReward> rewards = PendingRewardsManager.getPlayerRewards(level, playerUuid);
        
        if (rewards.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7You don't have any mail to claim."));
            return false;
        }
        
        FreeMarket.LOGGER.info("Player {} claiming {} mail items", player.getName().getString(), rewards.size());
        
        // Track what was claimed
        StringBuilder messageBuilder = new StringBuilder("§6§l=== Mail Claimed ===§r\n");
        int itemCount = 0;
        int moneyAmount = 0;
        
        for (PendingReward reward : rewards) {
            // Give money if present
            if (reward.hasMoney()) {
                try {
                    ServerWalletHandler.addMoney(player, reward.getMoneyAmount());
                    moneyAmount += reward.getMoneyAmount();
                    messageBuilder.append("§a+ $").append(reward.getMoneyAmount());
                    messageBuilder.append(" §7(").append(reward.getReason()).append(")");
                    messageBuilder.append("\n");
                } catch (Exception e) {
                    FreeMarket.LOGGER.error("Failed to give pending money to player {}: {}", 
                        player.getName().getString(), e.getMessage());
                }
            }
            
            // Give item if present
            if (reward.hasItem()) {
                // Reconstruct ItemStack from stored data
                ItemStack itemStack = createItemStackFromReward(reward);
                if (itemStack != null && !itemStack.isEmpty()) {
                    boolean added = player.getInventory().add(itemStack);
                    
                    itemCount++;
                    if (added) {
                        messageBuilder.append("§b+ ").append(itemStack.getDisplayName().getString());
                        messageBuilder.append(" §7(").append(reward.getReason()).append(")");
                        messageBuilder.append("\n");
                    } else {
                        // Inventory full - drop the item
                        player.drop(itemStack, false);
                        messageBuilder.append("§c⚠ ").append(itemStack.getDisplayName().getString());
                        messageBuilder.append(" §7(dropped, inventory full - ").append(reward.getReason()).append(")");
                        messageBuilder.append("\n");
                        FreeMarket.LOGGER.warn("Inventory full for player {}, dropped pending reward item", 
                            player.getName().getString());
                    }
                } else {
                    FreeMarket.LOGGER.error("Failed to reconstruct item for reward: {}", reward.getReason());
                }
            }
            
            // Remove the reward after processing
            PendingRewardsManager.removePendingReward(level, reward);
        }
        
        // Send summary message to player
        String fullMessage = messageBuilder.toString().trim();
        player.sendSystemMessage(Component.literal(fullMessage));
        FreeMarket.LOGGER.info("Player {} claimed {} items and ${} from mail", 
            player.getName().getString(), itemCount, moneyAmount);
        
        return true;
    }
    
    /**
     * Creates an ItemStack from pending reward data.
     */
    private static ItemStack createItemStackFromReward(PendingReward reward) {
        try {
            // Get item from registry
            net.minecraft.resources.ResourceLocation itemLocation = net.minecraft.resources.ResourceLocation.parse(reward.getItemId());
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemLocation);
            
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                FreeMarket.LOGGER.error("Failed to find item: {}", reward.getItemId());
                return ItemStack.EMPTY;
            }
            
            // Create base item stack
            ItemStack itemStack = new ItemStack(item, reward.getItemCount());
            
            // Apply component data if present
            if (reward.getComponentData() != null && !reward.getComponentData().isEmpty()) {
                com.freemarket.common.attachments.ItemComponentHandler.applyComponentData(itemStack, reward.getComponentData());
            }
            
            return itemStack;
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to create ItemStack from reward: {}", e.getMessage(), e);
            return ItemStack.EMPTY;
        }
    }
}

