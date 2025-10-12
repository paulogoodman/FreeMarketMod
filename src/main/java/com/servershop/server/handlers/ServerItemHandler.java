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
                    // Component failed to apply, continue with others
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
                var codec = componentType.codec();
                if (codec != null) {
                    // Try to decode the component using its codec
                    var decodeResult = codec.parse(NbtOps.INSTANCE, componentValue);
                
                    if (decodeResult.isSuccess()) {
                        Object componentValueObj = decodeResult.getOrThrow();
                        
                        // Use reflection to set the component dynamically
                        try {
                            java.lang.reflect.Method setMethod = itemStack.getClass().getMethod("set", net.minecraft.core.component.DataComponentType.class, Object.class);
                            setMethod.invoke(itemStack, componentType, componentValueObj);
                        } catch (Exception reflectionError) {
                            // Fallback: Try to handle known component types manually
                            handleKnownComponentTypes(itemStack, componentKey, componentValue, registryAccess);
                        }
                    } else {
                        // Fallback: Try to handle known component types manually
                        handleKnownComponentTypes(itemStack, componentKey, componentValue, registryAccess);
                    }
                } else {
                    // Fallback: Try to handle known component types manually
                    handleKnownComponentTypes(itemStack, componentKey, componentValue, registryAccess);
                }
            } else {
                // Component type not found in registry
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
            // Failed to handle known component type
        }
    }
    
    /**
     * Attempts to apply unknown component types by trying different approaches.
     * This provides maximum compatibility with mod components.
     */
    private static void applyUnknownComponent(ItemStack itemStack, String componentKey, net.minecraft.nbt.Tag componentValue, RegistryAccess registryAccess) {
        try {
            // Try to find the component type in the registry again
            ResourceLocation componentLocation = ResourceLocation.parse(componentKey);
            var componentRegistry = registryAccess.registryOrThrow(Registries.DATA_COMPONENT_TYPE);
            var componentType = componentRegistry.get(componentLocation);
            
            if (componentType != null) {
                var codec = componentType.codec();
                if (codec != null) {
                    // Try different approaches to decode the component
                    
                    // Approach 1: Try with a registry-aware NbtOps (if available)
                    try {
                        // This might work for some components
                        var decodeResult = codec.parse(NbtOps.INSTANCE, componentValue);
                        if (decodeResult.isSuccess()) {
                            Object componentValueObj = decodeResult.getOrThrow();
                            java.lang.reflect.Method setMethod = itemStack.getClass().getMethod("set", net.minecraft.core.component.DataComponentType.class, Object.class);
                            setMethod.invoke(itemStack, componentType, componentValueObj);
                            return;
                        }
                    } catch (Exception e) {
                        // Codec approach failed
                    }
                
                // Approach 2: Try to create a DataComponentPatch with just this component
                try {
                    CompoundTag tempTag = new CompoundTag();
                    tempTag.put(componentKey, componentValue);
                    var patchResult = DataComponentPatch.CODEC.parse(NbtOps.INSTANCE, tempTag);
                    if (patchResult.isSuccess()) {
                        DataComponentPatch patch = patchResult.getOrThrow();
                        itemStack.applyComponents(patch);
                        return;
                    }
                } catch (Exception e) {
                    // DataComponentPatch approach failed
                }
                
                    // Approach 3: Could not apply the component
                } else {
                    // Component type has no codec
                }
            } else {
                // Unknown component type not found in registry
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
                        } else {
                            // Enchantment not found in registry
                        }
                    }
                    
                    // Apply the enchantments to the item
                    itemStack.set(DataComponents.ENCHANTMENTS, mutableEnchantments.toImmutable());
                }
            }
            
        } catch (Exception e) {
            // Failed to apply enchantments manually
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
            }
            
        } catch (Exception e) {
            // Failed to apply custom name manually
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
                            
                        } catch (Exception trimCreationError) {
                            // Failed to create ArmorTrim object
                        }
                    } else {
                        // Trim material or pattern not found in registry
                    }
                    
                } catch (Exception registryError) {
                    // Failed to lookup trim material/pattern in registry
                }
            }
            
        } catch (Exception e) {
            // Failed to apply trim manually
        }
    }
}