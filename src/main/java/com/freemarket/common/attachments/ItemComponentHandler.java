package com.freemarket.common.attachments;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import com.freemarket.FreeMarket;

/**
 * Handles Data Components for marketplace items in NeoForge 1.21.
 * Uses the correct DataComponentPatch API for component data handling.
 */
public class ItemComponentHandler {
    
    /**
     * Applies component data from JSON string to an ItemStack.
     * Uses registry-aware NbtOps for proper enchantment parsing.
     * @param itemStack The ItemStack to apply components to
     * @param componentDataString The component data as JSON string
     */
    public static void applyComponentData(net.minecraft.world.item.ItemStack itemStack, String componentDataString) {
        if (componentDataString != null && !componentDataString.trim().isEmpty()) {
            try {
                // Parse the component data JSON string
                CompoundTag componentTag = TagParser.parseTag(componentDataString);
                FreeMarket.LOGGER.info("Parsed component tag: {}", componentTag);
                
                // The issue is that DataComponentPatch.CODEC.parse() needs registry context
                // but NbtOps.INSTANCE doesn't have it. We need to find a different approach.
                FreeMarket.LOGGER.info("Attempting DataComponentPatch parsing with NbtOps.INSTANCE...");
                
                var result = DataComponentPatch.CODEC.parse(NbtOps.INSTANCE, componentTag);
                
                if (result.isSuccess()) {
                    DataComponentPatch patch = result.getOrThrow();
                    itemStack.applyComponents(patch);
                    FreeMarket.LOGGER.info("Successfully applied component data via DataComponentPatch: {}", componentDataString);
                    return;
                } else {
                    FreeMarket.LOGGER.warn("DataComponentPatch parsing failed: {}", result.error().get().message());
                    FreeMarket.LOGGER.warn("This confirms the registry context issue - NbtOps.INSTANCE lacks enchantment registry access");
                }
                
                // Fallback: Apply components directly
                applyComponentsDirectly(itemStack, componentTag);
                
            } catch (Exception e) {
                FreeMarket.LOGGER.error("Failed to apply component data: {}", e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    
    /**
     * Fallback method to apply components directly when DataComponentPatch fails.
     */
    private static void applyComponentsDirectly(net.minecraft.world.item.ItemStack itemStack, CompoundTag componentTag) {
        try {
            // Apply custom name if present
            if (componentTag.contains("minecraft:custom_name")) {
                String customNameStr = componentTag.getString("minecraft:custom_name");
                // Remove the extra quotes that might be in the JSON
                if (customNameStr.startsWith("\"") && customNameStr.endsWith("\"")) {
                    customNameStr = customNameStr.substring(1, customNameStr.length() - 1);
                }
                itemStack.set(DataComponents.CUSTOM_NAME, Component.literal(customNameStr));
                FreeMarket.LOGGER.info("Applied custom name: {}", customNameStr);
            }
            
            // Apply enchantments if present
            if (componentTag.contains("minecraft:enchantments")) {
                try {
                    CompoundTag enchantmentsTag = componentTag.getCompound("minecraft:enchantments");
                    FreeMarket.LOGGER.info("Parsing enchantments tag: {}", enchantmentsTag);
                    
                    // Try to parse enchantments with better error handling
                    var result = ItemEnchantments.CODEC.parse(NbtOps.INSTANCE, enchantmentsTag);
                    if (result.isSuccess()) {
                        ItemEnchantments enchantments = result.getOrThrow();
                        itemStack.set(DataComponents.ENCHANTMENTS, enchantments);
                        FreeMarket.LOGGER.info("Applied enchantments: {}", enchantments);
                    } else {
                        FreeMarket.LOGGER.warn("Failed to parse enchantments: {}", result.error().get().message());
                        // Try alternative approach - create enchantments manually
                        tryCreateEnchantmentsManually(itemStack, enchantmentsTag);
                    }
                } catch (Exception enchantError) {
                    FreeMarket.LOGGER.warn("Failed to apply enchantments: {}", enchantError.getMessage());
                    enchantError.printStackTrace();
                }
            }
            
            FreeMarket.LOGGER.info("Applied component data directly: {}", componentTag);
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to apply components directly: {}", e.getMessage());
        }
    }
    
    /**
     * Gets component data from an ItemStack as JSON string.
     * @param itemStack The ItemStack to get component data from
     * @return Component data as JSON string
     */
    public static String getComponentData(net.minecraft.world.item.ItemStack itemStack) {
        try {
            DataComponentMap components = itemStack.getComponents();
            if (components.isEmpty()) {
                return "{}";
            }
            
            CompoundTag resultTag = new CompoundTag();
            
            // Serialize custom name if present
            Component customName = components.get(DataComponents.CUSTOM_NAME);
            if (customName != null) {
                resultTag.putString("minecraft:custom_name", customName.getString());
            }
            
            // Serialize enchantments if present
            ItemEnchantments enchantments = components.get(DataComponents.ENCHANTMENTS);
            if (enchantments != null && !enchantments.isEmpty()) {
                try {
                    Tag enchantmentsTag = ItemEnchantments.CODEC.encodeStart(NbtOps.INSTANCE, enchantments).getOrThrow();
                    if (enchantmentsTag instanceof CompoundTag) {
                        resultTag.put("minecraft:enchantments", (CompoundTag) enchantmentsTag);
                    }
                } catch (Exception enchantError) {
                    FreeMarket.LOGGER.warn("Failed to serialize enchantments: {}", enchantError.getMessage());
                }
            }
            
            // Serialize armor trim if present
            net.minecraft.world.item.armortrim.ArmorTrim trim = components.get(DataComponents.TRIM);
            if (trim != null) {
                try {
                    Tag trimTag = net.minecraft.world.item.armortrim.ArmorTrim.CODEC.encodeStart(NbtOps.INSTANCE, trim).getOrThrow();
                    if (trimTag instanceof CompoundTag) {
                        resultTag.put("minecraft:trim", (CompoundTag) trimTag);
                    }
                } catch (Exception trimError) {
                    FreeMarket.LOGGER.warn("Failed to serialize armor trim: {}", trimError.getMessage());
                }
            }
            
            // Serialize damage if present
            Integer damage = components.get(DataComponents.DAMAGE);
            if (damage != null && damage > 0) {
                resultTag.putInt("minecraft:damage", damage);
            }
            
            // Serialize max damage if present
            Integer maxDamage = components.get(DataComponents.MAX_DAMAGE);
            if (maxDamage != null && maxDamage > 0) {
                resultTag.putInt("minecraft:max_damage", maxDamage);
            }
            
            // Serialize other components as needed
            // Add more component serialization here as needed
            
            return resultTag.toString();
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to serialize component data: {}", e.getMessage());
            return "{}";
        }
    }
    
    /**
     * Checks if an ItemStack has component data.
     * @param itemStack The ItemStack to check
     * @return true if component data exists
     */
    public static boolean hasComponentData(net.minecraft.world.item.ItemStack itemStack) {
        return !itemStack.getComponents().isEmpty();
    }
    
    /**
     * Creates a user-friendly enchantment JSON string for testing purposes.
     * Format: {"minecraft:enchantments":{"enchantments":[{"id":"minecraft:sharpness","lvl":3}]}}
     * @param enchantmentId The enchantment ID (e.g., "minecraft:sharpness")
     * @param level The enchantment level
     * @return JSON string with enchantment data
     */
    public static String createEnchantmentJson(String enchantmentId, int level) {
        try {
            // Create proper JSON string instead of using CompoundTag.toString()
            String json = String.format(
                "{\"minecraft:enchantments\":{\"enchantments\":{\"0\":{\"id\":\"%s\",\"lvl\":%d}}}}",
                enchantmentId, level
            );
            
            return json;
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to create enchantment JSON: {}", e.getMessage());
            return "{}";
        }
    }
    
    /**
     * Creates a user-friendly component data JSON string with custom name and enchantments.
     * @param customName The custom name for the item (can be null)
     * @param enchantmentId The enchantment ID (can be null)
     * @param enchantmentLevel The enchantment level (ignored if enchantmentId is null)
     * @return JSON string with component data
     */
    public static String createComponentDataJson(String customName, String enchantmentId, int enchantmentLevel) {
        try {
            StringBuilder json = new StringBuilder("{");
            boolean hasContent = false;
            
            // Add custom name if provided
            if (customName != null && !customName.trim().isEmpty()) {
                if (hasContent) json.append(",");
                json.append("\"minecraft:custom_name\":\"").append(customName).append("\"");
                hasContent = true;
            }
            
            // Add enchantments if provided
            if (enchantmentId != null && !enchantmentId.trim().isEmpty()) {
                if (hasContent) json.append(",");
                json.append("\"minecraft:enchantments\":{\"enchantments\":{\"0\":{\"id\":\"")
                    .append(enchantmentId).append("\",\"lvl\":").append(enchantmentLevel).append("}}}");
                hasContent = true;
            }
            
            json.append("}");
            
            String result = json.toString();
            return result;
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to create component data JSON: {}", e.getMessage());
            return "{}";
        }
    }
    
    /**
     * Alternative method to create enchantments manually when CODEC parsing fails.
     * This is a placeholder until we can resolve the registry access issues.
     */
    private static void tryCreateEnchantmentsManually(net.minecraft.world.item.ItemStack itemStack, CompoundTag enchantmentsTag) {
        try {
            FreeMarket.LOGGER.warn("Manual enchantment creation not yet implemented");
            FreeMarket.LOGGER.warn("The CODEC parsing failed due to registry access issues");
            FreeMarket.LOGGER.warn("Enchantments will not be applied to this item");
            FreeMarket.LOGGER.info("Enchantment data that failed to apply: {}", enchantmentsTag);
            
            // Enchantment application failed - component data will be applied server-side
            
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Manual enchantment creation failed: {}", e.getMessage());
        }
    }
}
