package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.model.render.RenderJsonItemModel;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.property.CatItemProperties;
import decok.dfcdvadstf.catframe.model.state.property.ItemPropertyProvider;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4d;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 物品状态模型接口 — CatFrame 物品渲染的唯一抽象。
 * <p>
 * 本接口统一了原来的“状态发现接口”与“物品模型接口”两层抽象：
 * <ul>
 *   <li>作为<b>发现标记</b>：外部 {@link net.minecraft.item.Item} 实现此接口后，
 *       CatFrame 在 {@link ModelManagerDataLoader#init()} 阶段自动发现并收集纹理。</li>
 *   <li>作为<b>渲染模型</b>：实现类直接提供 {@link #render(ItemStack, RenderPhase)}
 *       与 {@link #handles(RenderPhase)}，由 {@link RenderJsonItemModel}
 *       在 Forge 渲染管线中调用。</li>
 * </ul>
 * <p>
 * 模型发现由四层体系处理（items/ ItemState → model_mappings → 约定路径），
 * 本接口同时承担发现与渲染职责。
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
 *   <li>{@link #render(ItemStack, RenderPhase)} — 实际渲染逻辑</li>
 * </ul>
 * <p>
 * 对标方块侧 {@link IBlockStateProvider#getStateDefinition()} 的可选声明钩子，
 * {@link #getPropertyDefinitions()} 允许实现类就地声明自定义物品属性，
 * 由发现阶段自动注册（见下方方法说明）。
 * <br>Mirroring the optional declaration hook {@link IBlockStateProvider#getStateDefinition()}
 * on the block side, {@link #getPropertyDefinitions()} lets implementations declare
 * custom item properties in place, auto-registered during discovery.
 */
public interface IItemStateProvider {

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
        return true;
    }

    /**
     * 渲染物品。
     *
     * @param stack 物品栈（含 NBT、damage 等信息）
     * @param phase 渲染上下文（GUI / 手持 / 掉落物等）
     */
    void render(ItemStack stack, RenderPhase phase);

    /**
     * 渲染物品，带预变换（反抵消）。
     * <p>
     * 预变换在管线内部的 display transform 之前应用，以 Matrix4d 向量空间矩阵形式传入。
     * 默认实现直接委托给 {@link #render(ItemStack, RenderPhase)}。
     *
     * @param stack        物品栈
     * @param phase        渲染上下文
     * @param preTransform 预变换矩阵（可为 null），在 display transform 之前作用于顶点
     */
    default void render(ItemStack stack, RenderPhase phase,
                        @Nullable Matrix4d preTransform) {
        render(stack, phase);
    }

    /**
     * 返回该物品在 GUI 阶段选中的烘焙模型部件列表 — 供 GUI 溢出检测（oversized 保护）测量几何边界。
     * <p>
     * 默认返回空列表（视为不超出槽位，不触发钳制）。决策树驱动的实现应求值 GUI 阶段属性、
     * 收集选中路径对应的 {@link BlockStateModelPart}。
     *
     * @param stack 物品栈
     * @return GUI 阶段选中的模型部件（可能为空）
     */
    default List<BlockStateModelPart> getGuiModelParts(ItemStack stack) {
        return Collections.emptyList();
    }

    /**
     * 可选声明本物品需要的自定义属性（{@code modid:name} → provider）。
     * <p>
     * 对标 {@link IBlockStateProvider#getStateDefinition()} 的接口化声明范式：
     * 实现类在此返回非空 Map 后，CatFrame 在 {@link ModelManagerDataLoader#init()}
     * 发现阶段经 {@link CatItemProperties} 校验并自动注册，实现类无需再手动调用注册 facade。
     * key 必须为完整命名空间形式（{@code modid:name}），裸名会被拒绝并记录警告。
     * <p>
     * Optionally declares the custom properties this item needs
     * ({@code modid:name} → provider). Mirrors the interface-based declaration
     * paradigm of {@link IBlockStateProvider#getStateDefinition()}: when a non-empty
     * map is returned, CatFrame validates and registers the entries through
     * {@link CatItemProperties} during discovery, so implementations never need to
     * call the registration facade manually. Keys must be fully namespaced
     * ({@code modid:name}); bare names are rejected with a warning.
     * <p>
     * 默认返回空 Map（不声明任何属性）。
     * <br>Default returns an empty map (no property declarations).
     *
     * @return 属性声明 Map（{@code modid:name} → provider），可为空
     */
    default Map<String, ItemPropertyProvider> getPropertyDefinitions() {
        return Collections.emptyMap();
    }
}
