package decok.dfcdvadstf.catframe.model.render.extension.ao.light;

import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.RenderContext;
import net.minecraft.block.Block;
import decok.dfcdvadstf.catframe.core.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一方块模型光照计算器 — 对齐 26.1.2 {@code BlockModelLighter}。
 *
 * <p>提供两条光照路径：
 * <ul>
 *   <li><b>AO 路径</b> ({@link #prepareQuadAmbientOcclusion})：逐顶点环境光遮蔽 + 平滑光照混合</li>
 *   <li><b>Flat 路径</b> ({@link #prepareQuadFlat})：单一亮度 + 方向阴影</li>
 * </ul>
 *
 * <p>核心设计：
 * <ol>
 *   <li>{@link AdjacencyInfo} — 每个面的 4 角方向 + 4 组顶点权重索引（SizeInfo）</li>
 *   <li>{@link AmbientVertexRemap} — quad 顶点到 AO 计算结果的映射</li>
 *   <li>{@link SizeInfo} — 12 个尺寸分量（6 面 + 6 翻转），用于部分面加权</li>
 *   <li>{@link Cache} — LRU 缓存（亮度 + shade），容量 100</li>
 * </ol>
 *
 * <p>输出写入 {@link RenderContext} 的 aoBrightness[4] 和 aoColorMul[4]：
 * <ul>
 *   <li>aoBrightness[v] = 平滑混合后的 packed 光照坐标</li>
 *   <li>aoColorMul[v] = AO shade × CardinalLighting（已含方向光照）</li>
 * </ul>
 */
public final class BlockModelLighter {

    /** LRU 缓存容量。 */
    private static final int CACHE_SIZE = 100;

    /** ThreadLocal 实例，每个渲染线程一个（对齐 26.1.2 ThreadLocal<Cache> 模式）。 */
    private static final ThreadLocal<BlockModelLighter> THREAD_LOCAL =
            ThreadLocal.withInitial(BlockModelLighter::new);

    private final Cache cache = new Cache(CACHE_SIZE);

    private boolean faceCubic;
    private boolean facePartial;
    private final float[] faceShape = new float[SizeInfo.COUNT];

    public static BlockModelLighter get() {
        return THREAD_LOCAL.get();
    }

    // ==================== 公共 API ====================

    /**
     * AO 路径：计算逐顶点环境光遮蔽 + 平滑光照。
     *
     * <p>对齐 26.1.2 {@code prepareQuadAmbientOcclusion}：
     * <ol>
     *   <li>面形状分析（faceCubic / facePartial）</li>
     *   <li>4 角 + 4 边 + 1 中心 = 9 点亮度/shade 采样</li>
     *   <li>边角回退（双不透明时复用边 shade）</li>
     *   <li>smoothBlend 平滑混合</li>
     *   <li>部分面双线性加权（smoothWeightedBlend）</li>
     *   <li>CardinalLighting 方向光照乘入 aoColorMul</li>
     * </ol>
     *
     * @param world       世界
     * @param bx, by, bz 方块坐标
     * @param block       方块实例
     * @param quad        待渲染 quad
     * @param ctx         输出目标（aoBrightness / aoColorMul）
     *
     * <p>注意：aoColorMul 仅包含 AO shade（不含 CardinalLighting），
     * 方向光照由 {@link RenderContext#shade} 统一承载（由管线设置为 CardinalLighting.byFace）。
     */
    public void prepareQuadAmbientOcclusion(
            IBlockAccess world, int bx, int by, int bz,
            Block block, BakedQuad quad,
            RenderContext ctx) {

        prepareQuadShape(world, bx, by, bz, block, quad, true);

        Direction direction = quad.face;
        if (direction == null) return;

        // 满块面：基准位置 = 面外侧；非满块面：基准位置 = 方块自身
        int baseX = faceCubic ? bx + direction.getStepX() : bx;
        int baseY = faceCubic ? by + direction.getStepY() : by;
        int baseZ = faceCubic ? bz + direction.getStepZ() : bz;

        AdjacencyInfo info = AdjacencyInfo.fromFacing(direction);

        // --- 4 角采样 ---
        int[] cx = new int[4], cy = new int[4], cz = new int[4];
        int[] light = new int[4];
        float[] shade = new float[4];

        for (int i = 0; i < 4; i++) {
            cx[i] = baseX + info.corners[i].getStepX();
            cy[i] = baseY + info.corners[i].getStepY();
            cz[i] = baseZ + info.corners[i].getStepZ();
            Block cornerBlock = world.getBlock(cx[i], cy[i], cz[i]);
            light[i] = cache.getBrightness(cornerBlock, world, cx[i], cy[i], cz[i]);
            shade[i] = cache.getShadeBrightness(cornerBlock, world, cx[i], cy[i], cz[i]);
        }

        // --- 4 个面外侧角（用于边角回退判定） ---
        boolean[] translucent = new boolean[4];
        for (int i = 0; i < 4; i++) {
            int ox = cx[i] + direction.getStepX();
            int oy = cy[i] + direction.getStepY();
            int oz = cz[i] + direction.getStepZ();
            Block outerBlock = world.getBlock(ox, oy, oz);
            translucent[i] = !outerBlock.isOpaqueCube();
        }

        // --- 边角 shade/light（带透明回退） ---
        float shadeCorner02, shadeCorner03, shadeCorner12, shadeCorner13;
        int lightCorner02, lightCorner03, lightCorner12, lightCorner13;

        // corner 0-2
        if (!translucent[2] && !translucent[0]) {
            shadeCorner02 = shade[0]; lightCorner02 = light[0];
        } else {
            int dx = baseX + info.corners[0].getStepX() + info.corners[2].getStepX();
            int dy = baseY + info.corners[0].getStepY() + info.corners[2].getStepY();
            int dz = baseZ + info.corners[0].getStepZ() + info.corners[2].getStepZ();
            Block b02 = world.getBlock(dx, dy, dz);
            shadeCorner02 = cache.getShadeBrightness(b02, world, dx, dy, dz);
            lightCorner02 = cache.getBrightness(b02, world, dx, dy, dz);
        }
        // corner 0-3
        if (!translucent[3] && !translucent[0]) {
            shadeCorner03 = shade[0]; lightCorner03 = light[0];
        } else {
            int dx = baseX + info.corners[0].getStepX() + info.corners[3].getStepX();
            int dy = baseY + info.corners[0].getStepY() + info.corners[3].getStepY();
            int dz = baseZ + info.corners[0].getStepZ() + info.corners[3].getStepZ();
            Block b03 = world.getBlock(dx, dy, dz);
            shadeCorner03 = cache.getShadeBrightness(b03, world, dx, dy, dz);
            lightCorner03 = cache.getBrightness(b03, world, dx, dy, dz);
        }
        // corner 1-2
        if (!translucent[2] && !translucent[1]) {
            shadeCorner12 = shade[0]; lightCorner12 = light[0];
        } else {
            int dx = baseX + info.corners[1].getStepX() + info.corners[2].getStepX();
            int dy = baseY + info.corners[1].getStepY() + info.corners[2].getStepY();
            int dz = baseZ + info.corners[1].getStepZ() + info.corners[2].getStepZ();
            Block b12 = world.getBlock(dx, dy, dz);
            shadeCorner12 = cache.getShadeBrightness(b12, world, dx, dy, dz);
            lightCorner12 = cache.getBrightness(b12, world, dx, dy, dz);
        }
        // corner 1-3
        if (!translucent[3] && !translucent[1]) {
            shadeCorner13 = shade[0]; lightCorner13 = light[0];
        } else {
            int dx = baseX + info.corners[1].getStepX() + info.corners[3].getStepX();
            int dy = baseY + info.corners[1].getStepY() + info.corners[3].getStepY();
            int dz = baseZ + info.corners[1].getStepZ() + info.corners[3].getStepZ();
            Block b13 = world.getBlock(dx, dy, dz);
            shadeCorner13 = cache.getShadeBrightness(b13, world, dx, dy, dz);
            lightCorner13 = cache.getBrightness(b13, world, dx, dy, dz);
        }

        // --- 中心亮度 ---
        int lightCenter = cache.getBrightness(block, world, bx, by, bz);
        int outX = bx + direction.getStepX();
        int outY = by + direction.getStepY();
        int outZ = bz + direction.getStepZ();
        Block outsideBlock = world.getBlock(outX, outY, outZ);
        if (faceCubic || !outsideBlock.isOpaqueCube()) {
            lightCenter = cache.getBrightness(outsideBlock, world, outX, outY, outZ);
        }

        // --- 中心 shade ---
        float shadeCenter;
        if (faceCubic) {
            shadeCenter = cache.getShadeBrightness(
                    world.getBlock(baseX, baseY, baseZ), world, baseX, baseY, baseZ);
        } else {
            shadeCenter = cache.getShadeBrightness(block, world, bx, by, bz);
        }

        AmbientVertexRemap remap = AmbientVertexRemap.fromFacing(direction);

        if (facePartial && info.doNonCubicWeight) {
            // --- 部分面：加权混合 ---
            float ts1 = (shade[3] + shade[0] + shadeCorner03 + shadeCenter) * 0.25F;
            float ts2 = (shade[2] + shade[0] + shadeCorner02 + shadeCenter) * 0.25F;
            float ts3 = (shade[2] + shade[1] + shadeCorner12 + shadeCenter) * 0.25F;
            float ts4 = (shade[3] + shade[1] + shadeCorner13 + shadeCenter) * 0.25F;

            // 4 个顶点的权重
            float[][] vertWeights = {
                    vertWeight(info.vert0Weights),
                    vertWeight(info.vert1Weights),
                    vertWeight(info.vert2Weights),
                    vertWeight(info.vert3Weights)
            };

            // 4 个 smooth-blended 光照坐标（每角一组）
            int tc1 = LightCoordsUtil.smoothBlend(light[3], light[0], lightCorner03, lightCenter);
            int tc2 = LightCoordsUtil.smoothBlend(light[2], light[0], lightCorner02, lightCenter);
            int tc3 = LightCoordsUtil.smoothBlend(light[2], light[1], lightCorner12, lightCenter);
            int tc4 = LightCoordsUtil.smoothBlend(light[3], light[1], lightCorner13, lightCenter);

            // 按 remap 写入各顶点
            writeVertexAO(ctx, remap.vert0, ts1, ts2, ts3, ts4, tc1, tc2, tc3, tc4,
                    vertWeights[0]);
            writeVertexAO(ctx, remap.vert1, ts1, ts2, ts3, ts4, tc1, tc2, tc3, tc4,
                    vertWeights[1]);
            writeVertexAO(ctx, remap.vert2, ts1, ts2, ts3, ts4, tc1, tc2, tc3, tc4,
                    vertWeights[2]);
            writeVertexAO(ctx, remap.vert3, ts1, ts2, ts3, ts4, tc1, tc2, tc3, tc4,
                    vertWeights[3]);
        } else {
            // --- 满块面：简单平均 ---
            float ll1 = (shade[3] + shade[0] + shadeCorner03 + shadeCenter) * 0.25F;
            float ll2 = (shade[2] + shade[0] + shadeCorner02 + shadeCenter) * 0.25F;
            float ll3 = (shade[2] + shade[1] + shadeCorner12 + shadeCenter) * 0.25F;
            float ll4 = (shade[3] + shade[1] + shadeCorner13 + shadeCenter) * 0.25F;

            int tc1 = LightCoordsUtil.smoothBlend(light[3], light[0], lightCorner03, lightCenter);
            int tc2 = LightCoordsUtil.smoothBlend(light[2], light[0], lightCorner02, lightCenter);
            int tc3 = LightCoordsUtil.smoothBlend(light[2], light[1], lightCorner12, lightCenter);
            int tc4 = LightCoordsUtil.smoothBlend(light[3], light[1], lightCorner13, lightCenter);

            // 按 remap 写入各顶点（等权，无需加权；aoColorMul 仅含 AO shade）
            ctx.aoColorMul[remap.vert0] = clamp(ll1);
            ctx.aoBrightness[remap.vert0] = tc1;
            ctx.aoColorMul[remap.vert1] = clamp(ll2);
            ctx.aoBrightness[remap.vert1] = tc2;
            ctx.aoColorMul[remap.vert2] = clamp(ll3);
            ctx.aoBrightness[remap.vert2] = tc3;
            ctx.aoColorMul[remap.vert3] = clamp(ll4);
            ctx.aoBrightness[remap.vert3] = tc4;
        }
    }

    /**
     * Flat 路径：计算单一亮度 + 方向阴影。
     *
     * <p>对齐 26.1.2 {@code prepareQuadFlat}。当 quad 无 AO 时使用此路径。
     *
     * @param world       世界
     * @param bx, by, bz 方块坐标
     * @param block       方块实例
     * @param quad        待渲染 quad
     * @param cardinal    方向亮度表
     * @param ctx         输出目标（brightnessOverride / shade）
     */
    public void prepareQuadFlat(
            IBlockAccess world, int bx, int by, int bz,
            Block block, BakedQuad quad,
            CardinalLighting cardinal, RenderContext ctx) {

        prepareQuadShape(world, bx, by, bz, block, quad, false);

        Direction direction = quad.face;
        int lightX = faceCubic ? bx + faceOffsetX(direction) : bx;
        int lightY = faceCubic ? by + faceOffsetY(direction) : by;
        int lightZ = faceCubic ? bz + faceOffsetZ(direction) : bz;

        Block lightBlock = world.getBlock(lightX, lightY, lightZ);
        ctx.brightnessOverride = cache.getBrightness(lightBlock, world, lightX, lightY, lightZ);

        // shade = shadeEnabled ? cardinal.byFace : cardinal.up()
        boolean useShade = quad.shadeEnabled == null || quad.shadeEnabled;
        float dirLight = useShade ? cardinal.byFace(direction) : cardinal.up();
        ctx.shade = dirLight;
    }

    // ==================== 面形状分析 ====================

    private void prepareQuadShape(IBlockAccess world, int bx, int by, int bz,
                                  Block block, BakedQuad quad, boolean computeAO) {
        float minX = 32.0F, minY = 32.0F, minZ = 32.0F;
        float maxX = -32.0F, maxY = -32.0F, maxZ = -32.0F;

        for (int i = 0; i < 4; i++) {
            float x = (float) quad.vx(i), y = (float) quad.vy(i), z = (float) quad.vz(i);
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }

        if (computeAO) {
            faceShape[SizeInfo.WEST.index] = minX;
            faceShape[SizeInfo.EAST.index] = maxX;
            faceShape[SizeInfo.DOWN.index] = minY;
            faceShape[SizeInfo.UP.index] = maxY;
            faceShape[SizeInfo.NORTH.index] = minZ;
            faceShape[SizeInfo.SOUTH.index] = maxZ;
            faceShape[SizeInfo.FLIP_WEST.index] = 1.0F - minX;
            faceShape[SizeInfo.FLIP_EAST.index] = 1.0F - maxX;
            faceShape[SizeInfo.FLIP_DOWN.index] = 1.0F - minY;
            faceShape[SizeInfo.FLIP_UP.index] = 1.0F - maxY;
            faceShape[SizeInfo.FLIP_NORTH.index] = 1.0F - minZ;
            faceShape[SizeInfo.FLIP_SOUTH.index] = 1.0F - maxZ;
        }

        float minEps = 1.0E-4F, maxEps = 0.9999F;
        Direction dir = quad.face;

        switch (dir) {
            case DOWN: case UP:
                facePartial = minX >= minEps || minZ >= minEps || maxX <= maxEps || maxZ <= maxEps;
                break;
            case NORTH: case SOUTH:
                facePartial = minX >= minEps || minY >= minEps || maxX <= maxEps || maxY <= maxEps;
                break;
            case WEST: case EAST:
                facePartial = minY >= minEps || minZ >= minEps || maxY <= maxEps || maxZ <= maxEps;
                break;
            default:
                facePartial = false;
        }

        switch (dir) {
            case DOWN:
                faceCubic = minY == maxY && (minY < minEps || block.isOpaqueCube()); break;
            case UP:
                faceCubic = minY == maxY && (maxY > maxEps || block.isOpaqueCube()); break;
            case NORTH:
                faceCubic = minZ == maxZ && (minZ < minEps || block.isOpaqueCube()); break;
            case SOUTH:
                faceCubic = minZ == maxZ && (maxZ > maxEps || block.isOpaqueCube()); break;
            case WEST:
                faceCubic = minX == maxX && (minX < minEps || block.isOpaqueCube()); break;
            case EAST:
                faceCubic = minX == maxX && (maxX > maxEps || block.isOpaqueCube()); break;
            default:
                faceCubic = false;
        }
    }

    // ==================== 辅助方法 ====================

    /** 部分面顶点加权写入（aoColorMul 仅含 AO shade，不含 CardinalLighting）。 */
    private void writeVertexAO(RenderContext ctx, int vertIdx,
                               float ts1, float ts2, float ts3, float ts4,
                               int tc1, int tc2, int tc3, int tc4,
                               float[] w) {
        float aoShade = Math.max(0.0F, Math.min(1.0F,
                ts1 * w[0] + ts2 * w[1] + ts3 * w[2] + ts4 * w[3]));
        ctx.aoColorMul[vertIdx] = clamp(aoShade);
        ctx.aoBrightness[vertIdx] = LightCoordsUtil.smoothWeightedBlend(
                tc1, tc2, tc3, tc4, w[0], w[1], w[2], w[3]);
    }

    /** 计算顶点在 8 个候选 cell 中的加权值。 */
    private float[] vertWeight(SizeInfo[] weights) {
        float[] result = new float[4];
        result[0] = faceShape[weights[0].index] * faceShape[weights[1].index];
        result[1] = faceShape[weights[2].index] * faceShape[weights[3].index];
        result[2] = faceShape[weights[4].index] * faceShape[weights[5].index];
        result[3] = faceShape[weights[6].index] * faceShape[weights[7].index];
        return result;
    }

    private static float clamp(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    // ==================== 面方向偏移 ====================

    private static int faceOffsetX(Direction f) {
        return f.getStepX();
    }
    private static int faceOffsetY(Direction f) {
        return f.getStepY();
    }
    private static int faceOffsetZ(Direction f) {
        return f.getStepZ();
    }

    // ==================== 缓存管理 ====================

    public static void enableCaching() {
        get().cache.enable();
    }

    public static void clearCache() {
        get().cache.disable();
    }

    // ==================== 内部枚举：SizeInfo ====================

    /**
     * 12 个尺寸分量索引：6 面 + 6 翻转面。
     * 用于 faceShape 数组索引和部分面顶点权重计算。
     */
    private enum SizeInfo {
        DOWN(0), UP(1), NORTH(2), SOUTH(3), WEST(4), EAST(5),
        FLIP_DOWN(6), FLIP_UP(7), FLIP_NORTH(8), FLIP_SOUTH(9), FLIP_WEST(10), FLIP_EAST(11);

        static final int COUNT = values().length;
        final int index;

        SizeInfo(int index) { this.index = index; }
    }

    // ==================== 内部枚举：AmbientVertexRemap ====================

    /**
     * quad 顶点到 AO 计算结果的映射。
     * 每个面对应一个排列，将 AO 计算的 4 个角值映射到 quad 的 4 个顶点。
     *
     * <p>对齐 26.1.2 AmbientVertexRemap — 与 JsonModelBake 的顶点顺序一致。
     */
    private enum AmbientVertexRemap {
        DOWN(0, 1, 2, 3),
        UP(2, 3, 0, 1),
        NORTH(3, 0, 1, 2),
        SOUTH(0, 1, 2, 3),
        WEST(3, 0, 1, 2),
        EAST(1, 2, 3, 0);

        final int vert0, vert1, vert2, vert3;

        private static final AmbientVertexRemap[] BY_FACING = new AmbientVertexRemap[6];
        static {
            BY_FACING[Direction.DOWN.ordinal()] = DOWN;
            BY_FACING[Direction.UP.ordinal()] = UP;
            BY_FACING[Direction.NORTH.ordinal()] = NORTH;
            BY_FACING[Direction.SOUTH.ordinal()] = SOUTH;
            BY_FACING[Direction.WEST.ordinal()] = WEST;
            BY_FACING[Direction.EAST.ordinal()] = EAST;
        }

        AmbientVertexRemap(int v0, int v1, int v2, int v3) {
            this.vert0 = v0; this.vert1 = v1; this.vert2 = v2; this.vert3 = v3;
        }

        static AmbientVertexRemap fromFacing(Direction face) {
            return BY_FACING[face.ordinal()];
        }
    }

    // ==================== 内部枚举：AdjacencyInfo ====================

    /**
     * 每个面的邻接信息：4 角方向、是否做非满块加权、4 组顶点权重索引。
     *
     * <p>对齐 26.1.2 AdjacencyInfo — 定义了每个面在 AO 计算时如何采样
     * 周围方块以及如何将结果映射到顶点。
     */
    private enum AdjacencyInfo {
        DOWN(
                new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH},
                true,
                new SizeInfo[]{SizeInfo.FLIP_WEST, SizeInfo.SOUTH, SizeInfo.FLIP_WEST, SizeInfo.FLIP_SOUTH, SizeInfo.WEST, SizeInfo.FLIP_SOUTH, SizeInfo.WEST, SizeInfo.SOUTH},
                new SizeInfo[]{SizeInfo.FLIP_WEST, SizeInfo.NORTH, SizeInfo.FLIP_WEST, SizeInfo.FLIP_NORTH, SizeInfo.WEST, SizeInfo.FLIP_NORTH, SizeInfo.WEST, SizeInfo.NORTH},
                new SizeInfo[]{SizeInfo.FLIP_EAST, SizeInfo.NORTH, SizeInfo.FLIP_EAST, SizeInfo.FLIP_NORTH, SizeInfo.EAST, SizeInfo.FLIP_NORTH, SizeInfo.EAST, SizeInfo.NORTH},
                new SizeInfo[]{SizeInfo.FLIP_EAST, SizeInfo.SOUTH, SizeInfo.FLIP_EAST, SizeInfo.FLIP_SOUTH, SizeInfo.EAST, SizeInfo.FLIP_SOUTH, SizeInfo.EAST, SizeInfo.SOUTH}
        ),
        UP(
                new Direction[]{Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH},
                true,
                new SizeInfo[]{SizeInfo.EAST, SizeInfo.SOUTH, SizeInfo.EAST, SizeInfo.FLIP_SOUTH, SizeInfo.FLIP_EAST, SizeInfo.FLIP_SOUTH, SizeInfo.FLIP_EAST, SizeInfo.SOUTH},
                new SizeInfo[]{SizeInfo.EAST, SizeInfo.NORTH, SizeInfo.EAST, SizeInfo.FLIP_NORTH, SizeInfo.FLIP_EAST, SizeInfo.FLIP_NORTH, SizeInfo.FLIP_EAST, SizeInfo.NORTH},
                new SizeInfo[]{SizeInfo.WEST, SizeInfo.NORTH, SizeInfo.WEST, SizeInfo.FLIP_NORTH, SizeInfo.FLIP_WEST, SizeInfo.FLIP_NORTH, SizeInfo.FLIP_WEST, SizeInfo.NORTH},
                new SizeInfo[]{SizeInfo.WEST, SizeInfo.SOUTH, SizeInfo.WEST, SizeInfo.FLIP_SOUTH, SizeInfo.FLIP_WEST, SizeInfo.FLIP_SOUTH, SizeInfo.FLIP_WEST, SizeInfo.SOUTH}
        ),
        NORTH(
                new Direction[]{Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST},
                true,
                new SizeInfo[]{SizeInfo.UP, SizeInfo.FLIP_WEST, SizeInfo.UP, SizeInfo.WEST, SizeInfo.FLIP_UP, SizeInfo.WEST, SizeInfo.FLIP_UP, SizeInfo.FLIP_WEST},
                new SizeInfo[]{SizeInfo.UP, SizeInfo.FLIP_EAST, SizeInfo.UP, SizeInfo.EAST, SizeInfo.FLIP_UP, SizeInfo.EAST, SizeInfo.FLIP_UP, SizeInfo.FLIP_EAST},
                new SizeInfo[]{SizeInfo.DOWN, SizeInfo.FLIP_EAST, SizeInfo.DOWN, SizeInfo.EAST, SizeInfo.FLIP_DOWN, SizeInfo.EAST, SizeInfo.FLIP_DOWN, SizeInfo.FLIP_EAST},
                new SizeInfo[]{SizeInfo.DOWN, SizeInfo.FLIP_WEST, SizeInfo.DOWN, SizeInfo.WEST, SizeInfo.FLIP_DOWN, SizeInfo.WEST, SizeInfo.FLIP_DOWN, SizeInfo.FLIP_WEST}
        ),
        SOUTH(
                new Direction[]{Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP},
                true,
                new SizeInfo[]{SizeInfo.UP, SizeInfo.FLIP_WEST, SizeInfo.FLIP_UP, SizeInfo.FLIP_WEST, SizeInfo.FLIP_UP, SizeInfo.WEST, SizeInfo.UP, SizeInfo.WEST},
                new SizeInfo[]{SizeInfo.DOWN, SizeInfo.FLIP_WEST, SizeInfo.FLIP_DOWN, SizeInfo.FLIP_WEST, SizeInfo.FLIP_DOWN, SizeInfo.WEST, SizeInfo.DOWN, SizeInfo.WEST},
                new SizeInfo[]{SizeInfo.DOWN, SizeInfo.FLIP_EAST, SizeInfo.FLIP_DOWN, SizeInfo.FLIP_EAST, SizeInfo.FLIP_DOWN, SizeInfo.EAST, SizeInfo.DOWN, SizeInfo.EAST},
                new SizeInfo[]{SizeInfo.UP, SizeInfo.FLIP_EAST, SizeInfo.FLIP_UP, SizeInfo.FLIP_EAST, SizeInfo.FLIP_UP, SizeInfo.EAST, SizeInfo.UP, SizeInfo.EAST}
        ),
        WEST(
                new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH},
                true,
                new SizeInfo[]{SizeInfo.UP, SizeInfo.SOUTH, SizeInfo.UP, SizeInfo.FLIP_SOUTH, SizeInfo.FLIP_UP, SizeInfo.FLIP_SOUTH, SizeInfo.FLIP_UP, SizeInfo.SOUTH},
                new SizeInfo[]{SizeInfo.UP, SizeInfo.NORTH, SizeInfo.UP, SizeInfo.FLIP_NORTH, SizeInfo.FLIP_UP, SizeInfo.FLIP_NORTH, SizeInfo.FLIP_UP, SizeInfo.NORTH},
                new SizeInfo[]{SizeInfo.DOWN, SizeInfo.NORTH, SizeInfo.DOWN, SizeInfo.FLIP_NORTH, SizeInfo.FLIP_DOWN, SizeInfo.FLIP_NORTH, SizeInfo.FLIP_DOWN, SizeInfo.NORTH},
                new SizeInfo[]{SizeInfo.DOWN, SizeInfo.SOUTH, SizeInfo.DOWN, SizeInfo.FLIP_SOUTH, SizeInfo.FLIP_DOWN, SizeInfo.FLIP_SOUTH, SizeInfo.FLIP_DOWN, SizeInfo.SOUTH}
        ),
        EAST(
                new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH},
                true,
                new SizeInfo[]{SizeInfo.FLIP_DOWN, SizeInfo.SOUTH, SizeInfo.FLIP_DOWN, SizeInfo.FLIP_SOUTH, SizeInfo.DOWN, SizeInfo.FLIP_SOUTH, SizeInfo.DOWN, SizeInfo.SOUTH},
                new SizeInfo[]{SizeInfo.FLIP_DOWN, SizeInfo.NORTH, SizeInfo.FLIP_DOWN, SizeInfo.FLIP_NORTH, SizeInfo.DOWN, SizeInfo.FLIP_NORTH, SizeInfo.DOWN, SizeInfo.NORTH},
                new SizeInfo[]{SizeInfo.FLIP_UP, SizeInfo.NORTH, SizeInfo.FLIP_UP, SizeInfo.FLIP_NORTH, SizeInfo.UP, SizeInfo.FLIP_NORTH, SizeInfo.UP, SizeInfo.NORTH},
                new SizeInfo[]{SizeInfo.FLIP_UP, SizeInfo.SOUTH, SizeInfo.FLIP_UP, SizeInfo.FLIP_SOUTH, SizeInfo.UP, SizeInfo.FLIP_SOUTH, SizeInfo.UP, SizeInfo.SOUTH}
        );

        final Direction[] corners;
        final boolean doNonCubicWeight;
        final SizeInfo[] vert0Weights, vert1Weights, vert2Weights, vert3Weights;

        private static final AdjacencyInfo[] BY_FACING = new AdjacencyInfo[6];
        static {
            BY_FACING[Direction.DOWN.ordinal()] = DOWN;
            BY_FACING[Direction.UP.ordinal()] = UP;
            BY_FACING[Direction.NORTH.ordinal()] = NORTH;
            BY_FACING[Direction.SOUTH.ordinal()] = SOUTH;
            BY_FACING[Direction.WEST.ordinal()] = WEST;
            BY_FACING[Direction.EAST.ordinal()] = EAST;
        }

        AdjacencyInfo(Direction[] corners, boolean doNonCubicWeight,
                       SizeInfo[] v0w, SizeInfo[] v1w, SizeInfo[] v2w, SizeInfo[] v3w) {
            this.corners = corners;
            this.doNonCubicWeight = doNonCubicWeight;
            this.vert0Weights = v0w;
            this.vert1Weights = v1w;
            this.vert2Weights = v2w;
            this.vert3Weights = v3w;
        }

        static AdjacencyInfo fromFacing(Direction face) {
            return BY_FACING[face.ordinal()];
        }
    }

    // ==================== 内部类：Cache ====================

    /**
     * LRU 缓存 — 缓存方块亮度和 shade 查询结果。
     *
     * <p>对齐 26.1.2 BlockModelLighter.Cache：
     * 使用两个 LRU map 分别缓存亮度和 shade，容量各 100。
     * 在 chunk 渲染开始时 enable()，结束时 disable()。
     */
    static final class Cache {
        private boolean enabled;
        private final int capacity;
        private final LinkedHashMap<Long, Integer> brightnessCache;
        private final LinkedHashMap<Long, Float> shadeCache;

        Cache(int capacity) {
            this.capacity = capacity;
            this.brightnessCache = new LinkedHashMap<Long, Integer>(capacity, 0.75F, true) {
                private static final long serialVersionUID = 1L;
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
                    return size() > Cache.this.capacity;
                }
            };
            this.shadeCache = new LinkedHashMap<Long, Float>(capacity, 0.75F, true) {
                private static final long serialVersionUID = 1L;
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Float> eldest) {
                    return size() > Cache.this.capacity;
                }
            };
        }

        void enable() {
            this.enabled = true;
        }

        void disable() {
            this.enabled = false;
            this.brightnessCache.clear();
            this.shadeCache.clear();
        }

        int getBrightness(Block block, IBlockAccess world, int x, int y, int z) {
            long key = encodePos(x, y, z);
            if (enabled) {
                Integer cached = brightnessCache.get(key);
                if (cached != null) return cached;
            }
            int value = block.getMixedBrightnessForBlock(world, x, y, z);
            if (enabled) {
                brightnessCache.put(key, value);
            }
            return value;
        }

        float getShadeBrightness(Block block, IBlockAccess world, int x, int y, int z) {
            long key = encodePos(x, y, z);
            if (enabled) {
                Float cached = shadeCache.get(key);
                if (cached != null) return cached;
            }
            float value = block.getAmbientOcclusionLightValue();
            if (enabled) {
                shadeCache.put(key, value);
            }
            return value;
        }

        /**
         * 将 3D 坐标编码为 long（用于缓存键）。
         * x: bits 0-25, z: bits 26-51, y: bits 52-63
         */
        private static long encodePos(int x, int y, int z) {
            return ((long) x & 0x3FFFFFFL)
                    | (((long) z & 0x3FFFFFFL) << 26)
                    | (((long) y & 0xFFFL) << 52);
        }
    }
}
