package decok.dfcdvadstf.catframe.tags.impl;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

import java.util.Set;

import decok.dfcdvadstf.catframe.Tags;
import decok.dfcdvadstf.catframe.tags.TagKey;
import decok.dfcdvadstf.catframe.tags.TagLoader;

/**
 * CatFrame tag utility class
 * 
 * Provides simple API for registering and querying tags
 * Bottom layer uses TagLoader implementation, with a uniform namespace of "catframe"
 * 
 * Usage:
 * <pre>
 * // In preInit or init phase, register tags
 * CatFrameTags.add("wool", Blocks.wool);
 * CatFrameTags.add("wool", Blocks.carpet_white);
 * 
 * // Check if a block/item belongs to a certain tag
 * if (CatFrameTags.is(Blocks.wool, "wool")) {
 *     // This is a wool block or item
 * }
 * </pre>
 */
public final class CatFrameTags {
    
    /** CatFrame namespace */
    public static final String NAMESPACE = Tags.MODID;
    
    /** Item tag loader */
    private static final TagLoader<Item> ITEM_LOADER = new TagLoader<>("item", TagLoader.createItemLookup());
    
    /** Block tag loader */
    private static final TagLoader<Block> BLOCK_LOADER = new TagLoader<>("block", TagLoader.createBlockLookup());
    
    private CatFrameTags() {
        // Utility class, do not instantiate
    }
    
    /**
     * Get item tag loader
     */
    public static TagLoader<Item> itemLoader() {
        return ITEM_LOADER;
    }
    
    /**
     * Get block tag loader
     */
    public static TagLoader<Block> blockLoader() {
        return BLOCK_LOADER;
    }
    
    // ==================== Item Tag API ====================
    
    /**
     * Add item to a specified tag
     * 
     * @param tagName 标签名称（不含命名空间，自动使用 "catframe"）
     * @param item 物品
     */
    public static void add(String tagName, Item item) {
        ITEM_LOADER.getOrCreateTagContents(NAMESPACE, tagName).add(item);
    }
    
    /**
     * Add item to a tag with custom namespace
     * 
     * @param namespace 自定义命名空间
     * @param tagName 标签名称
     * @param item 物品
     */
    public static void add(String namespace, String tagName, Item item) {
        ITEM_LOADER.getOrCreateTagContents(namespace, tagName).add(item);
    }
    
    /**
     * Batch add items to a tag
     */
    public static void addAll(String tagName, Item... items) {
        for (Item item : items) {
            add(tagName, item);
        }
    }
    
    /**
     * Batch add Items to a tag with custom namespace
     */
    public static void addAll(String namespace, String tagName, Item... items) {
        for (Item item : items) {
            add(namespace, tagName, item);
        }
    }
    
    /**
     * Check if an item belongs to a certain tag
     */
    public static boolean is(Item item, String tagName) {
        return ITEM_LOADER.is(item, NAMESPACE, tagName);
    }
    
    /**
     * Check if an item belongs to a tag with custom namespace
     */
    public static boolean is(Item item, String namespace, String tagName) {
        return ITEM_LOADER.is(item, namespace, tagName);
    }
    
    /**
     * Check if an item belongs to a certain tag (using TagKey)
     */
    public static boolean is(Item item, TagKey<Item> tag) {
        return ITEM_LOADER.is(item, tag.getLocation());
    }
    
    /**
     * Get all items in a tag
     */
    public static Set<Item> getItems(String tagName) {
        return ITEM_LOADER.getTagContents(NAMESPACE, tagName);
    }
    
    /**
     * Get all items in a tag with custom namespace
     */
    public static Set<Item> getItems(String namespace, String tagName) {
        return ITEM_LOADER.getTagContents(namespace, tagName);
    }
    
    // ==================== Block Tag API ====================
    
    /**
     * Add block to a specified tag
     * 
     * @param tagName 标签名称（不含命名空间，自动使用 "catframe"）
     * @param block 方块
     */
    public static void add(String tagName, Block block) {
        BLOCK_LOADER.getOrCreateTagContents(NAMESPACE, tagName).add(block);
    }
    
    /**
     * Add block to a tag with custom namespace
     * 
     * @param namespace 自定义命名空间
     * @param tagName 标签名称
     * @param block 方块
     */
    public static void add(String namespace, String tagName, Block block) {
        BLOCK_LOADER.getOrCreateTagContents(namespace, tagName).add(block);
    }
    
    /**
     * Batch add blocks to a tag
     */
    public static void addAll(String tagName, Block... blocks) {
        for (Block block : blocks) {
            add(tagName, block);
        }
    }
    
    /**
     * Batch add blocks to a tag with custom namespace
     */
    public static void addAll(String namespace, String tagName, Block... blocks) {
        for (Block block : blocks) {
            add(namespace, tagName, block);
        }
    }
    
    /**
     * Check if a block belongs to a certain tag
     */
    public static boolean is(Block block, String tagName) {
        return BLOCK_LOADER.is(block, NAMESPACE, tagName);
    }
    
    /**
     * Check if a block belongs to a tag with custom namespace
     */
    public static boolean is(Block block, String namespace, String tagName) {
        return BLOCK_LOADER.is(block, namespace, tagName);
    }
    
    /**
     * Check if a block belongs to a certain tag (using TagKey)
     */
    public static boolean is(Block block, TagKey<Block> tag) {
        return BLOCK_LOADER.is(block, tag.getLocation());
    }
    
    /**
     * Get all blocks in a tag
     */
    public static Set<Block> getBlocks(String tagName) {
        return BLOCK_LOADER.getTagContents(NAMESPACE, tagName);
    }
    
    /**
     * Get all blocks in a tag with custom namespace
     */
    public static Set<Block> getBlocks(String namespace, String tagName) {
        return BLOCK_LOADER.getTagContents(namespace, tagName);
    }
    
    // ==================== General API ====================
    
    /**
     * Check if an object belongs to a certain tag
     */
    public static boolean is(Object object, String tagName) {
        if (object instanceof Item) {
            return is((Item) object, tagName);
        } else if (object instanceof Block) {
            return is((Block) object, tagName);
        }
        return false;
    }
    
    /**
     * Check if an object belongs to a tag with custom namespace
     */
    public static boolean is(Object object, String namespace, String tagName) {
        if (object instanceof Item) {
            return is((Item) object, namespace, tagName);
        } else if (object instanceof Block) {
            return is((Block) object, namespace, tagName);
        }
        return false;
    }
    
    /**
     * Load JSON tags from directory (using default "catframe" namespace)
     * 
     * @param tagsDir 标签目录
     */
    public static void loadFromDirectory(java.io.File tagsDir) {
        ITEM_LOADER.loadFromDirectory(new java.io.File(tagsDir, "items"));
        BLOCK_LOADER.loadFromDirectory(new java.io.File(tagsDir, "blocks"));
    }
    
    /**
     * Load JSON tags from directory with custom namespace
     * 
     * @param tagsDir 标签目录
     * @param namespace 自定义命名空间
     */
    public static void loadFromDirectory(java.io.File tagsDir, String namespace) {
        String prevItemNs = ITEM_LOADER.getDefaultNamespace();
        String prevBlockNs = BLOCK_LOADER.getDefaultNamespace();
        try {
            ITEM_LOADER.setDefaultNamespace(namespace);
            BLOCK_LOADER.setDefaultNamespace(namespace);
            ITEM_LOADER.loadFromDirectory(new java.io.File(tagsDir, "items"));
            BLOCK_LOADER.loadFromDirectory(new java.io.File(tagsDir, "blocks"));
        } finally {
            ITEM_LOADER.setDefaultNamespace(prevItemNs);
            BLOCK_LOADER.setDefaultNamespace(prevBlockNs);
        }
    }
    
    /**
     * Clear all tags
     */
    public static void clear() {
        ITEM_LOADER.clear();
        BLOCK_LOADER.clear();
    }
}
