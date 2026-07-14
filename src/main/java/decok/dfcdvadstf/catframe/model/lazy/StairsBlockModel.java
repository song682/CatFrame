package decok.dfcdvadstf.catframe.model.lazy;

import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.RenderDispatcher;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.IMetadataMapper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.world.IBlockAccess;

import java.util.HashMap;
import java.util.Map;

/**
 * 懒烘焙楼梯模型。在运行时根据邻居方块动态计算转角形状（shape），
 * 与 metadata mapper 提供的 facing/half 组合成完整 variant key
 * 进行 blockstate 调度。
 *
 * <p>Shape 取值为 straight / inner_left / inner_right / outer_left / outer_right，
 * 检测逻辑对齐高版本 Minecraft BlockStairs 的转角算法。
 */
public class StairsBlockModel implements BlockStateModel {

    private final BlockstateJson bs;
    private final IMetadataMapper mapper;

    // CW (rotateY):  0(east)→2(south), 2(south)→1(west), 1(west)→3(north), 3(north)→0(east)
    private static final int[] CW  = {2, 3, 1, 0};
    // CCW (rotateYCCW): 0(east)→3(north), 3(north)→1(west), 1(west)→2(south), 2(south)→0(east)
    private static final int[] CCW = {3, 2, 0, 1};

    // 前方偏移（facing 方向），后方取其相反数
    // facing: 0=east(+x), 1=west(-x), 2=south(+z), 3=north(-z)
    private static final int[][] FRONT_OFFSET = {
        { 1, 0}, // east
        {-1, 0}, // west
        { 0, 1}, // south
        { 0, -1} // north
    };

    public StairsBlockModel(BlockstateJson bs, IMetadataMapper mapper) {
        this.bs = bs;
        this.mapper = mapper;
    }

    @Override
    public BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, int metadata) {
        if (bs == null || bs.variants == null) return BlockStateModelPart.empty();

        int facing = metadata & 3;
        boolean top = (metadata & 4) != 0;

        // 基础属性 + 运行时 shape
        Map<String, String> props;
        if (mapper != null) {
            props = mapper.map(metadata);
        } else {
            // metadata_map.json 未提供 stairs 映射时自建 facing/half
            String[] FACING_NAMES = {"east", "west", "south", "north"};
            props = new HashMap<>();
            props.put("facing", FACING_NAMES[facing]);
            props.put("half", top ? "top" : "bottom");
        }
        props.put("shape", computeShape(world, x, y, z, facing, top));

        String variantKey = RenderDispatcher.buildVariantKey(props);
        BlockstateJson.VariantEntry entry = bs.variants.get(variantKey);
        if (entry == null) entry = bs.variants.get("normal");
        if (entry == null) return BlockStateModelPart.empty();

        int seed = x * 3129871 ^ z * 116129781 ^ y;
        BlockstateJson.Variant variant = entry.getVariant(seed);
        if (variant == null || variant.model == null) return BlockStateModelPart.empty();

        String cacheKey = BakedModelCache.buildKey(variant.model, variant.x, variant.y);
        BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
        return part != null ? part : BlockStateModelPart.empty();
    }

    /**
     * 计算转角形状。算法对齐 1.12+ BlockStairs#getShape：
     * 1) 检查后方邻居——同半层、不同轴 → outer corner
     * 2) 检查前方邻居——同半层、不同轴 → inner corner
     * 3) 否则 → straight
     */
    private String computeShape(IBlockAccess world, int x, int y, int z, int facing, boolean top) {
        int[] fwd = FRONT_OFFSET[facing];

        // 检查后方（facing 的反方向）
        int[] behind = getStairData(world, x - fwd[0], y, z - fwd[1], top);
        if (behind != null) {
            int bf = behind[0];
            if ((bf & 1) != (facing & 1)) { // 不同轴
                return bf == cw(facing) ? "outer_left" : "outer_right";
            }
        }

        // 检查前方（facing 方向）
        int[] ahead = getStairData(world, x + fwd[0], y, z + fwd[1], top);
        if (ahead != null) {
            int af = ahead[0];
            if ((af & 1) != (facing & 1)) { // 不同轴
                return af == cw(facing) ? "inner_left" : "inner_right";
            }
        }

        return "straight";
    }

    /**
     * 查询邻居是否为同半层楼梯。返回 [facing] 数组，或 null。
     */
    private static int[] getStairData(IBlockAccess world, int nx, int ny, int nz, boolean top) {
        Block block = world.getBlock(nx, ny, nz);
        if (!(block instanceof BlockStairs)) return null;
        int meta = world.getBlockMetadata(nx, ny, nz);
        if (((meta & 4) != 0) != top) return null;
        return new int[]{meta & 3};
    }

    private static int cw(int f)  { return CW[f]; }
    private static int ccw(int f) { return CCW[f]; }
}
