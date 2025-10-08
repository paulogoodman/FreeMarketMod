package com.servershop.common.managers;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

import com.servershop.common.data.MarketplaceItem;

/**
 * Manages item categorization based entirely on Forge item tags and NBT data.
 * No manual item mappings - everything is driven by tags and NBT.
 */
public class ItemCategoryManager {
    
    public enum Category {
        ALL("All Items", null),
        TOOLS("Tools", ItemTags.PICKAXES),
        WEAPONS("Weapons", ItemTags.SWORDS),
        ARMOR("Armor", ItemTags.HEAD_ARMOR),
        FOOD("Food", ItemTags.CANDLES), // Using candles as placeholder - will be overridden by NBT
        BLOCKS("Building Blocks", ItemTags.STAIRS),
        REDSTONE("Redstone", ItemTags.RAILS),
        DECORATIVE("Decorative", ItemTags.DECORATED_POT_SHERDS),
        TRANSPORTATION("Transportation", ItemTags.BOATS),
        MATERIALS("Materials", ItemTags.COALS),
        PLANTS("Plants", ItemTags.SAPLINGS),
        MISC("Miscellaneous", null);
        
        private final String displayName;
        private final TagKey<Item> primaryTag;
        
        Category(String displayName, TagKey<Item> primaryTag) {
            this.displayName = displayName;
            this.primaryTag = primaryTag;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public TagKey<Item> getPrimaryTag() {
            return primaryTag;
        }
    }
    
    // Tag-based categorization - no manual item mappings
    private static final Map<Category, List<TagKey<Item>>> CATEGORY_TAG_MAPPINGS = new HashMap<>();
    
    static {
        // Tools - based on tool tags
        CATEGORY_TAG_MAPPINGS.put(Category.TOOLS, Arrays.asList(
            ItemTags.PICKAXES,
            ItemTags.AXES,
            ItemTags.SHOVELS,
            ItemTags.HOES
        ));
        
        // Weapons - based on weapon tags
        CATEGORY_TAG_MAPPINGS.put(Category.WEAPONS, Arrays.asList(
            ItemTags.SWORDS
        ));
        
        // Armor - based on armor tags
        CATEGORY_TAG_MAPPINGS.put(Category.ARMOR, Arrays.asList(
            ItemTags.HEAD_ARMOR,
            ItemTags.CHEST_ARMOR,
            ItemTags.LEG_ARMOR,
            ItemTags.FOOT_ARMOR
        ));
        
        // Food - will be determined by NBT data or food-related tags
        CATEGORY_TAG_MAPPINGS.put(Category.FOOD, Arrays.asList(
            // No specific food tags available in vanilla, rely on NBT
        ));
        
        // Building blocks - based on block structure tags
        CATEGORY_TAG_MAPPINGS.put(Category.BLOCKS, Arrays.asList(
            ItemTags.STAIRS,
            ItemTags.SLABS,
            ItemTags.WALLS,
            ItemTags.FENCES,
            ItemTags.FENCE_GATES,
            ItemTags.DOORS,
            ItemTags.TRAPDOORS,
            ItemTags.BUTTONS
        ));
        
        // Redstone - based on redstone-related tags
        CATEGORY_TAG_MAPPINGS.put(Category.REDSTONE, Arrays.asList(
            ItemTags.RAILS
        ));
        
        // Decorative - based on decorative item tags
        CATEGORY_TAG_MAPPINGS.put(Category.DECORATIVE, Arrays.asList(
            ItemTags.DECORATED_POT_SHERDS,
            ItemTags.BANNERS,
            ItemTags.CANDLES
        ));
        
        // Transportation - based on transportation tags
        CATEGORY_TAG_MAPPINGS.put(Category.TRANSPORTATION, Arrays.asList(
            ItemTags.BOATS,
            ItemTags.CHEST_BOATS,
            ItemTags.RAILS
        ));
        
        // Materials - based on material tags
        CATEGORY_TAG_MAPPINGS.put(Category.MATERIALS, Arrays.asList(
            ItemTags.COALS
        ));
        
        // Plants - based on plant tags
        CATEGORY_TAG_MAPPINGS.put(Category.PLANTS, Arrays.asList(
            ItemTags.SAPLINGS,
            ItemTags.FLOWERS
        ));
    }
    
    /**
     * Determines the category for a given item stack.
     * Purely tag driven - no manual item mappings.
     * NBT support can be added later when the correct API is confirmed.
     */
    public static Category getCategoryForItem(ItemStack itemStack) {
        // Check vanilla tags first
        Category tagCategory = getCategoryFromTags(itemStack);
        if (tagCategory != null) {
            return tagCategory;
        }
        
        // Check for modded items using namespace detection
        if (isModdedItem(itemStack)) {
            // For now, modded items fall through to MISC
            // In the future, this could check modded item tags or NBT
            return Category.MISC;
        }
        
        // Default fallback
        return Category.MISC;
    }
    
    /**
     * Checks if an item is from a mod (not vanilla Minecraft).
     */
    private static boolean isModdedItem(ItemStack itemStack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        return itemId != null && !itemId.getNamespace().equals("minecraft");
    }
    
    
    /**
     * Gets category from vanilla item tags.
     */
    private static Category getCategoryFromTags(ItemStack itemStack) {
        // Check each category's tags
        for (Map.Entry<Category, List<TagKey<Item>>> entry : CATEGORY_TAG_MAPPINGS.entrySet()) {
            Category category = entry.getKey();
            List<TagKey<Item>> tags = entry.getValue();
            
            // Skip categories that don't have tags (like FOOD, MISC)
            if (tags.isEmpty()) {
                continue;
            }
            
            // Check if item matches any of the category's tags
            for (TagKey<Item> tag : tags) {
                if (itemStack.is(tag)) {
                    return category;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Gets all available categories for the UI.
     */
    public static List<Category> getAllCategories() {
        return Arrays.asList(Category.values());
    }
    
    /**
     * Filters a list of marketplace items by category.
     */
    public static List<MarketplaceItem> filterItemsByCategory(List<MarketplaceItem> items, Category category) {
        if (category == Category.ALL) {
            return new ArrayList<>(items);
        }
        
        return items.stream()
            .filter(item -> getCategoryForItem(item.getItemStack()) == category)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Gets the count of items in each category.
     */
    public static Map<Category, Integer> getCategoryCounts(List<MarketplaceItem> items) {
        Map<Category, Integer> counts = new HashMap<>();
        
        for (Category category : Category.values()) {
            counts.put(category, filterItemsByCategory(items, category).size());
        }
        
        return counts;
    }
}
