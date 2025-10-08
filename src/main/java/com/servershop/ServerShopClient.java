package com.servershop;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = ServerShop.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = ServerShop.MODID, value = Dist.CLIENT)
public class ServerShopClient {
    // Keybind for opening the shop GUI
    public static final KeyMapping OPEN_SHOP_KEY = new KeyMapping(
        "key.servershop.open_shop",
        GLFW.GLFW_KEY_O,
        "key.categories.servershop"
    );
    
    public ServerShopClient(ModContainer container, IEventBus modEventBus) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        
        // Events are automatically registered by @EventBusSubscriber annotation
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        ServerShop.LOGGER.info("HELLO FROM CLIENT SETUP");
        ServerShop.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
    
    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        // Register the keybind
        event.register(OPEN_SHOP_KEY);
    }
    
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        
        // Check if the keybind was pressed and we're in-game
        if (OPEN_SHOP_KEY.consumeClick() && minecraft.player != null) {
            // Toggle the shop GUI - if it's already open, close it; otherwise open it
            if (minecraft.screen instanceof ShopGuiScreen) {
                minecraft.setScreen(null); // Close the GUI
            } else {
                minecraft.setScreen(new ShopGuiScreen()); // Open the GUI
            }
        }
    }
}
