package decok.dfcdvadstf.catframe.tags;

import net.minecraft.block.Block;

/**
 * CatFrame 方块标签常量
 * 
 * 类似 26.1 的 BlockTags，定义所有 CatFrame 使用的方块标签
 * 所有标签的命名空间统一为 "catframe"
 * 
 * 使用方式：
 * <pre>
 * if (CatFrameBlockTags.is(block, CatFrameBlockTags.MINEABLE_WITH_PICKAXE)) {
 *     // 这个方块可以用镐子挖掘
 * }
 * </pre>
 */
public final class CatFrameBlockTags {
    
    // ==================== 挖掘类标签 ====================
    
    /** 可用镐子挖掘的方块 */
    public static final TagKey<Block> MINEABLE_WITH_PICKAXE = create("mineable/pickaxe");
    
    /** 可用斧头挖掘的方块 */
    public static final TagKey<Block> MINEABLE_WITH_AXE = create("mineable/axe");
    
    /** 可用铲子挖掘的方块 */
    public static final TagKey<Block> MINEABLE_WITH_SHOVEL = create("mineable/shovel");
    
    /** 可用锄头挖掘的方块 */
    public static final TagKey<Block> MINEABLE_WITH_HOE = create("mineable/hoe");
    
    /** 需要石制工具 */
    public static final TagKey<Block> NEEDS_STONE_TOOL = create("needs_stone_tool");
    
    /** 需要铁制工具 */
    public static final TagKey<Block> NEEDS_IRON_TOOL = create("needs_iron_tool");
    
    /** 需要钻石工具 */
    public static final TagKey<Block> NEEDS_DIAMOND_TOOL = create("needs_diamond_tool");
    
    // ==================== 材料类标签 ====================
    
    /** 所有木板方块 */
    public static final TagKey<Block> PLANKS = create("planks");
    
    /** 所有原木方块 */
    public static final TagKey<Block> LOGS = create("logs");
    
    /** 所有石头方块 */
    public static final TagKey<Block> STONES = create("stones");
    
    /** 所有羊毛方块 */
    public static final TagKey<Block> WOOL = create("wool");
    
    /** 所有玻璃方块 */
    public static final TagKey<Block> GLASS = create("glass");
    
    // ==================== 行为类标签 ====================
    
    /** 可攀爬的方块 */
    public static final TagKey<Block> CLIMBABLE = create("climbable");
    
    /** 防止叶片decay的方块 */
    public static final TagKey<Block> PREVENTS_LEAF_DECAY = create("prevents_leaf_decay");
    
    /** 减少振动传播的方块 */
    public static final TagKey<Block> DAMPENS_VIBRATIONS = create("dampens_vibrations");
    
    /** 可作为信标基座的方块 */
    public static final TagKey<Block> BEACON_BASE_BLOCKS = create("beacon_base_blocks");
    
    /** 可以透明通过的方块 */
    public static final TagKey<Block> CAN_GLIDE_THROUGH = create("can_glide_through");
    
    private CatFrameBlockTags() {
        // 工具类，禁止实例化
    }
    
    /**
     * 创建一个方块 TagKey
     */
    private static TagKey<Block> create(String name) {
        return TagKey.createBlock("catframe", name);
    }
    
    /**
     * 检查方块是否属于某个标签
     */
    public static boolean is(Block block, TagKey<Block> tag) {
        return CatFrameTags.is(block, tag.getFullIdentifier());
    }
}
