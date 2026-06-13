package decok.dfcdvadstf.catframe.model.render;

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
                for (int i = 0; i < 4; i++) {
                    t.setBrightness(ctx.aoBrightness[i]);
                    float cr = ((ctx.color >> 16) & 0xFF) / 255.0f * ctx.shade * ctx.aoColorMul[i];
                    float cg = ((ctx.color >> 8) & 0xFF) / 255.0f * ctx.shade * ctx.aoColorMul[i];
                    float cb = (ctx.color & 0xFF) / 255.0f * ctx.shade * ctx.aoColorMul[i];
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

    // ==================== 光照与AO辅助 ====================

    /**
     * 逐顶点 AO 计算 — 模拟 26.1 BlockModelLighter.prepareQuadAmbientOcclusion 的采样算法。
     * <p>
     * 对满方块面（faceCubic），采样面平面上的 9 个位置：
     * <ul>
     *   <li>center：被渲染方块自身 (bx, by, bz)</li>
     *   <li>edge0~3：面平面上的 4 个边缘邻居</li>
     *   <li>corner0~3：面平面上的 4 个对角邻居（含透明回退逻辑）</li>
     * </ul>
     * 每个顶点的 AO = (center + 2个边缘 + 1个对角) / 4。
     */
    private static void computeVertexAO(IBlockAccess world, int bx, int by, int bz,
                                        Block block, BakedQuad q,
                                        int[] outBrightness, float[] outAO) {
        net.minecraft.util.EnumFacing face = q.face;
        if (face == null) return;

        int e1Axis, e2Axis;
        switch (face) {
            case DOWN:
            case UP:
                e1Axis = 0;  // X
                e2Axis = 2;  // Z
                break;
            case NORTH:
            case SOUTH:
                e1Axis = 0;  // X
                e2Axis = 1;  // Y
                break;
            case EAST:
            case WEST:
                e1Axis = 2;  // Z
                e2Axis = 1;  // Y
                break;
            default:
                return;
        }

        // center = 被渲染的方块自身（26.1 faceCubic 路径：shadeCenter 取自方块本身）
        float centerAO = block.getAmbientOcclusionLightValue();
        int centerBrightness = block.getMixedBrightnessForBlock(world, bx, by, bz);

        // 计算面在两个切线轴上的顶点包围盒
        double minE1 = Double.MAX_VALUE, maxE1 = -Double.MAX_VALUE;
        double minE2 = Double.MAX_VALUE, maxE2 = -Double.MAX_VALUE;
        for (int v = 0; v < 4; v++) {
            double v1 = (e1Axis == 0) ? q.vx[v] : (e1Axis == 1) ? q.vy[v] : q.vz[v];
            double v2 = (e2Axis == 0) ? q.vx[v] : (e2Axis == 1) ? q.vy[v] : q.vz[v];
            if (v1 < minE1) minE1 = v1;
            if (v1 > maxE1) maxE1 = v1;
            if (v2 < minE2) minE2 = v2;
            if (v2 > maxE2) maxE2 = v2;
        }

        for (int v = 0; v < 4; v++) {
            double v1 = (e1Axis == 0) ? q.vx[v] : (e1Axis == 1) ? q.vy[v] : q.vz[v];
            double v2 = (e2Axis == 0) ? q.vx[v] : (e2Axis == 1) ? q.vy[v] : q.vz[v];

            // 确定该顶点在两个切线轴上朝向哪个边缘邻居
            boolean isMinE1 = Math.abs(v1 - minE1) < 0.0001;
            boolean isMinE2 = Math.abs(v2 - minE2) < 0.0001;

            int s1 = isMinE1 ? -1 : 1;
            int s2 = isMinE2 ? -1 : 1;

            // 面平面内的 3 个采样偏移（不含法线轴分量）
            int[] offDiag = new int[3];  // 对角：两个切线轴都有偏移
            offDiag[e1Axis] = s1;
            offDiag[e2Axis] = s2;
            int[] offEdge1 = new int[3]; // 边缘1：仅 e1 轴偏移
            offEdge1[e1Axis] = s1;
            int[] offEdge2 = new int[3]; // 边缘2：仅 e2 轴偏移
            offEdge2[e2Axis] = s2;

            int diagX = bx + offDiag[0],  diagY = by + offDiag[1],  diagZ = bz + offDiag[2];
            int e1X   = bx + offEdge1[0], e1Y   = by + offEdge1[1], e1Z   = bz + offEdge1[2];
            int e2X   = bx + offEdge2[0], e2Y   = by + offEdge2[1], e2Z   = bz + offEdge2[2];

            // 边缘邻居是否遮挡（26.1: !isViewBlocking ≈ getCanBlockGrass）
            boolean solid1 = world.getBlock(e1X, e1Y, e1Z).getCanBlockGrass();
            boolean solid2 = world.getBlock(e2X, e2Y, e2Z).getCanBlockGrass();

            // 亮度采样
            int bDiag  = block.getMixedBrightnessForBlock(world, diagX, diagY, diagZ);
            int bEdge1 = block.getMixedBrightnessForBlock(world, e1X, e1Y, e1Z);
            int bEdge2 = block.getMixedBrightnessForBlock(world, e2X, e2Y, e2Z);

            // AO 采样 — 26.1 透明角落回退逻辑
            float aoDiag, aoEdge1, aoEdge2;
            if (!solid1 && !solid2) {
                // 两个边缘块都不遮挡 → 读取对角块（26.1: 取更远一格的方块）
                aoDiag  = world.getBlock(diagX, diagY, diagZ).getAmbientOcclusionLightValue();
                aoEdge1 = aoDiag;
                aoEdge2 = aoDiag;
            } else {
                // 至少一个边缘遮挡 → 各读各的
                aoDiag  = world.getBlock(diagX, diagY, diagZ).getAmbientOcclusionLightValue();
                aoEdge1 = world.getBlock(e1X, e1Y, e1Z).getAmbientOcclusionLightValue();
                aoEdge2 = world.getBlock(e2X, e2Y, e2Z).getAmbientOcclusionLightValue();
            }

            // 每顶点 AO = (center + edge1 + edge2 + diagonal) / 4
            outAO[v] = (centerAO + aoEdge1 + aoEdge2 + aoDiag) / 4.0f;
            outBrightness[v] = mixAoBrightness(centerBrightness, bEdge1, bEdge2, bDiag);
        }
    }

    private static int mixAoBrightness(int a, int b, int c, int center) {
        if (a == 0) a = center;
        if (b == 0) b = center;
        if (c == 0) c = center;
        return (a + b + c + center) >> 2 & 0xFF00FF;
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
