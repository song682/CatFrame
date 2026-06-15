package decok.dfcdvadstf.catframe.model.render;

import decok.dfcdvadstf.catframe.model.BlockJsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.ModelJson;
import decok.dfcdvadstf.catframe.model.render.extension.DisplayTransformExtension;
import decok.dfcdvadstf.catframe.model.render.extension.ao.AOComputeExtension;
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
 * 统一渲染管线。职责限定为：
 * <ol>
 *   <li>创建 {@link RenderContext} 并填入默认值（亮度、方向阴影）</li>
 *   <li>调度 {@link ModelRenderRegistry#apply(RenderContext)} 扩展链</li>
 *   <li>将处理后的数据提交到 {@link Tessellator}</li>
 * </ol>
 *
 * <p>所有扩展性逻辑（AO 计算、display transform、面剔除、染色、GL_LIGHTING 管理）
 * 均已下沉至对应的 {@link IModelRenderExtension} 实现中。
 * 管线仅负责正交的渲染生命周期（GL 矩阵 push/pop、纹理绑定、Tessellator 绘制）。
 */
public final class UniformRenderPipeline {

    private UniformRenderPipeline() {
    }

    // ==================== 方块世界/GUI 渲染 ====================

    /**
     * 渲染方块的 quads（世界或 GUI），包含扩展链处理和 Tessellator 提交。
     * AO 计算由 {@link AOComputeExtension}
     * 在扩展链中完成。
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

        // --- 应用 display transform（GUI 模式） ---
        boolean hasDisplay = (display != null && !display.isEmpty());
        String displayKey = isGui ? "gui" : null;
        ModelJson.DisplayTransform dt = (hasDisplay && displayKey != null) ? display.get(displayKey) : null;

        if (isGui) {
            GL11.glPushMatrix();
            if (dt != null) {
                DisplayTransformExtension.applyTransform(dt);
            }
        }

        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);

        double a = Math.toRadians(rotationDeg);
        double cos = Math.cos(a), sin = Math.sin(a);

        // 获取所有 quad（用于 AO 前置检测和遍历）
        List<BakedQuad> allQuads = part.getAllQuads();

        // 生命周期：quad 处理前
        ModelRenderRegistry.applyBeforePart(allQuads, phase);

        for (BakedQuad q : allQuads) {
            int baseBrightness = isGui ? 255 : getFaceBrightness(world, x, y, z, block, q.face);
            float baseShade = shadeByNormal(q.faceNormal);

            // 创建上下文并运行扩展链
            RenderContext ctx = new RenderContext(phase, q,
                    world, x, y, z, block, null, baseBrightness, baseShade);
            ModelRenderRegistry.apply(ctx);
            if (ctx.skip) continue;

            // 提交到 Tessellator
            boolean hasVertexAO = ctx.aoBrightness[0] >= 0;

            if (hasVertexAO && !isGui) {
                for (int i = 0; i < 4; i++) {
                    t.setBrightness(ctx.aoBrightness[i]);
                    float cr = ((ctx.color >> 16) & 0xFF) / 255.0f * ctx.shade * ctx.aoColorMul[i];
                    float cg = ((ctx.color >> 8) & 0xFF) / 255.0f * ctx.shade * ctx.aoColorMul[i];
                    float cb = (ctx.color & 0xFF) / 255.0f * ctx.shade * ctx.aoColorMul[i];
                    t.setColorOpaque_F(cr, cg, cb);

                    double vx = q.vx[i], vy = q.vy[i], vz = q.vz[i];
                    if (rotationDeg != 0) {
                        double px = vx - 0.5, pz = vz - 0.5;
                        vx = px * cos - pz * sin + 0.5;
                        vz = px * sin + pz * cos + 0.5;
                    }
                    IIcon icon = (ctx.iconOverride != null) ? ctx.iconOverride : q.icon;
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

                IIcon icon = (ctx.iconOverride != null) ? ctx.iconOverride : q.icon;
                for (int i = 0; i < 4; i++) {
                    double vx = q.vx[i], vy = q.vy[i], vz = q.vz[i];
                    if (rotationDeg != 0) {
                        double px = vx - 0.5, pz = vz - 0.5;
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

        // 生命周期：quad 处理后
        ModelRenderRegistry.applyAfterPart();
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
     * 渲染物品的 quads（GUI / 手持），含 display transform 和 GL_LIGHTING 管理。
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

        // --- display transform（优先使用 quad 自带的 modelDisplay，fallback 到传入的 display） ---
        List<BakedQuad> allQuads = part.getAllQuads();
        Map<String, ModelJson.DisplayTransform> effectiveDisplay = null;

        // 优先从 quad 自带的 modelDisplay 读取
        if (!allQuads.isEmpty() && allQuads.get(0).modelDisplay != null) {
            effectiveDisplay = allQuads.get(0).modelDisplay;
        } else {
            effectiveDisplay = display;
        }

        String displayKey = DisplayTransformExtension.phaseToDisplayKey(phase.name());
        ModelJson.DisplayTransform dt = (effectiveDisplay != null && displayKey != null)
                ? effectiveDisplay.get(displayKey) : null;

        if (dt != null || !centered) {
            GL11.glPushMatrix();
        }
        if (dt != null) {
            DisplayTransformExtension.applyTransform(dt);
        }

        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationItemsTexture);

        // 生命周期：quad 处理前（GuiLightExtension 在此管理 GL_LIGHTING）
        ModelRenderRegistry.applyBeforePart(allQuads, phase);

        try {
            t.startDrawingQuads();

            int baseBrightness = (phase == RenderPhase.ITEM_GUI) ? 255 : 15728880;

            for (BakedQuad q : allQuads) {
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
            // 生命周期：quad 处理后（GuiLightExtension 在此恢复 GL_LIGHTING）
            ModelRenderRegistry.applyAfterPart();
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

    // ==================== 光照与阴影辅助 ====================

    /**
     * 根据面法线计算方向阴影系数。
     * 对齐原版 1.7.10 RenderBlocks 的 shade 值：
     * 顶面 1.0、底面 0.5、侧面 0.6/0.8。
     */
    static float shadeByNormal(double[] n) {
        if (n == null) return 1.0f;
        double ax = Math.abs(n[0]), ay = Math.abs(n[1]), az = Math.abs(n[2]);
        if (ay >= ax && ay >= az) return n[1] > 0 ? 1.0f : 0.5f;
        if (ax >= ay && ax >= az) return 0.6f;
        return 0.8f;
    }

    /**
     * 获取指定面的亮度值。对齐原版 RenderBlocks.getFaceBrightness。
     */
    private static int getFaceBrightness(IBlockAccess w, int x, int y, int z,
                                          Block b, net.minecraft.util.EnumFacing face) {
        if (face == null) return b.getMixedBrightnessForBlock(w, x, y, z);
        switch (face) {
            case UP:    return w.getLightBrightnessForSkyBlocks(x, y + 1, z, 0);
            case DOWN:  return w.getLightBrightnessForSkyBlocks(x, y - 1, z, 0);
            case NORTH: return w.getLightBrightnessForSkyBlocks(x, y, z - 1, 0);
            case SOUTH: return w.getLightBrightnessForSkyBlocks(x, y, z + 1, 0);
            case WEST:  return w.getLightBrightnessForSkyBlocks(x - 1, y, z, 0);
            case EAST:  return w.getLightBrightnessForSkyBlocks(x + 1, y, z, 0);
            default:    return b.getMixedBrightnessForBlock(w, x, y, z);
        }
    }
}
