package decok.dfcdvadstf.catframe.model.state;

/**
 * Typed state updater — the render-time second phase of the blockstate pipeline, aligned with
 * modern vanilla's {@code BlockBehaviour#updateShape} / {@code getStateForPlacement}.
 *
 * <p>A {@link BlockStateDefinition} precomputes the <b>base state</b> for every metadata value
 * (the resident {@code resolvedByMeta} table). Properties whose values depend on the surrounding
 * world rather than on the block's own 4-bit metadata (a stair's {@code shape}, a pane's
 * {@code north/east/south/west}, a door's cross-half {@code facing}/{@code hinge}/{@code open}) or
 * on a tile entity (a flower pot's {@code plant}) cannot be decoded from meta; 1.7.10 has no room
 * for them and no neighbor-change refresh hook, so they are resolved at render time by an updater
 * that reads the world through the read-only {@link StateUpdateContext} and returns a new
 * {@link BlockState} via {@link BlockState#setValue}.
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>The incoming {@code state} is the base state decoded from meta. An updater reads the
 *       values it needs from that state (typed {@link BlockState#getValue}) — it must not
 *       re-decode the block's own metadata with raw bit math.</li>
 *   <li>The updater returns the updated state (O(1) jump-table transitions, no allocation).
 *       Returning the same instance means "nothing to update".</li>
 *   <li>Returning {@code null} is an error: the render side logs it once and falls back to the
 *       base state.</li>
 *   <li>An updater may set any declared property. Setting a property that is itself decodable
 *       from meta is reported once (warning) and the updater's result wins.</li>
 *   <li>{@link StateUpdateContext#world} may be {@code null} (item / GUI preview, coordinate-less
 *       particle resolution); implementations must tolerate this and typically return the base
 *       state unchanged.</li>
 * </ul>
 *
 * <p>Common-side: depends only on {@link StateUpdateContext} and {@link BlockState}, so a
 * definition can expose an updater without pulling in client-only classes.
 */
@FunctionalInterface
public interface StateUpdater {

    /**
     * Computes the updated state from the base state and the world context.
     *
     * @param ctx   the read-only world / position context (world may be null)
     * @param state the base state decoded from metadata
     * @return the updated state, or {@code null} to signal an error (base state is used)
     */
    BlockState update(StateUpdateContext ctx, BlockState state);
}
