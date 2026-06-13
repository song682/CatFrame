package decok.dfcdvadstf.catframe.tags;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

/**
 * CatFrame 物品标签常量
 * 
 * 类似 26.1 的 ItemTags，定义所有 CatFrame 使用的物品标签
 * 所有标签的命名空间统一为 "catframe"
 * 
 * 使用方式：
 * <pre>
 * if (CatFrameItemTags.is(item, CatFrameItemTags.TOOLS)) {
 *     // 这是一个工具
 * }
 * </pre>
 */
public final class CatFrameItemTags {
    
    // ==================== 工具类标签 ====================
    
    /** 所有剑 */
    public static final TagKey<Item> SWORDS = create("swords");
    
    /** 所有斧头 */
    public static final TagKey<Item> AXES = create("axes");
    
    /** 所有镐子 */
    public static final TagKey<Item> PICKAXES = create("pickaxes");
    
    /** 所有铲子 */
    public static final TagKey<Item> SHOVELS = create("shovels");
    
    /** 所有锄头 */
    public static final TagKey<Item> HOES = create("hoes");
    
    /** 所有工具 */
    public static final TagKey<Item> TOOLS = create("tools");
    
    // ==================== 材料类标签 ====================
    
    /** 所有木板 */
    public static final TagKey<Item> PLANKS = create("planks");
    
    /** 所有原木 */
    public static final TagKey<Item> LOGS = create("logs");
    
    /** 所有石头 */
    public static final TagKey<Item> STONES = create("stones");
    
    /** 所有羊毛 */
    public static final TagKey<Item> WOOL = create("wool");
    
    /** 所有染料 */
    public static final TagKey<Item> DYES = create("dyes");
    
    // ==================== 功能类标签 ====================
    
    /** 可食用物品 */
    public static final TagKey<Item> FOODS = create("foods");
    
    /** 可酿造物品 */
    public static final TagKey<Item> BREWING_ITEMS = create("brewing_items");
    
    /** 燃料物品 */
    public static final TagKey<Item> FUELS = create("fuels");
    
    private CatFrameItemTags() {
        // 工具类，禁止实例化
    }
    
    /**
     * 创建一个物品 TagKey
     */
    private static TagKey<Item> create(String name) {
        return TagKey.createItem("catframe", name);
    }
    
    /**
     * 检查物品是否属于某个标签
     */
    public static boolean is(Item item, TagKey<Item> tag) {
        return CatFrameTags.is(item, tag.getFullIdentifier());
    }
}
