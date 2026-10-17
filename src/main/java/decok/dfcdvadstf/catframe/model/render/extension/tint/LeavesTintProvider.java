package decok.dfcdvadstf.catframe.model.render.extension.tint;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.ColorizerFoliage;
import net.minecraft.world.IBlockAccess;

/**
 * Leaves block tint registration. Registers biome tinting and fixed colours for all
 * leaves blocks through {@link TintRegistry}.
 *
 * <p>Fully matches the behaviour of vanilla 1.7.10 {@link net.minecraft.block.BlockOldLeaf} / {@link net.minecraft.block.BlockNewLeaf}:
 * <ul>
 *   <li>Oak (meta 0) / jungle (meta 3): 3x3-area averaged biome foliage colour;</li>
 *   <li>Spruce (meta 1): {@link ColorizerFoliage#getFoliageColorPine()};</li>
 *   <li>Birch (meta 2): {@link ColorizerFoliage#getFoliageColorBirch()};</li>
 *   <li>Acacia / dark oak (leaves2): 3x3-area average (BlockNewLeaf does not override the colour method);</li>
 *   <li>Item form: {@link ColorizerFoliage#getFoliageColorBasic()}.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class LeavesTintProvider implements IBlockTintProvider {

    @Override
    public int getTint(IBlockAccess world, int x, int y, int z, Block block, int tintIndex) {
        // leaves2 (acacia/dark oak): always the 3x3 averaged foliage colour
        if (block == getLeaves2Block()) {
            if (world != null) {
                return averageFoliageColor(world, x, y, z);
            }
            return ColorizerFoliage.getFoliageColorBasic();
        }

        // leaves1 (oak/spruce/birch/jungle): differentiated by metadata
        int meta = world.getBlockMetadata(x, y, z) & 3;
        return getBlockColor(block, meta, world, x, y, z);
    }

    @Override
    public void register() {
        registerLeaves1();
        registerLeaves2();
    }

    // ==================== Registration (Blocks.leaves: oak, spruce, birch, jungle) ====================

    private void registerLeaves1() {
        if (Blocks.leaves == null) return;
        TintRegistry.registerBlockTint(Blocks.leaves, this);
    }

    // ==================== Registration (Blocks.leaves2: acacia, dark_oak — may not exist) ====================

    private void registerLeaves2() {
        Block leaves2 = getLeaves2Block();
        if (leaves2 == null) return;
        TintRegistry.registerBlockTint(leaves2, this);
    }

    // ==================== Colour lookup (matching vanilla BlockOldLeaf / BlockNewLeaf) ====================

    /**
     * Gets the leaves colour from the block type, metadata and world position.
     * <p>Only used for Blocks.leaves (BlockOldLeaf).
     *
     * @param block block instance
     * @param meta  block metadata (already masked with & 3)
     * @param world world reference (may be null; returns the default foliage colour when null)
     * @param x,y,z block coordinates
     * @return 0xRRGGBB colour
     */
    private static int getBlockColor(Block block, int meta, IBlockAccess world, int x, int y, int z) {
        // BlockOldLeaf.colorMultiplier logic:
        //   meta 1 (spruce) → getFoliageColorPine()
        //   meta 2 (birch)  → getFoliageColorBirch()
        //   meta 0 (oak), 3 (jungle) → super.colorMultiplier (3x3 avg)
        switch (meta) {
            case 1:
                return ColorizerFoliage.getFoliageColorPine();

            case 2:
                return ColorizerFoliage.getFoliageColorBirch();

            case 0:
            case 3:
            default:
                if (world != null) {
                    return averageFoliageColor(world, x, y, z);
                }
                return ColorizerFoliage.getFoliageColorBasic();
        }
    }

    /**
     * Vanilla 3x3-area averaged biome foliage colour.
     * Copied directly from {@link net.minecraft.block.BlockLeaves#colorMultiplier}.
     */
    private static int averageFoliageColor(IBlockAccess world, int x, int y, int z) {
        int r = 0, g = 0, b = 0;
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                int color = world.getBiomeGenForCoords(x + dx, z + dz)
                        .getBiomeFoliageColor(x + dx, y, z + dz);
                r += (color >> 16) & 0xFF;
                g += (color >> 8) & 0xFF;
                b += color & 0xFF;
            }
        }
        return ((r / 9) & 0xFF) << 16 | ((g / 9) & 0xFF) << 8 | (b / 9) & 0xFF;
    }

    /**
     * Safely obtains the leaves2 block (may not exist in 1.7.10; returns null).
     */
    public static Block getLeaves2Block() {
        try {
            return (Block) Block.blockRegistry.getObject("leaves2");
        } catch (Exception e) {
            return null;
        }
    }
}
