package decok.dfcdvadstf.catframe.model.render.extension.ao;

import decok.dfcdvadstf.catframe.model.BlockJsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.RenderContext;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.block.Block;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;

/**
 * 内建渲染扩展：执行逐顶点 AO（环境光遮蔽）计算。
 *
 * <p>本扩展在 BLOCK_WORLD 阶段对每个 quad 的 4 个顶点采样相邻方块的
 * {@link Block#getMixedBrightnessForBlock}（亮度）和
 * {@link Block#getAmbientOcclusionLightValue}（AO 遮挡系数），
 * 将计算结果写入 {@link RenderContext#aoBrightness}[4] 和 {@link RenderContext#aoColorMul}[4]。
 *
 * <p>算法对齐 26.1 {@code BlockModelLighter.prepareQuadAmbientOcclusion}，
 * shade 值与边角回退逻辑对齐 1.7.10 原版 {@code RenderBlocks}。
 *
 * <p>本扩展强制为扩展链的第一个扩展，确保后续扩展（如 AOShadeExtension）
 * 可以读取/修改 AO 计算结果。
 */
public final class AOComputeExtension implements IModelRenderExtension {

    @Override
    public void apply(RenderContext ctx) {
        // 仅在世界渲染阶段执行 AO 计算
        if (ctx.phase != RenderPhase.BLOCK_WORLD) return;
        if (ctx.world == null || ctx.block == null) return;

        BakedQuad q = ctx.quad;
        if (q.face == null) return;

        computeVertexAO(ctx.world, ctx.x, ctx.y, ctx.z, ctx.block, q,
                ctx.aoBrightness, ctx.aoColorMul);
    }

    // ==================== 逐顶点 AO 计算 ====================

    /**
     * 逐顶点 AO 计算 — 算法结构对齐 26.1 {@code BlockModelLighter.prepareQuadAmbientOcclusion}，
     * shade 值与边角回退逻辑对齐 1.7.10 原版 {@code RenderBlocks}。
     *
     * <p>核心特性：
     * <ol>
     *   <li>面形状分析：区分 faceCubic（满块面）和 facePartial（部分面如台阶侧面）</li>
     *   <li>中心位置逻辑：满块面取面外侧，非满块面取方块自身（若外侧不透明则取外侧亮度）</li>
     *   <li>边/角采样统一在面外侧层（对齐 1.7.10 原版 RenderBlocks — 先偏移至面外侧层再采样）</li>
     *   <li>边角回退：对齐 1.7.10 原版 — 至少一边不透明时读取对角，双透明时复用边邻居</li>
     *   <li>部分面加权混合：用 faceShape 包围盒数据对 4 个候选 AO 值做双线性插值</li>
     *   <li>shade：使用 1.7.10 {@code getAmbientOcclusionLightValue}（不透明→0.0、透明→1.0），
     *       与 1.7.10 静态 LightMap 管线校准（26.1 的 0.2 需要动态 LightMap 补偿，不适用于 1.7.10）</li>
     * </ol>
     */
    static void computeVertexAO(IBlockAccess world, int bx, int by, int bz,
                                        Block block, BakedQuad q,
                                        int[] outBrightness, float[] outAO) {
        // 初始化：未计算时保持 -1（uniform 退化）
        for (int i = 0; i < 4; i++) {
            outBrightness[i] = -1;
            outAO[i] = 1.0f;
        }

        EnumFacing face = q.face;
        if (face == null) return;

        // --- 1. 确定边轴 (对齐 AdjacencyInfo 的 axis1/axis2 约定) ---
        int e1Axis, e2Axis;
        switch (face) {
            case DOWN: case UP:   e1Axis = 0; e2Axis = 2; break; // X, Z
            case NORTH: case SOUTH: e1Axis = 0; e2Axis = 1; break; // X, Y
            case EAST: case WEST:  e1Axis = 2; e2Axis = 1; break; // Z, Y
            default: return;
        }

        // --- 2. 面形状分析 (对齐 prepareQuadShape) ---
        double minE1 = Double.MAX_VALUE, maxE1 = -Double.MAX_VALUE;
        double minE2 = Double.MAX_VALUE, maxE2 = -Double.MAX_VALUE;
        for (int v = 0; v < 4; v++) {
            double v1 = axisVal(q.vx[v], q.vy[v], q.vz[v], e1Axis);
            double v2 = axisVal(q.vx[v], q.vy[v], q.vz[v], e2Axis);
            if (v1 < minE1) minE1 = v1;
            if (v1 > maxE1) maxE1 = v1;
            if (v2 < minE2) minE2 = v2;
            if (v2 > maxE2) maxE2 = v2;
        }

        // 手动计算面法线偏移，绕过 1.7.10 EnumFacing.getFrontOffsetX() 的东西反向 Bug
        // （EAST.getFrontOffsetX() 返回 -1，WEST.getFrontOffsetX() 返回 +1）
        int foX, foY, foZ;
        switch (face) {
            case DOWN:  foX = 0; foY = -1; foZ = 0; break;
            case UP:    foX = 0; foY =  1; foZ = 0; break;
            case NORTH: foX = 0; foY =  0; foZ = -1; break;
            case SOUTH: foX = 0; foY =  0; foZ =  1; break;
            case WEST:  foX = -1; foY = 0; foZ = 0; break;
            case EAST:  foX =  1; foY = 0; foZ = 0; break;
            default:    foX = 0; foY = 0; foZ = 0;
        }
        // 面法线轴 = face 方向的轴（与 e1Axis/e2Axis 正交的第三个轴）
        int faceAxis = (foX != 0) ? 0 : (foY != 0) ? 1 : 2;
        // 取面上任一顶点在面法线轴上的坐标（同一面所有顶点该值相同）
        double facePos = axisVal(q.vx[0], q.vy[0], q.vz[0], faceAxis);
        boolean faceAtEdge = facePos <= 0.001 || facePos >= 0.999;
        boolean faceCubic = faceAtEdge
                && (minE1 <= 0.001 && maxE1 >= 0.999)
                && (minE2 <= 0.001 && maxE2 >= 0.999);
        boolean facePartial = !faceCubic
                && (minE1 > 0.001 || maxE1 < 0.999 || minE2 > 0.001 || maxE2 < 0.999);

        // faceShape[]: [0]=minE1 [1]=maxE1 [2]=minE2 [3]=maxE2 [4]=1-maxE1 [5]=1-minE1 [6]=1-maxE2 [7]=1-minE2
        double[] fs = { minE1, maxE1, minE2, maxE2,
                        1.0 - maxE1, 1.0 - minE1, 1.0 - maxE2, 1.0 - minE2 };

        // --- 3. 中心位置 (对齐 26.1 center 逻辑) ---
        int cbx = bx + foX, cby = by + foY, cbz = bz + foZ;
        Block outsideBlock = world.getBlock(cbx, cby, cbz);

        int centerBrightness;
        float centerShade;
        if (faceCubic) {
            // 满块面：取面外侧
            centerBrightness = block.getMixedBrightnessForBlock(world, cbx, cby, cbz);
            centerShade = outsideBlock.getAmbientOcclusionLightValue();
        } else {
            // 非满块面：亮度取自身；shade 取自身，但若外侧不透明则取外侧
            centerBrightness = block.getMixedBrightnessForBlock(world, bx, by, bz);
            centerShade = world.getBlock(bx, by, bz).getAmbientOcclusionLightValue();
            if (outsideBlock.isOpaqueCube()) {
                centerShade = outsideBlock.getAmbientOcclusionLightValue();
            }
        }

        // --- 4. 逐顶点 AO ---
        for (int v = 0; v < 4; v++) {
            double v1 = axisVal(q.vx[v], q.vy[v], q.vz[v], e1Axis);
            double v2 = axisVal(q.vx[v], q.vy[v], q.vz[v], e2Axis);

            // 确定该顶点在两条边轴上的方向 (min→-1, max→+1)
            int s1 = Math.abs(v1 - minE1) < 0.001 ? -1 : 1;
            int s2 = Math.abs(v2 - minE2) < 0.001 ? -1 : 1;

            // 边偏移 1 = e1方向 + e2方向 (对角邻居)
            int[] off1 = new int[3]; off1[e1Axis] = s1; off1[e2Axis] = s2;
            // 边偏移 2 = 仅 e1方向
            int[] off2 = new int[3]; off2[e1Axis] = s1;
            // 边偏移 3 = 仅 e2方向
            int[] off3 = new int[3]; off3[e2Axis] = s2;

            // 关键修正（对齐 1.7.10 原版 RenderBlocks）：所有边/角采样加面法线偏移
            int nx1 = bx + off1[0] + foX, ny1 = by + off1[1] + foY, nz1 = bz + off1[2] + foZ;
            int nx2 = bx + off2[0] + foX, ny2 = by + off2[1] + foY, nz2 = bz + off2[2] + foZ;
            int nx3 = bx + off3[0] + foX, ny3 = by + off3[1] + foY, nz3 = bz + off3[2] + foZ;

            // 采样亮度
            int b1 = block.getMixedBrightnessForBlock(world, nx1, ny1, nz1);
            int b2 = block.getMixedBrightnessForBlock(world, nx2, ny2, nz2);
            int b3 = block.getMixedBrightnessForBlock(world, nx3, ny3, nz3);

            // 采样 shade (1.7.10: 不透明→0.0, 透明→1.0)
            float shade1 = world.getBlock(nx1, ny1, nz1).getAmbientOcclusionLightValue();
            float shade2 = world.getBlock(nx2, ny2, nz2).getAmbientOcclusionLightValue();
            float shade3 = world.getBlock(nx3, ny3, nz3).getAmbientOcclusionLightValue();

            boolean solid2 = world.getBlock(nx2, ny2, nz2).isOpaqueCube();
            boolean solid3 = world.getBlock(nx3, ny3, nz3).isOpaqueCube();

            // 始终读取对角位置（取消原版边角回退，否则对角线阴影会被丢弃）
            float cornerShade = shade1;
            int cornerBrightness = b1;

            // --- 计算顶点 AO shade ---
            if (!facePartial) {
                // 非部分面：简单平均 (edge2 + edge3 + corner + center) / 4
                outAO[v] = (shade2 + shade3 + cornerShade + centerShade) * 0.25f;
                outBrightness[v] = mixAoBrightness(b2, b3, cornerBrightness, centerBrightness);
            } else {
                // 部分面：4 个候选值 + 双线性加权混合
                float t1 = (shade3 + shade2 + cornerShade + centerShade) * 0.25f;
                float t2 = (shade3 + cornerShade + cornerShade + centerShade) * 0.25f;
                float t3 = (shade2 + cornerShade + cornerShade + centerShade) * 0.25f;
                float t4 = (shade3 + shade2 + cornerShade + centerShade) * 0.25f;

                double wE1Min = fs[5]; // 1-minE1 (weight toward max side)
                double wE1Max = fs[0]; // minE1 (weight toward min side)
                double wE2Min = fs[7]; // 1-minE2
                double wE2Max = fs[2]; // minE2

                boolean atMinE1 = Math.abs(v1 - minE1) < 0.001;
                boolean atMinE2 = Math.abs(v2 - minE2) < 0.001;

                double w1 = (atMinE1 ? wE1Min : wE1Max) * (atMinE2 ? wE2Min : wE2Max);
                double w2 = (atMinE1 ? wE1Min : wE1Max) * (atMinE2 ? wE2Max : wE2Min);
                double w3 = (atMinE1 ? wE1Max : wE1Min) * (atMinE2 ? wE2Max : wE2Min);
                double w4 = (atMinE1 ? wE1Max : wE1Min) * (atMinE2 ? wE2Min : wE2Max);

                double wSum = w1 + w2 + w3 + w4;
                if (wSum < 0.0001) wSum = 1.0; // 防除零
                float ao = (float) ((t1 * w1 + t2 * w2 + t3 * w3 + t4 * w4) / wSum);
                outAO[v] = Math.max(0f, Math.min(1f, ao));

                // 亮度加权混合
                outBrightness[v] = mixAoBrightness(b2, b3, cornerBrightness, centerBrightness);
            }
        }
    }

    /** 取顶点在指定轴上的坐标值 */
    private static double axisVal(double vx, double vy, double vz, int axis) {
        return axis == 0 ? vx : axis == 1 ? vy : vz;
    }

    /**
     * 混合 4 个 packed brightness 值 (blockLight&lt;&lt;4 | skyLight&lt;&lt;20)。
     * 分别对 block 和 sky 分量做平均。
     */
    private static int mixAoBrightness(int a, int b, int c, int center) {
        if (a == 0) a = center;
        if (b == 0) b = center;
        if (c == 0) c = center;
        int blockAvg = (((a >> 4) & 15) + ((b >> 4) & 15) + ((c >> 4) & 15) + ((center >> 4) & 15)) >> 2;
        int skyAvg   = (((a >> 20) & 15) + ((b >> 20) & 15) + ((c >> 20) & 15) + ((center >> 20) & 15)) >> 2;
        return (blockAvg << 4) | (skyAvg << 20);
    }
}
