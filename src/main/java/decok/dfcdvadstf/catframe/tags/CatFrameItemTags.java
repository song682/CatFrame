package decok.dfcdvadstf.catframe.tags;

import net.minecraft.item.Item;

/**
 * CatFrame Item Tags Constants
 * 
 * Similar to 26.1's ItemTags, defining all item tags used by CatFrame
 * All tags have a uniform namespace of "catframe"
 * 
 * Usage:
 * <pre>
 * if (CatFrameItemTags.is(item, CatFrameItemTags.TOOLS)) {
 *     // This is a tool
 * }
 * </pre>
 */
public final class CatFrameItemTags {
    
    // ==================== Tool Class Tags ====================
    
    /** All swords */
    public static final TagKey<Item> SWORDS = create("swords");
    
    /** All axes */
    public static final TagKey<Item> AXES = create("axes");
    
    /** All pickaxes */
    public static final TagKey<Item> PICKAXES = create("pickaxes");
    
    /** All shovels */
    public static final TagKey<Item> SHOVELS = create("shovels");
    
    /** All hoes */
    public static final TagKey<Item> HOES = create("hoes");
    
    /** All tools */
    public static final TagKey<Item> TOOLS = create("tools");
    
    // ==================== Material Class Tags ====================
    
    /** All planks */
    public static final TagKey<Item> PLANKS = create("planks");
    
    /** All logs */
    public static final TagKey<Item> LOGS = create("logs");
    
    /** All stones */
    public static final TagKey<Item> STONES = create("stones");
    
    /** All wool */
    public static final TagKey<Item> WOOL = create("wool");
    
    /** All dyes */
    public static final TagKey<Item> DYES = create("dyes");
    
    // ==================== Function Class Tags ====================
    
    /** Edible items */
    public static final TagKey<Item> FOODS = create("foods");
    
    /** Brewable items */
    public static final TagKey<Item> BREWING_ITEMS = create("brewing_items");
    
    /** Fuel items */
    public static final TagKey<Item> FUELS = create("fuels");
    
    private CatFrameItemTags() {
        // Tool class, do not instantiate
    }
    
    /**
     * Create an item TagKey
     */
    private static TagKey<Item> create(String name) {
        return TagKey.createItem("catframe", name);
    }
    
    /**
     * Check if an item belongs to a certain tag
     */
    public static boolean is(Item item, TagKey<Item> tag) {
        return CatFrameTags.is(item, tag.getFullIdentifier());
    }
}
