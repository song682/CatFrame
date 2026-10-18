package decok.dfcdvadstf.catframe.tags.impl;

import decok.dfcdvadstf.catframe.tags.TagKey;
import decok.dfcdvadstf.catframe.tags.TagManager;
import decok.dfcdvadstf.catframe.tags.TagRegistry;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;

/**
 * Built-in block tag constants, mirroring the modern {@code BlockTags}.
 * <p>
 * Tag files live under {@code data/minecraft/tags/block}.
 * </p>
 */
public final class BlockTags {

    private static final String NAMESPACE = "minecraft";

    // ==================== Mining ====================

    public static final TagKey<Block> MINABLE_WITH_PICKAXE = create("mineable/pickaxe");
    public static final TagKey<Block> MINABLE_WITH_AXE = create("mineable/axe");
    public static final TagKey<Block> MINABLE_WITH_SHOVEL = create("mineable/shovel");
    public static final TagKey<Block> MINABLE_WITH_HOE = create("mineable/hoe");

    public static final TagKey<Block> NEEDS_STONE_TOOL = create("needs_stone_tool");
    public static final TagKey<Block> NEEDS_IRON_TOOL = create("needs_iron_tool");
    public static final TagKey<Block> NEEDS_DIAMOND_TOOL = create("needs_diamond_tool");

    // ==================== Materials ====================

    public static final TagKey<Block> PLANKS = create("planks");
    public static final TagKey<Block> LOGS = create("logs");
    public static final TagKey<Block> STONES = create("stones");
    public static final TagKey<Block> WOOL = create("wool");
    public static final TagKey<Block> GLASS = create("glass");

    // ==================== Behaviour ====================

    public static final TagKey<Block> CLIMBABLE = create("climbable");
    public static final TagKey<Block> PREVENTS_LEAF_DECAY = create("prevents_leaf_decay");
    public static final TagKey<Block> DAMPENS_VIBRATIONS = create("dampens_vibrations");
    public static final TagKey<Block> BEACON_BASE_BLOCKS = create("beacon_base_blocks");
    public static final TagKey<Block> CAN_GLIDE_THROUGH = create("can_glide_through");

    private BlockTags() {
    }

    private static TagKey<Block> create(String name) {
        return TagKey.create(TagRegistry.BLOCKS, new ResourceLocation(NAMESPACE, name));
    }

    /** @return true when the block belongs to the tag. */
    public static boolean is(Block block, TagKey<Block> tag) {
        return TagManager.is(block, tag);
    }
}
