package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.model.state.CatBlockState;
import net.minecraft.world.IBlockAccess;

/**
 * 方块状态模型接口。类似于 1.21.5 的 BlockStateModel，
 * 负责将方块状态（world + x/y/z + metadata/CatBlockState）解析为一个渲染部件集合。
 *
 * <p>实现类：
 * <ul>
 *   <li>{@link SingleBlockModel} — 单一静态模型（metadata 不影响形状）</li>
 *   <li>{@link MetadataBlockModel} — metadata → variant 调度</li>
 *   <li>{@link StateProviderBlockModel} — 委托 IBlockStateProvider（字符串属性映射）</li>
 *   <li>{@link StateBlockModel} — 基于 CatBlockState 的类型安全属性调度</li>
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
     * true=替换整个方块渲染, false=叠加层（与 RenderJsonBlock 的 autoOverlay 对应）。
     */
    default boolean isFullModel() {
        return true;
    }
}
