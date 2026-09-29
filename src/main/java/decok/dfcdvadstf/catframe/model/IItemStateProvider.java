package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.model.render.RenderJsonItemModel;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.property.ItemPropertyRegistry;
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
 *
 * <h3>实现即接入 / Implementation as declaration</h3>
 * 与方块侧「实现即接入」哲学对齐：物品注册到 {@code Item.itemRegistry} 且实现本接口，
 * 这个动作本身就是接入声明——
 * <ul>
 *   <li>纹理收集接口化：经 {@link #getDeclaredModelPaths()} 完成，无 instanceof 特判；</li>
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
     * Global switch: whether CatFrame takes over this item's rendering.
     * <p>
     * Returns {@code false} → CatFrame registers no IItemRenderer,
     * item stays on vanilla render path entirely.
     *
     * @return {@code true} to take over (default), {@code false} to leave to vanilla
     */
    default boolean shouldHandle() {
        return true;
    }

    /**
     * Per-phase fine control: whether CatFrame handles the given render phase.
     * <p>
     * Only effective when {@link #shouldHandle()} is {@code true}.
     * Returns {@code false} → that phase uses vanilla rendering.
     *
     * @param phase render phase
     * @return {@code true} to handle, {@code false} to use vanilla
     */
    default boolean handles(RenderPhase phase) {
        return true;
    }

    /**
     * Render item.
     *
     * @param stack item stack (with NBT, damage, etc.)
     * @param phase render context (GUI / handheld / dropped / etc.)
     */
    void render(ItemStack stack, RenderPhase phase);

    /**
     * Render item with pre-transform (counter-cancellation).
     * <p>
     * Pre-transform applied before pipeline's internal display transform, passed as Matrix4d.
     * Default impl delegates to {@link #render(ItemStack, RenderPhase)}.
     *
     * @param stack        item stack
     * @param phase        render context
     * @param preTransform pre-transform matrix (may be null), applied to vertices before display transform
     */
    default void render(ItemStack stack, RenderPhase phase,
                        @Nullable Matrix4d preTransform) {
        render(stack, phase);
    }

    /**
     * Return list of baked model parts this item selects in GUI phase — for GUI overflow detection
     * (oversized clamp) measuring geometry bounds.
     * <p>
     * Default returns empty list (treated as not oversized, no clamping). Decision-tree implementations
     * should evaluate GUI phase properties, collect {@link BlockStateModelPart} for selected paths.
     *
     * @param stack item stack
     * @return GUI-phase selected model parts (may be empty)
     */
    default List<BlockStateModelPart> getGuiModelParts(ItemStack stack) {
        return Collections.emptyList();
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
