package com.servershop.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.servershop.common.handlers.WalletHandler;
import com.servershop.common.handlers.AdminModeHandler;
import com.servershop.server.data.MarketplaceDataManager;
import com.servershop.common.data.MarketplaceItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import com.servershop.ServerShop;
import com.servershop.common.attachments.ItemComponentHandler;
import java.util.ArrayList;
import java.util.List;

/**
 * Marketplace-related commands for the ServerShop mod.
 * These commands allow OPs to manage player money balances and admin mode.
 */
@EventBusSubscriber(modid = ServerShop.MODID)
public class MarketplaceCommands {

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
        registerMarketplaceCommands(dispatcher, "marketplace");
        
        // Register mp alias
        registerMarketplaceCommands(dispatcher, "mp");
    }
    
    /**
     * Registers marketplace commands with the given command name.
     */
    private static void registerMarketplaceCommands(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
        // Commands available to all players
        dispatcher.register(Commands.literal(commandName)
            .then(Commands.literal("balance")
                .executes(MarketplaceCommands::getOwnBalance)
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(MarketplaceCommands::getBalance)))
            .then(Commands.literal("pay")
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(MarketplaceCommands::payPlayer))))
            .then(Commands.literal("itemdata")
                .executes(MarketplaceCommands::getHeldItemData))
            .then(Commands.literal("adminmode")
                .requires(source -> source.hasPermission(2)) // OP level 2 required
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                    .executes(MarketplaceCommands::executeAdminMode)
                )
                .executes(MarketplaceCommands::toggleAdminMode))
            .then(Commands.literal("add")
                .requires(source -> source.hasPermission(2)) // OP level 2 required
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(MarketplaceCommands::addMoney))))
            .then(Commands.literal("remove")
                .requires(source -> source.hasPermission(2)) // OP level 2 required
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(MarketplaceCommands::removeMoney))))
            .then(Commands.literal("set")
                .requires(source -> source.hasPermission(2)) // OP level 2 required
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                        .executes(MarketplaceCommands::setMoney))))
            .then(Commands.literal("clear")
                .requires(source -> source.hasPermission(2)) // OP level 2 required
                .executes(MarketplaceCommands::clearMarketplace))
            .then(Commands.literal("additem")
                .requires(source -> source.hasPermission(2)) // OP level 2 required
                .then(Commands.argument("item", StringArgumentType.word())
                    .then(Commands.argument("buyPrice", IntegerArgumentType.integer(1))
                        .then(Commands.argument("sellPrice", IntegerArgumentType.integer(0))
                            .then(Commands.argument("quantity", IntegerArgumentType.integer(1))
                                .executes(MarketplaceCommands::addItemToMarketplace))))))
            .then(Commands.literal("testdata")
                .requires(source -> source.hasPermission(2)) // OP level 2 required
                .executes(MarketplaceCommands::generateTestData)));;
    }

    /**
     * Gets the balance of the current user (no parameters).
     */
    private static int getOwnBalance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        
        if (source.getEntity() instanceof ServerPlayer player) {
            long balance = WalletHandler.getPlayerMoney(player);
            Component message = Component.translatable("command.servershop.economy.balance", 
                player.getName().getString(), balance);
            source.sendSuccess(() -> message, false);
            return 1;
        } else {
            Component message = Component.translatable("command.servershop.economy.not_player");
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
            long balance = WalletHandler.getPlayerMoney(player);
            Component message = Component.translatable("command.servershop.economy.balance", 
                playerName, balance);
            source.sendSuccess(() -> message, false);
        } else {
            // Player not found
            Component message = Component.translatable("command.servershop.economy.player_not_found", 
                playerName);
            source.sendFailure(message);
        }

        return 1;
    }

    /**
     * Adds money to a player by name.
     */
    private static int addMoney(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String playerName = StringArgumentType.getString(context, "player");
        int amount = IntegerArgumentType.getInteger(context, "amount");
        CommandSourceStack source = context.getSource();

        ServerPlayer player = findPlayer(source, playerName);
        
        if (player != null) {
            // Player found - update NBT data
            WalletHandler.addMoney(player, amount);
            long newBalance = WalletHandler.getPlayerMoney(player);
            
            Component message = Component.translatable("command.servershop.economy.add.success", 
                amount, playerName, newBalance);
            source.sendSuccess(() -> message, false);
            
            // Notify the player
            Component playerMessage = Component.translatable("command.servershop.economy.add.notify", 
                amount, newBalance);
            player.sendSystemMessage(playerMessage);
        } else {
            // Player not found
            Component message = Component.translatable("command.servershop.economy.player_not_found", 
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
        int amount = IntegerArgumentType.getInteger(context, "amount");
        CommandSourceStack source = context.getSource();

        ServerPlayer player = findPlayer(source, playerName);
        
        if (player != null) {
            // Player found - update NBT data
            boolean success = WalletHandler.removeMoney(player, amount);
            
            if (success) {
                long newBalance = WalletHandler.getPlayerMoney(player);
                
                Component message = Component.translatable("command.servershop.economy.remove.success", 
                    amount, playerName, newBalance);
                source.sendSuccess(() -> message, false);
                
                // Notify the player
                Component playerMessage = Component.translatable("command.servershop.economy.remove.notify", 
                    amount, newBalance);
                player.sendSystemMessage(playerMessage);
            } else {
                long currentBalance = WalletHandler.getPlayerMoney(player);
                
                Component message = Component.translatable("command.servershop.economy.remove.insufficient", 
                    amount, playerName, currentBalance);
                source.sendFailure(message);
            }
        } else {
            // Player not found
            Component message = Component.translatable("command.servershop.economy.player_not_found", 
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
        int amount = IntegerArgumentType.getInteger(context, "amount");
        CommandSourceStack source = context.getSource();

        ServerPlayer player = findPlayer(source, playerName);
        
        if (player != null) {
            // Player found - update NBT data
            long oldBalance = WalletHandler.getPlayerMoney(player);
            WalletHandler.setPlayerMoney(player, amount);
            
            Component message = Component.translatable("command.servershop.economy.set.success", 
                playerName, oldBalance, amount);
            source.sendSuccess(() -> message, false);
            
            // Notify the player
            Component playerMessage = Component.translatable("command.servershop.economy.set.notify", 
                amount);
            player.sendSystemMessage(playerMessage);
        } else {
            // Player not found
            Component message = Component.translatable("command.servershop.economy.player_not_found", 
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
        AdminModeHandler.setAdminMode(enabled);
        
        Component message = Component.translatable("command.servershop.adminmode.set", 
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
        boolean newState = AdminModeHandler.toggleAdminMode();
        
        Component message = Component.translatable("command.servershop.adminmode.toggle", 
            newState ? "enabled" : "disabled");
        context.getSource().sendSuccess(() -> message, true);
        
        return 1;
    }
    
    /**
     * Pays money from one player to another.
     */
    private static int payPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String targetPlayerName = StringArgumentType.getString(context, "player");
        int amount = IntegerArgumentType.getInteger(context, "amount");
        CommandSourceStack source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer sender)) {
            Component message = Component.translatable("command.servershop.economy.not_player");
            source.sendFailure(message);
            return 0;
        }
        
        // Check if sender has enough money
        if (!WalletHandler.hasEnoughMoney(sender, amount)) {
            long currentBalance = WalletHandler.getPlayerMoney(sender);
            Component message = Component.translatable("command.servershop.marketplace.pay.insufficient", 
                amount, currentBalance);
            source.sendFailure(message);
            return 0;
        }
        
        // Find target player
        ServerPlayer targetPlayer = findPlayer(source, targetPlayerName);
        if (targetPlayer == null) {
            Component message = Component.translatable("command.servershop.economy.player_not_found", targetPlayerName);
            source.sendFailure(message);
            return 0;
        }
        
        // Check if trying to pay yourself
        if (sender.getUUID().equals(targetPlayer.getUUID())) {
            Component message = Component.translatable("command.servershop.marketplace.pay.self");
            source.sendFailure(message);
            return 0;
        }
        
        // Perform the transaction
        boolean success = WalletHandler.removeMoney(sender, amount);
        if (!success) {
            Component message = Component.translatable("command.servershop.marketplace.pay.failed");
            source.sendFailure(message);
            return 0;
        }
        
        WalletHandler.addMoney(targetPlayer, amount);
        
        // Get updated balances
        long senderBalance = WalletHandler.getPlayerMoney(sender);
        long targetBalance = WalletHandler.getPlayerMoney(targetPlayer);
        
        // Send success message to sender
        Component senderMessage = Component.translatable("command.servershop.marketplace.pay.sender_success", 
            amount, targetPlayerName, senderBalance);
        source.sendSuccess(() -> senderMessage, false);
        
        // Send notification to target player
        Component targetMessage = Component.translatable("command.servershop.marketplace.pay.target_notify", 
            amount, sender.getName().getString(), targetBalance);
        targetPlayer.sendSystemMessage(targetMessage);
        
        return 1;
    }
    
    /**
     * Gets the component data for the held item in a copy-pastable format.
     */
    private static int getHeldItemData(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            Component message = Component.translatable("command.servershop.economy.not_player");
            source.sendFailure(message);
            return 0;
        }
        
        ItemStack heldItem = player.getMainHandItem();
        
        if (heldItem.isEmpty()) {
            Component message = Component.translatable("command.servershop.marketplace.itemdata.no_item");
            source.sendFailure(message);
            return 0;
        }
        
        // Get item ID
        String itemId = BuiltInRegistries.ITEM.getKey(heldItem.getItem()).toString();
        
        // Get component data
        String componentData = ItemComponentHandler.getComponentData(heldItem);
        
        // Format the output for easy copy-pasting
        String output = String.format("Item ID: %s\nComponent Data: %s", itemId, componentData);
        
        // Send the formatted data to the player
        Component message = Component.literal(output);
        source.sendSuccess(() -> message, false);
        
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
        
        // Save empty marketplace
        MarketplaceDataManager.saveMarketplaceItems(level, new ArrayList<>());
        
        Component message = Component.translatable("command.servershop.marketplace.clear.success");
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
                Component message = Component.translatable("command.servershop.marketplace.additem.invalid_item", itemId);
                source.sendFailure(message);
                return 0;
            }
            
            // Create ItemStack
            ItemStack itemStack = new ItemStack(BuiltInRegistries.ITEM.get(itemLocation), quantity);
            
            // Create MarketplaceItem
            String seller = source.getTextName();
            String guid = java.util.UUID.randomUUID().toString();
            MarketplaceItem marketplaceItem = new MarketplaceItem(itemStack, buyPrice, sellPrice, quantity, seller, guid, "{}");
            
            // Load existing items and add new one
            List<MarketplaceItem> existingItems = MarketplaceDataManager.loadMarketplaceItems(level);
            existingItems.add(marketplaceItem);
            
            // Save back to file
            MarketplaceDataManager.saveMarketplaceItems(level, existingItems);
            
            Component message = Component.translatable("command.servershop.marketplace.additem.success", 
                itemId, quantity, buyPrice, sellPrice);
            source.sendSuccess(() -> message, true);
            
        } catch (Exception e) {
            Component message = Component.translatable("command.servershop.marketplace.additem.error", e.getMessage());
            source.sendFailure(message);
            return 0;
        }
        
        return 1;
    }
    
    /**
     * Generates test data for the marketplace.
     * @param context the command context
     * @return command result
     */
    private static int generateTestData(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        
        List<MarketplaceItem> testItems = new ArrayList<>();
        String seller = source.getTextName();
        
        // Add various test items
        testItems.add(new MarketplaceItem(
            new ItemStack(Items.DIAMOND, 1), 100, 80, 1, seller, 
            java.util.UUID.randomUUID().toString(), "{}"));
        
        testItems.add(new MarketplaceItem(
            new ItemStack(Items.IRON_INGOT, 1), 10, 8, 1, seller, 
            java.util.UUID.randomUUID().toString(), "{}"));
        
        testItems.add(new MarketplaceItem(
            new ItemStack(Items.GOLD_INGOT, 1), 20, 16, 1, seller, 
            java.util.UUID.randomUUID().toString(), "{}"));
        
        testItems.add(new MarketplaceItem(
            new ItemStack(Items.EMERALD, 1), 50, 40, 1, seller, 
            java.util.UUID.randomUUID().toString(), "{}"));
        
        testItems.add(new MarketplaceItem(
            new ItemStack(Items.DIAMOND_SWORD, 1), 200, 160, 1, seller, 
            java.util.UUID.randomUUID().toString(), "{\"minecraft:enchantments\":{\"enchantments\":{\"0\":{\"id\":\"minecraft:sharpness\",\"lvl\":3}}}}"));
        
        testItems.add(new MarketplaceItem(
            new ItemStack(Items.DIAMOND_PICKAXE, 1), 150, 120, 1, seller, 
            java.util.UUID.randomUUID().toString(), "{\"minecraft:enchantments\":{\"enchantments\":{\"0\":{\"id\":\"minecraft:efficiency\",\"lvl\":5}}}}"));
        
        testItems.add(new MarketplaceItem(
            new ItemStack(Items.APPLE, 1), 2, 1, 1, seller, 
            java.util.UUID.randomUUID().toString(), "{}"));
        
        testItems.add(new MarketplaceItem(
            new ItemStack(Items.BREAD, 1), 3, 2, 1, seller, 
            java.util.UUID.randomUUID().toString(), "{}"));
        
        // Save test data
        MarketplaceDataManager.saveMarketplaceItems(level, testItems);
        
        Component message = Component.translatable("command.servershop.marketplace.testdata.success", testItems.size());
        source.sendSuccess(() -> message, true);
        
        return 1;
    }
}