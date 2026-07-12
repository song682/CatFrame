package decok.dfcdvadstf.catframe.tags;

import net.minecraft.block.Block;

/**
 * CatFrame Block Tag Constants<br>
 * Similar to the modern vanilla's {@code BlockTag}<br>
 * All the tag's namespace are "catframe"<br>
 * Usage：
 * <pre>
 * if (CatFrameBlockTags.is(block, CatFrameBlockTags.MINABLE_WITH_PICKAXE)) {
 *    // This block can be mine with pickaxe
 * }
 * </pre>
 */
public final class CatFrameBlockTags {
    
    // ==================== Tag of Minable ====================
    
    /** Minable by the pickaxe*/
    public static final TagKey<Block> MINABLE_WITH_PICKAXE = create("mineable/pickaxe");
    
    /** Minable by the axe */
    public static final TagKey<Block> MINABLE_WITH_AXE = create("mineable/axe");
    
    /** Minable by the shovel */
    public static final TagKey<Block> MINABLE_WITH_SHOVEL = create("mineable/shovel");
    
    /** Minable by the hoe */
    public static final TagKey<Block> MINABLE_WITH_HOE = create("mineable/hoe");
    
    /** require stone tool */
    public static final TagKey<Block> NEEDS_STONE_TOOL = create("needs_stone_tool");
    
    /** require iron tool */
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
        return CatFrameTags.blockLoader().is(block, tag.getLocation());
    }
}
