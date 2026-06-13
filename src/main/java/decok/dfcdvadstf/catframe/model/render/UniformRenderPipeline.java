package decok.dfcdvadstf.catframe.model.render;

import decok.dfcdvadstf.catframe.CatFrameConfig;
import decok.dfcdvadstf.catframe.model.BlockJsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.ModelJson;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Map;

/**
 * 统一渲染管线。集中管理所有 quad 的最终提交逻辑：
 * <ol>
 *   <li>逐顶点 AO 计算（仅方块世界渲染）</li>
 *   <li>扩展链调用（染色、面剔除、纹理覆盖等）</li>
 *   <li>Tessellator 提交</li>
 * </ol>
 *
 * <p>类似于 1.21.5 中 ModelBlockRenderer 和 ItemStackRenderState 的职责。
 */
public final class UniformRenderPipeline {

    private UniformRenderPipeline() {
    }



    // ==================== 方块世界/GUI 渲染 ====================

    /**
     * 渲染方块的 quads（世界或 GUI），包含逐顶点 AO（仅世界）和扩展链处理。
     *
     * @param part        渲染部件
     * @param world       世界（GUI 时可传 null）
     * @param x           方块 X（GUI 时为 0）
     * @param y           方块 Y（GUI 时为 0）
     * @param z           方块 Z（GUI 时为 0）
     * @param block       方块
     * @param rotationDeg Y 轴旋转角度（0/90/180/270）
     * @param phase       渲染阶段（BLOCK_WORLD / BLOCK_GUI）
     * @param display     可选的 display transforms（从 ModelJson.display 传入，可为 null）
     */
    public static void renderBlockQuads(BlockStateModelPart part,
                                        IBlockAccess world, int x, int y, int z,
                                        Block block, int rotationDeg,
                                        RenderPhase phase,
                                        Map<String, ModelJson.DisplayTransform> display) {
        Tessellator t = Tessellator.instance;

        boolean isGui = (phase == RenderPhase.BLOCK_GUI);

        // 应用 display transform（GUI 模式）
        boolean hasDisplay = (display != null && !display.isEmpty());
        String displayKey = isGui ? "gui" : null;
        ModelJson.DisplayTransform dt = (hasDisplay && displayKey != null) ? display.get(displayKey) : null;

        if (isGui) {
            GL11.glPushMatrix();
            if (dt != null) {
                applyDisplayTransform(dt);
            }
        }

        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);

        double a = Math.toRadians(rotationDeg);
        double cos = Math.cos(a);
        double sin = Math.sin(a);

        // 逐顶点 AO 临时缓冲区
        int[] vAO = new int[4];
        float[] vAOMul = new float[4];

        for (BakedQuad q : part.getAllQuads()) {
            // 1. 计算逐顶点 AO
            for (int i = 0; i < 4; i++) {
                vAO[i] = -1;
                vAOMul[i] = 1.0f;
            }
            if (!isGui) {
                computeVertexAO(world, x, y, z, block, q, vAO, vAOMul);
            }

            int baseBrightness = isGui ? 255 : getFaceBrightness(world, x, y, z, block, q.face);
            float baseShade = shadeByNormal(q.faceNormal);

            // 2. 扩展链
            RenderContext ctx = new RenderContext(phase, q,
                    world, x, y, z, block, null, baseBrightness, baseShade);
            System.arraycopy(vAO, 0, ctx.aoBrightness, 0, 4);
            System.arraycopy(vAOMul, 0, ctx.aoColorMul, 0, 4);

            ModelRenderRegistry.apply(ctx);
            if (ctx.skip) continue;

            // 3. 提交
            boolean hasVertexAO = ctx.aoBrightness[0] >= 0;
            IIcon icon = (ctx.iconOverride != null) ? ctx.iconOverride : q.icon;

            if (hasVertexAO && !isGui) {
//                // --- 调试日志：记录第一个有 AO 面的实际提交值 ---
//                boolean submitLog = !isGui && q.face != null
//                        && CatFrameConfig.shouldLogDebug();
//                if (submitLog) {
//                    System.out.println("[AO-DBG] SUBMIT face=" + q.face
//                            + " color=0x" + Integer.toHexString(ctx.color)
//                            + " shade=" + ctx.shade);
//                }
                for (int i = 0; i < 4; i++) {
                    t.setBrightness(ctx.aoBrightness[i]);
                    float cr = ((ctx.color >> 16) & 0xFF) / 255.0f * ctx.shade * ctx.aoColorMul[i];
                    float cg = ((ctx.color >> 8) & 0xFF) / 255.0f * ctx.shade * ctx.aoColorMul[i];
                    float cb = (ctx.color & 0xFF) / 255.0f * ctx.shade * ctx.aoColorMul[i];
//                    if (submitLog && i == 0) {
//                        System.out.println("[AO-DBG]   v0 GPU: cr=" + String.format("%.3f", cr)
//                                + " cg=" + String.format("%.3f", cg)
//                                + " cb=" + String.format("%.3f", cb)
//                                + " bright=" + ctx.aoBrightness[0]
//                                + " aoMul=" + String.format("%.3f", ctx.aoColorMul[0]));
//                    }
                    t.setColorOpaque_F(cr, cg, cb);

                    double vx = q.vx[i], vy = q.vy[i], vz = q.vz[i];
                    if (rotationDeg != 0) {
                        double px = vx - 0.5;
                        double pz = vz - 0.5;
                        vx = px * cos - pz * sin + 0.5;
                        vz = px * sin + pz * cos + 0.5;
                    }
                    double U = icon.getInterpolatedU(q.up[i]);
                    double V = icon.getInterpolatedV(q.vp[i]);
                    t.addVertexWithUV(x + vx, y + vy, z + vz, U, V);
                }
            } else {
                t.setBrightness(ctx.effectiveBrightness());
                float cr = ((ctx.color >> 16) & 0xFF) / 255.0f * ctx.shade;
                float cg = ((ctx.color >> 8) & 0xFF) / 255.0f * ctx.shade;
                float cb = (ctx.color & 0xFF) / 255.0f * ctx.shade;
                t.setColorOpaque_F(cr, cg, cb);

                for (int i = 0; i < 4; i++) {
                    double vx = q.vx[i], vy = q.vy[i], vz = q.vz[i];
                    if (rotationDeg != 0) {
                        double px = vx - 0.5;
                        double pz = vz - 0.5;
                        vx = px * cos - pz * sin + 0.5;
                        vz = px * sin + pz * cos + 0.5;
                    }
                    double U = icon.getInterpolatedU(q.up[i]);
                    double V = icon.getInterpolatedV(q.vp[i]);
                    t.addVertexWithUV(x + vx, y + vy, z + vz, U, V);
                }
            }
        }

        if (isGui) {
            GL11.glPopMatrix();
        }
    }

    /**
     * 渲染方块世界的 quads（向后兼容，默认 BLOCK_WORLD 且无 display）。
     */
    public static void renderBlockQuads(BlockStateModelPart part,
                                        IBlockAccess world, int x, int y, int z,
                                        Block block, int rotationDeg) {
        renderBlockQuads(part, world, x, y, z, block, rotationDeg,
                RenderPhase.BLOCK_WORLD, null);
    }

    /**
     * 渲染方块的 quads（GUI 模式快捷方法）。
     */
    public static void renderBlockQuadsGUI(BlockStateModelPart part, Block block,
                                           Map<String, ModelJson.DisplayTransform> display) {
        renderBlockQuads(part, null, 0, 0, 0, block, 0,
                RenderPhase.BLOCK_GUI, display);
    }

    // ==================== 物品渲染 ====================

    /**
     * 渲染物品的 quads（GUI / 手持），包含扩展链处理。
     *
     * @param part  渲染部件
     * @param stack 物品栈
     * @param phase 渲染阶段（ITEM_GUI / ITEM_HAND）
     */
    /**
     * 渲染物品的 quads（GUI / 手持），含 display transform 支持。
     *
     * @param part    渲染部件
     * @param stack   物品栈
     * @param phase   渲染阶段（ITEM_GUI / ITEM_HAND）
     * @param display 可选的 display transforms（从 ModelJson.display 传入，可为 null）
     */
    public static void renderItemQuads(BlockStateModelPart part,
                                       ItemStack stack, RenderPhase phase,
                                       Map<String, ModelJson.DisplayTransform> display) {
        Tessellator t = Tessellator.instance;
        boolean centered = (phase == RenderPhase.ITEM_HAND_FIRST_PERSON
                || phase == RenderPhase.ITEM_HAND_THIRD_PERSON
                || phase == RenderPhase.ITEM_HAND);

        // 应用 display transform
        boolean hasDisplay = (display != null && !display.isEmpty());
        String displayKey = phaseToDisplayKey(phase);
        ModelJson.DisplayTransform dt = (hasDisplay && displayKey != null) ? display.get(displayKey) : null;

        if (dt != null || !centered) {
            GL11.glPushMatrix();
        }
        if (dt != null) {
            applyDisplayTransform(dt);
        }

        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationItemsTexture);

        // 检测模型的 gui_light 设置
        boolean isFrontLit = false;
        List<BakedQuad> allQuads = part.getAllQuads();
        if (!allQuads.isEmpty() && "front".equals(allQuads.get(0).guiLight)) {
            isFrontLit = true;
        }

        // gui_light: "front" → 正面光照，关闭 GL_LIGHTING（与 vanilla 处理非方块物品一致）
        // gui_light: "side" 或 null → 保留 GL_LIGHTING，让扩展系统处理
        if (isFrontLit) {
            GL11.glDisable(GL11.GL_LIGHTING);
        }

        try {
            t.startDrawingQuads();

            int baseBrightness = (phase == RenderPhase.ITEM_GUI) ? 255 : 15728880;

            for (BakedQuad q : part.getAllQuads()) {
                float baseShade = shadeByNormal(q.faceNormal);
                RenderContext ctx = new RenderContext(phase, q,
                        null, 0, 0, 0, null, stack, baseBrightness, baseShade);
                ModelRenderRegistry.apply(ctx);
                if (ctx.skip) continue;

                t.setBrightness(ctx.effectiveBrightness());
                float cr = ((ctx.color >> 16) & 0xFF) / 255.0f * ctx.shade;
                float cg = ((ctx.color >> 8) & 0xFF) / 255.0f * ctx.shade;
                float cb = (ctx.color & 0xFF) / 255.0f * ctx.shade;
                t.setColorOpaque_F(cr, cg, cb);

                IIcon icon = (ctx.iconOverride != null) ? ctx.iconOverride : q.icon;
                for (int i = 0; i < 4; i++) {
                    double U = icon.getInterpolatedU(q.up[i]);
                    double V = icon.getInterpolatedV(q.vp[i]);
                    double vx = q.vx[i], vy = q.vy[i], vz = q.vz[i];
                    if (centered) {
                        vx -= 0.5;
                        vy -= 0.5;
                        vz -= 0.5;
                    }
                    t.addVertexWithUV(vx, vy, vz, U, V);
                }
            }

            t.draw();
        } finally {
            // 恢复 GL_LIGHTING 状态（如果被修改过），vanilla renderItem 对非方块物品渲染后
            // 也会重新启用 GL_LIGHTING
            if (isFrontLit) {
                GL11.glEnable(GL11.GL_LIGHTING);
            }
        }
        if (dt != null || !centered) {
            GL11.glPopMatrix();
        }
    }

    /**
     * 旧版兼容 — 无 display transform 时委托给新版。
     */
    public static void renderItemQuads(BlockStateModelPart part,
                                       ItemStack stack, RenderPhase phase) {
        renderItemQuads(part, stack, phase, null);
    }

    // ==================== Display Transform 辅助 ====================

    /**
     * 将 RenderPhase 映射到 JSON model 的 display 键名。
     */
    private static String phaseToDisplayKey(RenderPhase phase) {
        switch (phase) {
            case ITEM_GUI:
                return "gui";
            case ITEM_HAND_FIRST_PERSON:
                return "firstperson_righthand";
            case ITEM_HAND_THIRD_PERSON:
                return "thirdperson_righthand";
            case ITEM_HAND:
                // 兼容旧代码，默认映射到 firstperson
                return "firstperson_righthand";
            case BLOCK_GUI:
                return "gui";
            default:
                return null;
        }
    }

    /**
     * 应用 JSON model 的 display transform（平移 → 旋转 ZYX → 缩放）。
     * 顺序与 Minecraft 原版一致。
     */
    private static void applyDisplayTransform(ModelJson.DisplayTransform dt) {
        if (dt.translation != null && dt.translation.length >= 3) {
            GL11.glTranslatef(dt.translation[0] / 100f, dt.translation[1] / 100f, dt.translation[2] / 100f);
        }
        if (dt.rotation != null && dt.rotation.length >= 3) {
            GL11.glRotatef(dt.rotation[0], 1, 0, 0);
            GL11.glRotatef(dt.rotation[1], 0, 1, 0);
            GL11.glRotatef(dt.rotation[2], 0, 0, 1);
        }
        if (dt.scale != null && dt.scale.length >= 3) {
            GL11.glScalef(dt.scale[0], dt.scale[1], dt.scale[2]);
        }
    }

    // ==================== 光照与 AO 辅助 ====================

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
    private static void computeVertexAO(IBlockAccess world, int bx, int by, int bz,
                                        Block block, BakedQuad q,
                                        int[] outBrightness, float[] outAO) {
        net.minecraft.util.EnumFacing face = q.face;
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

        int foX = face.getFrontOffsetX(), foY = face.getFrontOffsetY(), foZ = face.getFrontOffsetZ();
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
            // 原版对每个面先将坐标偏移至面外侧层（--y / ++y / --z / ++z / --x / ++x），
            // 然后在该层采样 4 个边+4 个角邻居。不加此偏移会采样到方块自身层（错误），
            // 导致邻居全是实心方块 → AO 值系统性偏低 → 整体偏暗。
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

            // 边角回退 (对齐 1.7.10 原版 RenderBlocks 逻辑)
            // 1.7.10: 至少一边不透明 → 读取对角；双透明 → 复用边邻居
            // (与 26.1 相反：26.1 在双透明时读对角，有不透明边时复用边)
            boolean solid2 = world.getBlock(nx2, ny2, nz2).isOpaqueCube();
            boolean solid3 = world.getBlock(nx3, ny3, nz3).isOpaqueCube();

            // 始终读取对角位置（取消原版边角回退，否则对角线阴影会被丢弃）
            // 原版 1.7.10 和 26.1 都有 fallback 逻辑：双透明边 → 复用边值，
            // 这会导致对角线上的方块阴影丢失，两条阴影在交接处出现缝隙
            float cornerShade = shade1;
            int cornerBrightness = b1;

//            // --- 调试日志：记录每个面的完整 AO 计算过程 ---
//            boolean aoLog = CatFrameConfig.shouldLogDebug();
//            if (aoLog && v == 0) {
//                System.out.println("[AO-DBG] === face=" + face + " pos=(" + bx + "," + by + "," + bz + ") ===");
//                System.out.println("[AO-DBG] faceCubic=" + faceCubic + " facePartial=" + facePartial
//                        + " faceAtEdge=" + faceAtEdge);
//                System.out.println("[AO-DBG] outsideBlock=" + outsideBlock
//                        + " centerBright=" + unpackBright(centerBrightness)
//                        + " centerShade=" + centerShade);
//            }

            // --- 计算顶点 AO shade ---
            if (!facePartial) {
                // 非部分面：简单平均 (edge2 + edge3 + corner + center) / 4
                outAO[v] = (shade2 + shade3 + cornerShade + centerShade) * 0.25f;
                outBrightness[v] = mixAoBrightness(b2, b3, cornerBrightness, centerBrightness);
            } else {
                // 部分面：4 个候选值 + 双线性加权混合
                // 使用 cornerShade 统一：corner=read 时=shade1, corner=edge 时=shade2
                float t1 = (shade3 + shade2 + cornerShade + centerShade) * 0.25f;
                float t2 = (shade3 + cornerShade + cornerShade + centerShade) * 0.25f;
                float t3 = (shade2 + cornerShade + cornerShade + centerShade) * 0.25f;
                float t4 = (shade3 + shade2 + cornerShade + centerShade) * 0.25f;

                // 双线性权重 (对齐 faceShape 权重)
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
//            if (aoLog) {
//                System.out.println("[AO-DBG]   v" + v + " sample pos=(" + nx1 + "," + ny1 + "," + nz1 + ")"
//                        + " corner_blk=" + world.getBlock(nx1, ny1, nz1)
//                        + " b1=" + unpackBright(b1) + " shade1=" + shade1
//                       + " | edge2=(" + nx2 + "," + ny2 + "," + nz2 + ")"
//                        + " b2=" + unpackBright(b2) + " shade2=" + shade2 + " solid2=" + solid2
//                        + " | edge3=(" + nx3 + "," + ny3 + "," + nz3 + ")"
//                        + " b3=" + unpackBright(b3) + " shade3=" + shade3 + " solid3=" + solid3);
//                System.out.println("[AO-DBG]   v" + v + " cornerFallback=" + (solid2 || solid3)
//                        + " cornerBright=" + unpackBright(cornerBrightness)
//                        + " cornerShade=" + cornerShade
//                        + " \u2192 aoMul=" + outAO[v] + " bright=" + unpackBright(outBrightness[v]));
//            }
        }
    }

    /** 将 packed brightness 解包为可读字符串 */
    private static String unpackBright(int packed) {
        if (packed == 0) return "0(sk=0,bl=0)";
        int sky = (packed >> 20) & 15;
        int blk = (packed >> 4) & 15;
        return packed + "(sk=" + sky + ",bl=" + blk + ")";
    }
    /** 取顶点在指定轴上的坐标值 */
    private static double axisVal(double vx, double vy, double vz, int axis) {
        return axis == 0 ? vx : axis == 1 ? vy : vz;
    }

    /**
     * 混合 4 个 packed brightness 值 (blockLight&lt;&lt;4 | skyLight&lt;&lt;20)。
     * 分别对 block 和 sky 分量做平均，避免旧版 {@code & 0xFF00FF} 掩码丢失进位的问题。
     */
    private static int mixAoBrightness(int a, int b, int c, int center) {
        if (a == 0) a = center;
        if (b == 0) b = center;
        if (c == 0) c = center;
        int blockAvg = (((a >> 4) & 15) + ((b >> 4) & 15) + ((c >> 4) & 15) + ((center >> 4) & 15)) >> 2;
        int skyAvg   = (((a >> 20) & 15) + ((b >> 20) & 15) + ((c >> 20) & 15) + ((center >> 20) & 15)) >> 2;
        return (blockAvg << 4) | (skyAvg << 20);
    }

    private static int getFaceBrightness(IBlockAccess w, int x, int y, int z, Block b, net.minecraft.util.EnumFacing face) {
        if (face == null) return b.getMixedBrightnessForBlock(w, x, y, z);
        switch (face) {
            case UP:
                return w.getLightBrightnessForSkyBlocks(x, y + 1, z, 0);
            case DOWN:
                return w.getLightBrightnessForSkyBlocks(x, y - 1, z, 0);
            case NORTH:
                return w.getLightBrightnessForSkyBlocks(x, y, z - 1, 0);
            case SOUTH:
                return w.getLightBrightnessForSkyBlocks(x, y, z + 1, 0);
            case WEST:
                return w.getLightBrightnessForSkyBlocks(x - 1, y, z, 0);
            case EAST:
                return w.getLightBrightnessForSkyBlocks(x + 1, y, z, 0);
            default:
                return b.getMixedBrightnessForBlock(w, x, y, z);
        }
    }

    static float shadeByNormal(double[] n) {
        if (n == null) return 1.0f;
        double ax = Math.abs(n[0]), ay = Math.abs(n[1]), az = Math.abs(n[2]);
        if (ay >= ax && ay >= az) return n[1] > 0 ? 1.0f : 0.5f;
        if (ax >= ay && ax >= az) return 0.6f;
        return 0.8f;
    }
}
