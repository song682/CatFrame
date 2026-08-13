package decok.dfcdvadstf.catframe.model.render.api;

import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4d;
import java.util.List;

/**
 * 一次渲染提交的只读视图（retained command 的快照投影）。
 * <p>
 * 供分组认领 SPI（执行端）与外部模组读取渲染数据：覆盖 {@code RenderSubmit}
 * 的全部输入面，但<b>不暴露内部可变状态</b> —— 变换矩阵经防御性拷贝返回
 * （{@link #preTransformCopy()} / {@link #transformationCopy()}），
 * 内部类型 {@code BlockStateModelPart} 本身不泄露（以 {@link #getAllQuads()} 投影）。
 * <p>
 * A read-only projection of a retained render submission for external
 * consumers. Matrices are returned as defensive copies; the internal part
 * object is only reachable through its quad list.
 */
public interface RenderSubmitView {

    /** 渲染阶段（决定 brightness/gui 分支等）。 */
    RenderPhase phase();

    /** 分组类型（图集 + 混合 + 排序键）。 */
    RenderTypeKey type();

    /** 方块坐标（物品阶段通常为 0）。 */
    int x();

    /** 方块坐标（物品阶段通常为 0）。 */
    int y();

    /** 方块坐标（物品阶段通常为 0）。 */
    int z();

    /** Y 轴旋转角度（方块世界/GUI 使用，0/90/180/270）。 */
    int rotationDeg();

    /** 方块实例（物品阶段可为 null）。 */
    @Nullable
    Block block();

    /** 物品栈（方块阶段为 null）。 */
    @Nullable
    ItemStack stack();

    /** 世界访问（无世界上下文时为 null）。 */
    @Nullable
    IBlockAccess world();

    /** 方块 metadata（用于染色等场景）。 */
    int metadata();

    /** flush 时是否需要关闭面剔除（物品路径 true，方块路径 false）。 */
    boolean disableCull();

    /** flush 时是否需要开启混合（与 {@link #type()} 的 blend 一致）。 */
    boolean blend();

    /** 待渲染部件的全部 quad（只读约定：调用方不得修改）。 */
    List<BakedQuad> getAllQuads();

    /**
     * 预变换矩阵（反抵消）的防御性拷贝，可为 null。
     * 返回副本，修改不影响内部提交数据。
     */
    @Nullable
    Matrix4d preTransformCopy();

    /**
     * 物品模型渲染变换（items JSON {@code transformation} 标签）的防御性拷贝，可为 null。
     * 返回副本，修改不影响内部提交数据。
     */
    @Nullable
    Matrix4d transformationCopy();
}
