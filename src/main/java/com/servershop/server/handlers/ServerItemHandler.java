package com.servershop.server.handlers;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import com.servershop.ServerShop;

/**
 * Server-side handler for creating items with component data.
 * This class leverages server-side registry access to correctly apply enchantments
 * and other component data to ItemStacks.
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
                ServerShop.LOGGER.info("Server-side: Creating ItemStack with component data: {}", componentDataString);
                
                // Parse the component data JSON string
                CompoundTag componentTag = TagParser.parseTag(componentDataString);
                
                // Apply components directly using their individual codecs
                // This bypasses DataComponentPatch which has registry access issues
                applyComponentsDirectly(itemStack, componentTag, server.registryAccess());
                
            } catch (Exception e) {
                ServerShop.LOGGER.error("Server-side: Failed to apply component data: {}", e.getMessage());
                e.printStackTrace();
            }
        }
        
        return itemStack;
    }
    
    /**
     * Applies components directly to the ItemStack using their individual codecs.
     * This bypasses DataComponentPatch which has registry access issues.
     */
    private static void applyComponentsDirectly(ItemStack itemStack, CompoundTag componentTag, RegistryAccess registryAccess) {
        try {
            // Apply each component individually using their specific codecs
            for (String componentKey : componentTag.getAllKeys()) {
                try {
                    applySpecificComponent(itemStack, componentKey, componentTag.get(componentKey), registryAccess);
                } catch (Exception componentError) {
                    ServerShop.LOGGER.warn("Server-side: Failed to apply component {}: {}", componentKey, componentError.getMessage());
                }
            }
            
        } catch (Exception e) {
            ServerShop.LOGGER.error("Server-side: Failed to apply components directly: {}", e.getMessage());
        }
    }
    
    /**
     * Applies a specific component to the ItemStack by dynamically handling any component type.
     * This is truly universal - it works for ANY component from ANY mod.
     */
    private static void applySpecificComponent(ItemStack itemStack, String componentKey, net.minecraft.nbt.Tag componentValue, RegistryAccess registryAccess) {
        try {
            // Find the component type in the registry
            ResourceLocation componentLocation = ResourceLocation.parse(componentKey);
            var componentRegistry = registryAccess.registryOrThrow(Registries.DATA_COMPONENT_TYPE);
            var componentType = componentRegistry.get(componentLocation);
            
            if (componentType != null) {
                // Try to decode the component using its codec
                var decodeResult = componentType.codec().parse(NbtOps.INSTANCE, componentValue);
                
                if (decodeResult.isSuccess()) {
                    Object componentValueObj = decodeResult.getOrThrow();
                    
                    // Use reflection to set the component dynamically
                    try {
                        java.lang.reflect.Method setMethod = itemStack.getClass().getMethod("set", net.minecraft.core.component.DataComponentType.class, Object.class);
                        setMethod.invoke(itemStack, componentType, componentValueObj);
                        ServerShop.LOGGER.info("Server-side: Successfully applied component {} via dynamic codec", componentKey);
                    } catch (Exception reflectionError) {
                        ServerShop.LOGGER.warn("Server-side: Reflection failed for {}: {}", componentKey, reflectionError.getMessage());
                        
                        // Fallback: Try to handle known component types manually
                        handleKnownComponentTypes(itemStack, componentKey, componentValue, registryAccess);
                    }
                } else {
                    ServerShop.LOGGER.warn("Server-side: Failed to decode component {}: {}", componentKey, decodeResult.error().get().message());
                    
                    // Fallback: Try to handle known component types manually
                    handleKnownComponentTypes(itemStack, componentKey, componentValue, registryAccess);
                }
            } else {
                ServerShop.LOGGER.warn("Server-side: Component type not found in registry: {}", componentKey);
                ServerShop.LOGGER.warn("Server-side: This component may be from a mod that's not loaded or uses a different namespace");
            }
            
        } catch (Exception e) {
            ServerShop.LOGGER.warn("Server-side: Failed to apply component {}: {}", componentKey, e.getMessage());
        }
    }
    
    /**
     * Fallback method to handle known component types manually when codec parsing fails.
     * This ensures compatibility with components that have registry access issues.
     */
    private static void handleKnownComponentTypes(ItemStack itemStack, String componentKey, net.minecraft.nbt.Tag componentValue, RegistryAccess registryAccess) {
        try {
            if (componentKey.equals("minecraft:enchantments")) {
                applyEnchantmentsManually(itemStack, componentValue, registryAccess);
            } else if (componentKey.equals("minecraft:custom_name")) {
                applyCustomNameManually(itemStack, componentValue);
            } else if (componentKey.equals("minecraft:trim")) {
                applyTrimManually(itemStack, componentValue, registryAccess);
            } else {
                // For unknown components, try to apply them as raw data
                applyUnknownComponent(itemStack, componentKey, componentValue, registryAccess);
            }
        } catch (Exception e) {
            ServerShop.LOGGER.warn("Server-side: Failed to handle known component type {}: {}", componentKey, e.getMessage());
        }
    }
    
    /**
     * Attempts to apply unknown component types by trying different approaches.
     * This provides maximum compatibility with mod components.
     */
    private static void applyUnknownComponent(ItemStack itemStack, String componentKey, net.minecraft.nbt.Tag componentValue, RegistryAccess registryAccess) {
        try {
            ServerShop.LOGGER.info("Server-side: Attempting to apply unknown component: {}", componentKey);
            
            // Try to find the component type in the registry again
            ResourceLocation componentLocation = ResourceLocation.parse(componentKey);
            var componentRegistry = registryAccess.registryOrThrow(Registries.DATA_COMPONENT_TYPE);
            var componentType = componentRegistry.get(componentLocation);
            
            if (componentType != null) {
                // Try different approaches to decode the component
                
                // Approach 1: Try with a registry-aware NbtOps (if available)
                try {
                    // This might work for some components
                    var decodeResult = componentType.codec().parse(NbtOps.INSTANCE, componentValue);
                    if (decodeResult.isSuccess()) {
                        Object componentValueObj = decodeResult.getOrThrow();
                        java.lang.reflect.Method setMethod = itemStack.getClass().getMethod("set", net.minecraft.core.component.DataComponentType.class, Object.class);
                        setMethod.invoke(itemStack, componentType, componentValueObj);
                        ServerShop.LOGGER.info("Server-side: Successfully applied unknown component {} via codec", componentKey);
                        return;
                    }
                } catch (Exception e) {
                    ServerShop.LOGGER.debug("Server-side: Codec approach failed for {}: {}", componentKey, e.getMessage());
                }
                
                // Approach 2: Try to create a DataComponentPatch with just this component
                try {
                    CompoundTag tempTag = new CompoundTag();
                    tempTag.put(componentKey, componentValue);
                    var patchResult = DataComponentPatch.CODEC.parse(NbtOps.INSTANCE, tempTag);
                    if (patchResult.isSuccess()) {
                        DataComponentPatch patch = patchResult.getOrThrow();
                        itemStack.applyComponents(patch);
                        ServerShop.LOGGER.info("Server-side: Successfully applied unknown component {} via DataComponentPatch", componentKey);
                        return;
                    }
                } catch (Exception e) {
                    ServerShop.LOGGER.debug("Server-side: DataComponentPatch approach failed for {}: {}", componentKey, e.getMessage());
                }
                
                // Approach 3: Log that we couldn't apply the component
                ServerShop.LOGGER.warn("Server-side: Could not apply unknown component: {}", componentKey);
                ServerShop.LOGGER.warn("Server-side: Component value: {}", componentValue);
                ServerShop.LOGGER.warn("Server-side: This component may require special handling or the mod may not be compatible");
                
            } else {
                ServerShop.LOGGER.warn("Server-side: Unknown component type not found in registry: {}", componentKey);
                ServerShop.LOGGER.warn("Server-side: This suggests the component is from a mod that's not loaded");
            }
            
        } catch (Exception e) {
            ServerShop.LOGGER.warn("Server-side: Failed to apply unknown component {}: {}", componentKey, e.getMessage());
        }
    }
    
    /**
     * Manually applies enchantments by parsing the NBT and creating ItemEnchantments.
     */
    private static void applyEnchantmentsManually(ItemStack itemStack, net.minecraft.nbt.Tag enchantmentsTag, RegistryAccess registryAccess) {
        try {
            if (enchantmentsTag instanceof CompoundTag) {
                CompoundTag enchantmentsCompound = (CompoundTag) enchantmentsTag;
                
                if (enchantmentsCompound.contains("enchantments")) {
                    CompoundTag enchantmentsList = enchantmentsCompound.getCompound("enchantments");
                    
                    // Create a mutable enchantments container
                    ItemEnchantments.Mutable mutableEnchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                    
                    // Get the enchantment registry
                    var enchantmentRegistry = registryAccess.registryOrThrow(Registries.ENCHANTMENT);
                    
                    for (String key : enchantmentsList.getAllKeys()) {
                        CompoundTag enchantmentTag = enchantmentsList.getCompound(key);
                        String enchantmentId = enchantmentTag.getString("id");
                        int level = enchantmentTag.getInt("lvl");
                        
                        // Get the enchantment from the registry
                        ResourceLocation enchantmentLocation = ResourceLocation.parse(enchantmentId);
                        var enchantmentHolder = enchantmentRegistry.getHolder(enchantmentLocation);
                        
                        if (enchantmentHolder.isPresent()) {
                            mutableEnchantments.set(enchantmentHolder.get(), level);
                            ServerShop.LOGGER.info("Server-side: Added enchantment {} level {}", enchantmentId, level);
                        } else {
                            ServerShop.LOGGER.warn("Server-side: Enchantment not found in registry: {}", enchantmentId);
                        }
                    }
                    
                    // Apply the enchantments to the item
                    itemStack.set(DataComponents.ENCHANTMENTS, mutableEnchantments.toImmutable());
                    ServerShop.LOGGER.info("Server-side: Successfully applied enchantments manually");
                }
            }
            
        } catch (Exception e) {
            ServerShop.LOGGER.warn("Server-side: Failed to apply enchantments manually: {}", e.getMessage());
        }
    }
    
    /**
     * Manually applies custom name by parsing the string.
     */
    private static void applyCustomNameManually(ItemStack itemStack, net.minecraft.nbt.Tag nameTag) {
        try {
            if (nameTag instanceof net.minecraft.nbt.StringTag) {
                String nameText = ((net.minecraft.nbt.StringTag) nameTag).getAsString();
                
                // Create a simple text component
                Component customName = Component.literal(nameText);
                itemStack.set(DataComponents.CUSTOM_NAME, customName);
                ServerShop.LOGGER.info("Server-side: Successfully applied custom name: {}", nameText);
            }
            
        } catch (Exception e) {
            ServerShop.LOGGER.warn("Server-side: Failed to apply custom name manually: {}", e.getMessage());
        }
    }
    
    /**
     * Manually applies armor trim by parsing the NBT and looking up materials/patterns in registries.
     * This attempts to create a proper ArmorTrim object with registry lookups.
     */
    private static void applyTrimManually(ItemStack itemStack, net.minecraft.nbt.Tag trimTag, RegistryAccess registryAccess) {
        try {
            if (trimTag instanceof CompoundTag) {
                CompoundTag trimCompound = (CompoundTag) trimTag;
                
                // Parse trim material and pattern
                String materialId = trimCompound.getString("material");
                String patternId = trimCompound.getString("pattern");
                
                ServerShop.LOGGER.info("Server-side: Attempting to apply trim - material: {}, pattern: {}", materialId, patternId);
                
                // Try to find the material and pattern in their respective registries
                try {
                    ResourceLocation materialLocation = ResourceLocation.parse(materialId);
                    ResourceLocation patternLocation = ResourceLocation.parse(patternId);
                    
                    // Get the trim material and pattern registries
                    var trimMaterialRegistry = registryAccess.registryOrThrow(Registries.TRIM_MATERIAL);
                    var trimPatternRegistry = registryAccess.registryOrThrow(Registries.TRIM_PATTERN);
                    
                    var materialHolder = trimMaterialRegistry.getHolder(materialLocation);
                    var patternHolder = trimPatternRegistry.getHolder(patternLocation);
                    
                    if (materialHolder.isPresent() && patternHolder.isPresent()) {
                        // Try to create an ArmorTrim object
                        try {
                            // This is the proper way to create an ArmorTrim
                            var armorTrim = new net.minecraft.world.item.armortrim.ArmorTrim(
                                materialHolder.get(), 
                                patternHolder.get()
                            );
                            
                            // Apply the trim to the item
                            itemStack.set(DataComponents.TRIM, armorTrim);
                            ServerShop.LOGGER.info("Server-side: Successfully applied armor trim - material: {}, pattern: {}", materialId, patternId);
                            
                        } catch (Exception trimCreationError) {
                            ServerShop.LOGGER.warn("Server-side: Failed to create ArmorTrim object: {}", trimCreationError.getMessage());
                            ServerShop.LOGGER.warn("Server-side: Material: {}, Pattern: {}", materialId, patternId);
                        }
                    } else {
                        ServerShop.LOGGER.warn("Server-side: Trim material or pattern not found in registry");
                        ServerShop.LOGGER.warn("Server-side: Material found: {}, Pattern found: {}", materialHolder.isPresent(), patternHolder.isPresent());
                        ServerShop.LOGGER.warn("Server-side: Material: {}, Pattern: {}", materialId, patternId);
                    }
                    
                } catch (Exception registryError) {
                    ServerShop.LOGGER.warn("Server-side: Failed to lookup trim material/pattern in registry: {}", registryError.getMessage());
                    ServerShop.LOGGER.warn("Server-side: Material: {}, Pattern: {}", materialId, patternId);
                }
            }
            
        } catch (Exception e) {
            ServerShop.LOGGER.warn("Server-side: Failed to apply trim manually: {}", e.getMessage());
        }
    }
}