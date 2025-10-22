package com.freemarket;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import com.freemarket.server.data.FreeMarketDataManager;
import com.freemarket.server.data.LeaderboardDataManager;
import com.freemarket.server.data.AuctionDataManager;
import com.freemarket.server.commands.FreeMarketCommands;
import com.freemarket.common.attachments.PlayerWalletAttachment;
import com.freemarket.common.network.NetworkRegistry;
import com.freemarket.server.events.ServerEventHandler;
import com.freemarket.server.events.ServerMarketplaceEventHandler;
import com.freemarket.server.events.LeaderboardEventHandler;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(FreeMarket.MODID)
public class FreeMarket {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "freemarket";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public FreeMarket(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register attachment types for persistent player data
        PlayerWalletAttachment.ATTACHMENT_TYPES.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (FreeMarket) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);
        
        // Register centralized network packet handler
        modEventBus.addListener(NetworkRegistry::register);
        
        // Register server event handler for player join events
        NeoForge.EVENT_BUS.register(ServerEventHandler.class);
        
        // Register marketplace event handler for marketplace sync
        NeoForge.EVENT_BUS.register(ServerMarketplaceEventHandler.class);
        
        // Register leaderboard event handler for player login/logout
        NeoForge.EVENT_BUS.register(LeaderboardEventHandler.class);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Initialize FreeMarket mod
        LOGGER.info("FreeMarket mod initialized");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Server starting event
    }
    
    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        // Create empty marketplace.json file when a world is loaded for the first time
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            if (!FreeMarketDataManager.marketplaceFileExists(serverLevel)) {
                FreeMarketDataManager.createEmptyMarketplaceFile(serverLevel);
            }
            
            // Create empty leaderboard.json file if it doesn't exist
            if (!LeaderboardDataManager.leaderboardFileExists(serverLevel)) {
                LeaderboardDataManager.createEmptyLeaderboardFile(serverLevel);
            }
            
            // Load leaderboard data into cache on world load
            LeaderboardDataManager.loadLeaderboardData(serverLevel);
            
        }
    }
    
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        // Register economy commands
        FreeMarketCommands.register(event.getDispatcher());
    }
    
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        // Player join event
    }
    
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // Player respawn event
    }
}
