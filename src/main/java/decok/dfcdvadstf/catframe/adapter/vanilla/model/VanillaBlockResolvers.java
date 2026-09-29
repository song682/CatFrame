package decok.dfcdvadstf.catframe.adapter.vanilla.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.state.block.ResidentStateModel.DynamicPropertyResolver;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockWall;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.Map;

import static net.minecraft.util.Direction.rotateOpposite;

/**
 * Collection of runtime dynamic property resolvers for vanilla blocks.
 * <p>
 * Gathers the in-world property computation logic that used to be scattered across
 * {@code StairsBlockModel} / {@code PaneMultipartRedirectModel}, exposing it to
 * {@link ResidentStateModel} as {@link DynamicPropertyResolver}s.
 * <ul>
 * <li>{@link #STAIRS} — stair corner shape (facing/half + shape)</li>
 * <li>{@link #PANE} — glass pane / iron bars connections (north/east/south/west)</li>
 * <li>{@link #REDSTONE_WIRE} — redstone wire connections (north/east/south/west + up_* climbing faces)</li>
 * <li>{@link #DOOR} — door upper/lower half meta merge (facing/half/hinge/open)</li>
 * <li>{@link #DOUBLE_PLANT} — double plant upper-half variant read from the block below (variant/half)</li>
 * <li>{@link #SNOWY} — grass block snow cover (snowy, block above is snow / snow block)</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class VanillaBlockResolvers {

    private VanillaBlockResolvers() {
    }

    // ==================== Stairs ====================

    /** facing: 0=east,1=west,2=south,3=north */
    private static final String[] STAIR_FACINGS = { "east", "west", "south", "north" };
    // CW (rotateY): 0(east)→2(south), 2(south)→1(west), 1(west)→3(north),
    // 3(north)→0(east)
    private static final int[] CW = { 2, 3, 1, 0 };
    // Front offset (along facing); the rear offset is its negation
    private static final int[][] FRONT_OFFSET = {
            { 1, 0 }, // east
            { -1, 0 }, // west
            { 0, 1 }, // south
            { 0, -1 } // north
    };

    /**
     * Stairs dynamic resolver: writes facing/half/shape.
     * <p>
     * Corner detection aligns with 1.12+ {@code BlockStairs#getShape} (ported from the former {@code StairsBlockModel}).
     */
    public static final DynamicPropertyResolver STAIRS = new DynamicPropertyResolver() {
        @Override
        public void resolve(IBlockAccess world, int x, int y, int z, int meta, Map<String, String> props) {
            int facing = meta & 3;
            boolean top = (meta & 4) != 0;
            props.put("facing", STAIR_FACINGS[facing]);
            props.put("half", top ? "top" : "bottom");
            props.put("shape", computeShape(world, x, y, z, facing, top));
        }
    };

    private static String computeShape(IBlockAccess world, int x, int y, int z, int facing, boolean top) {
        int[] fwd = FRONT_OFFSET[facing];

        // Check behind (opposite of facing)
        Integer behind = getStairFacing(world, x - fwd[0], y, z - fwd[1], top);
        if (behind != null && (behind & 1) != (facing & 1)) { // different axis
            return behind == CW[facing] ? "outer_left" : "outer_right";
        }

        // Check ahead (along facing)
        Integer ahead = getStairFacing(world, x + fwd[0], y, z + fwd[1], top);
        if (ahead != null && (ahead & 1) != (facing & 1)) { // different axis
            return ahead == CW[facing] ? "inner_left" : "inner_right";
        }

        return "straight";
    }

    private static Integer getStairFacing(IBlockAccess world, int nx, int ny, int nz, boolean top) {
        Block block = world.getBlock(nx, ny, nz);
        if (!(block instanceof BlockStairs))
            return null;
        int meta = world.getBlockMetadata(nx, ny, nz);
        if (((meta & 4) != 0) != top)
            return null;
        return meta & 3;
    }

    // ==================== Glass panes / iron bars ====================

    /**
     * Connection-block dynamic resolver: writes north/east/south/west.
     * <p>
     * The connection check is dispatched by the rendered block's own type (ported from the former
     * {@code PaneMultipartRedirectModel}):
     * <ul>
     * <li>{@link BlockPane} subclasses (glass panes / iron bars) → {@link BlockPane#canPaneConnectTo}</li>
     * <li>{@link BlockWall} (1.7.10 stone walls are not BlockPane) →
     * {@link BlockWall#canConnectWallTo}</li>
     * <li>{@link BlockFence} (1.7.10 fences are not BlockPane) →
     * {@link BlockFence#canConnectFenceTo}</li>
     * </ul>
     */
    public static final DynamicPropertyResolver PANE = new DynamicPropertyResolver() {
        @Override
        public void resolve(IBlockAccess world, int x, int y, int z, int meta, Map<String, String> props) {
            Block self = (world != null) ? world.getBlock(x, y, z) : null;
            props.put("north", canConnect(self, world, x, y, z - 1, ForgeDirection.NORTH) ? "true" : "false");
            props.put("east", canConnect(self, world, x + 1, y, z, ForgeDirection.EAST) ? "true" : "false");
            props.put("south", canConnect(self, world, x, y, z + 1, ForgeDirection.SOUTH) ? "true" : "false");
            props.put("west", canConnect(self, world, x - 1, y, z, ForgeDirection.WEST) ? "true" : "false");
        }

        /**
         * Dispatch the connection check by the rendered block's own type.
         */
        private boolean canConnect(Block self, IBlockAccess world, int x, int y, int z, ForgeDirection dir) {
            if (self instanceof BlockWall) {
                // 1.7.10 wall: connects to other walls / fence_gate / opaque normally-rendered blocks
                return ((BlockWall) self).canConnectWallTo(world, x, y, z);
            }
            if (self instanceof BlockFence) {
                // 1.7.10 fence: connects to other fences / fence_gate / opaque normally-rendered blocks
                return ((BlockFence) self).canConnectFenceTo(world, x, y, z);
            }
            if (!(self instanceof BlockPane))
                return false;
            return ((BlockPane) self).canPaneConnectTo(world, x, y, z, dir);
        }
    };

    // ==================== Redstone wire (1.7.10 renderBlockRedstoneWire connection logic)
    // ====================

    /**
     * Redstone wire dynamic resolver: writes north/east/south/west + up_north/up_east/up_south/up_west.
     * <p>
     * Faithfully reproduces the connection logic of 1.7.10 {@code RenderBlocks#renderBlockRedstoneWire}:
     * <ul>
     * <li>horizontal: {@code isPowerProviderOrWire(neighbour, side)}, or, when the neighbour is not a
     * full cube, a downward connection via {@code isPowerProviderOrWire(neighbour below, -1)} (-1 only holds for redstone wire);</li>
     * <li>upward: the block above this wire is not a full cube, the neighbour is a full cube, and {@code isPowerProviderOrWire(neighbour above, -1)};</li>
     * <li>climbing faces: the neighbour is a full cube and the block above it is exactly redstone wire
     * ({@code Blocks.redstone_wire}); independent of the horizontal connection flags.</li>
     * </ul>
     * side direction codes (vanilla Direction XZ plane): 0=south, 1=west, 2=north, 3=east.
     * Repeaters/comparators connect only when side equals their facing or the opposite side
     * ({@code side == (meta&3) || side == rotateOpposite[meta&3]}).
     */
    public static final DynamicPropertyResolver REDSTONE_WIRE = new DynamicPropertyResolver() {
        @Override
        public void resolve(IBlockAccess world, int x, int y, int z, int meta, Map<String, String> props) {
            if (world == null) {
                // No world context (e.g. GUI preview): render as an isolated dot
                props.put("north", "false");
                props.put("east", "false");
                props.put("south", "false");
                props.put("west", "false");
                props.put("up_north", "false");
                props.put("up_east", "false");
                props.put("up_south", "false");
                props.put("up_west", "false");
                return;
            }

            // Horizontal + downward connections (flag = west, flag1 = east, flag2 = north, flag3 = south)
            boolean west = isPowerProviderOrWire(world, x - 1, y, z, 1)
                    || !world.getBlock(x - 1, y, z).isNormalCube()
                            && isPowerProviderOrWire(world, x - 1, y - 1, z, -1);
            boolean east = isPowerProviderOrWire(world, x + 1, y, z, 3)
                    || !world.getBlock(x + 1, y, z).isNormalCube()
                            && isPowerProviderOrWire(world, x + 1, y - 1, z, -1);
            boolean north = isPowerProviderOrWire(world, x, y, z - 1, 2)
                    || !world.getBlock(x, y, z - 1).isNormalCube()
                            && isPowerProviderOrWire(world, x, y - 1, z - 1, -1);
            boolean south = isPowerProviderOrWire(world, x, y, z + 1, 0)
                    || !world.getBlock(x, y, z + 1).isNormalCube()
                            && isPowerProviderOrWire(world, x, y - 1, z + 1, -1);

            // Upward connections: the block above this wire is not a full cube, the neighbour is a full cube,
            // and the block above the neighbour can supply power / is wire
            boolean upWireAbove = !world.getBlock(x, y + 1, z).isNormalCube();
            if (upWireAbove) {
                if (world.getBlock(x - 1, y, z).isNormalCube()
                        && isPowerProviderOrWire(world, x - 1, y + 1, z, -1))
                    west = true;
                if (world.getBlock(x + 1, y, z).isNormalCube()
                        && isPowerProviderOrWire(world, x + 1, y + 1, z, -1))
                    east = true;
                if (world.getBlock(x, y, z - 1).isNormalCube()
                        && isPowerProviderOrWire(world, x, y + 1, z - 1, -1))
                    north = true;
                if (world.getBlock(x, y, z + 1).isNormalCube()
                        && isPowerProviderOrWire(world, x, y + 1, z + 1, -1))
                    south = true;
            }

            props.put("north", north ? "true" : "false");
            props.put("east", east ? "true" : "false");
            props.put("south", south ? "true" : "false");
            props.put("west", west ? "true" : "false");

            // Climbing faces: the neighbour is a full cube and the block above it is exactly redstone wire
            // (1.7.10 lines 2553/2567/2581/2595)
            props.put("up_north", upWireAbove && world.getBlock(x, y, z - 1).isNormalCube()
                    && world.getBlock(x, y + 1, z - 1) == Blocks.redstone_wire ? "true" : "false");
            props.put("up_east", upWireAbove && world.getBlock(x + 1, y, z).isNormalCube()
                    && world.getBlock(x + 1, y + 1, z) == Blocks.redstone_wire ? "true" : "false");
            props.put("up_south", upWireAbove && world.getBlock(x, y, z + 1).isNormalCube()
                    && world.getBlock(x, y + 1, z + 1) == Blocks.redstone_wire ? "true" : "false");
            props.put("up_west", upWireAbove && world.getBlock(x - 1, y, z).isNormalCube()
                    && world.getBlock(x - 1, y + 1, z) == Blocks.redstone_wire ? "true" : "false");
        }

        /**
         * Reproduction of 1.7.10 {@code BlockRedstoneWire#isPowerProviderOrWire}:
         * redstone wire always connects; repeaters/comparators connect by facing + opposite side
         * (meta&3 and rotateOpposite); all other blocks go through {@link Block#canConnectRedstone}
         * (by default only power providers, and side != -1).
         */
        private boolean isPowerProviderOrWire(IBlockAccess world, int x, int y, int z, int side) {
            Block block = world.getBlock(x, y, z);
            if (block == Blocks.redstone_wire)
                return true;
            if (isDiode(block)) {
                int meta = world.getBlockMetadata(x, y, z);
                int facing = meta & 3;
                return side == facing || side == rotateOpposite[facing];
            }
            return block.canConnectRedstone(world, x, y, z, side);
        }

        /** Repeaters/comparators (4 in total: powered + unpowered), corresponding to 1.7.10 func_149907_e. */
        private boolean isDiode(Block block) {
            return block == Blocks.unpowered_repeater || block == Blocks.powered_repeater
                    || block == Blocks.unpowered_comparator || block == Blocks.powered_comparator;
        }
    };

    // ==================== Doors ====================

    /**
     * Door facing lookup: 1.7.10 lower-half meta&3 → modern facing.
     * <p>
     * Aligned with 1.8 {@code BlockDoor#getStateFromMeta}'s
     * {@code getHorizontal(meta&3).rotateYCCW()}。
     */
    private static final String[] DOOR_FACINGS = { "east", "south", "west", "north" };

    /**
     * Door dynamic resolver: writes facing/half/hinge/open.
     * <p>
     * A 1.7.10 door splits its full state across the meta of its upper and lower halves
     * (aligned with {@code BlockDoor#func_150012_g}):
     * <ul>
     * <li>lower half (bit3=0): bit0-1 = facing, bit2 = open</li>
     * <li>upper half (bit3=1): bit0 = hinge (1=right)</li>
     * </ul>
     * So rendering either half must read the other half's meta across blocks to assemble the full
     * blockstate key. When the other half is missing (e.g. a leftover door placed by setblock),
     * fall back to defaults: facing=east, open=false, hinge=left.
     */
    public static final DynamicPropertyResolver DOOR = new DynamicPropertyResolver() {
        @Override
        public void resolve(IBlockAccess world, int x, int y, int z, int meta, Map<String, String> props) {
            boolean upper = (meta & 8) != 0;
            // Fallback meta when the other half is missing: lower 0 (east+closed), upper 8 (hinge=left)
            int lowerMeta = upper ? 0 : meta;
            int upperMeta = upper ? meta : 8;
            if (world != null) {
                Block self = world.getBlock(x, y, z);
                if (upper) {
                    if (world.getBlock(x, y - 1, z) == self) {
                        lowerMeta = world.getBlockMetadata(x, y - 1, z);
                    }
                } else {
                    if (world.getBlock(x, y + 1, z) == self) {
                        upperMeta = world.getBlockMetadata(x, y + 1, z);
                    }
                }
            }
            props.put("half", upper ? "upper" : "lower");
            props.put("facing", DOOR_FACINGS[lowerMeta & 3]);
            props.put("open", (lowerMeta & 4) != 0 ? "true" : "false");
            props.put("hinge", (upperMeta & 1) != 0 ? "right" : "left");
        }
    };

    // ==================== Double plants ====================

    /**
     * Double plant variant name lookup: 0=sunflower, 1=lilac, 2=double_grass, 3=double_fern,
     * 4=rose_bush, 5=peony — must match the variant keys of the blockstate double-plant JSON
     * (corresponding to 1.7.10 {@code BlockDoublePlant#field_149892_a}'s
     * sunflower/syringa/grass/fern/rose/paeonia, mapped to the 1.8+ vocabulary).
     */
    private static final String[] DOUBLE_PLANT_VARIANTS = { "sunflower", "lilac", "double_grass", "double_fern",
            "rose_bush", "peony" };

    /**
     * Double plant dynamic resolver: writes variant/half.
     * <p>
     * 1.7.10's {@code BlockDoublePlant} splits its full state across the meta of the upper and
     * lower halves (aligned with {@code BlockDoublePlant#func_149885_e}):
     * <ul>
     * <li>lower half (bit3=0): the low 3 bits are the variant (0=sunflower, 1=syringa, 2=grass,
     * 3=fern, 4=rose, 5=paeonia, then mapped to the blockstate 1.8+ vocabulary)</li>
     * <li>upper half (bit3=1): the low 2 bits are a residue written by {@code onBlockPlacedBy} from
     * the player's facing; the real variant must be read from the block below</li>
     * </ul>
     * So rendering the upper half requires reading the meta of the block below across blocks.
     * When that block is missing / is a different block (e.g. a leftover stalk placed by setblock),
     * fall back to this half's own low 3 bits.
     */
    public static final DynamicPropertyResolver DOUBLE_PLANT = new DynamicPropertyResolver() {
        @Override
        public void resolve(IBlockAccess world, int x, int y, int z, int meta, Map<String, String> props) {
            boolean upper = (meta & 8) != 0;
            int variantMeta = meta & 7;
            if (upper && world != null) {
                Block self = world.getBlock(x, y, z);
                // The upper half's variant lives only in the block below (vanilla func_149885_e)
                if (world.getBlock(x, y - 1, z) == self) {
                    variantMeta = world.getBlockMetadata(x, y - 1, z);
                }
            }
            props.put("variant", DOUBLE_PLANT_VARIANTS[Math.min(variantMeta & 7, 5)]);
            props.put("half", upper ? "upper" : "lower");
        }
    };

    // ==================== Grass block snow cover ====================

    /**
     * Grass block snow cover dynamic resolver: writes snowy.
     * <p>
     * Reproduces the side-texture switch of 1.7.10 {@code BlockGrass#getIcon(IBlockAccess,...)}:
     * when the block above is {@link Material#snow} (snow layer) or {@link Material#craftedSnow}
     * (snow block), vanilla swaps the side texture from {@code grass_side} to
     * {@code grass_side_snowed}. Here that in-world check is promoted to the blockstate's snowy
     * dynamic property, driving the blockstate JSON to switch from the {@code grass_block} to the
     * {@code grass_block_snow} model (same semantics as 1.13+).
     */
    public static final DynamicPropertyResolver SNOWY = new DynamicPropertyResolver() {
        @Override
        public void resolve(IBlockAccess world, int x, int y, int z, int meta, Map<String, String> props) {
            if (world == null) {
                // No world context (e.g. GUI preview): render as a normal grass block
                props.put("snowy", "false");
                return;
            }
            Material material = world.getBlock(x, y + 1, z).getMaterial();
            props.put("snowy", material != Material.snow && material != Material.craftedSnow ? "false" : "true");
        }
    };
}
