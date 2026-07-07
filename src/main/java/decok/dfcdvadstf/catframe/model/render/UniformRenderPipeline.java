package decok.dfcdvadstf.catframe.model.render;

import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import javax.annotation.Nullable;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;
import decok.dfcdvadstf.catframe.model.render.extension.ao.AOComputeExtension;
import decok.dfcdvadstf.catframe.model.render.extension.ao.light.CardinalLighting;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Uniform Render Pipeline. The responsibility is limited to:
 * <ol>
 *   <li>Create {@link RenderContext} and fill default values (brightness, direction shadow)</li>
 *   <li>Dispatch the {@link ModelRenderRegistry#apply(RenderContext)} extension chain</li>
 *   <li>Submit the processed data to {@link Tessellator}</li>
 * </ol>
 *
 * <p> The pipeline is responsible for the orthogonal rendering lifecycle (GL matrix push/pop, texture binding, Tessellator drawing).
 * All extension logic (AO computation, display transform, face culling, coloring, GL_LIGHTING management)
 * has been sunk to the corresponding {@link IModelRenderExtension} implementation.
 * </p>
 */
public final class UniformRenderPipeline {

    private UniformRenderPipeline() {
    }

    // ==================== 方块世界/GUI 渲染 ====================

    /**
     * 渲染方块的 quads（世界或 GUI），包含扩展链处理和 Tessellator 提交。
     * AO 计算由 {@link AOComputeExtension}
     * 在扩展链中完成。Display transform 由 {@link decok.dfcdvadstf.catframe.model.render.extension.DisplayTransformExtension}
     * 在扩展链中管理 GL 矩阵生命周期。
     *
     * @param part        渲染部件
     * @param world       世界（GUI 时可传 null）
     * @param x           方块 X（GUI 时为 0）
     * @param y           方块 Y（GUI 时为 0）
     * @param z           方块 Z（GUI 时为 0）
     * @param block       方块
     * @param rotationDeg Y 轴旋转角度（0/90/180/270）
     * @param phase       渲染阶段（BLOCK_WORLD / BLOCK_GUI）
     */
    public static void renderBlockQuads(BlockStateModelPart part,
                                        IBlockAccess world, int x, int y, int z,
                                        Block block, int rotationDeg,
                                        RenderPhase phase) {
        renderBlockQuads(part, world, x, y, z, block, rotationDeg, phase, 0);
    }

    /**
     * 渲染方块的 quads（带 metadata 支持，用于 BLOCK_GUI 染色等场景）。
     * Display transform 已由 {@link BlockStateModelPart#getDisplay()} 持有，
     * 由 {@link DisplayTransformExtension} 在扩展链中消费，无需通过参数传递。
     *
     * @param part        渲染部件
     * @param metadata    方块 metadata（用于染色）
     */
    public static void renderBlockQuads(BlockStateModelPart part,
                                        IBlockAccess world, int x, int y, int z,
                                        Block block, int rotationDeg,
                                        RenderPhase phase,
                                        int metadata) {
        Tessellator t = Tessellator.instance;
        boolean isGui = (phase == RenderPhase.BLOCK_GUI);

        List<BakedQuad> allQuads = part.getAllQuads();

        double a = Math.toRadians(rotationDeg);
        double cos = Math.cos(a), sin = Math.sin(a);
        Point3d tmpVec = new Point3d();

        // [C3 修复] 所有 GL 状态操作移入 try 块内，确保异常时 finally 能正确恢复
        try {
            if (isGui) {
                // GUI 方块渲染：启用混合以支持纹理 alpha 通道（如玻璃、树叶透明边缘）
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }

            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);

            // 生命周期：quad 处理前
            ModelRenderRegistry.applyBeforePart(allQuads, phase, part);

            // BLOCK_GUI 需要自行管理 Tessellator 绘制周期
            // （ISBRH.renderInventoryBlock 不会为自定义渲染器管理 Tessellator）
            if (isGui) {
                t.startDrawingQuads();
            }

        boolean hasVertices = false;
        for (BakedQuad q : allQuads) {
            int baseBrightness = isGui ? 255
                    : (world != null ? block.getMixedBrightnessForBlock(world, x, y, z) : 0);
            float baseShade = isGui ? 1.0f
                    : CardinalLighting.DEFAULT.byFace(q.face);

            // 创建上下文并运行扩展链
            RenderContext ctx = new RenderContext(phase, q,
                    world, x, y, z, block, null, baseBrightness, baseShade);
            ctx.metadata = metadata;
            ModelRenderRegistry.apply(ctx);
            if (ctx.skip) continue;
            hasVertices = true;

            // 提交到 Tessellator
            boolean hasVertexAO = ctx.aoBrightness[0] >= 0;

            if (hasVertexAO && !isGui) {
                for (int i = 0; i < 4; i++) {
                    t.setBrightness(ctx.aoBrightness[i]);
                    float cr = ((ctx.color >> 16) & 0xFF) / 255.0f * ctx.shade * ctx.aoColorMul[i];
                    float cg = ((ctx.color >> 8) & 0xFF) / 255.0f * ctx.shade * ctx.aoColorMul[i];
                    float cb = (ctx.color & 0xFF) / 255.0f * ctx.shade * ctx.aoColorMul[i];
                    t.setColorOpaque_F(cr, cg, cb);

                    double vx = q.vx(i), vy = q.vy(i), vz = q.vz(i);
                    if (rotationDeg != 0) {
                        double px = vx - 0.5, pz = vz - 0.5;
                        vx = px * cos - pz * sin + 0.5;
                        vz = px * sin + pz * cos + 0.5;
                    }
                    // 应用 display transform（仅 BLOCK_GUI 时有值）
                    if (ctx.displayTransform != null) {
                        tmpVec.set(vx, vy, vz);
                        ctx.displayTransform.transform(tmpVec);
                        vx = tmpVec.x; vy = tmpVec.y; vz = tmpVec.z;
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

                IIcon icon = (ctx.iconOverride != null) ? ctx.iconOverride : q.icon;
                if (isGui) {
                    // GUI 模式使用 RGBA 以正确处理纹理 alpha 通道
                    t.setColorRGBA_F(cr, cg, cb, 1.0f);
                } else {
                    t.setColorOpaque_F(cr, cg, cb);
                }
                for (int i = 0; i < 4; i++) {
                    double vx = q.vx(i), vy = q.vy(i), vz = q.vz(i);
                    if (rotationDeg != 0) {
                        double px = vx - 0.5, pz = vz - 0.5;
                        vx = px * cos - pz * sin + 0.5;
                        vz = px * sin + pz * cos + 0.5;
                    }
                    // 应用 display transform（仅 BLOCK_GUI 时有值）
                    if (ctx.displayTransform != null) {
                        tmpVec.set(vx, vy, vz);
                        ctx.displayTransform.transform(tmpVec);
                        vx = tmpVec.x; vy = tmpVec.y; vz = tmpVec.z;
                    }
                    double U = icon.getInterpolatedU(q.up[i]);
                    double V = icon.getInterpolatedV(q.vp[i]);
                    t.addVertexWithUV(x + vx, y + vy, z + vz, U, V);
                }
            }
        }

        // [W6] Tessellator 绘制与 GL 状态恢复
        // 仅在有顶点时 draw，避免空 Tessellator 抛 IllegalStateException
        if (isGui && hasVertices) {
            t.draw();
        }

        } finally {
            if (isGui) {
                GL11.glDisable(GL11.GL_BLEND);
            }
            // 生命周期：quad 处理后（GuiLightExtension/DisplayTransformExtension 在此恢复 GL 状态）
            ModelRenderRegistry.applyAfterPart();
        }
    }

    /**
     * 渲染方块世界的 quads（向后兼容，默认 BLOCK_WORLD 且无 display）。
     */
    public static void renderBlockQuads(BlockStateModelPart part,
                                        IBlockAccess world, int x, int y, int z,
                                        Block block, int rotationDeg) {
        renderBlockQuads(part, world, x, y, z, block, rotationDeg,
                RenderPhase.BLOCK_WORLD, 0);
    }

    /**
     * 渲染方块的 quads（GUI 模式快捷方法）。
     */
    public static void renderBlockQuadsGUI(BlockStateModelPart part, Block block) {
        renderBlockQuadsGUI(part, block, 0);
    }

    /**
     * 渲染方块的 quads（GUI 模式，带 metadata 支持）。
     * Display transform 已由 {@link BlockStateModelPart#getDisplay()} 持有，
     * 由 {@link DisplayTransformExtension} 在扩展链中消费，无需通过参数传递。
     *
     * @param metadata 方块 metadata（用于 Block.getRenderColor(metadata) 染色）
     */
    public static void renderBlockQuadsGUI(BlockStateModelPart part, Block block,
                                           int metadata) {
        renderBlockQuads(part, null, 0, 0, 0, block, 0,
                RenderPhase.BLOCK_GUI, metadata);
    }

    /**
     * 渲染物品的 quads（GUI / 手持 / 掉落物），含向量空间 display transform 和 GL_LIGHTING 管理。
     * Display transform 由 {@link decok.dfcdvadstf.catframe.model.render.extension.DisplayTransformExtension}
     * 在扩展链中计算为 {@link Matrix4d} 矩阵，管线在顶点提交时统一执行矩阵乘法。
     * 预变换（反抵消）同样以 Matrix4d 形式传入，在 display transform 之后应用于顶点。
     *
     * @param part    渲染部件
     * @param stack   物品栈
     * @param phase   渲染阶段（ITEM_GUI / ITEM_HAND_* / DROPPED_* ）
     * @param world   世界上下文（仅 DROPPED_BLOCK_GROUND 等需要方块信息的阶段使用，其他传 null）
     * @param x       方块 X 坐标（无世界上下文时传 0）
     * @param y       方块 Y 坐标
     * @param z       方块 Z 坐标
     * @param block   方块实例（无时传 null）
     * @param preTransform 可选的预变换矩阵（反抵消），在 display transform 之后作用于顶点，可为 null
     */
    public static void renderItemQuads(BlockStateModelPart part,
                                       ItemStack stack, RenderPhase phase,
                                       IBlockAccess world, int x, int y, int z, Block block,
                                       @Nullable Matrix4d preTransform) {
        Tessellator t = Tessellator.instance;
        List<BakedQuad> allQuads = part.getAllQuads();

        boolean gui = (phase == RenderPhase.ITEM_GUI);
        boolean dropped = (phase == RenderPhase.DROPPED_ITEM_GROUND
                || phase == RenderPhase.DROPPED_BLOCK_GROUND);

        Point3d tmpVec = new Point3d();

        try {
            // 禁用面剔除
            GL11.glDisable(GL11.GL_CULL_FACE);
            // 纹理图集绑定：ItemBlock 的模型 quad 引用 blocks atlas 的纹理，
            // 必须绑定 locationBlocksTexture；非方块物品绑定 locationItemsTexture。
            // 注意：getSpriteNumber() 不可靠——许多 ItemBlock 返回 1 导致绑错图集，
            // 而世界渲染（renderBlockQuads）始终硬编码绑定 blocks atlas。
            boolean isBlockItem = (stack != null && stack.getItem() instanceof net.minecraft.item.ItemBlock);
            int spriteNumber = (stack != null && stack.getItem() != null)
                    ? stack.getItem().getSpriteNumber() : 1;
            Minecraft.getMinecraft().getTextureManager().bindTexture(
                    (isBlockItem || spriteNumber == 0)
                            ? TextureMap.locationBlocksTexture
                            : TextureMap.locationItemsTexture);

            if (gui || dropped) {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }

            // 生命周期：quad 处理前（DisplayTransformExtension 在此计算 display 矩阵）
            ModelRenderRegistry.applyBeforePart(allQuads, phase, part);

            t.startDrawingQuads();

            int baseBrightness = gui ? 255 : 15728880;
            boolean hasVertices = false;

            for (BakedQuad q : allQuads) {
                float baseShade = 1.0f;
                RenderContext ctx = new RenderContext(phase, q,
                        world, x, y, z, block, stack, baseBrightness, baseShade);
                ModelRenderRegistry.apply(ctx);
                if (ctx.skip) continue;
                hasVertices = true;

                t.setBrightness(ctx.effectiveBrightness());
                float cr = ((ctx.color >> 16) & 0xFF) / 255.0f * ctx.shade;
                float cg = ((ctx.color >> 8) & 0xFF) / 255.0f * ctx.shade;
                float cb = (ctx.color & 0xFF) / 255.0f * ctx.shade;
                if (gui) {
                    t.setColorRGBA_F(cr, cg, cb, 1.0f);
                } else {
                    t.setColorOpaque_F(cr, cg, cb);
                }

                IIcon icon = (ctx.iconOverride != null) ? ctx.iconOverride : q.icon;
                for (int i = 0; i < 4; i++) {
                    double U = icon.getInterpolatedU(q.up[i]);
                    double V = icon.getInterpolatedV(q.vp[i]);

                    // 向量空间变换：先应用 display transform，再应用 preTransform
                    // 顶点变换顺序：v' = M_pre × M_display × v
                    // 对应 GL 矩阵栈：preTransform(外) 包裹 displayTransform(内)
                    tmpVec.set(q.vx(i), q.vy(i), q.vz(i));
                    if (ctx.displayTransform != null) {
                        ctx.displayTransform.transform(tmpVec);
                    }
                    if (preTransform != null) {
                        preTransform.transform(tmpVec);
                    }
                    t.addVertexWithUV(tmpVec.x, tmpVec.y, tmpVec.z, U, V);
                }
            }

            if (hasVertices) {
                t.draw();
            }
        } finally {
            // 生命周期：quad 处理后（扩展恢复状态）
            ModelRenderRegistry.applyAfterPart();
            // 恢复 GL 状态
            GL11.glEnable(GL11.GL_CULL_FACE);
            if (gui || dropped) {
                GL11.glDisable(GL11.GL_BLEND);
            }
        }
    }

    /**
     * 旧版兼容 — 无 preTransform 且无世界上下文时委托给新版。
     */
    public static void renderItemQuads(BlockStateModelPart part,
                                       ItemStack stack, RenderPhase phase) {
        renderItemQuads(part, stack, phase, null, 0, 0, 0, null, (Matrix4d) null);
    }

    // ==================== 光照与阴影辅助 ====================
}
