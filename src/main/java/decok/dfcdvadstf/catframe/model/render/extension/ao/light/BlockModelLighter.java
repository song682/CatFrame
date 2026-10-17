package decok.dfcdvadstf.catframe.model.render.extension.ao.light;

import decok.dfcdvadstf.catframe.core.Direction;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.api.RenderContext;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unified block model lighting calculator — aligned with 26.1.2 {@code BlockModelLighter}.
 *
 * <p>Provides two lighting paths:
 * <ul>
 *   <li><b>AO path</b> ({@link #prepareQuadAmbientOcclusion}): per-vertex ambient occlusion + smooth lighting blending</li>
 *   <li><b>Flat path</b> ({@link #prepareQuadFlat}): single brightness + directional shading</li>
 * </ul>
 *
 * <p>Core design:
 * <ol>
 *   <li>{@link AdjacencyInfo} — the 4 corner directions of each face + 4 sets of vertex weight indices (SizeInfo)</li>
 *   <li>{@link AmbientVertexRemap} — mapping from quad vertices to AO computation results</li>
 *   <li>{@link SizeInfo} — 12 size components (6 faces + 6 flipped), used for partial-face weighting</li>
 *   <li>{@link Cache} — LRU cache (brightness + shade), capacity 100</li>
 * </ol>
 *
 * <p>Output is written to aoBrightness[4] and aoColorMul[4] of {@link RenderContext}:
 * <ul>
 *   <li>aoBrightness[v] = packed light coordinate after smooth blending</li>
 *   <li>aoColorMul[v] = AO shade × CardinalLighting (already includes directional lighting)</li>
 * </ul>
 */
public final class BlockModelLighter {

    /** LRU cache capacity. */
    private static final int CACHE_SIZE = 100;

    /** ThreadLocal instance, one per render thread (aligned with the 26.1.2 ThreadLocal<Cache> pattern). */
    private static final ThreadLocal<BlockModelLighter> THREAD_LOCAL =
            ThreadLocal.withInitial(BlockModelLighter::new);

    private final Cache cache = new Cache(CACHE_SIZE);

    private boolean faceCubic;
    private boolean facePartial;
    private final float[] faceShape = new float[SizeInfo.COUNT];

    public static BlockModelLighter get() {
        return THREAD_LOCAL.get();
    }

    // ==================== Public API ====================

    /**
     * AO path: computes per-vertex ambient occlusion + smooth lighting.
     *
     * <p>Aligned with 26.1.2 {@code prepareQuadAmbientOcclusion}:
     * <ol>
     *   <li>Face shape analysis (faceCubic / facePartial)</li>
     *   <li>4 corners + 4 edges + 1 center = 9-point brightness/shade sampling</li>
     *   <li>Corner fallback (reuse the edge shade when both sides are opaque)</li>
     *   <li>smoothBlend smooth blending</li>
     *   <li>Partial-face bilinear weighting (smoothWeightedBlend)</li>
     *   <li>CardinalLighting directional lighting multiplied into aoColorMul</li>
     * </ol>
     *
     * @param world       world
     * @param bx, by, bz block coordinates
     * @param block       block instance
     * @param quad        quad to render
     * @param ctx         output target (aoBrightness / aoColorMul)
     *
     * <p>Note: aoColorMul contains only AO shade (not CardinalLighting);
     * directional lighting is carried uniformly by {@link RenderContext#shade} (set by the pipeline to CardinalLighting.byFace).
     */
    public void prepareQuadAmbientOcclusion(
            IBlockAccess world, int bx, int by, int bz,
            Block block, BakedQuad quad,
            RenderContext ctx) {

        prepareQuadShape(world, bx, by, bz, block, quad, true);

        Direction direction = quad.face;
        if (direction == null) return;

        // Cubic face: base position = outside the face; non-cubic face: base position = the block itself
        int baseX = faceCubic ? bx + direction.getStepX() : bx;
        int baseY = faceCubic ? by + direction.getStepY() : by;
        int baseZ = faceCubic ? bz + direction.getStepZ() : bz;

        AdjacencyInfo info = AdjacencyInfo.fromFacing(direction);

        // --- 4 corner samples ---
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

        // --- 4 outer corners of the face (used for corner-fallback decisions) ---
        boolean[] translucent = new boolean[4];
        for (int i = 0; i < 4; i++) {
            int ox = cx[i] + direction.getStepX();
            int oy = cy[i] + direction.getStepY();
            int oz = cz[i] + direction.getStepZ();
            Block outerBlock = world.getBlock(ox, oy, oz);
            translucent[i] = !outerBlock.isOpaqueCube();
        }

        // --- Corner shade/light (with translucent fallback) ---
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

        // --- Center brightness ---
        int lightCenter = cache.getBrightness(block, world, bx, by, bz);
        int outX = bx + direction.getStepX();
        int outY = by + direction.getStepY();
        int outZ = bz + direction.getStepZ();
        Block outsideBlock = world.getBlock(outX, outY, outZ);
        if (faceCubic || !outsideBlock.isOpaqueCube()) {
            lightCenter = cache.getBrightness(outsideBlock, world, outX, outY, outZ);
        }

        // --- Center shade ---
        float shadeCenter;
        if (faceCubic) {
            shadeCenter = cache.getShadeBrightness(
                    world.getBlock(baseX, baseY, baseZ), world, baseX, baseY, baseZ);
        } else {
            shadeCenter = cache.getShadeBrightness(block, world, bx, by, bz);
        }

        AmbientVertexRemap remap = AmbientVertexRemap.fromFacing(direction);

        if (facePartial && info.doNonCubicWeight) {
            // --- Partial face: weighted blending ---
            float ts1 = (shade[3] + shade[0] + shadeCorner03 + shadeCenter) * 0.25F;
            float ts2 = (shade[2] + shade[0] + shadeCorner02 + shadeCenter) * 0.25F;
            float ts3 = (shade[2] + shade[1] + shadeCorner12 + shadeCenter) * 0.25F;
            float ts4 = (shade[3] + shade[1] + shadeCorner13 + shadeCenter) * 0.25F;

            // Weights of the 4 vertices
            float[][] vertWeights = {
                    vertWeight(info.vert0Weights),
                    vertWeight(info.vert1Weights),
                    vertWeight(info.vert2Weights),
                    vertWeight(info.vert3Weights)
            };

            // 4 smooth-blended light coordinates (one set per corner)
            int tc1 = LightCoordsUtil.smoothBlend(light[3], light[0], lightCorner03, lightCenter);
            int tc2 = LightCoordsUtil.smoothBlend(light[2], light[0], lightCorner02, lightCenter);
            int tc3 = LightCoordsUtil.smoothBlend(light[2], light[1], lightCorner12, lightCenter);
            int tc4 = LightCoordsUtil.smoothBlend(light[3], light[1], lightCorner13, lightCenter);

            // Write each vertex according to the remap
            writeVertexAO(ctx, remap.vert0, ts1, ts2, ts3, ts4, tc1, tc2, tc3, tc4,
                    vertWeights[0]);
            writeVertexAO(ctx, remap.vert1, ts1, ts2, ts3, ts4, tc1, tc2, tc3, tc4,
                    vertWeights[1]);
            writeVertexAO(ctx, remap.vert2, ts1, ts2, ts3, ts4, tc1, tc2, tc3, tc4,
                    vertWeights[2]);
            writeVertexAO(ctx, remap.vert3, ts1, ts2, ts3, ts4, tc1, tc2, tc3, tc4,
                    vertWeights[3]);
        } else {
            // --- Cubic face: simple average ---
            float ll1 = (shade[3] + shade[0] + shadeCorner03 + shadeCenter) * 0.25F;
            float ll2 = (shade[2] + shade[0] + shadeCorner02 + shadeCenter) * 0.25F;
            float ll3 = (shade[2] + shade[1] + shadeCorner12 + shadeCenter) * 0.25F;
            float ll4 = (shade[3] + shade[1] + shadeCorner13 + shadeCenter) * 0.25F;

            int tc1 = LightCoordsUtil.smoothBlend(light[3], light[0], lightCorner03, lightCenter);
            int tc2 = LightCoordsUtil.smoothBlend(light[2], light[0], lightCorner02, lightCenter);
            int tc3 = LightCoordsUtil.smoothBlend(light[2], light[1], lightCorner12, lightCenter);
            int tc4 = LightCoordsUtil.smoothBlend(light[3], light[1], lightCorner13, lightCenter);

            // Write each vertex according to the remap (equal weight, no weighting needed; aoColorMul contains only AO shade)
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
     * Flat path: computes a single brightness + directional shading.
     *
     * <p>Aligned with 26.1.2 {@code prepareQuadFlat}. This path is used when the quad has no AO.
     *
     * @param world       world
     * @param bx, by, bz block coordinates
     * @param block       block instance
     * @param quad        quad to render
     * @param cardinal    directional brightness table
     * @param ctx         output target (brightnessOverride / shade)
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
        // 26.3+: shade_direction_override takes precedence; look up the table by the override direction (the face's actual orientation is used only for the brightness sampling position)
        boolean useShade = quad.shadeDirectionOverride != null
                || quad.shadeEnabled == null || quad.shadeEnabled;
        Direction shadeFace = quad.shadeDirectionOverride != null ? quad.shadeDirectionOverride : direction;
        float dirLight = useShade ? cardinal.byFace(shadeFace) : cardinal.up();
        ctx.shade = dirLight;
    }

    // ==================== Face shape analysis ====================

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

    // ==================== Helper methods ====================

    /** Weighted per-vertex write for a partial face (aoColorMul contains only AO shade, not CardinalLighting). */
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

    /** Computes the vertex's weighted value across the 8 candidate cells. */
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

    // ==================== Face direction offsets ====================

    private static int faceOffsetX(Direction f) {
        return f.getStepX();
    }
    private static int faceOffsetY(Direction f) {
        return f.getStepY();
    }
    private static int faceOffsetZ(Direction f) {
        return f.getStepZ();
    }

    // ==================== Cache management ====================

    public static void enableCaching() {
        get().cache.enable();
    }

    public static void clearCache() {
        get().cache.disable();
    }

    // ==================== Internal enum: SizeInfo ====================

    /**
     * 12 size component indices: 6 faces + 6 flipped faces.
     * Used for faceShape array indexing and partial-face vertex weight calculation.
     */
    private enum SizeInfo {
        DOWN(0), UP(1), NORTH(2), SOUTH(3), WEST(4), EAST(5),
        FLIP_DOWN(6), FLIP_UP(7), FLIP_NORTH(8), FLIP_SOUTH(9), FLIP_WEST(10), FLIP_EAST(11);

        static final int COUNT = values().length;
        final int index;

        SizeInfo(int index) { this.index = index; }
    }

    // ==================== Internal enum: AmbientVertexRemap ====================

    /**
     * Mapping from quad vertices to AO computation results.
     * Each face has a permutation that maps the 4 corner values from AO computation to the quad's 4 vertices.
     *
     * <p>Aligned with 26.1.2 AmbientVertexRemap — consistent with JsonModelBake's vertex order.
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

    // ==================== Internal enum: AdjacencyInfo ====================

    /**
     * Adjacency information for each face: 4 corner directions, whether to apply non-cubic weighting, and 4 sets of vertex weight indices.
     *
     * <p>Aligned with 26.1.2 AdjacencyInfo — defines how each face samples
     * surrounding blocks during AO computation and how the results map to vertices.
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

    // ==================== Internal class: Cache ====================

    /**
     * LRU cache — caches block brightness and shade query results.
     *
     * <p>Aligned with 26.1.2 BlockModelLighter.Cache:
     * two LRU maps cache brightness and shade separately, each with capacity 100.
     * enable() is called at the start of a chunk render and disable() at the end.
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
         * Encodes 3D coordinates into a long (used as a cache key).
         * x: bits 0-25, z: bits 26-51, y: bits 52-63
         */
        private static long encodePos(int x, int y, int z) {
            return ((long) x & 0x3FFFFFFL)
                    | (((long) z & 0x3FFFFFFL) << 26)
                    | (((long) y & 0xFFFL) << 52);
        }
    }
}
