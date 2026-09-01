package decok.dfcdvadstf.catframe.model.state;

import decok.dfcdvadstf.catframe.model.core.baking.ModelBaker;
import decok.dfcdvadstf.catframe.model.state.block.MultipartBlockModel;
import decok.dfcdvadstf.catframe.model.state.block.ResidentStateModel;
import decok.dfcdvadstf.catframe.model.state.block.StateProviderBlockModel;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * 方块状态模型接口。类似于 1.21.5 的 BlockStateModel，
 * 负责将方块状态（world + x/y/z + metadata/CatBlockState）解析为一个渲染部件集合。
 *
 * <p>实现类：
 * <ul>
 *   <li>{@link ResidentStateModel} — 常驻 State 表统一实现（variants / multipart / 动态属性合一）</li>
 *   <li>{@link StateProviderBlockModel} — 委托 IBlockStateProvider（字符串属性映射）</li>
 *   <li>{@link MultipartBlockModel} — multipart 条件组合</li>
 * </ul>
 */
public interface BlockStateModel {

    /**
     * 通过 metadata 收集渲染部件（旧路径，保留向后兼容）。
     *
     * @param world    世界（可为 null，物品渲染场景）
     * @param x        方块 X 坐标
     * @param y        方块 Y 坐标
     * @param z        方块 Z 坐标
     * @param metadata 方块 metadata
     * @return 渲染部件集合
     */
    BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, int metadata);

    /**
     * 收集渲染部件的同时带回匹配期构造的方块状态属性（如 pane 的 north/east/south/west）。
     * <p>
     * 默认实现委托 {@link #collectParts(IBlockAccess, int, int, int, int)} 且属性为 null；
     * 实现类在已计算属性时覆写本方法复用同一 Map，避免二次计算。
     * 返回的属性 Map 为只读约定（不可修改视图），仅供本次提交携带。
     *
     * @param world    世界（可为 null，物品渲染场景）
     * @param x        方块 X 坐标
     * @param y        方块 Y 坐标
     * @param z        方块 Z 坐标
     * @param metadata 方块 metadata
     * @return 部件 + 属性（属性可为 null）
     */
    default CollectedPart collectPartsWithProps(IBlockAccess world, int x, int y, int z, int metadata) {
        return new CollectedPart(collectParts(world, x, y, z, metadata), null);
    }

    /**
     * 收集结果：渲染部件 + 匹配期构造的方块状态属性（可为 null）。
     * <p>
     * 属性 Map 仅存活于单次渲染提交生命周期，调用方不得修改、不得跨帧保留。
     */
    final class CollectedPart {
        public final BlockStateModelPart part;
        @Nullable
        public final Map<String, String> blockstateProps;

        public CollectedPart(BlockStateModelPart part,
                             @Nullable Map<String, String> blockstateProps) {
            this.part = part;
            this.blockstateProps = blockstateProps;
        }
    }

    /**
     * v0.3.0: 通过 {@link CatBlockState} 收集渲染部件（新路径）。
     * 默认实现委托给 metadata 版本，子类可覆盖以获得类型安全的属性匹配。
     *
     * @param world 世界（可为 null，物品渲染场景）
     * @param x     方块 X 坐标
     * @param y     方块 Y 坐标
     * @param z     方块 Z 坐标
     * @param state CatBlockState 实例
     * @return 渲染部件集合
     */
    default BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, CatBlockState state) {
        return collectParts(world, x, y, z, 0);
    }

    /**
     * 此模型是否完全覆盖方块的渲染。
     * true=替换整个方块渲染, false=叠加层（与 autoOverlay 对应）。
     */
    default boolean isFullModel() {
        return true;
    }

    // ==================== 新增（对齐 26.1.2） ====================

    /**
     * 随机源收集部件——支持 WeightedVariants 的随机选择。
     * 默认实现委托给旧版 collectParts。
     *
     * @param random 随机源
     * @param output 收集输出列表
     */
    default void collectParts(Random random, List<BlockStateModelPart> output) {
        BlockStateModelPart part = collectParts(null, 0, 0, 0, 0);
        if (part != null && !part.isEmpty()) {
            output.add(part);
        }
    }

    /**
     * 粒子纹理材质。默认实现返回 null。
     *
     * @return 粒子用的 IIcon，可为 null
     */
    @Nullable
    default IIcon particleIcon() {
        return null;
    }

    // ==================== Unbaked 子接口 ====================

    /**
     * Unbaked 模型接口——尚未烘焙的 BlockStateModel 定义。
     * 对标 26.1.2 的 {@code BlockStateModel.Unbaked}。
     */
    interface Unbaked {
        /**
         * 烘焙为完整的 BlockStateModel。
         *
         * @param baker ModelBaker 实例
         * @return 烘焙后的 BlockStateModel
         */
        BlockStateModel bake(ModelBaker baker);

        /**
         * 解析依赖：将依赖的模型路径通知给 consumer。
         *
         * @param dependencyConsumer 接收依赖模型路径的 consumer
         */
        void resolveDependencies(Consumer<String> dependencyConsumer);
    }
}
