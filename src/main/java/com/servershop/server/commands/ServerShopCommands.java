package com.servershop.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.servershop.ServerShop;
import com.servershop.common.handlers.AdminModeHandler;

/**
 * Handles command registration for the ServerShop mod.
 * Registers the /servershop adminmode command.
 */
@EventBusSubscriber(modid = ServerShop.MODID)
public class ServerShopCommands {
    
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        
        // Register the /servershop command with adminmode subcommand
        dispatcher.register(
            Commands.literal("servershop")
                .then(Commands.literal("adminmode")
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ServerShopCommands::executeAdminMode)
                    )
                    .executes(ServerShopCommands::toggleAdminMode)
                )
        );
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
}
