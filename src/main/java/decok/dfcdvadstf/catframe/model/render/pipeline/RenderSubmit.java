package decok.dfcdvadstf.catframe.model.render.pipeline;

import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.api.RenderSubmitView;
import decok.dfcdvadstf.catframe.model.render.api.RenderTypeKey;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4d;
import java.util.List;

/**
 * 一次渲染提交的不可变数据快照，对标原版 26w+ 管线中的 {@code Submit} 命令 record。
 * <p>
 * 纯数据、无 GPU 逻辑：承载 {@link QuadWriter} 复现"逐顶点写入循环"所需的全部输入。
 * 由 {@link decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline} 构建，
 * 经 {@link RenderCommandBuffers#submit(RenderSubmit)} 进入命令缓冲或即时 flush。
 * <p>
 * display transform 与 preTransform 已在 Java 侧逐顶点烘焙进坐标（向量空间），
 * 因此 flush 时无需 GL 矩阵快照 —— 作用域的 flush 恒发生在调用方已建立的 GL 矩阵上下文内。
 */
public final class RenderSubmit implements RenderSubmitView {

    /** 渲染阶段（决定 brightness/gui 分支等）。 */
    public final RenderPhase phase;
    /** 待渲染部件（提供 {@code getAllQuads()}）。 */
    public final BlockStateModelPart part;
    /** 分组类型（图集 + 混合）。 */
    public final RenderType type;

    /** 方块坐标（物品阶段通常为 0）。 */
    public final int x, y, z;
    /** Y 轴旋转角度（方块世界/GUI 使用，0/90/180/270）。 */
    public final int rotationDeg;

    /** 方块实例（物品阶段可为 null）。 */
    @Nullable
    public final Block block;
    /** 物品栈（方块阶段为 null）。 */
    @Nullable
    public final ItemStack stack;
    /** 世界访问（无世界上下文时为 null）。 */
    @Nullable
    public final IBlockAccess world;
    /** 方块 metadata（用于 BLOCK_GUI 染色等）。 */
    public final int metadata;

    /** 预变换矩阵（反抵消），在 display transform 之后逐顶点应用，可为 null。 */
    @Nullable
    public final Matrix4d preTransform;

    /**
     * 物品模型渲染变换（items JSON {@code minecraft:model} 节点的 {@code transformation} 标签），
     * 在 display transform 之后、preTransform 之前逐顶点应用，可为 null（单位变换）。
     * <p>Per-model item transformation (the {@code transformation} tag), applied after
     * the display transform and before preTransform; null means identity.
     */
    @Nullable
    public final Matrix4d transformation;

    /** flush 时是否需要关闭面剔除（物品路径 true，方块路径 false）。 */
    public final boolean disableCull;
    /** flush 时是否需要开启混合（与 {@link RenderType#blend()} 一致）。 */
    public final boolean blend;

    public RenderSubmit(RenderPhase phase, BlockStateModelPart part, RenderType type,
                        int x, int y, int z, int rotationDeg,
                        @Nullable Block block, @Nullable ItemStack stack,
                        @Nullable IBlockAccess world, int metadata,
                        @Nullable Matrix4d preTransform,
                        @Nullable Matrix4d transformation,
                        boolean disableCull, boolean blend) {
        this.phase = phase;
        this.part = part;
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.rotationDeg = rotationDeg;
        this.block = block;
        this.stack = stack;
        this.world = world;
        this.metadata = metadata;
        this.preTransform = preTransform;
        this.transformation = transformation;
        this.disableCull = disableCull;
        this.blend = blend;
    }

    // ==================== RenderSubmitView（只读视图实现） ====================

    @Override
    public RenderPhase phase() {
        return phase;
    }

    @Override
    public RenderTypeKey type() {
        return type;
    }

    @Override
    public int x() {
        return x;
    }

    @Override
    public int y() {
        return y;
    }

    @Override
    public int z() {
        return z;
    }

    @Override
    public int rotationDeg() {
        return rotationDeg;
    }

    @Override
    public Block block() {
        return block;
    }

    @Override
    public ItemStack stack() {
        return stack;
    }

    @Override
    public IBlockAccess world() {
        return world;
    }

    @Override
    public int metadata() {
        return metadata;
    }

    @Override
    public boolean disableCull() {
        return disableCull;
    }

    @Override
    public boolean blend() {
        return blend;
    }

    @Override
    public List<BakedQuad> getAllQuads() {
        return part.getAllQuads();
    }

    @Override
    public Matrix4d preTransformCopy() {
        return preTransform == null ? null : new Matrix4d(preTransform);
    }

    @Override
    public Matrix4d transformationCopy() {
        return transformation == null ? null : new Matrix4d(transformation);
    }
}
