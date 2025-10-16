package com.freemarket.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.freemarket.server.handlers.ServerWalletHandler;
import com.freemarket.common.handlers.AdminModeHandler;
import com.freemarket.server.data.FreeMarketDataManager;
import com.freemarket.common.data.FreeMarketItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import com.freemarket.FreeMarket;
import com.freemarket.common.attachments.ItemComponentHandler;
import java.util.ArrayList;
import java.util.List;

/**
 * Marketplace-related commands for the FreeMarket mod.
 * These commands allow OPs to manage player money balances and admin mode.
 */
@EventBusSubscriber(modid = FreeMarket.MODID)
public class FreeMarketCommands {

    @SubscribeEvent
    public static void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        register(dispatcher);
    }

    /**
     * Registers all marketplace commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Register main marketplace command
        registerFreeMarketCommands(dispatcher, "freemarket");
        
        // Register fm alias
        registerFreeMarketCommands(dispatcher, "fm");
    }
    
    /**
     * Registers marketplace commands with the given command name.
     */
    private static void registerFreeMarketCommands(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
        // Commands available to all players
        dispatcher.register(Commands.literal(commandName)
            .then(Commands.literal("help")
                .executes(FreeMarketCommands::showHelp))
            .then(Commands.literal("balance")
                .executes(FreeMarketCommands::getOwnBalance)
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(FreeMarketCommands::getBalance)))
            .then(Commands.literal("pay")
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("amount", LongArgumentType.longArg(1))
                        .executes(FreeMarketCommands::payPlayer))))
            .then(Commands.literal("itemdata")
                .executes(FreeMarketCommands::getHeldItemData))
            .then(Commands.literal("list")
                .requires(source -> source.hasPermission(2)) // OP level 2 required
                .then(Commands.literal("hand")
                    .then(Commands.argument("buyPrice", LongArgumentType.longArg(1))
                        .then(Commands.argument("sellPrice", LongArgumentType.longArg(1))
                            .executes(FreeMarketCommands::listHeldItem)))))
            .then(Commands.literal("adminmode")
                .requires(source -> source.hasPermission(2)) // OP level 2 required
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                    .executes(FreeMarketCommands::executeAdminMode)
                )
                .executes(FreeMarketCommands::toggleAdminMode))
            .then(Commands.literal("add")
                .requires(source -> source.hasPermission(2)) // OP level 2 required
                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(FreeMarketCommands::addMoney))))
            .then(Commands.literal("remove")
                .requires(source -> source.hasPermission(2)) // OP level 2 required
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("amount", LongArgumentType.longArg(1))
                        .executes(FreeMarketCommands::removeMoney))))
            .then(Commands.literal("set")
                .requires(source -> source.hasPermission(2)) // OP level 2 required
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("amount", LongArgumentType.longArg(0))
                        .executes(FreeMarketCommands::setMoney))))
            .then(Commands.literal("clear")
                .requires(source -> source.hasPermission(2)) // OP level 2 required
                .executes(FreeMarketCommands::clearMarketplace))
            .then(Commands.literal("additem")
                .requires(source -> source.hasPermission(2)) // OP level 2 required
                .then(Commands.argument("item", StringArgumentType.word())
                    .then(Commands.argument("buyPrice", IntegerArgumentType.integer(1))
                        .then(Commands.argument("sellPrice", IntegerArgumentType.integer(0))
                            .then(Commands.argument("quantity", IntegerArgumentType.integer(1))
                                .executes(FreeMarketCommands::addItemToMarketplace)))))));;
    }

    /**
     * Gets the balance of the current user (no parameters).
     */
    private static int getOwnBalance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        
        if (source.getEntity() instanceof ServerPlayer player) {
            long balance = ServerWalletHandler.getPlayerMoney(player);
            Component message = Component.translatable("command.FreeMarket.economy.balance", 
                player.getName().getString(), balance);
            source.sendSuccess(() -> message, false);
            return 1;
        } else {
            Component message = Component.translatable("command.FreeMarket.economy.not_player");
            source.sendFailure(message);
            return 0;
        }
    }

    /**
     * Gets the balance of a player by name.
     */
    private static int getBalance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String playerName = StringArgumentType.getString(context, "player");
        CommandSourceStack source = context.getSource();

        ServerPlayer player = findPlayer(source, playerName);
        
        if (player != null) {
            // Player found - get balance from NBT
            long balance = ServerWalletHandler.getPlayerMoney(player);
            Component message = Component.translatable("command.FreeMarket.economy.balance", 
                playerName, balance);
            source.sendSuccess(() -> message, false);
        } else {
            // Player not found
            Component message = Component.translatable("command.FreeMarket.economy.player_not_found", 
                playerName);
            source.sendFailure(message);
        }

        return 1;
    }

    /**
     * Adds money to a player by name.
     */
    private static int addMoney(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        long amount = LongArgumentType.getLong(context, "amount");
        String playerName = StringArgumentType.getString(context, "player");
        CommandSourceStack source = context.getSource();

        ServerPlayer player = findPlayer(source, playerName);
        
        if (player != null) {
            // Player found - update NBT data
            ServerWalletHandler.addMoney(player, amount);
            long newBalance = ServerWalletHandler.getPlayerMoney(player);
            
            Component message = Component.translatable("command.FreeMarket.economy.add.success", 
                amount, playerName, newBalance);
            source.sendSuccess(() -> message, false);
            
            // Notify the player
            Component playerMessage = Component.translatable("command.FreeMarket.economy.add.notify", 
                amount, newBalance);
            player.sendSystemMessage(playerMessage);
        } else {
            // Player not found
            Component message = Component.translatable("command.FreeMarket.economy.player_not_found", 
                playerName);
            source.sendFailure(message);
        }

        return 1;
    }

    /**
     * Removes money from a player by name.
     */
    private static int removeMoney(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String playerName = StringArgumentType.getString(context, "player");
        long amount = LongArgumentType.getLong(context, "amount");
        CommandSourceStack source = context.getSource();

        ServerPlayer player = findPlayer(source, playerName);
        
        if (player != null) {
            // Player found - update NBT data
            boolean success = ServerWalletHandler.removeMoney(player, amount);
            
            if (success) {
                long newBalance = ServerWalletHandler.getPlayerMoney(player);
                
                Component message = Component.translatable("command.FreeMarket.economy.remove.success", 
                    amount, playerName, newBalance);
                source.sendSuccess(() -> message, false);
                
                // Notify the player
                Component playerMessage = Component.translatable("command.FreeMarket.economy.remove.notify", 
                    amount, newBalance);
                player.sendSystemMessage(playerMessage);
            } else {
                long currentBalance = ServerWalletHandler.getPlayerMoney(player);
                
                Component message = Component.translatable("command.FreeMarket.economy.remove.insufficient", 
                    amount, playerName, currentBalance);
                source.sendFailure(message);
            }
        } else {
            // Player not found
            Component message = Component.translatable("command.FreeMarket.economy.player_not_found", 
                playerName);
            source.sendFailure(message);
        }

        return 1;
    }

    /**
     * Sets the money of a player by name.
     */
    private static int setMoney(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String playerName = StringArgumentType.getString(context, "player");
        long amount = LongArgumentType.getLong(context, "amount");
        CommandSourceStack source = context.getSource();

        ServerPlayer player = findPlayer(source, playerName);
        
        if (player != null) {
            // Player found - update NBT data
            long oldBalance = ServerWalletHandler.getPlayerMoney(player);
            ServerWalletHandler.setPlayerMoney(player, amount);
            
            Component message = Component.translatable("command.FreeMarket.economy.set.success", 
                playerName, oldBalance, amount);
            source.sendSuccess(() -> message, false);
            
            // Notify the player
            Component playerMessage = Component.translatable("command.FreeMarket.economy.set.notify", 
                amount);
            player.sendSystemMessage(playerMessage);
        } else {
            // Player not found
            Component message = Component.translatable("command.FreeMarket.economy.player_not_found", 
                playerName);
            source.sendFailure(message);
        }

        return 1;
    }
    
    /**
     * Helper method to find a player by name, works in both singleplayer and multiplayer.
     */
    private static ServerPlayer findPlayer(CommandSourceStack source, String playerName) {
        // First try the standard player list lookup
        ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(playerName);
        
        if (player != null) {
            return player;
        }
        
        // If not found, try case-insensitive search through all players
        for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
            if (p.getName().getString().equalsIgnoreCase(playerName)) {
                return p;
            }
        }
        
        // For singleplayer, if the command executor is a player and matches the name
        if (source.getEntity() instanceof ServerPlayer commandPlayer) {
            String commandPlayerName = commandPlayer.getName().getString();
            if (commandPlayerName.equalsIgnoreCase(playerName)) {
                return commandPlayer;
            }
        }
        
        // Last resort: try to find by UUID or profile name
        try {
            // Try to get player by profile
            var profileCache = source.getServer().getProfileCache();
            if (profileCache != null) {
                var gameProfile = profileCache.get(playerName);
                if (gameProfile.isPresent()) {
                    return source.getServer().getPlayerList().getPlayer(gameProfile.get().getId());
                }
            }
        } catch (Exception e) {
            // Ignore exceptions, just return null
        }
        
        return null;
    }
    
    /**
     * Executes the adminmode command with a boolean argument.
     * @param context the command context
     * @return command result
     */
    private static int executeAdminMode(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        AdminModeHandler.setAdminMode(enabled, context.getSource().getServer());
        
        Component message = Component.translatable("command.FreeMarket.adminmode.set", 
            enabled ? "enabled" : "disabled");
        context.getSource().sendSuccess(() -> message, true);
        
        return 1;
    }
    
    /**
     * Toggles admin mode when no argument is provided.
     * @param context the command context
     * @return command result
     */
    private static int toggleAdminMode(CommandContext<CommandSourceStack> context) {
        boolean newState = AdminModeHandler.toggleAdminMode(context.getSource().getServer());
        
        Component message = Component.translatable("command.FreeMarket.adminmode.toggle", 
            newState ? "enabled" : "disabled");
        context.getSource().sendSuccess(() -> message, true);
        
        return 1;
    }
    
    /**
     * Pays money from one player to another.
     */
    private static int payPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String targetPlayerName = StringArgumentType.getString(context, "player");
        long amount = LongArgumentType.getLong(context, "amount");
        CommandSourceStack source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer sender)) {
            Component message = Component.translatable("command.FreeMarket.economy.not_player");
            source.sendFailure(message);
            return 0;
        }
        
        // Check if sender has enough money
        if (!ServerWalletHandler.hasEnoughMoney(sender, amount)) {
            long currentBalance = ServerWalletHandler.getPlayerMoney(sender);
            Component message = Component.translatable("command.FreeMarket.freemarket.pay.insufficient", 
                amount, currentBalance);
            source.sendFailure(message);
            return 0;
        }
        
        // Find target player
        ServerPlayer targetPlayer = findPlayer(source, targetPlayerName);
        if (targetPlayer == null) {
            Component message = Component.translatable("command.FreeMarket.economy.player_not_found", targetPlayerName);
            source.sendFailure(message);
            return 0;
        }
        
        // Check if trying to pay yourself
        if (sender.getUUID().equals(targetPlayer.getUUID())) {
            Component message = Component.translatable("command.FreeMarket.freemarket.pay.self");
            source.sendFailure(message);
            return 0;
        }
        
        // Perform the transaction
        boolean success = ServerWalletHandler.removeMoney(sender, amount);
        if (!success) {
            Component message = Component.translatable("command.FreeMarket.freemarket.pay.failed");
            source.sendFailure(message);
            return 0;
        }
        
        ServerWalletHandler.addMoney(targetPlayer, amount);
        
        // Get updated balances
        long senderBalance = ServerWalletHandler.getPlayerMoney(sender);
        long targetBalance = ServerWalletHandler.getPlayerMoney(targetPlayer);
        
        // Send success message to sender
        Component senderMessage = Component.translatable("command.FreeMarket.freemarket.pay.sender_success", 
            amount, targetPlayerName, senderBalance);
        source.sendSuccess(() -> senderMessage, false);
        
        // Send notification to target player
        Component targetMessage = Component.translatable("command.FreeMarket.freemarket.pay.target_notify", 
            amount, sender.getName().getString(), targetBalance);
        targetPlayer.sendSystemMessage(targetMessage);
        
        return 1;
    }
    
    /**
     * Gets the component data for the held item in a copy-pastable format.
     * Shows detailed information about enchantments, armor trims, and other component data.
     */
    private static int getHeldItemData(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            Component message = Component.translatable("command.FreeMarket.economy.not_player");
            source.sendFailure(message);
            return 0;
        }
        
        ItemStack heldItem = player.getMainHandItem();
        
        if (heldItem.isEmpty()) {
            Component message = Component.translatable("command.FreeMarket.freemarket.itemdata.no_item");
            source.sendFailure(message);
            return 0;
        }
        
        try {
            // Get item ID
            String itemId = BuiltInRegistries.ITEM.getKey(heldItem.getItem()).toString();
            
            // Get component data
            String componentData = ItemComponentHandler.getComponentData(heldItem);
            
            // Get item display name
            String itemName = heldItem.getDisplayName().getString();
            
            // Check if item has component data
            boolean hasComponents = ItemComponentHandler.hasComponentData(heldItem);
            
            // Send header
            source.sendSuccess(() -> Component.literal("§6=== Item Data ===§r"), false);
            
            // Send basic item info
            source.sendSuccess(() -> Component.literal("§eItem Name: §f" + itemName), false);
            source.sendSuccess(() -> Component.literal("§eItem ID: §f" + itemId), false);
            source.sendSuccess(() -> Component.literal("§eCount: §f" + heldItem.getCount()), false);
            
            // Send component data if it exists
            if (hasComponents && !componentData.equals("{}")) {
                source.sendSuccess(() -> Component.literal("§eComponent Data:§r"), false);
                
                // Split long component data into multiple messages if needed
                if (componentData.length() > 200) {
                    // Send in chunks
                    String[] lines = componentData.split("(?<=\\})");
                    for (String line : lines) {
                        if (!line.trim().isEmpty()) {
                            source.sendSuccess(() -> Component.literal("§7" + line.trim()), false);
                        }
                    }
                } else {
                    source.sendSuccess(() -> Component.literal("§7" + componentData), false);
                }
                
                // Log detailed component info for debugging
                FreeMarket.LOGGER.info("ItemData command - Item: {}, Components: {}", itemId, componentData);
                
            } else {
                source.sendSuccess(() -> Component.literal("§7No component data found"), false);
                FreeMarket.LOGGER.info("ItemData command - Item: {} has no component data", itemId);
            }
            
        } catch (Exception e) {
            Component message = Component.translatable("command.FreeMarket.freemarket.itemdata.error", e.getMessage());
            source.sendFailure(message);
            FreeMarket.LOGGER.error("Error getting item data: {}", e.getMessage(), e);
            return 0;
        }
        
        return 1;
    }
    
    /**
     * Clears all items from the marketplace.
     * @param context the command context
     * @return command result
     */
    private static int clearMarketplace(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        
        // Clear marketplace using JSON system
        FreeMarketDataManager.saveFreeMarketItems(level, new ArrayList<>());
        
        // Sync empty marketplace data to all players
        com.freemarket.server.network.ServerMarketplaceSync.syncToAllPlayers(level, new ArrayList<>());
        
        Component message = Component.translatable("command.FreeMarket.freemarket.clear.success");
        source.sendSuccess(() -> message, true);
        
        return 1;
    }
    
    /**
     * Adds an item to the marketplace via command.
     * @param context the command context
     * @return command result
     */
    private static int addItemToMarketplace(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String itemId = StringArgumentType.getString(context, "item");
        int buyPrice = IntegerArgumentType.getInteger(context, "buyPrice");
        int sellPrice = IntegerArgumentType.getInteger(context, "sellPrice");
        int quantity = IntegerArgumentType.getInteger(context, "quantity");
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        
        try {
            // Parse item ID
            ResourceLocation itemLocation = ResourceLocation.parse(itemId);
            if (!BuiltInRegistries.ITEM.containsKey(itemLocation)) {
                Component message = Component.translatable("command.FreeMarket.freemarket.additem.invalid_item", itemId);
                source.sendFailure(message);
                return 0;
            }
            
            // Create ItemStack
            ItemStack itemStack = new ItemStack(BuiltInRegistries.ITEM.get(itemLocation), quantity);
            
            // Create FreeMarketItem
            String seller = source.getTextName();
            String guid = java.util.UUID.randomUUID().toString();
            FreeMarketItem FreeMarketItem = new FreeMarketItem(itemStack, buyPrice, sellPrice, quantity, seller, guid, "{}");
            
            // Add item using JSON system
            List<FreeMarketItem> existingItems = FreeMarketDataManager.loadFreeMarketItems(level);
            existingItems.add(FreeMarketItem);
            FreeMarketDataManager.saveFreeMarketItems(level, existingItems);
            
            // Sync marketplace data to all players
            com.freemarket.server.network.ServerMarketplaceSync.syncToAllPlayers(level, existingItems);
            
            Component message = Component.translatable("command.FreeMarket.freemarket.additem.success", 
                itemId, quantity, buyPrice, sellPrice);
            source.sendSuccess(() -> message, true);
            
        } catch (Exception e) {
            Component message = Component.translatable("command.FreeMarket.freemarket.additem.error", e.getMessage());
            source.sendFailure(message);
            return 0;
        }
        
        return 1;
    }
    
    /**
     * Shows help information for all available FreeMarket commands.
     * @param context the command context
     * @return command result
     */
    private static int showHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        // Send header
        source.sendSuccess(() -> Component.literal("§6=== FreeMarket Commands ===§r"), false);
        
        // Player commands (available to all)
        source.sendSuccess(() -> Component.literal("§ePlayer Commands:§r"), false);
        source.sendSuccess(() -> Component.literal("§7/freemarket help§r - Shows this help message"), false);
        source.sendSuccess(() -> Component.literal("§7/freemarket balance§r - Shows your balance"), false);
        source.sendSuccess(() -> Component.literal("§7/freemarket balance <player>§r - Shows another player's balance"), false);
        source.sendSuccess(() -> Component.literal("§7/freemarket pay <player> <amount>§r - Pay money to another player"), false);
        source.sendSuccess(() -> Component.literal("§7/freemarket itemdata§r - Shows data about the item in your hand"), false);
        
        // Admin commands (OP only)
        if (source.hasPermission(2)) {
            source.sendSuccess(() -> Component.literal("§cAdmin Commands (OP Required):§r"), false);
            source.sendSuccess(() -> Component.literal("§7/freemarket adminmode [true/false]§r - Enable/disable admin mode"), false);
            source.sendSuccess(() -> Component.literal("§7/freemarket list hand§r - Add the item in your hand to marketplace"), false);
            source.sendSuccess(() -> Component.literal("§7/freemarket add <amount> <player>§r - Add money to a player"), false);
            source.sendSuccess(() -> Component.literal("§7/freemarket remove <player> <amount>§r - Remove money from a player"), false);
            source.sendSuccess(() -> Component.literal("§7/freemarket set <player> <amount>§r - Set a player's money"), false);
            source.sendSuccess(() -> Component.literal("§7/freemarket clear§r - Clear all marketplace items"), false);
            source.sendSuccess(() -> Component.literal("§7/freemarket additem <item> <buyPrice> <sellPrice> <quantity>§r - Add item to marketplace"), false);
        }
        
        source.sendSuccess(() -> Component.literal("§6Use §e/fm§6 as a shortcut for §e/freemarket§r"), false);
        
        return 1;
    }
    
    /**
     * Lists the item currently held in the player's hand to the marketplace.
     * Captures all NBT data including enchantments and armor trims.
     * @param context the command context
     * @return command result
     */
    private static int listHeldItem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            Component message = Component.translatable("command.FreeMarket.economy.not_player");
            source.sendFailure(message);
            return 0;
        }
        
        ItemStack heldItem = player.getMainHandItem();
        
        if (heldItem.isEmpty()) {
            Component message = Component.translatable("command.FreeMarket.list.hand.empty");
            source.sendFailure(message);
            return 0;
        }
        
        try {
            ServerLevel level = source.getLevel();
            
            // Get buy and sell prices from command arguments
            long buyPrice = LongArgumentType.getLong(context, "buyPrice");
            long sellPrice = LongArgumentType.getLong(context, "sellPrice");
            
            // Create a copy of the item with all NBT data preserved
            ItemStack itemToSell = heldItem.copy();
            
            // Create FreeMarketItem with the exact item data (including NBT)
            String seller = player.getName().getString();
            String guid = java.util.UUID.randomUUID().toString();
            
            // Serialize the item with all NBT data
            String itemData = ItemComponentHandler.getComponentData(itemToSell);
            
            // Create marketplace item with provided prices
            FreeMarketItem marketplaceItem = new FreeMarketItem(
                itemToSell, 
                buyPrice,  // Buy price from argument
                sellPrice, // Sell price from argument
                itemToSell.getCount(), 
                seller, 
                guid, 
                itemData
            );
            
            // Add to marketplace
            List<FreeMarketItem> existingItems = FreeMarketDataManager.loadFreeMarketItems(level);
            existingItems.add(marketplaceItem);
            FreeMarketDataManager.saveFreeMarketItems(level, existingItems);
            
            // Sync marketplace data to all players
            com.freemarket.server.network.ServerMarketplaceSync.syncToAllPlayers(level, existingItems);
            
            // Get item display name for confirmation message
            String itemName = itemToSell.getDisplayName().getString();
            String itemId = BuiltInRegistries.ITEM.getKey(itemToSell.getItem()).toString();
            
            Component message = Component.translatable("command.FreeMarket.list.hand.success", 
                itemName, itemToSell.getCount(), itemId);
            source.sendSuccess(() -> message, true);
            
            // Log additional details about component data
            if (ItemComponentHandler.hasComponentData(itemToSell)) {
                FreeMarket.LOGGER.info("Listed item with component data: {} - Components: {}", 
                    itemId, itemData);
            }
            
        } catch (Exception e) {
            Component message = Component.translatable("command.FreeMarket.list.hand.error", e.getMessage());
            source.sendFailure(message);
            FreeMarket.LOGGER.error("Error listing held item: {}", e.getMessage(), e);
            return 0;
        }
        
        return 1;
    }
}
