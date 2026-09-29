package decok.dfcdvadstf.catframe.tags.impl;

import decok.dfcdvadstf.catframe.Tags;
import decok.dfcdvadstf.catframe.tags.TagKey;
import net.minecraft.block.Block;

/**
 * CatFrame Block Tag Constants<br>
 * Similar to the modern vanilla's {@code BlockTag}<br>
 * All the tag's namespace are "catframe"<br>
 * Usage:
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
    
    /** require diamond tool */
    public static final TagKey<Block> NEEDS_DIAMOND_TOOL = create("needs_diamond_tool");
    
    // ==================== Material tags ====================
    
    /** All plank blocks */
    public static final TagKey<Block> PLANKS = create("planks");
    
    /** All log blocks */
    public static final TagKey<Block> LOGS = create("logs");
    
    /** All stone blocks */
    public static final TagKey<Block> STONES = create("stones");
    
    /** All wool blocks */
    public static final TagKey<Block> WOOL = create("wool");
    
    /** All glass blocks */
    public static final TagKey<Block> GLASS = create("glass");
    
    // ==================== Behavior tags ====================
    
    /** Blocks that can be climbed */
    public static final TagKey<Block> CLIMBABLE = create("climbable");
    
    /** Blocks that prevent leaf decay */
    public static final TagKey<Block> PREVENTS_LEAF_DECAY = create("prevents_leaf_decay");
    
    /** Blocks that dampen vibrations */
    public static final TagKey<Block> DAMPENS_VIBRATIONS = create("dampens_vibrations");
    
    /** Blocks that can serve as a beacon base */
    public static final TagKey<Block> BEACON_BASE_BLOCKS = create("beacon_base_blocks");
    
    /** Blocks that can be glided through */
    public static final TagKey<Block> CAN_GLIDE_THROUGH = create("can_glide_through");
    
    private CatFrameBlockTags() {
        // Utility class, do not instantiate
    }
    
    /**
     * Create a block TagKey
     */
    private static TagKey<Block> create(String name) {
        return TagKey.createBlock(Tags.MODID, name);
    }
    
    /**
     * Check whether a block belongs to a tag
     */
    public static boolean is(Block block, TagKey<Block> tag) {
        return CatFrameTags.blockLoader().is(block, tag.getLocation());
    }
}
