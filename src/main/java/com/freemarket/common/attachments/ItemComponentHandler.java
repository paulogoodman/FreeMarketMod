package com.freemarket.common.attachments;

import com.freemarket.FreeMarket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.BundleContents;

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
        if (componentDataString != null && !componentDataString.trim().isEmpty() && !componentDataString.equals("{}")) {
            try {
                CompoundTag componentTag = TagParser.parseTag(componentDataString);
                
                // Get registry access (try client)
                net.minecraft.core.RegistryAccess registryAccess = getRegistryAccess();
                if (registryAccess == null) {
                    FreeMarket.LOGGER.warn("No registry access available, skipping component data application");
                    return;
                }
                
                // Handle each component type separately with proper registry access
                applyEnchantments(itemStack, componentTag, registryAccess);
                applyArmorTrim(itemStack, componentTag, registryAccess);
                applyCustomData(itemStack, componentTag);
                applyOtherComponents(itemStack, componentTag);
            } catch (Exception e) {
                FreeMarket.LOGGER.error("Failed to apply component data: {}", e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Gets registry access from server or client context.
     */
    private static net.minecraft.core.RegistryAccess getRegistryAccess() {
        // Try client-side (always works in both contexts)
        var clientLevel = net.minecraft.client.Minecraft.getInstance().level;
        if (clientLevel != null) {
            return clientLevel.registryAccess();
        }
        
        return null;
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
            
            return resultTag.toString();
        } catch (Exception e) {
            FreeMarket.LOGGER.error("Failed to serialize component data: {}", e.getMessage());
            return "{}";
        }
    }
    
    /**
     * Applies enchantments to the ItemStack using proper registry access.
     */
    private static void applyEnchantments(ItemStack itemStack, CompoundTag componentTag, net.minecraft.core.RegistryAccess registryAccess) {
        if (componentTag.contains("minecraft:enchantments")) {
            try {
                CompoundTag enchantmentsTag = componentTag.getCompound("minecraft:enchantments");
                
                // Parse enchantments from the tag
                ItemEnchantments.Mutable mutableEnchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                
                if (enchantmentsTag.contains("enchantments")) {
                    CompoundTag enchantmentsData = enchantmentsTag.getCompound("enchantments");
                    
                    for (String key : enchantmentsData.getAllKeys()) {
                        try {
                            CompoundTag enchantmentTag = enchantmentsData.getCompound(key);
                            String enchantmentId = enchantmentTag.getString("id");
                            int level = enchantmentTag.getInt("lvl");
                            
                            // Get enchantment from registry using provided registry access
                            var enchantmentRegistry = registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                            var enchantmentHolder = enchantmentRegistry.getHolder(net.minecraft.resources.ResourceLocation.parse(enchantmentId));
                            if (enchantmentHolder.isPresent()) {
                                mutableEnchantments.set(enchantmentHolder.get(), level);
                            } else {
                                FreeMarket.LOGGER.warn("Enchantment not found in registry: {}", enchantmentId);
                            }
                        } catch (Exception e) {
                            FreeMarket.LOGGER.warn("Failed to parse enchantment {}: {}", key, e.getMessage());
                        }
                    }
                }
                
                // Apply enchantments to the item stack
                itemStack.set(DataComponents.ENCHANTMENTS, mutableEnchantments.toImmutable());
                
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to apply enchantments: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Applies armor trim to the ItemStack using proper registry access.
     */
    private static void applyArmorTrim(ItemStack itemStack, CompoundTag componentTag, net.minecraft.core.RegistryAccess registryAccess) {
        if (componentTag.contains("minecraft:trim")) {
            try {
                CompoundTag trimTag = componentTag.getCompound("minecraft:trim");
                
                // Parse armor trim data
                if (trimTag.contains("pattern") && trimTag.contains("material")) {
                    String patternId = trimTag.getString("pattern");
                    String materialId = trimTag.getString("material");
                    
                    // Get pattern and material from registries using provided registry access
                    var patternRegistry = registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.TRIM_PATTERN);
                    var materialRegistry = registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.TRIM_MATERIAL);
                    
                    var patternHolder = patternRegistry.getHolder(net.minecraft.resources.ResourceLocation.parse(patternId));
                    var materialHolder = materialRegistry.getHolder(net.minecraft.resources.ResourceLocation.parse(materialId));
                    
                    if (patternHolder.isPresent() && materialHolder.isPresent()) {
                        // Create armor trim component (material first, then pattern)
                        var armorTrim = new net.minecraft.world.item.armortrim.ArmorTrim(materialHolder.get(), patternHolder.get());
                        itemStack.set(DataComponents.TRIM, armorTrim);
                    } else {
                        FreeMarket.LOGGER.warn("Trim pattern or material not found in registry: pattern={}, material={}", patternId, materialId);
                    }
                }
                
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
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to apply custom data: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Applies other relevant components (damage, repair cost, lore, custom name, etc.).
     */
    private static void applyOtherComponents(ItemStack itemStack, CompoundTag componentTag) {
        // Apply custom name
        if (componentTag.contains("minecraft:custom_name")) {
            try {
                String customNameJson = componentTag.getString("minecraft:custom_name");
                if (customNameJson != null && !customNameJson.trim().isEmpty()) {
                    var registryAccess = getRegistryAccess();
                    if (registryAccess != null) {
                        net.minecraft.network.chat.Component customName = net.minecraft.network.chat.Component.Serializer.fromJson(customNameJson, registryAccess);
                        if (customName != null) {
                            itemStack.set(DataComponents.CUSTOM_NAME, customName);
                        }
                    }
                }
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to apply custom name: {}", e.getMessage());
            }
        }
        
        // Apply item name (for renamed items in anvil)
        if (componentTag.contains("minecraft:item_name")) {
            try {
                String itemNameJson = componentTag.getString("minecraft:item_name");
                if (itemNameJson != null && !itemNameJson.trim().isEmpty()) {
                    var registryAccess = getRegistryAccess();
                    if (registryAccess != null) {
                        net.minecraft.network.chat.Component itemName = net.minecraft.network.chat.Component.Serializer.fromJson(itemNameJson, registryAccess);
                        if (itemName != null) {
                            itemStack.set(DataComponents.ITEM_NAME, itemName);
                        }
                    }
                }
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to apply item name: {}", e.getMessage());
            }
        }
        
        // Apply unbreakable flag
        if (componentTag.contains("minecraft:unbreakable")) {
            try {
                boolean showInTooltip = componentTag.getCompound("minecraft:unbreakable").getBoolean("show_in_tooltip");
                itemStack.set(DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(showInTooltip));
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to apply unbreakable: {}", e.getMessage());
            }
        }
        
        // Apply damage
        if (componentTag.contains("minecraft:damage")) {
            try {
                int damage = componentTag.getInt("minecraft:damage");
                itemStack.set(DataComponents.DAMAGE, damage);
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to apply damage: {}", e.getMessage());
            }
        }
        
        // Apply repair cost
        if (componentTag.contains("minecraft:repair_cost")) {
            try {
                int repairCost = componentTag.getInt("minecraft:repair_cost");
                itemStack.set(DataComponents.REPAIR_COST, repairCost);
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to apply repair cost: {}", e.getMessage());
            }
        }
        
        // Apply written book content (for signed books) using codec
        if (componentTag.contains("minecraft:written_book_content")) {
            try {
                CompoundTag bookTag = componentTag.getCompound("minecraft:written_book_content");
                var registryAccess = getRegistryAccess();
                if (registryAccess != null) {
                    // Use the codec to decode the book content
                    var codec = net.minecraft.world.item.component.WrittenBookContent.CODEC;
                    var result = codec.parse(net.minecraft.nbt.NbtOps.INSTANCE, bookTag);
                    result.resultOrPartial(error -> FreeMarket.LOGGER.warn("Failed to parse written book content: {}", error))
                          .ifPresent(content -> itemStack.set(DataComponents.WRITTEN_BOOK_CONTENT, content));
                }
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to apply written book content: {}", e.getMessage());
            }
        }
        
        // Apply lore
        if (componentTag.contains("minecraft:lore")) {
            try {
                CompoundTag loreTag = componentTag.getCompound("minecraft:lore");
                
                // Parse lore lines from the tag structure
                if (loreTag.contains("lines")) {
                    CompoundTag linesTag = loreTag.getCompound("lines");
                    
                    // Create a list to hold the lore lines
                    java.util.List<net.minecraft.network.chat.Component> loreLines = new java.util.ArrayList<>();
                    
                    // Extract lore lines in order
                    for (String key : linesTag.getAllKeys()) {
                        try {
                            String loreText = linesTag.getString(key);
                            if (loreText != null && !loreText.trim().isEmpty()) {
                                // Parse the lore text as a component (handles JSON formatting)
                                var clientLevel = net.minecraft.client.Minecraft.getInstance().level;
                                net.minecraft.network.chat.Component loreComponent = null;
                                if (clientLevel != null) {
                                    loreComponent = net.minecraft.network.chat.Component.Serializer.fromJson(loreText, clientLevel.registryAccess());
                                }
                                if (loreComponent != null) {
                                    loreLines.add(loreComponent);
                                } else {
                                    // Fallback: treat as plain text
                                    loreLines.add(net.minecraft.network.chat.Component.literal(loreText));
                                }
                            }
                        } catch (Exception e) {
                            FreeMarket.LOGGER.warn("Failed to parse lore line {}: {}", key, e.getMessage());
                        }
                    }
                    
                    // Apply lore to the item stack if we have any lines
                    if (!loreLines.isEmpty()) {
                        itemStack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(loreLines));
                    }
                }
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to apply lore: {}", e.getMessage());
            }
        }
        
        applyContainerComponent(itemStack, componentTag);
        applyBundleComponent(itemStack, componentTag);
    }
    
    private static void serializeContainerComponent(ItemStack itemStack, CompoundTag resultTag) {
        if (!itemStack.has(DataComponents.CONTAINER)) {
            return;
        }
        ItemContainerContents contents = itemStack.get(DataComponents.CONTAINER);
        if (contents == null || contents.stream().allMatch(ItemStack::isEmpty)) {
            return;
        }
        ItemContainerContents.CODEC.encodeStart(NbtOps.INSTANCE, contents)
            .resultOrPartial(error -> FreeMarket.LOGGER.warn("Failed to encode container component: {}", error))
            .ifPresent(tag -> resultTag.put("minecraft:container", tag));
    }
    
    private static void serializeBundleComponent(ItemStack itemStack, CompoundTag resultTag) {
        if (!itemStack.has(DataComponents.BUNDLE_CONTENTS)) {
            return;
        }
        BundleContents contents = itemStack.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null) {
            return;
        }
        BundleContents.CODEC.encodeStart(NbtOps.INSTANCE, contents)
            .resultOrPartial(error -> FreeMarket.LOGGER.warn("Failed to encode bundle contents: {}", error))
            .ifPresent(tag -> resultTag.put("minecraft:bundle_contents", tag));
    }
    
    private static void applyContainerComponent(ItemStack itemStack, CompoundTag componentTag) {
        if (!componentTag.contains("minecraft:container")) {
            return;
        }
        Tag tag = componentTag.get("minecraft:container");
        if (tag == null) {
            return;
        }
        ItemContainerContents.CODEC.parse(NbtOps.INSTANCE, tag)
            .resultOrPartial(error -> FreeMarket.LOGGER.warn("Failed to decode container component: {}", error))
            .ifPresent(contents -> itemStack.set(DataComponents.CONTAINER, contents));
    }
    
    private static void applyBundleComponent(ItemStack itemStack, CompoundTag componentTag) {
        if (!componentTag.contains("minecraft:bundle_contents")) {
            return;
        }
        Tag tag = componentTag.get("minecraft:bundle_contents");
        if (tag == null) {
            return;
        }
        BundleContents.CODEC.parse(NbtOps.INSTANCE, tag)
            .resultOrPartial(error -> FreeMarket.LOGGER.warn("Failed to decode bundle contents: {}", error))
            .ifPresent(contents -> itemStack.set(DataComponents.BUNDLE_CONTENTS, contents));
    }
    
    /**
     * Serializes enchantments from the ItemStack.
     */
    private static void serializeEnchantments(ItemStack itemStack, CompoundTag resultTag) {
        if (itemStack.has(DataComponents.ENCHANTMENTS)) {
            try {
                ItemEnchantments enchantments = itemStack.get(DataComponents.ENCHANTMENTS);
                
                // Only serialize if there are actual enchantments
                if (enchantments != null && !enchantments.keySet().isEmpty()) {
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
                if (trim != null) {
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
                }
                
                resultTag.put("minecraft:trim", trimTag);
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
                if (customData != null && !customData.isEmpty()) {
                    // Custom data is already a CompoundTag, so we can use it directly
                    CompoundTag customDataTag = customData.copyTag();
                    resultTag.put("minecraft:custom_data", customDataTag);
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
        // Serialize custom name
        if (itemStack.has(DataComponents.CUSTOM_NAME)) {
            try {
                net.minecraft.network.chat.Component customName = itemStack.get(DataComponents.CUSTOM_NAME);
                if (customName != null) {
                    var registryAccess = getRegistryAccess();
                    if (registryAccess != null) {
                        String customNameJson = net.minecraft.network.chat.Component.Serializer.toJson(customName, registryAccess);
                        resultTag.putString("minecraft:custom_name", customNameJson);
                    }
                }
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to serialize custom name: {}", e.getMessage());
            }
        }
        
        // Serialize item name
        if (itemStack.has(DataComponents.ITEM_NAME)) {
            try {
                net.minecraft.network.chat.Component itemName = itemStack.get(DataComponents.ITEM_NAME);
                if (itemName != null) {
                    var registryAccess = getRegistryAccess();
                    if (registryAccess != null) {
                        String itemNameJson = net.minecraft.network.chat.Component.Serializer.toJson(itemName, registryAccess);
                        resultTag.putString("minecraft:item_name", itemNameJson);
                    }
                }
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to serialize item name: {}", e.getMessage());
            }
        }
        
        // Serialize unbreakable flag
        if (itemStack.has(DataComponents.UNBREAKABLE)) {
            try {
                var unbreakable = itemStack.get(DataComponents.UNBREAKABLE);
                if (unbreakable != null) {
                    CompoundTag unbreakableTag = new CompoundTag();
                    unbreakableTag.putBoolean("show_in_tooltip", unbreakable.showInTooltip());
                    resultTag.put("minecraft:unbreakable", unbreakableTag);
                }
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to serialize unbreakable: {}", e.getMessage());
            }
        }
        
        // Serialize damage (only if > 0)
        if (itemStack.has(DataComponents.DAMAGE)) {
            try {
                Integer damage = itemStack.get(DataComponents.DAMAGE);
                if (damage != null && damage > 0) {
                    resultTag.putInt("minecraft:damage", damage);
                }
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to serialize damage: {}", e.getMessage());
            }
        }
        
        // Serialize repair cost (only if > 0)
        if (itemStack.has(DataComponents.REPAIR_COST)) {
            try {
                Integer repairCost = itemStack.get(DataComponents.REPAIR_COST);
                if (repairCost != null && repairCost > 0) {
                    resultTag.putInt("minecraft:repair_cost", repairCost);
                }
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to serialize repair cost: {}", e.getMessage());
            }
        }
        
        // Serialize written book content (for signed books) using codec
        if (itemStack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
            try {
                var bookContent = itemStack.get(DataComponents.WRITTEN_BOOK_CONTENT);
                if (bookContent != null) {
                    // Use the codec to encode the book content
                    var codec = net.minecraft.world.item.component.WrittenBookContent.CODEC;
                    var result = codec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, bookContent);
                    result.resultOrPartial(error -> FreeMarket.LOGGER.warn("Failed to encode written book content: {}", error))
                          .ifPresent(tag -> {
                              if (tag instanceof CompoundTag bookTag) {
                                  resultTag.put("minecraft:written_book_content", bookTag);
                              }
                          });
                }
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to serialize written book content: {}", e.getMessage());
            }
        }
        
        // Serialize lore
        if (itemStack.has(DataComponents.LORE)) {
            try {
                var lore = itemStack.get(DataComponents.LORE);
                
                // Only serialize if there are actual lore lines
                if (lore != null && !lore.lines().isEmpty()) {
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
                }
            } catch (Exception e) {
                FreeMarket.LOGGER.warn("Failed to serialize lore: {}", e.getMessage());
            }
        }
        
        serializeContainerComponent(itemStack, resultTag);
        serializeBundleComponent(itemStack, resultTag);
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
               itemStack.has(DataComponents.CUSTOM_NAME) ||
               itemStack.has(DataComponents.ITEM_NAME) ||
               itemStack.has(DataComponents.UNBREAKABLE) ||
               itemStack.has(DataComponents.DAMAGE) ||
               itemStack.has(DataComponents.REPAIR_COST) ||
               itemStack.has(DataComponents.LORE) ||
               itemStack.has(DataComponents.WRITTEN_BOOK_CONTENT) ||
               itemStack.has(DataComponents.CONTAINER) ||
               itemStack.has(DataComponents.BUNDLE_CONTENTS);
    }
}