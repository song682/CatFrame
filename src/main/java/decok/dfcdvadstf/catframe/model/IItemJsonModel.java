package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.model.render.RenderPhase;

/**
 * 物品 JSON 模型自动发现接口。
 * <p>
 * 外部 Item 实现此接口后，CatFrame 在 {@link VanillaModelManager.DataLoading#init()} 阶段
 * 自动完成：收集纹理 → 烘焙模型 → 注册 Forge IItemRenderer。
 * 无需手动编辑 {@code model_mappings.json} 或调用
 * {@link VanillaModelManager.TextureManagement#collectTextures}。
 *
 * <h3>三层发现优先级</h3>
 * <ol>
 *   <li>{@code model_mappings.json} 显式条目</li>
 *   <li>Item 实现 {@code IItemJsonModel}（本接口）</li>
 *   <li>约定路径 {@code assets/{namespace}/models/item/{name}.json} 懒发现</li>
 * </ol>
 *
 * <h3>渲染接管控制</h3>
 * <ul>
 *   <li>{@link #shouldHandle()} — 全局开关，{@code false} 时 CatFrame 完全不注册此物品</li>
 *   <li>{@link #handles(RenderPhase)} — 每阶段精细控制，{@code false} 时走原版渲染</li>
 * </ul>
 */
public interface IItemJsonModel {

    /**
     * 模型路径，相对于 {@code /assets/{namespace}/models/}。
     * 例如 {@code "item/my_sword"} 将解析为
     * {@code /assets/{namespace}/models/item/my_sword.json}。
     *
     * @return 模型路径，不可为 null
     */
    String getModelPath();

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
