package decok.dfcdvadstf.catframe.model.state.item;

import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Decision tree evaluation context, threaded through the recursive evaluation of
 * {@link ItemStateNode#evaluate(java.util.Map, EvalContext)}.
 * <p>
 * Historically evaluation depended only on the property set
 * {@code Map<String, Comparable<?>>}, enough to drive pure data nodes (model /
 * condition / range_dispatch / select / composite / empty). Special nodes
 * ({@link ItemStateNode.SpecialNode}, aligned with the modern {@code SpecialRenderer#appendItemLayers})
 * need to read the current {@link ItemStack} (NBT etc.) and the render phase at evaluation
 * time, so this context is introduced and threaded down the tree.
 * <p>
 * Evaluation context threaded through the item-state decision tree. Ordinary data nodes only
 * need the property map; special nodes additionally read the live {@link ItemStack} and
 * {@link RenderPhase} at evaluation time, so the context is passed down the tree.
 * <p>
 * May be {@code null} (e.g. non-rendering evaluation paths such as texture collection and
 * tint resolution); special nodes should degrade to producing no dynamic layers under a
 * {@code null} context.
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

    /** The item stack currently being rendered/evaluated; may be {@code null} on non-rendering paths. */
    @Nullable
    public ItemStack getStack() {
        return stack;
    }

    /** The current render phase; may be {@code null} on non-rendering paths. */
    @Nullable
    public RenderPhase getPhase() {
        return phase;
    }
}
