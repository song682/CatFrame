package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.model.render.RenderPhase;

/**
 * 物品状态映射接口 — 对齐方块侧的 BlockState 体系。
 * <p>
 * 外部 Item 实现此接口后，CatFrame 在 {@link VMMDataLoader#init()} 阶段
 * 自动完成：收集纹理 → 烘焙模型 → 注册 Forge IItemRenderer。
 * <p>
 * 模型发现由四层体系处理（items/ ItemState → model_mappings → 约定路径），
 * 本接口专注于渲染接管控制。
 *
 * <h3>四层发现优先级</h3>
 * <ol>
 *   <li>{@code items/{item}.json} (ItemState 决策树) — 最高优先</li>
 *   <li>{@code model_mappings.json} items 字段 — 旧扁平映射</li>
 *   <li>Item 实现 {@code IItemState}（本接口）— 代码级注册</li>
 *   <li>约定路径 {@code assets/{namespace}/models/item/{name}.json} — 懒发现兜底</li>
 * </ol>
 *
 * <h3>渲染接管控制</h3>
 * <ul>
 *   <li>{@link #shouldHandle()} — 全局开关，{@code false} 时 CatFrame 完全不注册此物品</li>
 *   <li>{@link #handles(RenderPhase)} — 每阶段精细控制，{@code false} 时走原版渲染</li>
 * </ul>
 */
public interface IItemState {

    /**
     * 全局开关：是否由 CatFrame 接管此物品的渲染。
     * <p>
     * 返回 {@code false} 时，CatFrame 不注册 Forge IItemRenderer，
     * 物品完全走原版渲染路径。
     *
     * @return {@code true} 接管（默认），{@code false} 让原版接管
     */
    default boolean shouldHandle() {
        return true;
    }

    /**
     * 每阶段精细控制：指定的渲染阶段是否由 CatFrame 接管。
     * <p>
     * 仅在 {@link #shouldHandle()} 为 {@code true} 时有效。
     * 返回 {@code false} 时，该阶段走原版渲染。
     *
     * @param phase 渲染阶段
     * @return {@code true} 接管，{@code false} 走原版
     */
    default boolean handles(RenderPhase phase) {
        return phase != RenderPhase.ITEM_HAND_FIRST_PERSON;
    }
}
