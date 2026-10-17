package decok.dfcdvadstf.catframe.model.render.pipeline;

import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;

/**
 * Render scope facade, mirroring the "begin submit → accumulate → end flush" submission scope
 * in the vanilla 26w+ pipeline.
 * <p>
 * Holds the currently active {@link SubmitNodeStorage} in a {@link ThreadLocal} (client rendering
 * is single-threaded), and supports reentrant nesting (composite / dual models entering render
 * multiple times).
 *
 * <h3>Three submission paths</h3>
 * <ul>
 *   <li><b>World blocks</b> ({@link RenderPhase#BLOCK_WORLD}): [three-domain render architecture]
 *       the only path for the vanilla backend — writes inline via
 *       {@link FeatureRenderDispatcher#flushInline} into the current chunk Tessellator (the batch
 *       binds the vanilla blocks atlas, quads carry vanilla UVs, no GL calls, and it is naturally
 *       compatible with Beddium's multithreaded chunk compilation).</li>
 *   <li><b>Within a scope</b>: when there is an active scope, accumulate into
 *       {@link SubmitNodeStorage}; when the {@link #endScope()} count reaches zero, flush in
 *       batches according to the registry sort key.</li>
 *   <li><b>No-scope fallback</b>: any call path not wrapped by {@link #beginScope()} immediately
 *       flushes in batches as a "single-item scope" — exactly the same behavior as a single draw
 *       before the refactor, with zero regression.</li>
 * </ul>
 */
public final class RenderCommandBuffers {

    private RenderCommandBuffers() {
    }

    /** The current thread's active command buffer; null means there is no active scope. */
    private static final ThreadLocal<SubmitNodeStorage> ACTIVE = new ThreadLocal<>();
    /** Scope reentrancy depth (supports nesting). */
    private static final ThreadLocal<int[]> DEPTH = new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
            return new int[1];
        }
    };

    /**
     * Enters the render scope. On first entry (depth 0→1) the active command buffer is created;
     * nested entries only increment the count.
     */
    public static void beginScope() {
        int[] depth = DEPTH.get();
        if (depth[0] == 0) {
            ACTIVE.set(new SubmitNodeStorage());
        }
        depth[0]++;
    }

    /**
     * Exits the render scope. When the count reaches zero it flushes in batches and cleans up the
     * active buffer.
     * <p>
     * A flush always happens within the GL matrix context already established by the caller
     * (see the {@link RenderSubmit} class comment).
     */
    public static void endScope() {
        int[] depth = DEPTH.get();
        if (depth[0] == 0) {
            // Defensive: unmatched endScope
            return;
        }
        depth[0]--;
        if (depth[0] == 0) {
            SubmitNodeStorage storage = ACTIVE.get();
            ACTIVE.remove();
            if (storage != null && !storage.isEmpty()) {
                FeatureRenderDispatcher.flushBatched(storage);
            }
        }
    }

    /**
     * Submits one render command.
     *
     * @param s the immutable render snapshot
     */
    public static void submit(RenderSubmit s) {
        // World block rendering: [three-domain render architecture] the vanilla backend (the only
        // path) writes inline into the current chunk Tessellator — it does not bind a texture
        // (vanilla already bound the blocks atlas) and does not change GL state; quads carry the
        // vanilla atlas UVs and are naturally compatible with Beddium's background thread
        // (see FeatureRenderDispatcher.flushInline).
        if (s.phase == RenderPhase.BLOCK_WORLD) {
            FeatureRenderDispatcher.flushInline(s);
            return;
        }

        SubmitNodeStorage active = ACTIVE.get();
        if (active != null) {
            // Within a scope: accumulate, flush in batches later
            active.submit(s);
        } else {
            // No active scope: immediate single-item batched flush (equivalent to a single draw before the refactor, zero regression)
            SubmitNodeStorage single = new SubmitNodeStorage();
            single.submit(s);
            FeatureRenderDispatcher.flushBatched(single);
        }
    }
}
