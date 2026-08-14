package decok.dfcdvadstf.catframe.adapter.vanilla.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.state.block.ResidentStateModel.DynamicPropertyResolver;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockWall;
import net.minecraft.init.Blocks;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.Map;

import static net.minecraft.util.Direction.rotateOpposite;

/**
 * Vanilla 方块运行时动态属性解析器集合。
 * <p>
 * 收纳原先散落在 {@code StairsBlockModel} / {@code PaneMultipartRedirectModel} 中的世界内
 * 属性计算逻辑，作为 {@link DynamicPropertyResolver} 提供给 {@link ResidentStateModel}。
 * <ul>
 * <li>{@link #STAIRS} — 楼梯转角形状（facing/half + shape）</li>
 * <li>{@link #PANE} — 玻璃板/铁栏杆连接（north/east/south/west）</li>
 * <li>{@link #REDSTONE_WIRE} — 红石粉连接（north/east/south/west + up_* 爬线）</li>
 * <li>{@link #DOOR} — 门上下两半 meta 合并（facing/half/hinge/open）</li>
 * <li>{@link #DOUBLE_PLANT} — 双植物上半块变体从下方读取（variant/half）</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class VanillaBlockResolvers {

    private VanillaBlockResolvers() {
    }

    // ==================== 楼梯 ====================

    /** facing: 0=east,1=west,2=south,3=north */
    private static final String[] STAIR_FACINGS = { "east", "west", "south", "north" };
    // CW (rotateY): 0(east)→2(south), 2(south)→1(west), 1(west)→3(north),
    // 3(north)→0(east)
    private static final int[] CW = { 2, 3, 1, 0 };
    // 前方偏移（facing 方向），后方取其相反数
    private static final int[][] FRONT_OFFSET = {
            { 1, 0 }, // east
            { -1, 0 }, // west
            { 0, 1 }, // south
            { 0, -1 } // north
    };

    /**
     * 楼梯动态解析器：写入 facing/half/shape。
     * <p>
     * 转角检测对齐 1.12+ {@code BlockStairs#getShape}（移植自原 {@code StairsBlockModel}）。
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

        // 检查后方（facing 的反方向）
        Integer behind = getStairFacing(world, x - fwd[0], y, z - fwd[1], top);
        if (behind != null && (behind & 1) != (facing & 1)) { // 不同轴
            return behind == CW[facing] ? "outer_left" : "outer_right";
        }

        // 检查前方（facing 方向）
        Integer ahead = getStairFacing(world, x + fwd[0], y, z + fwd[1], top);
        if (ahead != null && (ahead & 1) != (facing & 1)) { // 不同轴
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

    // ==================== 玻璃板 / 铁栏杆 ====================

    /**
     * 连接类方块动态解析器：写入 north/east/south/west。
     * <p>
     * 连接判定按自方块类型分派（移植自原 {@code PaneMultipartRedirectModel}）：
     * <ul>
     * <li>{@link BlockPane} 子类（玻璃板/铁栏杆）→ {@link BlockPane#canPaneConnectTo}</li>
     * <li>{@link BlockWall}（1.7.10 石墙非 BlockPane）→
     * {@link BlockWall#canConnectWallTo}</li>
     * <li>{@link BlockFence}（1.7.10 栅栏非 BlockPane）→
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
         * 按自方块类型判定相邻方块是否可连接。
         * Dispatch the connection check by the rendered block's own type.
         */
        private boolean canConnect(Block self, IBlockAccess world, int x, int y, int z, ForgeDirection dir) {
            if (self instanceof BlockWall) {
                // 1.7.10 wall: 连接其他墙 / fence_gate / 不透明正常渲染方块
                return ((BlockWall) self).canConnectWallTo(world, x, y, z);
            }
            if (self instanceof BlockFence) {
                // 1.7.10 fence: 连接其他栅栏 / fence_gate / 不透明正常渲染方块
                return ((BlockFence) self).canConnectFenceTo(world, x, y, z);
            }
            if (!(self instanceof BlockPane))
                return false;
            return ((BlockPane) self).canPaneConnectTo(world, x, y, z, dir);
        }
    };

    // ==================== 红石粉（1.7.10 renderBlockRedstoneWire 连接判定）
    // ====================

    /**
     * 红石粉动态解析器：写入 north/east/south/west + up_north/up_east/up_south/up_west。
     * <p>
     * 完全复刻 1.7.10 {@code RenderBlocks#renderBlockRedstoneWire} 的连接判定：
     * <ul>
     * <li>水平连接：{@code isPowerProviderOrWire(邻, side)}，或邻块非完整方块时
     * 下坡连 {@code isPowerProviderOrWire(邻下, -1)}（-1 只对红石线成立）；</li>
     * <li>上坡：本线上方非完整方块、邻块完整方块、且 {@code isPowerProviderOrWire(邻上, -1)}；</li>
     * <li>爬线面：邻块完整方块且邻块上方严格为红石线（{@code Blocks.redstone_wire}），
     * 与水平连接标志相互独立。</li>
     * </ul>
     * side 方向码（vanilla Direction XZ 平面）：0=south, 1=west, 2=north, 3=east。
     * 中继器/比较器仅当 side 等于其朝向或背面时连接
     * （{@code side == (meta&3) || side == rotateOpposite[meta&3]}）。
     */
    public static final DynamicPropertyResolver REDSTONE_WIRE = new DynamicPropertyResolver() {
        @Override
        public void resolve(IBlockAccess world, int x, int y, int z, int meta, Map<String, String> props) {
            if (world == null) {
                // 无世界上下文（如 GUI 预览）：按孤立点渲染
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

            // 水平连接 + 下坡连接（flag = west, flag1 = east, flag2 = north, flag3 = south）
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

            // 上坡连接：本线上方非完整方块，邻块完整方块，邻块上方可供电/导线
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

            // 爬线面：邻块完整方块且邻块上方严格为红石线（1.7.10 2553/2567/2581/2595 行）
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
         * 1.7.10 {@code BlockRedstoneWire#isPowerProviderOrWire} 复刻：
         * 红石线恒连；中继器/比较器按朝向+背面（meta&3 与 rotateOpposite）；
         * 其余方块走 {@link Block#canConnectRedstone}（默认仅可供电方块，且 side != -1）。
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

        /** 中继器/比较器（powered + unpowered 共 4 种），对应 1.7.10 func_149907_e。 */
        private boolean isDiode(Block block) {
            return block == Blocks.unpowered_repeater || block == Blocks.powered_repeater
                    || block == Blocks.unpowered_comparator || block == Blocks.powered_comparator;
        }
    };

    // ==================== 门 ====================

    /**
     * Door facing lookup: 1.7.10 lower-half meta&3 → modern facing.
     * <p>
     * 门朝向查表：1.7.10 下半 meta&3 → 现代 facing。
     * 对齐 1.8 {@code BlockDoor#getStateFromMeta} 的
     * {@code getHorizontal(meta&3).rotateYCCW()}。
     */
    private static final String[] DOOR_FACINGS = { "east", "south", "west", "north" };

    /**
     * 门动态解析器：写入 facing/half/hinge/open。
     * <p>
     * 1.7.10 的门把完整状态拆在上下两半 meta 中（对齐 {@code BlockDoor#func_150012_g}）：
     * <ul>
     * <li>下半（bit3=0）：bit0-1 = 朝向，bit2 = open</li>
     * <li>上半（bit3=1）：bit0 = hinge（1=right）</li>
     * </ul>
     * 因此渲染任意一半时都必须跨方块读取另一半的 meta 才能拼出完整 blockstate 键。
     * 另一半缺失（如 setblock 摆出的残门）时按默认值兜底：facing=east, open=false, hinge=left。
     */
    public static final DynamicPropertyResolver DOOR = new DynamicPropertyResolver() {
        @Override
        public void resolve(IBlockAccess world, int x, int y, int z, int meta, Map<String, String> props) {
            boolean upper = (meta & 8) != 0;
            // 缺失另一半时的兜底 meta：下半 0（east+closed），上半 8（hinge=left）
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

    // ==================== 双植物 ====================

    /**
     * 双植物变体名查表：0=sunflower, 1=lilac, 2=double_grass, 3=double_fern,
     * 4=rose_bush, 5=peony —— 必须与 blockstate 双植物 JSON 的 variant 键一致
     * （对应 1.7.10 {@code BlockDoublePlant#field_149892_a} 的
     * sunflower/syringa/grass/fern/rose/paeonia，映射到 1.8+ 词汇）。
     */
    private static final String[] DOUBLE_PLANT_VARIANTS = { "sunflower", "lilac", "double_grass", "double_fern",
            "rose_bush", "peony" };

    /**
     * 双植物动态解析器：写入 variant/half。
     * <p>
     * 1.7.10 的 {@code BlockDoublePlant} 把完整状态拆在上下两半 meta 中
     * （对齐 {@code BlockDoublePlant#func_149885_e}）：
     * <ul>
     * <li>下半（bit3=0）：低 3 位 = 变体（0=sunflower, 1=syringa, 2=grass, 3=fern,
     * 4=rose, 5=paeonia，再映射为 blockstate 的 1.8+ 词汇）</li>
     * <li>上半（bit3=1）：低 2 位是 {@code onBlockPlacedBy} 按玩家朝向写入的残值，
     * 真实变体必须从下方方块读取</li>
     * </ul>
     * 因此渲染上半块时必须跨方块读取下方 meta 才能得到真实变体。
     * 下方缺失/非同方块（如 setblock 摆出的残株）时按自身低 3 位兜底。
     */
    public static final DynamicPropertyResolver DOUBLE_PLANT = new DynamicPropertyResolver() {
        @Override
        public void resolve(IBlockAccess world, int x, int y, int z, int meta, Map<String, String> props) {
            boolean upper = (meta & 8) != 0;
            int variantMeta = meta & 7;
            if (upper && world != null) {
                Block self = world.getBlock(x, y, z);
                // 上半块变体只存于下方方块（vanilla func_149885_e）
                if (world.getBlock(x, y - 1, z) == self) {
                    variantMeta = world.getBlockMetadata(x, y - 1, z);
                }
            }
            props.put("variant", DOUBLE_PLANT_VARIANTS[Math.min(variantMeta & 7, 5)]);
            props.put("half", upper ? "upper" : "lower");
        }
    };
}
