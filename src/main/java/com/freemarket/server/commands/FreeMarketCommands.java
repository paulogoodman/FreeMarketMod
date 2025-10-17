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
import java.util.List;

/**
 * Marketplace-related commands for the FreeMarket mod.
 * These commands allow players to manage their economy and OPs to manage the marketplace.
 * 
 * <p>Command Structure:</p>
 * <ul>
 *   <li>Player Commands: help, balance, pay</li>
 *   <li>Admin Commands: adminmode, itemdata, list (hand/item), balance management</li>
 * </ul>
 * 
 * <p>Available Command Aliases:</p>
 * <ul>
 *   <li>/freemarket - Main command</li>
 *   <li>/fm - Short alias</li>
 * </ul>
 */
@EventBusSubscriber(modid = FreeMarket.MODID)
public class FreeMarketCommands {
    
    // Permission levels
    private static final int ADMIN_PERMISSION_LEVEL = 2;
    
    // Command names
    private static final String MAIN_COMMAND = "freemarket";
    private static final String ALIAS_COMMAND = "fm";
    
    // Argument names
    private static final String ARG_PLAYER = "player";
    private static final String ARG_AMOUNT = "amount";
    private static final String ARG_ITEM = "item";
    private static final String ARG_BUY_PRICE = "buyPrice";
    private static final String ARG_SELL_PRICE = "sellPrice";
    private static final String ARG_QUANTITY = "quantity";
    private static final String ARG_ENABLED = "enabled";

    @SubscribeEvent
    public static void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        register(dispatcher);
    }

    /**
     * Registers all marketplace commands with both main command and alias.
     * 
     * @param dispatcher The command dispatcher to register commands with
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerCommands(dispatcher, MAIN_COMMAND);
        registerCommands(dispatcher, ALIAS_COMMAND);
    }
    
    /**
     * Registers all marketplace commands with the given command name.
     * 
     * @param dispatcher The command dispatcher to register commands with
     * @param commandName The name of the command to register
     */
    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
        dispatcher.register(Commands.literal(commandName)
            .then(buildHelpCommand())
            .then(buildBalanceCommands())
            .then(buildPayCommand())
            .then(buildAdminModeCommand())
            .then(buildItemDataCommand())
            .then(buildListCommand()));
    }
    
    /**
     * Builds the help command.
     * 
     * @return Command builder for help command
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildHelpCommand() {
        return Commands.literal("help")
            .executes(FreeMarketCommands::showHelp);
    }
    
    /**
     * Builds balance-related commands (both player and admin).
     * 
     * @return Command builder for balance commands
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildBalanceCommands() {
        return Commands.literal("balance")
            .executes(FreeMarketCommands::getOwnBalance)
            .then(Commands.argument(ARG_PLAYER, StringArgumentType.word())
                .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                .executes(FreeMarketCommands::getBalance)
                .then(Commands.literal("add")
                    .then(Commands.argument(ARG_AMOUNT, LongArgumentType.longArg(1))
                        .executes(FreeMarketCommands::addMoney)))
                .then(Commands.literal("remove")
                    .then(Commands.argument(ARG_AMOUNT, LongArgumentType.longArg(1))
                        .executes(FreeMarketCommands::removeMoney)))
                .then(Commands.literal("set")
                    .then(Commands.argument(ARG_AMOUNT, LongArgumentType.longArg(0))
                        .executes(FreeMarketCommands::setMoney))));
    }
    
    /**
     * Builds the pay command for players.
     * 
     * @return Command builder for pay command
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildPayCommand() {
        return Commands.literal("pay")
            .then(Commands.argument(ARG_PLAYER, StringArgumentType.word())
                .then(Commands.argument(ARG_AMOUNT, LongArgumentType.longArg(1))
                    .executes(FreeMarketCommands::payPlayer)));
    }
    
    /**
     * Builds admin mode commands.
     * 
     * @return Command builder for admin mode commands
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildAdminModeCommand() {
        return Commands.literal("adminmode")
            .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
            .then(Commands.argument(ARG_ENABLED, BoolArgumentType.bool())
                .executes(FreeMarketCommands::executeAdminMode))
            .executes(FreeMarketCommands::toggleAdminMode);
    }
    
    /**
     * Builds item data command.
     * 
     * @return Command builder for item data command
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildItemDataCommand() {
        return Commands.literal("itemdata")
            .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
            .executes(FreeMarketCommands::getHeldItemData);
    }
    
    /**
     * Builds list command for adding items to marketplace.
     * 
     * @return Command builder for list command
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildListCommand() {
        return Commands.literal("list")
            .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
            .then(Commands.literal("hand")
                .then(Commands.argument(ARG_BUY_PRICE, LongArgumentType.longArg(0))
                    .then(Commands.argument(ARG_SELL_PRICE, LongArgumentType.longArg(0))
                        .executes(FreeMarketCommands::listHeldItem))))
            .then(Commands.literal("item")
                .then(Commands.argument(ARG_ITEM, StringArgumentType.word())
                    .then(Commands.argument(ARG_BUY_PRICE, LongArgumentType.longArg(0))
                        .then(Commands.argument(ARG_SELL_PRICE, LongArgumentType.longArg(0))
                            .then(Commands.argument(ARG_QUANTITY, IntegerArgumentType.integer(1))
                                .executes(FreeMarketCommands::addItemToMarketplace))))));
    }
    

    // ============================================================================
    // COMMAND EXECUTION METHODS
    // ============================================================================
    
    /**
     * Gets the balance of the current user (no parameters).
     * 
     * <p>Usage: /freemarket balance</p>
     * <p>Permission: None (available to all players)</p>
     * 
     * @param context The command context containing the source and arguments
     * @return 1 if successful, 0 if the source is not a player
     * @throws CommandSyntaxException if command syntax is invalid
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
     * Gets the balance of a specific player by name.
     * 
     * <p>Usage: /freemarket balance &lt;player&gt;</p>
     * <p>Permission: OP Level 2 (admin only)</p>
     * 
     * @param context The command context containing the source and arguments
     * @return 1 if successful (regardless of player found/not found)
     * @throws CommandSyntaxException if command syntax is invalid
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
     * Adds money to a player's account.
     * 
     * <p>Usage: /freemarket balance add &lt;player&gt; &lt;amount&gt;</p>
     * <p>Permission: OP Level 2 (admin only)</p>
     * 
     * @param context The command context containing the source and arguments
     * @return 1 if successful (regardless of player found/not found)
     * @throws CommandSyntaxException if command syntax is invalid
     */
    private static int addMoney(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String playerName = StringArgumentType.getString(context, "player");
        long amount = LongArgumentType.getLong(context, "amount");
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
     * Removes money from a player's account.
     * 
     * <p>Usage: /freemarket balance remove &lt;player&gt; &lt;amount&gt;</p>
     * <p>Permission: OP Level 2 (admin only)</p>
     * 
     * @param context The command context containing the source and arguments
     * @return 1 if successful (regardless of player found/not found)
     * @throws CommandSyntaxException if command syntax is invalid
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
     * Sets a player's money to a specific amount.
     * 
     * <p>Usage: /freemarket balance set &lt;player&gt; &lt;amount&gt;</p>
     * <p>Permission: OP Level 2 (admin only)</p>
     * 
     * @param context The command context containing the source and arguments
     * @return 1 if successful (regardless of player found/not found)
     * @throws CommandSyntaxException if command syntax is invalid
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
    
    // ============================================================================
    // HELPER METHODS
    // ============================================================================
    
    /**
     * Helper method to find a player by name, works in both singleplayer and multiplayer.
     * 
     * <p>This method performs multiple search strategies:</p>
     * <ul>
     *   <li>Standard player list lookup</li>
     *   <li>Case-insensitive search through all players</li>
     *   <li>Singleplayer command executor matching</li>
     *   <li>Profile cache lookup by UUID</li>
     * </ul>
     * 
     * @param source The command source for server access
     * @param playerName The name of the player to find
     * @return The ServerPlayer if found, null otherwise
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
     * Executes the admin mode command with a boolean argument.
     * 
     * <p>Usage: /freemarket adminmode &lt;true/false&gt;</p>
     * <p>Permission: OP Level 2 (admin only)</p>
     * 
     * @param context The command context containing the source and arguments
     * @return 1 if successful
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
     * 
     * <p>Usage: /freemarket adminmode</p>
     * <p>Permission: OP Level 2 (admin only)</p>
     * 
     * @param context The command context containing the source and arguments
     * @return 1 if successful
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
     * 
     * <p>Usage: /freemarket pay &lt;player&gt; &lt;amount&gt;</p>
     * <p>Permission: None (available to all players)</p>
     * 
     * @param context The command context containing the source and arguments
     * @return 1 if successful, 0 if transaction fails
     * @throws CommandSyntaxException if command syntax is invalid
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
     * 
     * <p>Usage: /freemarket itemdata</p>
     * <p>Permission: OP Level 2 (admin only)</p>
     * 
     * @param context The command context containing the source and arguments
     * @return 1 if successful, 0 if no item held or error occurs
     * @throws CommandSyntaxException if command syntax is invalid
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
     * Adds an item to the marketplace via command.
     * 
     * <p>Usage: /freemarket additem &lt;item&gt; &lt;buyPrice&gt; &lt;sellPrice&gt; &lt;quantity&gt;</p>
     * <p>Permission: OP Level 2 (admin only)</p>
     * 
     * @param context The command context containing the source and arguments
     * @return 1 if successful, 0 if item is invalid or error occurs
     * @throws CommandSyntaxException if command syntax is invalid
     */
    private static int addItemToMarketplace(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String itemId = StringArgumentType.getString(context, "item");
        long buyPrice = LongArgumentType.getLong(context, "buyPrice");
        long sellPrice = LongArgumentType.getLong(context, "sellPrice");
        int quantity = IntegerArgumentType.getInteger(context, "quantity");
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        
        try {
            // Validate that at least one price is greater than zero
            if (buyPrice <= 0 && sellPrice <= 0) {
                Component message = Component.translatable("command.FreeMarket.list.item.both_prices_zero");
                source.sendFailure(message);
                return 0;
            }
            
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
     * 
     * <p>Usage: /freemarket help</p>
     * <p>Permission: None (available to all players)</p>
     * 
     * @param context The command context containing the source and arguments
     * @return 1 if successful
     */
    private static int showHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        // Send header
        source.sendSuccess(() -> Component.literal("§6=== FreeMarket Commands ===§r"), false);
        
        // Player commands (available to all)
        source.sendSuccess(() -> Component.literal("§ePlayer Commands:§r"), false);
        source.sendSuccess(() -> Component.literal("§7/freemarket help§r - Shows this help message"), false);
        source.sendSuccess(() -> Component.literal("§7/freemarket balance§r - Shows your balance"), false);
        source.sendSuccess(() -> Component.literal("§7/freemarket pay <player> <amount>§r - Pay money to another player"), false);
        
        // Admin commands (OP only)
        if (source.hasPermission(ADMIN_PERMISSION_LEVEL)) {
            source.sendSuccess(() -> Component.literal("§cAdmin Commands (OP Required):§r"), false);
            source.sendSuccess(() -> Component.literal("§7/freemarket adminmode [true/false]§r - Enable/disable admin mode"), false);
            source.sendSuccess(() -> Component.literal("§7/freemarket balance <player>§r - Shows another player's balance"), false);
            source.sendSuccess(() -> Component.literal("§7/freemarket balance <player> add | remove | set <amount>§r - Manage player money"), false);
            source.sendSuccess(() -> Component.literal("§7/freemarket itemdata§r - Shows data about the item in your hand"), false);
            source.sendSuccess(() -> Component.literal("§7/freemarket list hand <buyPrice> <sellPrice>§r - Add the item in your hand to marketplace (at least one price must be > 0)"), false);
            source.sendSuccess(() -> Component.literal("§7/freemarket list item <item> <buyPrice> <sellPrice> <quantity>§r - Add item to marketplace (at least one price must be > 0)"), false);
        }
        
        source.sendSuccess(() -> Component.literal("§6Use §e/fm§6 as a shortcut for §e/freemarket§r"), false);
        
        return 1;
    }
    
    /**
     * Lists the item currently held in the player's hand to the marketplace.
     * Captures all NBT data including enchantments and armor trims.
     * 
     * <p>Usage: /freemarket list hand &lt;buyPrice&gt; &lt;sellPrice&gt;</p>
     * <p>Permission: OP Level 2 (admin only)</p>
     * 
     * @param context The command context containing the source and arguments
     * @return 1 if successful, 0 if no item held or error occurs
     * @throws CommandSyntaxException if command syntax is invalid
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
            
            // Validate that at least one price is greater than zero
            if (buyPrice <= 0 && sellPrice <= 0) {
                Component message = Component.translatable("command.FreeMarket.list.hand.both_prices_zero");
                source.sendFailure(message);
                return 0;
            }
            
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
