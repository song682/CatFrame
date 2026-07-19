package decok.dfcdvadstf.catframe.model.render;

import decok.dfcdvadstf.catframe.model.render.pipeline.RenderCommandBuffers;
import decok.dfcdvadstf.catframe.model.render.pipeline.RenderSubmit;
import decok.dfcdvadstf.catframe.model.render.pipeline.RenderType;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4d;

/**
 * Uniform Render Pipeline —— 延迟命令管线的<b>提交端</b>。
 * <p>
 * 参照原版 26w+ 延迟渲染管线（Extract → Submit → Render），本类不再"立即写 Tessellator"，
 * 而是把每次渲染调用<b>构建为不可变 {@link RenderSubmit} 命令</b>并交给
 * {@link RenderCommandBuffers#submit(RenderSubmit)}：
 * <ul>
 *   <li>方块<b>世界</b>渲染（{@link RenderPhase#BLOCK_WORLD}）→ 内联即时写入当前 chunk
 *       Tessellator（原版 chunk 系统已完成批处理）；</li>
 *   <li>物品 / GUI 独立绘制路径 → 进入渲染作用域命令缓冲，按 {@link RenderType}
 *       排序批量 flush（solid→translucent、单次纹理绑定）。</li>
 * </ul>
 * <p>
 * 逐顶点写入循环已抽取到
 * {@link decok.dfcdvadstf.catframe.model.render.pipeline.QuadWriter}，
 * 命令执行 / 批量绘制在
 * {@link decok.dfcdvadstf.catframe.model.render.pipeline.FeatureRenderDispatcher}。
 * <p>
 * <b>所有 public 方法签名保持不变</b>，调用方无需改动。
 */
public final class UniformRenderPipeline {

    private UniformRenderPipeline() {
    }

    // ==================== 方块世界/GUI 渲染 ====================

    /**
     * 渲染方块的 quads（世界或 GUI）。构建 {@link RenderSubmit} 并提交至命令管线。
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
     * 由 {@link decok.dfcdvadstf.catframe.model.render.extension.DisplayTransformExtension}
     * 在扩展链中消费。
     *
     * @param part     渲染部件
     * @param metadata 方块 metadata（用于染色）
     */
    public static void renderBlockQuads(BlockStateModelPart part,
                                        IBlockAccess world, int x, int y, int z,
                                        Block block, int rotationDeg,
                                        RenderPhase phase,
                                        int metadata) {
        if (part == null) return;

        boolean isGui = (phase == RenderPhase.BLOCK_GUI);
        // 方块渲染绑定 blocks atlas；GUI 恒开混合（对齐改造前 renderBlockQuads GUI 路径），
        // 世界渲染不透明。方块路径均不关闭面剔除（disableCull=false）。
        RenderType type = isGui ? RenderType.BLOCK_ATLAS_TRANSLUCENT : RenderType.BLOCK_ATLAS_SOLID;

        RenderSubmit s = new RenderSubmit(
                phase, part, type,
                x, y, z, rotationDeg,
                block, null, world, metadata,
                null,
                false, isGui);
        RenderCommandBuffers.submit(s);
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
     *
     * @param metadata 方块 metadata（用于 Block.getRenderColor(metadata) 染色）
     */
    public static void renderBlockQuadsGUI(BlockStateModelPart part, Block block,
                                           int metadata) {
        renderBlockQuads(part, null, 0, 0, 0, block, 0,
                RenderPhase.BLOCK_GUI, metadata);
    }

    // ==================== 物品渲染 ====================

    /**
     * 渲染物品的 quads（GUI / 手持 / 掉落物 / 展示框）。构建 {@link RenderSubmit} 并提交。
     * <p>
     * Display transform 与 preTransform 已在扩展链 / 管线中以向量空间矩阵形式逐顶点烘焙进坐标，
     * 因此批量 flush 时无需 GL 矩阵快照。
     *
     * @param part         渲染部件
     * @param stack        物品栈
     * @param phase        渲染阶段（ITEM_GUI / ITEM_HAND_* / DROPPED_* / ITEM_FIXED）
     * @param world        世界上下文（仅 DROPPED_BLOCK_GROUND 等需要方块信息的阶段使用，其他传 null）
     * @param x            方块 X 坐标（无世界上下文时传 0）
     * @param y            方块 Y 坐标
     * @param z            方块 Z 坐标
     * @param block        方块实例（无时传 null）
     * @param preTransform 可选的预变换矩阵（反抵消），在 display transform 之后作用于顶点，可为 null
     */
    public static void renderItemQuads(BlockStateModelPart part,
                                       ItemStack stack, RenderPhase phase,
                                       IBlockAccess world, int x, int y, int z, Block block,
                                       @Nullable Matrix4d preTransform) {
        if (part == null) return;

        boolean gui = (phase == RenderPhase.ITEM_GUI);
        boolean blendRequired = (gui
                || phase == RenderPhase.DROPPED_ITEM_GROUND
                || phase == RenderPhase.DROPPED_BLOCK_GROUND
                || phase == RenderPhase.ITEM_FIXED);

        // 纹理图集选择：ItemBlock 的模型 quad 引用 blocks atlas，必须绑定 locationBlocksTexture；
        // 非方块物品绑定 locationItemsTexture。getSpriteNumber()==0 亦视作 blocks atlas。
        // 注意：getSpriteNumber() 不可靠——许多 ItemBlock 返回 1 导致绑错图集，故优先判 ItemBlock。
        boolean isBlockItem = (stack != null && stack.getItem() instanceof ItemBlock);
        int spriteNumber = (stack != null && stack.getItem() != null)
                ? stack.getItem().getSpriteNumber() : 1;
        boolean blockAtlas = (isBlockItem || spriteNumber == 0);

        RenderType type = RenderType.of(blockAtlas, blendRequired);

        // 物品路径恒关闭面剔除（对齐改造前 renderItemQuads 的 glDisable(GL_CULL_FACE)）
        RenderSubmit s = new RenderSubmit(
                phase, part, type,
                x, y, z, 0,
                block, stack, world, 0,
                preTransform,
                true, blendRequired);
        RenderCommandBuffers.submit(s);
    }

    /**
     * 旧版兼容 — 无 preTransform 且无世界上下文时委托给新版。
     */
    public static void renderItemQuads(BlockStateModelPart part,
                                       ItemStack stack, RenderPhase phase) {
        renderItemQuads(part, stack, phase, null, 0, 0, 0, null, (Matrix4d) null);
    }
}
