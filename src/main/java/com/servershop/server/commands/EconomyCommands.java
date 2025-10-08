package com.servershop.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.servershop.common.handlers.WalletHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Economy-related commands for the ServerShop mod.
 * These commands allow OPs to manage player money balances.
 */
public class EconomyCommands {

    /**
     * Registers all economy commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("economy")
            .requires(source -> source.hasPermission(2)) // OP level 2 required
            .then(Commands.literal("balance")
                .executes(EconomyCommands::getOwnBalance)
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(EconomyCommands::getBalance)))
            .then(Commands.literal("add")
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(EconomyCommands::addMoney))))
            .then(Commands.literal("remove")
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(EconomyCommands::removeMoney))))
            .then(Commands.literal("set")
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                        .executes(EconomyCommands::setMoney)))));
    }

    /**
     * Gets the balance of the current user (no parameters).
     */
    private static int getOwnBalance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        
        if (source.getEntity() instanceof ServerPlayer player) {
            int balance = WalletHandler.getPlayerMoney(player);
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
            int balance = WalletHandler.getPlayerMoney(player);
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
            int newBalance = WalletHandler.getPlayerMoney(player);
            
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
                int newBalance = WalletHandler.getPlayerMoney(player);
                
                Component message = Component.translatable("command.servershop.economy.remove.success", 
                    amount, playerName, newBalance);
                source.sendSuccess(() -> message, false);
                
                // Notify the player
                Component playerMessage = Component.translatable("command.servershop.economy.remove.notify", 
                    amount, newBalance);
                player.sendSystemMessage(playerMessage);
            } else {
                int currentBalance = WalletHandler.getPlayerMoney(player);
                
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
            int oldBalance = WalletHandler.getPlayerMoney(player);
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
}