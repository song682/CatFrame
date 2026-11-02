package decok.dfcdvadstf.catframe.adapter.vanilla.model;

import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Regression tests for the stairs corner-shape detection in
 * {@link VanillaBlockResolvers#STAIRS} (private {@code computeShape}).
 *
 * <p>Guards the axis-grouping fix: with {@code STAIR_FACINGS = {east, west, south, north}}
 * the two facings on one axis share the high bit (0|1 = X, 2|3 = Z), so perpendicularity
 * must be {@code (a >> 1) != (b >> 1)}. The old parity check {@code (a & 1) != (b & 1)}
 * mis-grouped east×south (missed corner) and east×west (false corner) — exactly what a
 * 3x3 ring of stairs exposes at every corner stair.
 */
public class StairsCornerShapeTest {

    /** Facing codes per STAIR_FACINGS: 0=east,1=west,2=south,3=north. */
    private static final int EAST = 0, WEST = 1, SOUTH = 2, NORTH = 3;

    /** Plain material carrier for the stairs constructor (registry-free, test-env safe). */
    private static final class PlainBlock extends Block {
        private PlainBlock() {
            super(Material.wood);
        }
    }

    /** Standalone BlockStairs instance: the resolver's instanceof gate accepts any subclass. */
    private static final class TestStairs extends BlockStairs {
        private TestStairs() {
            super(new PlainBlock(), 0);
        }
    }

    private static final Block STAIRS = new TestStairs();

    /** Fake world: every listed cell holds a stair with the given meta, rest is air (null). */
    private static IBlockAccess stairWorld(Map<Long, Integer> cells) {
        final Block stairs = STAIRS;
        final Map<Long, Integer> meta = cells;
        return new IBlockAccess() {
            private int key(int x, int y, int z) {
                // small-coordinate packing, tests only use |coord| < 64
                return (x + 64) * 10000 + (y + 64) * 100 + (z + 64);
            }

            @Override
            public Block getBlock(int x, int y, int z) {
                return meta.containsKey((long) key(x, y, z)) ? stairs : null;
            }

            @Override
            public net.minecraft.tileentity.TileEntity getTileEntity(int x, int y, int z) {
                return null;
            }

            @Override
            public int getLightBrightnessForSkyBlocks(int x, int y, int z, int p_72802_4_) {
                return 0;
            }

            @Override
            public int getBlockMetadata(int x, int y, int z) {
                Integer m = meta.get((long) key(x, y, z));
                return m == null ? 0 : m;
            }

            @Override
            public int isBlockProvidingPowerTo(int x, int y, int z, int side) {
                return 0;
            }

            @Override
            public boolean isAirBlock(int x, int y, int z) {
                return !meta.containsKey((long) key(x, y, z));
            }

            @Override
            public BiomeGenBase getBiomeGenForCoords(int x, int z) {
                return null;
            }

            @Override
            public int getHeight() {
                return 256;
            }

            @Override
            public boolean extendedLevelsInChunkCache() {
                return false;
            }

            @Override
            public boolean isSideSolid(int x, int y, int z, net.minecraftforge.common.util.ForgeDirection side,
                    boolean _default) {
                return _default;
            }
        };
    }

    private static Map<Long, Integer> cells(Object... xyzMeta) {
        Map<Long, Integer> m = new HashMap<>();
        for (int i = 0; i < xyzMeta.length; i += 4) {
            int x = (Integer) xyzMeta[i], y = (Integer) xyzMeta[i + 1], z = (Integer) xyzMeta[i + 2];
            m.put((long) ((x + 64) * 10000 + (y + 64) * 100 + (z + 64)), (Integer) xyzMeta[i + 3]);
        }
        return m;
    }

    private static String shape(Map<Long, Integer> world, int facing, boolean top) throws Exception {
        Method m = VanillaBlockResolvers.class.getDeclaredMethod("computeShape",
                IBlockAccess.class, int.class, int.class, int.class, int.class, boolean.class);
        m.setAccessible(true);
        return (String) m.invoke(null, stairWorld(world), 0, 0, 0, facing, top);
    }

    @Test
    public void highSideCwStairIsOuterRight() throws Exception {
        // Self east, front cell (+X) is a south stair: south == CW(east) → outer_right
        assertEquals("outer_right", shape(cells(1, 0, 0, SOUTH), EAST, false));
    }

    @Test
    public void highSideCcwStairIsOuterLeft() throws Exception {
        // Self east, front cell is a north stair (CCW side) → outer_left
        assertEquals("outer_left", shape(cells(1, 0, 0, NORTH), EAST, false));
    }

    @Test
    public void sameAxisFrontStairIsStraight() throws Exception {
        // Regression: old parity check treated east×west (opposite, SAME axis) as perpendicular
        // and produced a false corner. Must be straight.
        assertEquals("straight", shape(cells(1, 0, 0, WEST), EAST, false));
    }

    @Test
    public void perpendicularEvenCodePairMissedByOldParityIsDetected() throws Exception {
        // Regression: self south (code 2) with an east (code 0) stair in front — both codes
        // are even, so the old parity check called them parallel and missed the corner.
        // Front of a south-rising stair is the +Z cell; east != CW(south)=west → outer_left.
        assertEquals("outer_left", shape(cells(0, 0, 1, EAST), SOUTH, false));
    }

    @Test
    public void lowSideStairIsInnerCorner() throws Exception {
        // Self east, low cell (-X) is a south stair: axis differs, side cell (z+1) empty → inner_right
        assertEquals("inner_right", shape(cells(-1, 0, 0, SOUTH), EAST, false));
        // …and a north one is inner_left
        assertEquals("inner_left", shape(cells(-1, 0, 0, NORTH), EAST, false));
    }

    @Test
    public void sameFacingSideCellBlocksTheCorner() throws Exception {
        // Inner corner suppressed when the side cell holds a stair with the same facing+half
        // (vanilla func_150146_f blocker check)
        assertEquals("straight", shape(cells(-1, 0, 0, SOUTH, 0, 0, 1, EAST), EAST, false));
    }

    @Test
    public void halfMismatchNeighbourIsIgnored() throws Exception {
        // Front stair with the other top bit is not a corner partner at all
        assertEquals("straight", shape(cells(1, 0, 0, SOUTH | 4), EAST, false));
    }

    @Test
    public void stairBlockIsAvailableInTestEnv() {
        // The fake world relies on STAIRS being a real BlockStairs instance (instanceof gate)
        org.junit.Assert.assertTrue(STAIRS instanceof BlockStairs);
    }
}
