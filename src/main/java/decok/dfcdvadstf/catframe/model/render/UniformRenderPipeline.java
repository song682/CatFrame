package decok.dfcdvadstf.catframe.model.render;

import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.render.extension.ao.AOComputeExtension;
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
     * @param display     可选的 display transforms（从 ModelJson.display 传入，可为 null，现已由扩展处理）
     */
    public static void renderBlockQuads(BlockStateModelPart part,
                                        IBlockAccess world, int x, int y, int z,
                                        Block block, int rotationDeg,
                                        RenderPhase phase,
                                        Map<String, ModelJson.DisplayTransform> display) {
        renderBlockQuads(part, world, x, y, z, block, rotationDeg, phase, display, 0);
    }

    /**
     * 渲染方块的 quads（带 metadata 支持，用于 BLOCK_GUI 染色等场景）。
     * [S1] 新增 metadata 参数，传入 RenderContext。
     */
    public static void renderBlockQuads(BlockStateModelPart part,
                                        IBlockAccess world, int x, int y, int z,
                                        Block block, int rotationDeg,
                                        RenderPhase phase,
                                        Map<String, ModelJson.DisplayTransform> display,
                                        int metadata) {
        Tessellator t = Tessellator.instance;
        boolean isGui = (phase == RenderPhase.BLOCK_GUI);

        List<BakedQuad> allQuads = part.getAllQuads();

        double a = Math.toRadians(rotationDeg);
        double cos = Math.cos(a), sin = Math.sin(a);

        // [C3 修复] 所有 GL 状态操作移入 try 块内，确保异常时 finally 能正确恢复
        try {
            if (isGui) {
                // GUI 方块渲染：启用混合以支持纹理 alpha 通道（如玻璃、树叶透明边缘）
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }

            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);

            // 生命周期：quad 处理前
            ModelRenderRegistry.applyBeforePart(allQuads, phase);

            // BLOCK_GUI 需要自行管理 Tessellator 绘制周期
            // （ISBRH.renderInventoryBlock 不会为自定义渲染器管理 Tessellator）
            if (isGui) {
                t.startDrawingQuads();
            }

        boolean hasVertices = false;
        for (BakedQuad q : allQuads) {
            int baseBrightness = isGui ? 255 : getFaceBrightness(world, x, y, z, block, q.face);
            float baseShade = shadeByNormal(q.faceNormal);

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

                IIcon icon = (ctx.iconOverride != null) ? ctx.iconOverride : q.icon;
                if (isGui) {
                    // GUI 模式使用 RGBA 以正确处理纹理 alpha 通道
                    t.setColorRGBA_F(cr, cg, cb, 1.0f);
                } else {
                    t.setColorOpaque_F(cr, cg, cb);
                }
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
                RenderPhase.BLOCK_WORLD, null);
    }

    /**
     * 渲染方块的 quads（GUI 模式快捷方法）。
     */
    public static void renderBlockQuadsGUI(BlockStateModelPart part, Block block,
                                           Map<String, ModelJson.DisplayTransform> display) {
        renderBlockQuadsGUI(part, block, display, 0);
    }

    /**
     * 渲染方块的 quads（GUI 模式，带 metadata 支持）。
     * [S1] metadata 用于 Block.getRenderColor(metadata) 染色。
     */
    public static void renderBlockQuadsGUI(BlockStateModelPart part, Block block,
                                           Map<String, ModelJson.DisplayTransform> display,
                                           int metadata) {
        renderBlockQuads(part, null, 0, 0, 0, block, 0,
                RenderPhase.BLOCK_GUI, display, metadata);
    }

    /**
     * 渲染物品的 quads（GUI / 手持 / 掉落物），含 display transform 和 GL_LIGHTING 管理。
     * Display transform 由 {@link decok.dfcdvadstf.catframe.model.render.extension.DisplayTransformExtension}
     * 在扩展链中管理 GL 矩阵生命周期。
     *
     * @param part    渲染部件
     * @param stack   物品栈
     * @param phase   渲染阶段（ITEM_GUI / ITEM_HAND_* / DROPPED_* ）
     * @param world   世界上下文（仅 DROPPED_BLOCK_GROUND 等需要方块信息的阶段使用，其他传 null）
     * @param x       方块 X 坐标（无世界上下文时传 0）
     * @param y       方块 Y 坐标
     * @param z       方块 Z 坐标
     * @param block   方块实例（无时传 null）
     * @param display 可选的 display transforms（从 ModelJson.display 传入，可为 null，现已由扩展处理）
     */
    public static void renderItemQuads(BlockStateModelPart part,
                                       ItemStack stack, RenderPhase phase,
                                       IBlockAccess world, int x, int y, int z, Block block,
                                       Map<String, ModelJson.DisplayTransform> display) {
        Tessellator t = Tessellator.instance;
        List<BakedQuad> allQuads = part.getAllQuads();

        boolean gui = (phase == RenderPhase.ITEM_GUI);
        boolean dropped = (phase == RenderPhase.DROPPED_ITEM_GROUND
                || phase == RenderPhase.DROPPED_BLOCK_GROUND);

        // [C4 修复] 所有 GL 状态操作移入 try 块内，确保异常时 finally 能正确恢复
        try {
            // 禁用面剔除：builtin/generated 物品的侧面 quad 是 1px 薄片，
            // 旋转后绕序会翻转，如果开启面剔除会导致侧面在第一人称/掉落物等角度消失。
            // 对标 26.1.2 ItemModelGenerator 中 addUnculledFace 语义。
            GL11.glDisable(GL11.GL_CULL_FACE);
            // 根据物品类型绑定正确的 atlas：方块物品用 blocks atlas（spriteNumber=0），
            // 非方块物品用 items atlas（spriteNumber=1）。
            int spriteNumber = (stack != null && stack.getItem() != null)
                    ? stack.getItem().getSpriteNumber() : 1;
            Minecraft.getMinecraft().getTextureManager().bindTexture(
                    spriteNumber == 0
                            ? TextureMap.locationBlocksTexture
                            : TextureMap.locationItemsTexture);

            // GUI 视口变换（对齐高版本 GuiItemAtlas.drawToSlot）：
            //   1. translate(8,8,0) — 从 Forge 的槽位左上角移到槽位中心
            //   2. scale(16,-16,16) — 方块单位→像素，Y 取反对齐图像坐标系
            // 这两个变换必须在 DisplayTransformExtension（pushMatrix/popMatrix）
            // 之前应用，以确保逻辑顺序对齐高版本：
            //   T(-0.5) → S(display) → R(display) → T(display) → S(16) → T(slotCenter)
            // [C4+额外保护] GUI 视口变换也放入 try 块，用 pushMatrix/popMatrix 保护
            if (gui) {
                GL11.glPushMatrix();
                GL11.glTranslatef(8F, 8F, 0F);
                GL11.glScalef(16F, -16F, 16F);
            }
            // 掉落物和 GUI 都需要 alpha 混合（掉落物在世界中需要纹理透明，GUI 同理）
            if (gui || dropped) {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }

            // 生命周期：quad 处理前（DisplayTransformExtension 在此应用 display 变换）
            // 必须在槽位视口变换之后调用，确保 display 变换在正确的空间中执行
            ModelRenderRegistry.applyBeforePart(allQuads, phase);

            t.startDrawingQuads();

            int baseBrightness = gui ? 255 : 15728880;
            boolean hasVertices = false;

            for (BakedQuad q : allQuads) {
                // 物品渲染固定 shade = 1.0——不做法线方向阴影采样
                // （方块世界由 BLOCK_WORLD 路径的 shadeByNormal 处理）
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
                    // UV 坐标已在烘焙阶段对齐 IIcon 图像坐标系，直接使用
                    double U = icon.getInterpolatedU(q.up[i]);
                    double V = icon.getInterpolatedV(q.vp[i]);
                    double vx = q.vx[i], vy = q.vy[i], vz = q.vz[i];
                    t.addVertexWithUV(vx, vy, vz, U, V);
                }
            }

            // [W6] 仅在有顶点时 draw
            if (hasVertices) {
                t.draw();
            }
        } finally {
            // 恢复面剔除
            GL11.glEnable(GL11.GL_CULL_FACE);
            if (gui || dropped) {
                GL11.glDisable(GL11.GL_BLEND);
            }
            // [C4] GUI 视口变换恢复
            if (gui) {
                GL11.glPopMatrix();
            }
            // 生命周期：quad 处理后（GuiLightExtension/DisplayTransformExtension 在此恢复 GL 状态）
            ModelRenderRegistry.applyAfterPart();
        }
    }

    /**
     * 旧版兼容 — 无 display transform 时委托给新版。
     */
    public static void renderItemQuads(BlockStateModelPart part,
                                       ItemStack stack, RenderPhase phase) {
        renderItemQuads(part, stack, phase, null, 0, 0, 0, null, null);
    }

    /**
     * 旧版兼容 — 有 display transform 但无世界上下文时委托给新版。
     */
    public static void renderItemQuads(BlockStateModelPart part,
                                       ItemStack stack, RenderPhase phase,
                                       Map<String, ModelJson.DisplayTransform> display) {
        renderItemQuads(part, stack, phase, null, 0, 0, 0, null, display);
    }

    // ==================== 光照与阴影辅助 ====================

    /**
     * 根据面法线计算方向阴影系数。
     * 对齐原版 1.7.10 RenderBlocks 的 shade 值：
     * 顶面 1.0、底面 0.5、侧面 0.6/0.8。
     *
     * TODO [S6] 当前简化为三档分类（顶/底/侧），混合角度面光照不连续。
     * 原版 1.7.10 使用更复杂的角度 shade 计算，待后续对齐。
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
