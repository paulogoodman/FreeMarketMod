package com.freemarket.common.attachments;

import com.freemarket.FreeMarket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.component.CustomData;

/**
 * Handles Data Components for marketplace items
 */
public class ItemComponentHandler {
    
    /**
     * Applies component data from JSON string to an ItemStack.
     * Handles each component type separately using proper registries.
     * @param itemStack The ItemStack to apply components to
     * @param componentDataString The component data as JSON string
     */
    public static void applyComponentData(ItemStack itemStack, String componentDataString) {
        if (componentDataString != null && !componentDataString.trim().isEmpty()) {
            try {
                CompoundTag componentTag = TagParser.parseTag(componentDataString);
                
                // Handle each component type separately
                applyEnchantments(itemStack, componentTag);
                applyArmorTrim(itemStack, componentTag);
                applyCustomData(itemStack, componentTag);
                applyOtherComponents(itemStack, componentTag);
            } catch (Exception e) {
                FreeMarket.LOGGER.error("Failed to apply component data: {}", e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Gets component data from an ItemStack as JSON string.
     * Handles each component type separately using proper registries.
     * @param itemStack The ItemStack to extract components from
     * @return JSON string containing component data
     */
    public static String getComponentData(ItemStack itemStack) {
        try {
            DataComponentMap components = itemStack.getComponents();
            if (components.isEmpty()) {
                return "{}";
            }
            
            CompoundTag resultTag = new CompoundTag();
            
            // Handle each component type separately
            serializeEnchantments(itemStack, resultTag);
            serializeArmorTrim(itemStack, resultTag);
            serializeCustomData(itemStack, resultTag);
            serializeOtherComponents(itemStack, resultTag);
            
            String result = resultTag.toString();
            FreeMarket.LOGGER.info("Serialized {} components to JSON: {}", resultTag.size(), result);
            return result;
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to serialize component data: {}", e.getMessage());
            return "{}";
        }
    }
    
    /**
     * Applies enchantments to the ItemStack.
     */
    private static void applyEnchantments(ItemStack itemStack, CompoundTag componentTag) {
        if (componentTag.contains("minecraft:enchantments")) {
            try {
                Tag enchantmentsTag = componentTag.get("minecraft:enchantments");
                // TODO: Implement proper enchantment application
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to apply enchantments: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Applies armor trim to the ItemStack.
     */
    private static void applyArmorTrim(ItemStack itemStack, CompoundTag componentTag) {
        if (componentTag.contains("minecraft:trim")) {
            try {
                CompoundTag trimTag = componentTag.getCompound("minecraft:trim");
                // For now, just log that we found armor trim
                FreeMarket.LOGGER.info("Found armor trim to apply: {}", trimTag);
                // TODO: Implement proper armor trim application
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to apply armor trim: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Applies custom data to the ItemStack.
     */
    private static void applyCustomData(ItemStack itemStack, CompoundTag componentTag) {
        if (componentTag.contains("minecraft:custom_data")) {
            try {
                CompoundTag customDataTag = componentTag.getCompound("minecraft:custom_data");
                CustomData customData = CustomData.of(customDataTag);
                itemStack.set(DataComponents.CUSTOM_DATA, customData);
                FreeMarket.LOGGER.info("Successfully applied custom data: {}", customDataTag);
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to apply custom data: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Applies other relevant components (damage, repair cost, lore, etc.).
     */
    private static void applyOtherComponents(ItemStack itemStack, CompoundTag componentTag) {
        // Apply damage
        if (componentTag.contains("minecraft:damage")) {
            try {
                int damage = componentTag.getInt("minecraft:damage");
                itemStack.set(DataComponents.DAMAGE, damage);
                FreeMarket.LOGGER.info("Successfully applied damage: {}", damage);
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to apply damage: {}", e.getMessage());
            }
        }
        
        // Apply repair cost
        if (componentTag.contains("minecraft:repair_cost")) {
            try {
                int repairCost = componentTag.getInt("minecraft:repair_cost");
                itemStack.set(DataComponents.REPAIR_COST, repairCost);
                FreeMarket.LOGGER.info("Successfully applied repair cost: {}", repairCost);
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to apply repair cost: {}", e.getMessage());
            }
        }
        
        // Apply lore
        if (componentTag.contains("minecraft:lore")) {
            try {
                Tag loreTag = componentTag.get("minecraft:lore");
                // For now, just log that we found lore
                FreeMarket.LOGGER.info("Found lore to apply: {}", loreTag);
                // TODO: Implement proper lore application
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to apply lore: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Serializes enchantments from the ItemStack.
     */
    private static void serializeEnchantments(ItemStack itemStack, CompoundTag resultTag) {
        if (itemStack.has(DataComponents.ENCHANTMENTS)) {
            try {
                ItemEnchantments enchantments = itemStack.get(DataComponents.ENCHANTMENTS);
                
                // Only serialize if there are actual enchantments
                if (!enchantments.keySet().isEmpty()) {
                    // Create a manual enchantments structure
                    CompoundTag enchantmentsTag = new CompoundTag();
                    CompoundTag enchantmentsList = new CompoundTag();
                    
                    int index = 0;
                    for (var enchantmentKey : enchantments.keySet()) {
                        CompoundTag enchantmentTag = new CompoundTag();
                        
                        // Extract enchantment ID from the ResourceKey
                        enchantmentKey.unwrap().ifLeft(resourceKey -> {
                            enchantmentTag.putString("id", resourceKey.location().toString());
                        }).ifRight(enchantment -> {
                            // Fallback: use enchantment toString() if ResourceKey not available
                            enchantmentTag.putString("id", enchantment.toString());
                        });
                        
                        enchantmentTag.putInt("lvl", enchantments.getLevel(enchantmentKey));
                        enchantmentsList.put(String.valueOf(index), enchantmentTag);
                        index++;
                    }
                    
                    enchantmentsTag.put("enchantments", enchantmentsList);
                    resultTag.put("minecraft:enchantments", enchantmentsTag);
                    
                    FreeMarket.LOGGER.info("Successfully serialized enchantments: {}", enchantmentsTag);
                }
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to serialize enchantments: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Serializes armor trim from the ItemStack.
     */
    private static void serializeArmorTrim(ItemStack itemStack, CompoundTag resultTag) {
        if (itemStack.has(DataComponents.TRIM)) {
            try {
                var trim = itemStack.get(DataComponents.TRIM);
                
                // Create a manual trim structure
                CompoundTag trimTag = new CompoundTag();
                
                // Extract trim material
                trim.material().unwrap().ifLeft(resourceKey -> {
                    trimTag.putString("material", resourceKey.location().toString());
                }).ifRight(material -> {
                    trimTag.putString("material", material.toString());
                });
                
                // Extract trim pattern
                trim.pattern().unwrap().ifLeft(resourceKey -> {
                    trimTag.putString("pattern", resourceKey.location().toString());
                }).ifRight(pattern -> {
                    trimTag.putString("pattern", pattern.toString());
                });
                
                resultTag.put("minecraft:trim", trimTag);
                
                FreeMarket.LOGGER.info("Successfully serialized armor trim: {}", trimTag);
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to serialize armor trim: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Serializes custom data from the ItemStack.
     */
    private static void serializeCustomData(ItemStack itemStack, CompoundTag resultTag) {
        if (itemStack.has(DataComponents.CUSTOM_DATA)) {
            try {
                CustomData customData = itemStack.get(DataComponents.CUSTOM_DATA);
                
                // Only serialize if there's actual custom data
                if (!customData.isEmpty()) {
                    // Custom data is already a CompoundTag, so we can use it directly
                    CompoundTag customDataTag = customData.copyTag();
                    resultTag.put("minecraft:custom_data", customDataTag);
                    
                    FreeMarket.LOGGER.info("Successfully serialized custom data: {}", customDataTag);
                }
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to serialize custom data: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Serializes other relevant components from the ItemStack.
     */
    private static void serializeOtherComponents(ItemStack itemStack, CompoundTag resultTag) {
        // Serialize damage (only if > 0)
        if (itemStack.has(DataComponents.DAMAGE)) {
            try {
                int damage = itemStack.get(DataComponents.DAMAGE);
                if (damage > 0) {
                    resultTag.putInt("minecraft:damage", damage);
                    FreeMarket.LOGGER.info("Successfully serialized damage: {}", damage);
                }
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to serialize damage: {}", e.getMessage());
            }
        }
        
        // Serialize repair cost (only if > 0)
        if (itemStack.has(DataComponents.REPAIR_COST)) {
            try {
                int repairCost = itemStack.get(DataComponents.REPAIR_COST);
                if (repairCost > 0) {
                    resultTag.putInt("minecraft:repair_cost", repairCost);
                    FreeMarket.LOGGER.info("Successfully serialized repair cost: {}", repairCost);
                }
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to serialize repair cost: {}", e.getMessage());
            }
        }
        
        // Serialize lore
        if (itemStack.has(DataComponents.LORE)) {
            try {
                var lore = itemStack.get(DataComponents.LORE);
                
                // Only serialize if there are actual lore lines
                if (!lore.lines().isEmpty()) {
                    // Create a manual lore structure
                    CompoundTag loreTag = new CompoundTag();
                    CompoundTag linesTag = new CompoundTag();
                    
                    int index = 0;
                    for (var line : lore.lines()) {
                        linesTag.putString(String.valueOf(index), line.getString());
                        index++;
                    }
                    
                    loreTag.put("lines", linesTag);
                    resultTag.put("minecraft:lore", loreTag);
                    
                    FreeMarket.LOGGER.info("Successfully serialized lore: {}", loreTag);
                }
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to serialize lore: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Checks if an ItemStack has any relevant component data.
     * @param itemStack The ItemStack to check
     * @return true if the ItemStack has relevant component data
     */
    public static boolean hasComponentData(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        
        DataComponentMap components = itemStack.getComponents();
        if (components.isEmpty()) {
            return false;
        }
        
        // Check for any of our supported component types
        return itemStack.has(DataComponents.ENCHANTMENTS) ||
               itemStack.has(DataComponents.TRIM) ||
               itemStack.has(DataComponents.CUSTOM_DATA) ||
               itemStack.has(DataComponents.DAMAGE) ||
               itemStack.has(DataComponents.REPAIR_COST) ||
               itemStack.has(DataComponents.LORE);
    }
}