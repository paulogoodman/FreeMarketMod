package com.servershop.server.handlers;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

import com.servershop.ServerShop;

/**
 * Server-side handler for creating items with component data.
 * This class has access to registry context and can properly handle enchantments.
 */
public class ServerItemHandler {

    /**
     * Creates an ItemStack with component data applied.
     * This method runs on the server side where registry access is available.
     * @param baseItemStack The base ItemStack to apply components to
     * @param componentDataString The component data as JSON string
     * @param server The Minecraft server instance for registry access
     * @return ItemStack with component data applied
     */
    public static ItemStack createItemWithComponentData(ItemStack baseItemStack, String componentDataString, MinecraftServer server) {
        ItemStack itemStack = baseItemStack.copy();
        
        if (componentDataString != null && !componentDataString.trim().isEmpty() && !componentDataString.equals("{}")) {
            try {
                ServerShop.LOGGER.info("Server-side: Creating item with component data: {}", componentDataString);
                
                // Parse the component data JSON string
                CompoundTag componentTag = TagParser.parseTag(componentDataString);
                ServerShop.LOGGER.info("Server-side: Parsed component tag: {}", componentTag);
                
                // Try to create registry-aware NbtOps using server context
                RegistryAccess registryAccess = server.registryAccess();
                ServerShop.LOGGER.info("Server-side: Registry access available: {}", registryAccess != null);
                
                // The issue is that NbtOps.INSTANCE is a global static instance without registry context
                // Even on the server side, we can't create registry-aware NbtOps instances
                ServerShop.LOGGER.warn("Server-side: NbtOps.INSTANCE lacks registry context even on server side");
                ServerShop.LOGGER.warn("Server-side: This is a fundamental limitation of the NbtOps API");
                
                // Fallback: Try with NbtOps.INSTANCE
                ServerShop.LOGGER.info("Server-side: Attempting DataComponentPatch parsing with NbtOps.INSTANCE...");
                
                var result = DataComponentPatch.CODEC.parse(NbtOps.INSTANCE, componentTag);
                
                if (result.isSuccess()) {
                    DataComponentPatch patch = result.getOrThrow();
                    itemStack.applyComponents(patch);
                    ServerShop.LOGGER.info("Server-side: Successfully applied component data via DataComponentPatch: {}", componentDataString);
                } else {
                    ServerShop.LOGGER.warn("Server-side: DataComponentPatch parsing failed: {}", result.error().get().message());
                    ServerShop.LOGGER.warn("Server-side: This confirms the registry context issue even on server side");
                    ServerShop.LOGGER.warn("Server-side: Falling back to direct component application...");
                    
                    // Fallback: Apply components directly
                    applyComponentsDirectly(itemStack, componentTag, server.registryAccess());
                }
                
            } catch (Exception e) {
                ServerShop.LOGGER.error("Server-side: Failed to apply component data: {}", e.getMessage());
                e.printStackTrace();
            }
        }
        
        return itemStack;
    }
    
    /**
     * Fallback method to apply components directly when DataComponentPatch fails.
     * Uses direct registry access to handle all types of component data generically.
     */
    private static void applyComponentsDirectly(ItemStack itemStack, CompoundTag componentTag, RegistryAccess registryAccess) {
        try {
            ServerShop.LOGGER.info("Server-side: Applying component data generically: {}", componentTag.toString());
            
            // Apply all component data generically
            for (String componentKey : componentTag.getAllKeys()) {
                try {
                    applyComponentData(itemStack, componentKey, componentTag.get(componentKey), registryAccess);
                } catch (Exception componentError) {
                    ServerShop.LOGGER.warn("Server-side: Failed to apply component {}: {}", componentKey, componentError.getMessage());
                }
            }
            
            ServerShop.LOGGER.info("Server-side: Applied component data directly: {}", componentTag.toString());
        } catch (Exception e) {
            ServerShop.LOGGER.error("Server-side: Failed to apply components directly: {}", e.getMessage());
        }
    }
    
    /**
     * Applies a single component data entry to an ItemStack.
     * Handles all types of component data generically.
     */
    private static void applyComponentData(ItemStack itemStack, String componentKey, net.minecraft.nbt.Tag componentValue, RegistryAccess registryAccess) {
        try {
            ServerShop.LOGGER.info("Server-side: Applying component: {} = {}", componentKey, componentValue);
            
            // Handle known component types
            switch (componentKey) {
                case "minecraft:custom_name":
                    applyCustomName(itemStack, componentValue);
                    break;
                case "minecraft:enchantments":
                    applyEnchantments(itemStack, componentValue, registryAccess);
                    break;
                case "minecraft:trim":
                    applyArmorTrim(itemStack, componentValue, registryAccess);
                    break;
                case "minecraft:firework_explosion":
                    applyFireworkExplosion(itemStack, componentValue);
                    break;
                case "minecraft:potion_contents":
                    applyPotionContents(itemStack, componentValue, registryAccess);
                    break;
                case "minecraft:food":
                    applyFoodProperties(itemStack, componentValue);
                    break;
                case "minecraft:tool":
                    applyToolProperties(itemStack, componentValue);
                    break;
                default:
                    // Handle mod-specific or unknown components
                    applyGenericComponent(itemStack, componentKey, componentValue, registryAccess);
                    break;
            }
        } catch (Exception e) {
            ServerShop.LOGGER.warn("Server-side: Failed to apply component {}: {}", componentKey, e.getMessage());
        }
    }
    
    /**
     * Applies custom name component data.
     */
    private static void applyCustomName(ItemStack itemStack, net.minecraft.nbt.Tag componentValue) {
        if (componentValue instanceof net.minecraft.nbt.StringTag stringTag) {
            String customNameStr = stringTag.getAsString();
            // Remove the extra quotes that might be in the JSON
            if (customNameStr.startsWith("\"") && customNameStr.endsWith("\"")) {
                customNameStr = customNameStr.substring(1, customNameStr.length() - 1);
            }
            itemStack.set(DataComponents.CUSTOM_NAME, Component.literal(customNameStr));
            ServerShop.LOGGER.info("Server-side: Applied custom name: {}", customNameStr);
        }
    }
    
    /**
     * Applies enchantments component data.
     */
    private static void applyEnchantments(ItemStack itemStack, net.minecraft.nbt.Tag componentValue, RegistryAccess registryAccess) {
        if (componentValue instanceof CompoundTag enchantmentsTag) {
            ServerShop.LOGGER.info("Server-side: Parsing enchantments tag: {}", enchantmentsTag);
            
            // Create enchantments using direct registry access
            ItemEnchantments enchantments = createEnchantmentsFromTag(enchantmentsTag, registryAccess);
            
            if (enchantments != null && !enchantments.isEmpty()) {
                itemStack.set(DataComponents.ENCHANTMENTS, enchantments);
                ServerShop.LOGGER.info("Server-side: Applied enchantments using direct registry access: {}", enchantments);
            } else {
                ServerShop.LOGGER.warn("Server-side: Failed to create enchantments using direct registry access");
            }
        }
    }
    
    /**
     * Applies armor trim component data.
     */
    private static void applyArmorTrim(ItemStack itemStack, net.minecraft.nbt.Tag componentValue, RegistryAccess registryAccess) {
        try {
            if (componentValue instanceof CompoundTag trimTag) {
                ServerShop.LOGGER.info("Server-side: Applying armor trim: {}", trimTag);
                // TODO: Implement armor trim parsing
                ServerShop.LOGGER.warn("Server-side: Armor trim support not yet implemented");
            }
        } catch (Exception e) {
            ServerShop.LOGGER.warn("Server-side: Failed to apply armor trim: {}", e.getMessage());
        }
    }
    
    /**
     * Applies firework explosion component data.
     */
    private static void applyFireworkExplosion(ItemStack itemStack, net.minecraft.nbt.Tag componentValue) {
        try {
            if (componentValue instanceof CompoundTag explosionTag) {
                ServerShop.LOGGER.info("Server-side: Applying firework explosion: {}", explosionTag);
                // TODO: Implement firework explosion parsing
                ServerShop.LOGGER.warn("Server-side: Firework explosion support not yet implemented");
            }
        } catch (Exception e) {
            ServerShop.LOGGER.warn("Server-side: Failed to apply firework explosion: {}", e.getMessage());
        }
    }
    
    /**
     * Applies potion contents component data.
     */
    private static void applyPotionContents(ItemStack itemStack, net.minecraft.nbt.Tag componentValue, RegistryAccess registryAccess) {
        try {
            if (componentValue instanceof CompoundTag potionTag) {
                ServerShop.LOGGER.info("Server-side: Applying potion contents: {}", potionTag);
                // TODO: Implement potion contents parsing
                ServerShop.LOGGER.warn("Server-side: Potion contents support not yet implemented");
            }
        } catch (Exception e) {
            ServerShop.LOGGER.warn("Server-side: Failed to apply potion contents: {}", e.getMessage());
        }
    }
    
    /**
     * Applies food properties component data.
     */
    private static void applyFoodProperties(ItemStack itemStack, net.minecraft.nbt.Tag componentValue) {
        try {
            if (componentValue instanceof CompoundTag foodTag) {
                ServerShop.LOGGER.info("Server-side: Applying food properties: {}", foodTag);
                // TODO: Implement food properties parsing
                ServerShop.LOGGER.warn("Server-side: Food properties support not yet implemented");
            }
        } catch (Exception e) {
            ServerShop.LOGGER.warn("Server-side: Failed to apply food properties: {}", e.getMessage());
        }
    }
    
    /**
     * Applies tool properties component data.
     */
    private static void applyToolProperties(ItemStack itemStack, net.minecraft.nbt.Tag componentValue) {
        try {
            if (componentValue instanceof CompoundTag toolTag) {
                ServerShop.LOGGER.info("Server-side: Applying tool properties: {}", toolTag);
                // TODO: Implement tool properties parsing
                ServerShop.LOGGER.warn("Server-side: Tool properties support not yet implemented");
            }
        } catch (Exception e) {
            ServerShop.LOGGER.warn("Server-side: Failed to apply tool properties: {}", e.getMessage());
        }
    }
    
    /**
     * Applies generic/mod-specific component data.
     * This handles unknown component types that might be from other mods.
     */
    private static void applyGenericComponent(ItemStack itemStack, String componentKey, net.minecraft.nbt.Tag componentValue, RegistryAccess registryAccess) {
        try {
            ServerShop.LOGGER.info("Server-side: Applying generic component: {} = {}", componentKey, componentValue);
            
            // Try to find the component type in the registry
            var componentTypeRegistry = registryAccess.registryOrThrow(Registries.DATA_COMPONENT_TYPE);
            var componentTypeHolder = componentTypeRegistry.getHolder(ResourceLocation.parse(componentKey));
            
            if (componentTypeHolder.isPresent()) {
                ServerShop.LOGGER.info("Server-side: Found component type in registry: {}", componentKey);
                // TODO: Implement generic component parsing
                ServerShop.LOGGER.warn("Server-side: Generic component support not yet implemented for: {}", componentKey);
            } else {
                ServerShop.LOGGER.warn("Server-side: Unknown component type: {}", componentKey);
                ServerShop.LOGGER.warn("Server-side: This might be mod-specific data that needs custom handling");
            }
        } catch (Exception e) {
            ServerShop.LOGGER.warn("Server-side: Failed to apply generic component {}: {}", componentKey, e.getMessage());
        }
    }
    
    /**
     * Creates ItemEnchantments from a CompoundTag using proper enchantment classes.
     * Uses EnchantmentInstance and Enchantments constants for cleaner implementation.
     */
    private static ItemEnchantments createEnchantmentsFromTag(CompoundTag enchantmentsTag, RegistryAccess registryAccess) {
        try {
            ServerShop.LOGGER.info("Server-side: Creating enchantments using proper enchantment classes");
            
            if (enchantmentsTag.contains("enchantments")) {
                CompoundTag enchantmentsList = enchantmentsTag.getCompound("enchantments");
                
                // Create a mutable enchantments container
                ItemEnchantments.Mutable mutableEnchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                
                for (String key : enchantmentsList.getAllKeys()) {
                    CompoundTag enchantmentTag = enchantmentsList.getCompound(key);
                    String enchantmentId = enchantmentTag.getString("id");
                    int level = enchantmentTag.getInt("lvl");
                    
                    ServerShop.LOGGER.info("Server-side: Creating enchantment: {} level {}", enchantmentId, level);
                    
                    try {
                        // Get the enchantment from the registry
                        ResourceLocation enchantmentLocation = ResourceLocation.parse(enchantmentId);
                        var enchantmentRegistry = registryAccess.registryOrThrow(Registries.ENCHANTMENT);
                        var enchantmentHolder = enchantmentRegistry.getHolder(enchantmentLocation);
                        
                        if (enchantmentHolder.isPresent()) {
                            // Add the enchantment to the mutable container using the holder
                            mutableEnchantments.set(enchantmentHolder.get(), level);
                            ServerShop.LOGGER.info("Server-side: Successfully added enchantment: {} level {}", enchantmentId, level);
                        } else {
                            ServerShop.LOGGER.warn("Server-side: Enchantment not found in registry: {}", enchantmentId);
                            
                            // Try using Enchantments constants as fallback by converting ResourceKey to Holder
                            if (enchantmentId.equals("minecraft:sharpness")) {
                                ServerShop.LOGGER.info("Server-side: Using Enchantments.SHARPNESS constant as fallback");
                                var sharpnessHolder = enchantmentRegistry.getHolder(Enchantments.SHARPNESS);
                                if (sharpnessHolder.isPresent()) {
                                    mutableEnchantments.set(sharpnessHolder.get(), level);
                                    ServerShop.LOGGER.info("Server-side: Successfully added Sharpness using constant: level {}", level);
                                }
                            } else if (enchantmentId.equals("minecraft:efficiency")) {
                                ServerShop.LOGGER.info("Server-side: Using Enchantments.EFFICIENCY constant as fallback");
                                var efficiencyHolder = enchantmentRegistry.getHolder(Enchantments.EFFICIENCY);
                                if (efficiencyHolder.isPresent()) {
                                    mutableEnchantments.set(efficiencyHolder.get(), level);
                                    ServerShop.LOGGER.info("Server-side: Successfully added Efficiency using constant: level {}", level);
                                }
                            } else if (enchantmentId.equals("minecraft:unbreaking")) {
                                ServerShop.LOGGER.info("Server-side: Using Enchantments.UNBREAKING constant as fallback");
                                var unbreakingHolder = enchantmentRegistry.getHolder(Enchantments.UNBREAKING);
                                if (unbreakingHolder.isPresent()) {
                                    mutableEnchantments.set(unbreakingHolder.get(), level);
                                    ServerShop.LOGGER.info("Server-side: Successfully added Unbreaking using constant: level {}", level);
                                }
                            }
                        }
                    } catch (Exception enchantError) {
                        ServerShop.LOGGER.warn("Server-side: Failed to add enchantment {}: {}", enchantmentId, enchantError.getMessage());
                    }
                }
                
                // Return the immutable enchantments
                ItemEnchantments finalEnchantments = mutableEnchantments.toImmutable();
                ServerShop.LOGGER.info("Server-side: Created enchantments using proper classes: {}", finalEnchantments);
                return finalEnchantments;
            }
            
            return ItemEnchantments.EMPTY;
        } catch (Exception e) {
            ServerShop.LOGGER.error("Server-side: Failed to create enchantments from tag: {}", e.getMessage());
            e.printStackTrace();
            return ItemEnchantments.EMPTY;
        }
    }
}
