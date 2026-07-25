package decok.dfcdvadstf.catframe.compact.vanilla.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.state.block.ResidentStateModel.DynamicPropertyResolver;
import net.minecraft.block.Block;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockStairs;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.Map;

/**
 * Vanilla 方块运行时动态属性解析器集合。
 * <p>
 * 收纳原先散落在 {@code StairsBlockModel} / {@code PaneMultipartRedirectModel} 中的世界内
 * 属性计算逻辑，作为 {@link DynamicPropertyResolver} 提供给 {@link ResidentStateModel}。
 * <ul>
 *   <li>{@link #STAIRS} — 楼梯转角形状（facing/half + shape）</li>
 *   <li>{@link #PANE} — 玻璃板/铁栏杆连接（north/east/south/west）</li>
 *   <li>{@link #DOOR} — 门上下两半 meta 合并（facing/half/hinge/open）</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class VanillaBlockResolvers {

    private VanillaBlockResolvers() {}

    // ==================== 楼梯 ====================

    /** facing: 0=east,1=west,2=south,3=north */
    private static final String[] STAIR_FACINGS = {"east", "west", "south", "north"};
    // CW (rotateY):  0(east)→2(south), 2(south)→1(west), 1(west)→3(north), 3(north)→0(east)
    private static final int[] CW = {2, 3, 1, 0};
    // 前方偏移（facing 方向），后方取其相反数
    private static final int[][] FRONT_OFFSET = {
        {1, 0},  // east
        {-1, 0}, // west
        {0, 1},  // south
        {0, -1}  // north
    };

    /**
     * 楼梯动态解析器：写入 facing/half/shape。
     * <p>转角检测对齐 1.12+ {@code BlockStairs#getShape}（移植自原 {@code StairsBlockModel}）。
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
        if (!(block instanceof BlockStairs)) return null;
        int meta = world.getBlockMetadata(nx, ny, nz);
        if (((meta & 4) != 0) != top) return null;
        return meta & 3;
    }

    // ==================== 玻璃板 / 铁栏杆 ====================

    /**
     * 连接类方块动态解析器：写入 north/east/south/west。
     * <p>连接判定用 {@link BlockPane#canPaneConnectTo}（移植自原 {@code PaneMultipartRedirectModel}）。
     * 从渲染坐标处的方块取得 {@link BlockPane} 实例。
     */
    public static final DynamicPropertyResolver PANE = new DynamicPropertyResolver() {
        @Override
        public void resolve(IBlockAccess world, int x, int y, int z, int meta, Map<String, String> props) {
            Block self = (world != null) ? world.getBlock(x, y, z) : null;
            if (!(self instanceof BlockPane)) {
                props.put("north", "false");
                props.put("east", "false");
                props.put("south", "false");
                props.put("west", "false");
                return;
            }
            BlockPane pane = (BlockPane) self;
            props.put("north", pane.canPaneConnectTo(world, x, y, z - 1, ForgeDirection.NORTH) ? "true" : "false");
            props.put("east",  pane.canPaneConnectTo(world, x + 1, y, z, ForgeDirection.EAST)  ? "true" : "false");
            props.put("south", pane.canPaneConnectTo(world, x, y, z + 1, ForgeDirection.SOUTH) ? "true" : "false");
            props.put("west",  pane.canPaneConnectTo(world, x - 1, y, z, ForgeDirection.WEST)  ? "true" : "false");
        }
    };

    // ==================== 门 ====================

    /**
     * Door facing lookup: 1.7.10 lower-half meta&3 → modern facing.
     * <p>门朝向查表：1.7.10 下半 meta&3 → 现代 facing。
     * 对齐 1.8 {@code BlockDoor#getStateFromMeta} 的 {@code getHorizontal(meta&3).rotateYCCW()}。
     */
    private static final String[] DOOR_FACINGS = {"east", "south", "west", "north"};

    /**
     * 门动态解析器：写入 facing/half/hinge/open。
     * <p>
     * 1.7.10 的门把完整状态拆在上下两半 meta 中（对齐 {@code BlockDoor#func_150012_g}）：
     * <ul>
     *   <li>下半（bit3=0）：bit0-1 = 朝向，bit2 = open</li>
     *   <li>上半（bit3=1）：bit0 = hinge（1=right）</li>
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
}
