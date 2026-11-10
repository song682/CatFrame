package decok.dfcdvadstf.catframe.model.state;

/**
 * Render predicate — an independent veto on whether a block state should emit geometry,
 * aligned with modern vanilla's {@code BlockBehaviour#skipRendering} / render-shape checks.
 *
 * <p>Kept separate from {@link StateUpdater} on purpose: deciding <i>what</i> the state is
 * (update) and deciding <i>whether</i> to draw it (predicate) are two concerns. The canonical
 * example is the double plant's upper half, which suppresses rendering when the lower half
 * below it is missing (vanilla {@code renderBlockDoublePlant} parity, avoiding a phantom upper
 * half during the post-break sync window).
 *
 * <p>Common-side: depends only on {@link StateUpdateContext} and {@link BlockState}. A base-class
 * fragment may ship a predicate that subclasses inherit; returning {@code false} makes the
 * resident model emit an empty part, which the render caller treats as "nothing to draw".
 */
@FunctionalInterface
public interface RenderPredicate {

    /**
     * Returns whether the given state should be rendered in the given context.
     *
     * @param ctx   the read-only world / position context (world may be null)
     * @param state the resolved state (base state, or the updater's result when one is bound)
     * @return {@code true} to render, {@code false} to suppress
     */
    boolean shouldRender(StateUpdateContext ctx, BlockState state);
}
