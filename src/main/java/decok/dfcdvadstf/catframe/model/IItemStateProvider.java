package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.model.render.RenderJsonItemModel;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateModel;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;
import decok.dfcdvadstf.catframe.model.state.property.ItemPropertyRegistry;
import decok.dfcdvadstf.catframe.model.state.property.ItemPropertyProvider;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 物品状态声明接口 — CatFrame 物品侧的唯一接入抽象。
 * <p>
 * 本接口只承担<b>声明与数据供给</b>职责，不含任何渲染行为：
 * <ul>
 *   <li>作为<b>发现标记</b>：外部 {@link net.minecraft.item.Item} 实现此接口后，
 *       CatFrame 在 {@link ModelManagerDataLoader#init()} 阶段自动发现并收集纹理。</li>
 *   <li>作为<b>数据供给</b>：实现类交付决策树（{@link #getItemStateRoot()}）
 *       或 ItemState JSON 定位（{@link #getItemStateNamespace()} /
 *       {@link #getItemStateName()}）；实际渲染由 {@link RenderJsonItemModel} 驱动
 *       管线侧的 {@link ItemStateModel} 完成。</li>
 * </ul>
 * <p>
 * 与方块侧 {@link IBlockStateProvider}（纯声明、零渲染行为）保持对称：
 * 「该不该由 CatFrame 渲染」是渲染系统与注册表的路由决策，不由本接口回答。
 * Mirrors the block-side {@link IBlockStateProvider} (pure declaration, no render
 * behavior): whether CatFrame renders an item is a routing decision owned by the
 * render pipeline and the registry, never by this interface.
 * <p>
 * 模型发现由四层体系处理（items/ ItemState → model_mappings → 约定路径），
 * 本接口是其中的代码级声明层。
 *
 * <h3>四层发现优先级</h3>
 * <ol>
 *   <li>{@code items/{item}.json} (ItemState 决策树) — 最高优先</li>
 *   <li>{@code model_mappings.json} items 字段 — 旧扁平映射</li>
 *   <li>Item 实现 {@code IItemStateProvider}（本接口）— 代码级注册</li>
 *   <li>约定路径 {@code assets/{namespace}/models/item/{name}.json} — 懒发现兜底</li>
 * </ol>
 *
 * <h3>接入即接管 / Implementation as takeover</h3>
 * 实现本接口即表示该物品交由 CatFrame 渲染域处理；不希望被接管就不要实现本接口
 * （注册表中无对应模型时，渲染自动回落到原版路径）。
 * 阶段级差异（GUI / 手持 / 掉落该用哪个模型）属于<b>数据</b>，
 * 由决策树按 {@code display_context} 属性分支表达，不由接口方法表达。
 * <p>
 * 对标方块侧 {@link IBlockStateProvider#getStateDefinition()} 的可选声明钩子，
 * {@link #getPropertyDefinitions()} 允许实现类就地声明自定义物品属性，
 * 由发现阶段自动注册（见下方方法说明）。
 * <br>Mirroring the optional declaration hook {@link IBlockStateProvider#getStateDefinition()}
 * on the block side, {@link #getPropertyDefinitions()} lets implementations declare
 * custom item properties in place, auto-registered during discovery.
 *
 * <h3>实现即接入 / Implementation as declaration</h3>
 * 与方块侧「实现即接入」哲学对齐：物品注册到 {@code Item.itemRegistry} 且实现本接口，
 * 这个动作本身就是接入声明——
 * <ul>
 *   <li>纹理收集接口化：经 {@link #getDeclaredModelPaths()} 完成，无 instanceof 特判；</li>
 *   <li>代码建树可就地交付（{@link #getItemStateRoot()}），适用于无 {@code items/} JSON 的物品；</li>
 *   <li>ItemState JSON 路径可显式声明（{@link #getItemStateNamespace()} /
 *       {@link #getItemStateName()}），缺省部分从注册名推导，位于标准路径即被自动加载；</li>
 *   <li>迟到的注册与声明在下一轮纹理缝合自动补票；</li>
 *   <li>显式注册入口仍然保留，但不再是接入的前提。</li>
 * </ul>
 * Aligned with the block-side philosophy: a registered item implementing this interface
 * IS the declaration — texture collection is interface-driven
 * ({@link #getDeclaredModelPaths()}), the ItemState JSON path may be declared explicitly
 * or fall back to the registry name, late registrations are picked up on the next
 * texture stitch, and explicit registration stays optional, never a prerequisite.
 */
public interface IItemStateProvider {

    /**
     * Deliver the ItemState decision tree root built in code, or {@code null} to let
     * CatFrame resolve the tree from {@code assets/<namespace>/items/<name>.json}
     * (located via {@link #getItemStateNamespace()} / {@link #getItemStateName()}).
     * <p>
     * Mirrors the block-side data-delivery hook {@link IBlockStateProvider#getBlockState}:
     * the interface supplies <b>data</b>; evaluation and quad submission belong to the
     * pipeline-side {@link ItemStateModel}. The delivered root is consumed at the code-level
     * discovery tier, so an {@code items/} JSON tree resolved for the same item still wins
     * (four-tier priority is unchanged).
     *
     * @return decision tree root node, or {@code null} to fall back to the ItemState JSON
     */
    @Nullable
    default ItemStateNode getItemStateRoot() {
        return null;
    }

    /**
     * Optionally declares the custom properties this item needs
     * ({@code modid:name} → provider). Mirrors the interface-based declaration
     * paradigm of {@link IBlockStateProvider#getStateDefinition()}: when a non-empty
     * map is returned, CatFrame validates and registers the entries through
     * {@link ItemPropertyRegistry} during discovery, so implementations never need to
     * call the registration facade manually. Keys must be fully namespaced
     * ({@code modid:name}); bare names are rejected with a warning.
     * <p>
     * Default returns an empty map (no property declarations).
     *
     * @return property declaration Map ({@code modid:name} → provider), may be empty
     */
    default Map<String, ItemPropertyProvider> getPropertyDefinitions() {
        return Collections.emptyMap();
    }

    /**
     * Optional namespace of this item's ItemState JSON
     * ({@code assets/<namespace>/items/<name>.json}). Mirrors the block-side
     * {@link IBlockStateProvider#getBlockstateNamespace()}: an empty string falls
     * back to the item's registry name (no colon → {@code minecraft}), and explicit
     * declarations win.
     *
     * @return ItemState namespace; empty string means derive from registry name
     */
    default String getItemStateNamespace() {
        return "";
    }

    /**
     * Optional name part of this item's ItemState JSON, paired with
     * {@link #getItemStateNamespace()}. Falls back to the name part of the item's
     * registry name when empty; explicit declarations win.
     *
     * @return ItemState name; empty string means derive from registry name
     */
    default String getItemStateName() {
        return "";
    }

    /**
     * Declare model paths this item needs textures for (interface-driven texture collection,
     * replacing the historical instanceof special case).
     * <p>
     * Implementation-as-declaration: discovery pass reruns this on every texture stitch
     * (collection sets are idempotent), so late declarations are picked up on the
     * next pass; returned paths are routed to the block/item atlas by prefix.
     *
     * @return list of model paths (may include namespace), may be empty
     */
    default List<String> getDeclaredModelPaths() {
        return Collections.emptyList();
    }
}
