package decok.dfcdvadstf.catframe.adapter.vanilla.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.state.BlockState;
import decok.dfcdvadstf.catframe.model.state.RenderPredicate;
import decok.dfcdvadstf.catframe.model.state.StateUpdateContext;
import decok.dfcdvadstf.catframe.model.state.StateUpdater;
import decok.dfcdvadstf.catframe.model.state.property.MappedMetaProperty;
import decok.dfcdvadstf.catframe.model.state.property.MetaBlockProperty;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockWall;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraftforge.common.util.ForgeDirection;

import static net.minecraft.util.Direction.rotateOpposite;

/**
 * Typed state updaters for vanilla blocks - the render-time second phase aligned with modern
 * vanilla's {@code BlockBehaviour#updateShape}.
 * <p>
 * Each updater receives the <b>base state</b> (decoded from the resident meta table) and a
 * {@link StateUpdateContext}, reads the values it needs from that state via typed
 * {@link BlockState#getValue}, and returns the resolved state through O(1)
 * {@link BlockState#setValue} jumps. Properties that already decode from meta (a stair's
 * {@code facing}/{@code half}, a door's {@code half}) are read from the base state - never
 * re-decoded from raw metadata. World-dependent properties (corner {@code shape}, connection
 * flags, cross-half door {@code facing}/{@code hinge}/{@code open}) are computed here.
 * <p>
 * All properties live in the string domain ({@link VanillaStateDefinitions} declares
 * {@code Property<String>} / {@code MappedMetaProperty<String>}), so flag values are the literals
 * {@code "true"} / {@code "false"} and enums are their serialized names. Meta decoding of a
 * neighbouring block always goes through the declared property instance, keeping a single decode
 * implementation per property - accessor order and clamping are decided by that property alone.
 * <ul>
 * <li>{@link #STAIRS} - stair corner shape ({@code shape}; facing/half read typed from the base state)</li>
 * <li>{@link #PANE} - glass pane / iron bars / wall / fence connections (north/east/south/west)</li>
 * <li>{@link #REDSTONE_WIRE} - redstone wire connections (north/east/south/west + up_* climbing faces)</li>
 * <li>{@link #DOOR} - door cross-half facing/hinge/open (half read typed from the base state)</li>
 * <li>{@link #DOUBLE_PLANT} - double plant upper-half variant read from the block below;
 *     {@link #DOUBLE_PLANT_PREDICATE} suppresses the phantom upper half</li>
 * <li>{@link #SNOWY} - grass block snow cover (snowy, block above is snow / snow block)</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class VanillaStateUpdaters {

    private VanillaStateUpdaters() {
    }

    /** String-domain flag literals written into the {@code Property<String>} connection flags. */
    private static final String TRUE = "true";
    private static final String FALSE = "false";

    // ==================== Stairs ====================

    /** Stair shape literals; the order must match {@link VanillaStateDefinitions#STAIR_SHAPE}. */
    private static final String SHAPE_STRAIGHT = "straight";
    private static final String SHAPE_INNER_LEFT = "inner_left";
    private static final String SHAPE_INNER_RIGHT = "inner_right";
    private static final String SHAPE_OUTER_LEFT = "outer_left";
    private static final String SHAPE_OUTER_RIGHT = "outer_right";

    // CW (rotateY) over the STAIR_FACING_META order {east, west, south, north}:
    // 0(east)→2(south), 2(south)→1(west), 1(west)→3(north), 3(north)→0(east)
    private static final int[] CW = { 2, 3, 1, 0 };
    // Front offset (along facing); the rear offset is its negation
    private static final int[][] FRONT_OFFSET = {
            { 1, 0 }, // east
            { -1, 0 }, // west
            { 0, 1 }, // south
            { 0, -1 } // north
    };

    /**
     * Stairs updater: resolves the corner {@code shape} from the world.
     * <p>
     * {@code facing} and {@code half} are read typed from the base state (they decode from meta).
     * Corner detection mirrors 1.7.10 {@code BlockStairs.func_150145_f} (outer corner, high side)
     * and {@code BlockStairs.func_150144_g} (inner corner, low side), including the blocker check.
     * With no world context the base state ({@code shape=straight}) is returned unchanged.
     */
    public static final StateUpdater STAIRS = (ctx, state) -> {
        if (ctx.world == null) return state;
        MetaBlockProperty<String> facingProp = VanillaStateDefinitions.STAIR_FACING_META;
        int facing = facingProp.getInternalIndex(state.getValue(facingProp));
        int half = halfIndex(state);
        return state.setValue(VanillaStateDefinitions.STAIR_SHAPE, computeShape(ctx, facing, half));
    };

    /**
     * facing is the direction the stair rises toward (facing=east raises the +X half).
     * The high neighbour (along facing) decides outer corners; the low neighbour (opposite
     * facing) decides inner corners. A perpendicular neighbour forms a corner unless a stair
     * with the same facing and half sits on the side cell (blocker check).
     * <p>
     * Axis grouping follows the {@link VanillaStateDefinitions#STAIR_FACING_META} order
     * {east, west, south, north}: the two facings on one axis share the high bit (0|1 = X axis,
     * 2|3 = Z axis), so perpendicularity is {@code (a >> 1) != (b >> 1)} - parity is NOT the axis
     * bit here. This mirrors vanilla 1.7.10 {@code func_150145_f}/{@code func_150144_g}, which
     * accept a corner neighbour only from the two facings on the other axis.
     *
     * @param facing decoded facing index
     * @param half   decoded half index (0 = bottom, 1 = top)
     */
    static String computeShape(StateUpdateContext ctx, int facing, int half) {
        int[] fwd = FRONT_OFFSET[facing];

        // Outer corner: high side (along facing)
        Integer high = neighbourStairFacing(ctx, ctx.x + fwd[0], ctx.z + fwd[1], half);
        if (high != null && (high >> 1) != (facing >> 1)) {
            int[] d = FRONT_OFFSET[high];
            if (!isCornerBlocked(ctx, ctx.x - d[0], ctx.z - d[1], facing, half)) {
                return high == CW[facing] ? SHAPE_OUTER_RIGHT : SHAPE_OUTER_LEFT;
            }
        }

        // Inner corner: low side (opposite facing)
        Integer low = neighbourStairFacing(ctx, ctx.x - fwd[0], ctx.z - fwd[1], half);
        if (low != null && (low >> 1) != (facing >> 1)) {
            int[] d = FRONT_OFFSET[low];
            if (!isCornerBlocked(ctx, ctx.x + d[0], ctx.z + d[1], facing, half)) {
                return low == CW[facing] ? SHAPE_INNER_RIGHT : SHAPE_INNER_LEFT;
            }
        }

        return SHAPE_STRAIGHT;
    }

    /** A stair with the same facing and half on the side cell cancels the corner. */
    private static boolean isCornerBlocked(StateUpdateContext ctx, int x, int z, int facing, int half) {
        Integer f = neighbourStairFacing(ctx, x, z, half);
        return f != null && f.intValue() == facing;
    }

    /**
     * Same-half neighbour stair lookup at an absolute horizontal position. Returns the neighbour
     * stair's facing index, or {@code null} when the cell is not a stair or holds a stair of the
     * other half. Both fields are decoded through the declared meta properties.
     */
    private static Integer neighbourStairFacing(StateUpdateContext ctx, int x, int z, int half) {
        Block block = ctx.world.getBlock(x, ctx.y, z);
        if (!(block instanceof BlockStairs)) return null;
        int meta = ctx.world.getBlockMetadata(x, ctx.y, z);
        MetaBlockProperty<String> halfProp = VanillaStateDefinitions.HALF_BOTTOM_TOP_B2;
        if (halfProp.getInternalIndex(halfProp.decode(meta)) != half) return null;
        MetaBlockProperty<String> facingProp = VanillaStateDefinitions.STAIR_FACING_META;
        return facingProp.getInternalIndex(facingProp.decode(meta));
    }

    /** Reads the stair half from the base state (meta bit 2) as its internal index. */
    private static int halfIndex(BlockState state) {
        MetaBlockProperty<String> halfProp = VanillaStateDefinitions.HALF_BOTTOM_TOP_B2;
        return halfProp.getInternalIndex(state.getValue(halfProp));
    }

    // ==================== Glass panes / iron bars / walls / fences ====================

    /**
     * Connection-block updater: writes north/east/south/west.
     * <p>
     * The connection check is dispatched by the rendered block's own type:
     * <ul>
     * <li>{@link BlockPane} subclasses (glass panes / iron bars) → {@link BlockPane#canPaneConnectTo}</li>
     * <li>{@link BlockWall} (1.7.10 stone walls are not BlockPane) → {@link BlockWall#canConnectWallTo}</li>
     * <li>{@link BlockFence} (1.7.10 fences are not BlockPane) → {@link BlockFence#canConnectFenceTo}</li>
     * </ul>
     * With no world context the base state (all connections {@code false}) is returned unchanged.
     */
    public static final StateUpdater PANE = (ctx, state) -> {
        if (ctx.world == null) return state;
        Block self = ctx.world.getBlock(ctx.x, ctx.y, ctx.z);
        return state
                .setValue(VanillaStateDefinitions.PANE_NORTH,
                        flag(canConnect(self, ctx, ctx.x, ctx.y, ctx.z - 1, ForgeDirection.NORTH)))
                .setValue(VanillaStateDefinitions.PANE_EAST,
                        flag(canConnect(self, ctx, ctx.x + 1, ctx.y, ctx.z, ForgeDirection.EAST)))
                .setValue(VanillaStateDefinitions.PANE_SOUTH,
                        flag(canConnect(self, ctx, ctx.x, ctx.y, ctx.z + 1, ForgeDirection.SOUTH)))
                .setValue(VanillaStateDefinitions.PANE_WEST,
                        flag(canConnect(self, ctx, ctx.x - 1, ctx.y, ctx.z, ForgeDirection.WEST)));
    };

    /** Dispatch the connection check by the rendered block's own type. */
    private static boolean canConnect(Block self, StateUpdateContext ctx, int x, int y, int z,
                                      ForgeDirection dir) {
        if (self instanceof BlockWall) {
            return ((BlockWall) self).canConnectWallTo(ctx.world, x, y, z);
        }
        if (self instanceof BlockFence) {
            return ((BlockFence) self).canConnectFenceTo(ctx.world, x, y, z);
        }
        if (!(self instanceof BlockPane))
            return false;
        return ((BlockPane) self).canPaneConnectTo(ctx.world, x, y, z, dir);
    }

    /** Maps a boolean to the string-domain flag literal of {@code Property<String>} flags. */
    private static String flag(boolean value) {
        return value ? TRUE : FALSE;
    }

    // ==================== Redstone wire (1.7.10 renderBlockRedstoneWire connection logic) ====================

    /**
     * Redstone wire updater: writes north/east/south/west + up_north/up_east/up_south/up_west.
     * <p>
     * Faithfully reproduces the connection logic of 1.7.10 {@code RenderBlocks#renderBlockRedstoneWire}:
     * <ul>
     * <li>horizontal: {@code isPowerProviderOrWire(neighbour, side)}, or, when the neighbour is not a
     * full cube, a downward connection via {@code isPowerProviderOrWire(neighbour below, -1)};</li>
     * <li>upward: the block above this wire is not a full cube, the neighbour is a full cube, and
     * {@code isPowerProviderOrWire(neighbour above, -1)};</li>
     * <li>climbing faces: the neighbour is a full cube and the block above it is exactly redstone wire;
     * independent of the horizontal connection flags.</li>
     * </ul>
     * side direction codes (vanilla Direction XZ plane): 0=south, 1=west, 2=north, 3=east.
     * Repeaters/comparators connect only when side equals their facing or the opposite side.
     * With no world context the base state (all connections {@code false}) is returned unchanged.
     */
    public static final StateUpdater REDSTONE_WIRE = (ctx, state) -> {
        if (ctx.world == null) return state;
        int x = ctx.x, y = ctx.y, z = ctx.z;

        // Horizontal + downward connections
        boolean west = isPowerProviderOrWire(ctx, x - 1, y, z, 1)
                || !ctx.world.getBlock(x - 1, y, z).isNormalCube()
                        && isPowerProviderOrWire(ctx, x - 1, y - 1, z, -1);
        boolean east = isPowerProviderOrWire(ctx, x + 1, y, z, 3)
                || !ctx.world.getBlock(x + 1, y, z).isNormalCube()
                        && isPowerProviderOrWire(ctx, x + 1, y - 1, z, -1);
        boolean north = isPowerProviderOrWire(ctx, x, y, z - 1, 2)
                || !ctx.world.getBlock(x, y, z - 1).isNormalCube()
                        && isPowerProviderOrWire(ctx, x, y - 1, z - 1, -1);
        boolean south = isPowerProviderOrWire(ctx, x, y, z + 1, 0)
                || !ctx.world.getBlock(x, y, z + 1).isNormalCube()
                        && isPowerProviderOrWire(ctx, x, y - 1, z + 1, -1);

        // Upward connections: the block above this wire is not a full cube, the neighbour is a full
        // cube, and the block above the neighbour can supply power / is wire
        boolean upWireAbove = !ctx.world.getBlock(x, y + 1, z).isNormalCube();
        if (upWireAbove) {
            if (ctx.world.getBlock(x - 1, y, z).isNormalCube()
                    && isPowerProviderOrWire(ctx, x - 1, y + 1, z, -1))
                west = true;
            if (ctx.world.getBlock(x + 1, y, z).isNormalCube()
                    && isPowerProviderOrWire(ctx, x + 1, y + 1, z, -1))
                east = true;
            if (ctx.world.getBlock(x, y, z - 1).isNormalCube()
                    && isPowerProviderOrWire(ctx, x, y + 1, z - 1, -1))
                north = true;
            if (ctx.world.getBlock(x, y, z + 1).isNormalCube()
                    && isPowerProviderOrWire(ctx, x, y + 1, z + 1, -1))
                south = true;
        }

        // Climbing faces: the neighbour is a full cube and the block above it is exactly redstone wire
        boolean upNorth = upWireAbove && ctx.world.getBlock(x, y, z - 1).isNormalCube()
                && ctx.world.getBlock(x, y + 1, z - 1) == Blocks.redstone_wire;
        boolean upEast = upWireAbove && ctx.world.getBlock(x + 1, y, z).isNormalCube()
                && ctx.world.getBlock(x + 1, y + 1, z) == Blocks.redstone_wire;
        boolean upSouth = upWireAbove && ctx.world.getBlock(x, y, z + 1).isNormalCube()
                && ctx.world.getBlock(x, y + 1, z + 1) == Blocks.redstone_wire;
        boolean upWest = upWireAbove && ctx.world.getBlock(x - 1, y, z).isNormalCube()
                && ctx.world.getBlock(x - 1, y + 1, z) == Blocks.redstone_wire;

        return state
                .setValue(VanillaStateDefinitions.PANE_NORTH, flag(north))
                .setValue(VanillaStateDefinitions.PANE_EAST, flag(east))
                .setValue(VanillaStateDefinitions.PANE_SOUTH, flag(south))
                .setValue(VanillaStateDefinitions.PANE_WEST, flag(west))
                .setValue(VanillaStateDefinitions.REDSTONE_UP_NORTH, flag(upNorth))
                .setValue(VanillaStateDefinitions.REDSTONE_UP_EAST, flag(upEast))
                .setValue(VanillaStateDefinitions.REDSTONE_UP_SOUTH, flag(upSouth))
                .setValue(VanillaStateDefinitions.REDSTONE_UP_WEST, flag(upWest));
    };

    /**
     * Reproduction of 1.7.10 {@code BlockRedstoneWire#isPowerProviderOrWire}: redstone wire always
     * connects; repeaters/comparators connect by facing + opposite side; all other blocks go through
     * {@link Block#canConnectRedstone}.
     */
    private static boolean isPowerProviderOrWire(StateUpdateContext ctx, int x, int y, int z, int side) {
        Block block = ctx.world.getBlock(x, y, z);
        if (block == Blocks.redstone_wire)
            return true;
        if (isDiode(block)) {
            int meta = ctx.world.getBlockMetadata(x, y, z);
            int facing = meta & 3;
            return side == facing || side == rotateOpposite[facing];
        }
        return block.canConnectRedstone(ctx.world, x, y, z, side);
    }

    /** Repeaters/comparators (4 in total: powered + unpowered), corresponding to 1.7.10 func_149907_e. */
    private static boolean isDiode(Block block) {
        return block == Blocks.unpowered_repeater || block == Blocks.powered_repeater
                || block == Blocks.unpowered_comparator || block == Blocks.powered_comparator;
    }

    // ==================== Doors ====================

    // Cross-half decoders: each one maps the other half's metadata through the value list of the
    // property that declares it, so the ordering / vocabulary has a single source of truth.
    private static final MappedMetaProperty<String> DOOR_FACING_DECODE = MappedMetaProperty.of(
            "door_facing_meta", VanillaStateDefinitions.DOOR_FACING.getValues(), 3, 0, false);
    private static final MappedMetaProperty<String> DOOR_HINGE_DECODE = MappedMetaProperty.of(
            "door_hinge_meta", VanillaStateDefinitions.DOOR_HINGE.getValues(), 1, 0, false);
    private static final MappedMetaProperty<String> DOOR_OPEN_DECODE = MappedMetaProperty.of(
            "door_open_meta", VanillaStateDefinitions.OPEN.getValues(), 4, 2, false);

    /**
     * Door updater: resolves facing/hinge/open from the door's two halves.
     * <p>
     * A 1.7.10 door splits its full state across the meta of its upper and lower halves (aligned
     * with {@code BlockDoor#func_150012_g}): lower half (bit3=0) bit0-1 = facing, bit2 = open;
     * upper half (bit3=1) bit0 = hinge (1=right). {@code half} is read typed from the base state;
     * rendering either half reads the other half's meta across blocks to assemble the full state.
     * When the other half is missing, fall back to the base state's defaults: facing=east,
     * open=false, hinge=left.
     */
    public static final StateUpdater DOOR = (ctx, state) -> {
        boolean upper = isUpperHalf(state);
        // Fallback meta when the other half is missing: lower 0 (east+closed), upper 8 (hinge=left)
        int lowerMeta = upper ? 0 : ctx.meta;
        int upperMeta = upper ? ctx.meta : 8;
        if (ctx.world != null) {
            Block self = ctx.world.getBlock(ctx.x, ctx.y, ctx.z);
            if (upper) {
                if (ctx.world.getBlock(ctx.x, ctx.y - 1, ctx.z) == self) {
                    lowerMeta = ctx.world.getBlockMetadata(ctx.x, ctx.y - 1, ctx.z);
                }
            } else {
                if (ctx.world.getBlock(ctx.x, ctx.y + 1, ctx.z) == self) {
                    upperMeta = ctx.world.getBlockMetadata(ctx.x, ctx.y + 1, ctx.z);
                }
            }
        }
        return state
                .setValue(VanillaStateDefinitions.DOOR_FACING, DOOR_FACING_DECODE.decode(lowerMeta))
                .setValue(VanillaStateDefinitions.OPEN, DOOR_OPEN_DECODE.decode(lowerMeta))
                .setValue(VanillaStateDefinitions.DOOR_HINGE, DOOR_HINGE_DECODE.decode(upperMeta));
    };

    // ==================== Double plants ====================

    /**
     * Double plant updater: resolves {@code variant}.
     * <p>
     * 1.7.10's {@code BlockDoublePlant} splits its state across halves (aligned with
     * {@code BlockDoublePlant#func_149885_e}): the lower half's low 3 bits are the variant; the
     * upper half's low bits are a facing residue written by {@code onBlockPlacedBy}, so the real
     * variant must be read from the block below. {@code half} is read typed from the base state.
     * When the block below is missing / different, fall back to this half's own low 3 bits, decoded
     * through the declared {@link VanillaStateDefinitions#DOUBLE_PLANT_VARIANT_META} (single decode
     * implementation, including the clamp to the six existing variants).
     */
    public static final StateUpdater DOUBLE_PLANT = (ctx, state) -> {
        boolean upper = isUpperHalf(state);
        int variantMeta = ctx.meta & 7;
        if (upper && ctx.world != null) {
            Block self = ctx.world.getBlock(ctx.x, ctx.y, ctx.z);
            if (ctx.world.getBlock(ctx.x, ctx.y - 1, ctx.z) == self) {
                variantMeta = ctx.world.getBlockMetadata(ctx.x, ctx.y - 1, ctx.z);
            }
        }
        return state.setValue(VanillaStateDefinitions.DOUBLE_PLANT_VARIANT,
                VanillaStateDefinitions.DOUBLE_PLANT_VARIANT_META.decode(variantMeta));
    };

    /**
     * Vanilla parity ({@code RenderBlocks#renderBlockDoublePlant}): the upper half renders only
     * while the block below is the same double plant - vanilla returns false and emits nothing
     * otherwise. Deliberately asymmetric, matching vanilla: the lower half has no such check, so a
     * lower half whose upper half was just broken keeps rendering like vanilla until the server
     * confirms the removal.
     */
    public static final RenderPredicate DOUBLE_PLANT_PREDICATE = (ctx, state) -> {
        if (ctx.world == null) return true;
        if (!isUpperHalf(state)) return true;
        Block self = ctx.world.getBlock(ctx.x, ctx.y, ctx.z);
        return ctx.world.getBlock(ctx.x, ctx.y - 1, ctx.z) == self;
    };

    /** Reads the lower/upper half from the base state (meta bit 3). */
    private static boolean isUpperHalf(BlockState state) {
        return "upper".equals(state.getValue(VanillaStateDefinitions.HALF_LOWER_UPPER_B3));
    }

    // ==================== Grass block snow cover ====================

    /**
     * Grass block snow cover updater: writes snowy.
     * <p>
     * Reproduces the side-texture switch of 1.7.10 {@code BlockGrass#getIcon(IBlockAccess,...)}:
     * when the block above is {@link Material#snow} (snow layer) or {@link Material#craftedSnow}
     * (snow block), vanilla swaps the side texture to {@code grass_side_snowed}. Here that in-world
     * check drives the blockstate's snowy property, switching from {@code grass_block} to
     * {@code grass_block_snow} (same semantics as 1.13+). With no world context the base state
     * (snowy=false) is returned unchanged.
     */
    public static final StateUpdater SNOWY = (ctx, state) -> {
        if (ctx.world == null) return state;
        Material material = ctx.world.getBlock(ctx.x, ctx.y + 1, ctx.z).getMaterial();
        boolean snowy = material == Material.snow || material == Material.craftedSnow;
        return state.setValue(VanillaStateDefinitions.SNOWY, flag(snowy));
    };
}
