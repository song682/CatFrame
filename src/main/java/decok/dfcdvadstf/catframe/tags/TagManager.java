package decok.dfcdvadstf.catframe.tags;

import decok.dfcdvadstf.catframe.Tags;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import java.util.List;

/**
 * Static entry point for the item and block tag loaders.
 * <p>
 * JSON-backed tags are managed by the data pack pipeline; this class covers
 * querying and hard-coded registration in code.
 * </p>
 *
 * <pre>
 * TagManager.add("wool", Blocks.wool);
 * if (TagManager.is(Blocks.wool, BlockTags.WOOL)) {
 *     ...
 * }
 * </pre>
 */
public final class TagManager {

    /** Default namespace for hard-coded registrations. */
    public static final String NAMESPACE = Tags.MODID;

    private static final TagLoader<Item> ITEM_LOADER =
            new TagLoader<>(TagRegistry.ITEMS, TagLoader.createItemLookup());

    private static final TagLoader<Block> BLOCK_LOADER =
            new TagLoader<>(TagRegistry.BLOCKS, TagLoader.createBlockLookup());

    private TagManager() {
    }

    public static TagLoader<Item> itemLoader() {
        return ITEM_LOADER;
    }

    public static TagLoader<Block> blockLoader() {
        return BLOCK_LOADER;
    }

    // ==================== Item registration ====================

    public static void add(String tagName, Item item) {
        ITEM_LOADER.add(new ResourceLocation(NAMESPACE, tagName), item);
    }

    public static void add(String namespace, String tagName, Item item) {
        ITEM_LOADER.add(new ResourceLocation(namespace, tagName), item);
    }

    public static void addAll(String tagName, Item... items) {
        for (Item item : items) {
            add(tagName, item);
        }
    }

    public static void addAll(String namespace, String tagName, Item... items) {
        for (Item item : items) {
            add(namespace, tagName, item);
        }
    }

    // ==================== Block registration ====================

    public static void add(String tagName, Block block) {
        BLOCK_LOADER.add(new ResourceLocation(NAMESPACE, tagName), block);
    }

    public static void add(String namespace, String tagName, Block block) {
        BLOCK_LOADER.add(new ResourceLocation(namespace, tagName), block);
    }

    public static void addAll(String tagName, Block... blocks) {
        for (Block block : blocks) {
            add(tagName, block);
        }
    }

    public static void addAll(String namespace, String tagName, Block... blocks) {
        for (Block block : blocks) {
            add(namespace, tagName, block);
        }
    }

    // ==================== Item queries ====================

    public static boolean is(Item item, String tagName) {
        return ITEM_LOADER.is(item, NAMESPACE, tagName);
    }

    public static boolean is(Item item, String namespace, String tagName) {
        return ITEM_LOADER.is(item, namespace, tagName);
    }

    public static boolean is(Item item, TagKey<Item> tag) {
        return ITEM_LOADER.is(item, tag);
    }

    public static List<Item> getItems(String tagName) {
        return ITEM_LOADER.getTagContents(NAMESPACE, tagName);
    }

    public static List<Item> getItems(String namespace, String tagName) {
        return ITEM_LOADER.getTagContents(namespace, tagName);
    }

    // ==================== Block queries ====================

    public static boolean is(Block block, String tagName) {
        return BLOCK_LOADER.is(block, NAMESPACE, tagName);
    }

    public static boolean is(Block block, String namespace, String tagName) {
        return BLOCK_LOADER.is(block, namespace, tagName);
    }

    public static boolean is(Block block, TagKey<Block> tag) {
        return BLOCK_LOADER.is(block, tag);
    }

    public static List<Block> getBlocks(String tagName) {
        return BLOCK_LOADER.getTagContents(NAMESPACE, tagName);
    }

    public static List<Block> getBlocks(String namespace, String tagName) {
        return BLOCK_LOADER.getTagContents(namespace, tagName);
    }

    // ==================== Generic queries ====================

    public static boolean is(Object object, String tagName) {
        if (object instanceof Item) {
            return is((Item) object, tagName);
        }
        if (object instanceof Block) {
            return is((Block) object, tagName);
        }
        return false;
    }

    public static boolean is(Object object, String namespace, String tagName) {
        if (object instanceof Item) {
            return is((Item) object, namespace, tagName);
        }
        if (object instanceof Block) {
            return is((Block) object, namespace, tagName);
        }
        return false;
    }

    /** Clears JSON-backed state, leaving hard-coded registrations intact. */
    public static void clearRaw() {
        ITEM_LOADER.clearRaw();
        BLOCK_LOADER.clearRaw();
    }

    /** Clears all tags, including hard-coded registrations. */
    public static void clear() {
        ITEM_LOADER.clear();
        BLOCK_LOADER.clear();
    }
}
