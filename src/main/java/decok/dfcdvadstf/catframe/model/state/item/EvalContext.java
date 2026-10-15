package decok.dfcdvadstf.catframe.model.state.item;

import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/**
 * 决策树求值上下文，贯穿 {@link ItemStateNode#evaluate(java.util.Map, EvalContext)} 的递归求值。
 * <p>
 * 历史求值仅依赖属性集 {@code Map<String, Comparable<?>>}，足以驱动纯数据节点（model /
 * condition / range_dispatch / select / composite / empty）。特殊节点
 * （{@link ItemStateNode.SpecialNode}，对齐高版本 {@code SpecialRenderer#appendItemLayers}）
 * 需要在求值时读取当前 {@link ItemStack}（NBT 等）与渲染阶段，故引入本上下文并将其沿树向下传递。
 * <p>
 * Evaluation context threaded through the item-state decision tree. Ordinary data nodes only
 * need the property map; special nodes additionally read the live {@link ItemStack} and
 * {@link RenderPhase} at evaluation time, so the context is passed down the tree.
 * <p>
 * 允许为 {@code null}（例如纹理收集、tint 解析等非渲染求值路径），特殊节点在 {@code null}
 * 上下文下应退化为不产出动态图层。
 */
public final class EvalContext {

    @Nullable
    private final ItemStack stack;
    @Nullable
    private final RenderPhase phase;

    public EvalContext(@Nullable ItemStack stack, @Nullable RenderPhase phase) {
        this.stack = stack;
        this.phase = phase;
    }

    /** 当前正在渲染/求值的物品栈；非渲染路径可能为 {@code null}。 */
    @Nullable
    public ItemStack getStack() {
        return stack;
    }

    /** 当前渲染阶段；非渲染路径可能为 {@code null}。 */
    @Nullable
    public RenderPhase getPhase() {
        return phase;
    }
}
