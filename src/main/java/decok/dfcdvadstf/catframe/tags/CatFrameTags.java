package decok.dfcdvadstf.catframe.tags;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import java.util.Set;

/**
 * CatFrame 标签便捷工具类
 * 
 * 提供简单的 API 来注册和查询标签
 * 底层使用 TagLoader 实现，命名空间统一为 "catframe"
 * 
 * 使用方式：
 * <pre>
 * // 在 preInit 或 init 阶段注册标签
 * CatFrameTags.add("wool", Blocks.wool);
 * CatFrameTags.add("wool", Blocks.carpet_white);
 * 
 * // 查询方块/物品是否属于某个标签
 * if (CatFrameTags.is(Blocks.wool, "wool")) {
 *     // 这是一个羊毛方块
 * }
 * </pre>
 */
public final class CatFrameTags {
    
    /** CatFrame 命名空间 */
    public static final String NAMESPACE = "catframe";
    
    /** 物品标签加载器 */
    private static final TagLoader<Item> ITEM_LOADER = new TagLoader<>("item", TagLoader.createItemLookup());
    
    /** 方块标签加载器 */
    private static final TagLoader<Block> BLOCK_LOADER = new TagLoader<>("block", TagLoader.createBlockLookup());
    
    private CatFrameTags() {
        // 工具类，禁止实例化
    }
    
    /**
     * 获取物品标签加载器
     */
    public static TagLoader<Item> itemLoader() {
        return ITEM_LOADER;
    }
    
    /**
     * 获取方块标签加载器
     */
    public static TagLoader<Block> blockLoader() {
        return BLOCK_LOADER;
    }
    
    // ==================== 物品标签 API ====================
    
    /**
     * 将物品添加到指定标签
     * 
     * @param tagName 标签名称（不含命名空间，自动使用 "catframe"）
     * @param item 物品
     */
    public static void add(String tagName, Item item) {
        ITEM_LOADER.getOrCreateTagContents(NAMESPACE, tagName).add(item);
    }
    
    /**
     * 批量添加物品到标签
     */
    public static void addAll(String tagName, Item... items) {
        for (Item item : items) {
            add(tagName, item);
        }
    }
    
    /**
     * 检查物品是否属于某个标签
     */
    public static boolean is(Item item, String tagName) {
        return ITEM_LOADER.is(item, NAMESPACE, tagName);
    }
    
    /**
     * 检查物品是否属于某个标签（使用 TagKey）
     */
    public static boolean is(Item item, TagKey<Item> tag) {
        return ITEM_LOADER.is(item, tag.getLocation());
    }
    
    /**
     * 获取标签中的所有物品
     */
    public static Set<Item> getItems(String tagName) {
        return ITEM_LOADER.getTagContents(NAMESPACE, tagName);
    }
    
    // ==================== 方块标签 API ====================
    
    /**
     * 将方块添加到指定标签
     * 
     * @param tagName 标签名称（不含命名空间，自动使用 "catframe"）
     * @param block 方块
     */
    public static void add(String tagName, Block block) {
        BLOCK_LOADER.getOrCreateTagContents(NAMESPACE, tagName).add(block);
    }
    
    /**
     * 批量添加方块到标签
     */
    public static void addAll(String tagName, Block... blocks) {
        for (Block block : blocks) {
            add(tagName, block);
        }
    }
    
    /**
     * 检查方块是否属于某个标签
     */
    public static boolean is(Block block, String tagName) {
        return BLOCK_LOADER.is(block, NAMESPACE, tagName);
    }
    
    /**
     * 检查方块是否属于某个标签（使用 TagKey）
     */
    public static boolean is(Block block, TagKey<Block> tag) {
        return BLOCK_LOADER.is(block, tag.getLocation());
    }
    
    /**
     * 获取标签中的所有方块
     */
    public static Set<Block> getBlocks(String tagName) {
        return BLOCK_LOADER.getTagContents(NAMESPACE, tagName);
    }
    
    // ==================== 通用 API ====================
    
    /**
     * 检查对象是否属于某个标签
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
     * 加载 JSON 标签文件
     * 
     * @param tagsDir 标签目录
     */
    public static void loadFromDirectory(java.io.File tagsDir) {
        ITEM_LOADER.loadFromDirectory(new java.io.File(tagsDir, "items"));
        BLOCK_LOADER.loadFromDirectory(new java.io.File(tagsDir, "blocks"));
    }
    
    /**
     * 清空所有标签
     */
    public static void clear() {
        ITEM_LOADER.clear();
        BLOCK_LOADER.clear();
    }
}
