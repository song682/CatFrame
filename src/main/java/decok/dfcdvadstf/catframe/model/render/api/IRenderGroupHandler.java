package decok.dfcdvadstf.catframe.model.render.api;

import java.util.List;

/**
 * Render-group claim SPI — the execution-side extension point through which a
 * third-party mod takes over the whole flush of a render group.
 * <p>
 * Registration: register through the internal registry
 * ({@code pipeline.RenderGroupHandlerRegistry}); when several handlers claim the
 * same group, the one with the lowest priority wins (arbitration happens at
 * registration/unregistration time). Once a claim succeeds, all submissions of
 * that group are handed to the handler for drawing through the read-only
 * {@link RenderSubmitView}, and CatFrame no longer runs its built-in flush logic
 * (texture binding / GL state / per-part lifecycle are fully taken over by the handler).
 * <p>
 * <b>Contract (v0.5+)</b>:
 * <ul>
 *   <li><b>Threading</b>: flush always runs on the caller (render) thread — never submit GL calls across threads yourself.</li>
 *   <li><b>GL state</b>: managed by the handler — set as needed within the group (blend / cull / texture), restore the previous state after the group.</li>
 *   <li><b>Fallback rules</b>: no handler / claim() returns false / flush() throws (including Error) →
 *       fall back to the built-in flushGroup unchanged; zero behaviour change on the default path.</li>
 *   <li><b>Inline submissions cannot be claimed</b>: {@link RenderPhase#BLOCK_WORLD} world rendering uses
 *       the {@code flushInline} inline path (large Tessellator batches written into the vanilla chunk),
 *       and this SPI does not apply to that path — world geometry must be rerouted on the collection
 *       side (e.g. by a render scope) before it can be claimed inside an execution-side scope.</li>
 *   <li><b>Claim timing</b>: the claim() decision is invoked once during registration/unregistration
 *       arbitration; to switch claims dynamically at runtime, re-register that handler
 *       (re-registering = update the priority and re-arbitrate).</li>
 * </ul>
 *
 * <h3>OIT (Opaque-independent Translucency) boundary design (for external OIT mods to hook into)</h3>
 * <p>CatFrame's own deferred command pipeline already provides the OIT-like prerequisites
 * (submission/execution separation + retained {@code RenderSubmit}); the three real OIT
 * components belong to external mods:
 * <ul>
 *   <li><b>Collection side</b>: {@code RenderWorldEvent.Pre/Post} are standard Forge events that
 *       external mods can simply subscribe to (CatFrame needs to provide no hook).</li>
 *   <li><b>Execution side</b>: an external mod may claim translucent groups in
 *       {@code RenderWorldLastEvent} (e.g. {@code catframe:block_atlas_translucent}) →
 *       {@link #claim(RenderTypeKey)} of this SPI is the execution-side takeover entry point;
 *       or build its own pipeline.</li>
 *   <li><b>Gate side</b>: mod blocks enter the VMM pipeline through ISBRH, while vanilla blocks
 *       need an external mod to mixin them itself — CatFrame provides no world gating.</li>
 *   <li><b>Zero GL on background threads</b> (Beddium multithreaded chunk compilation): compiling
 *       display lists during collection violates GL state ownership → an external implementation
 *       should bake geometry on the CPU and compile lazily on the render thread.</li>
 * </ul>
 */
public interface IRenderGroupHandler {

    /**
     * Claim decision: returning true means this handler takes over the flush of that group.
     * Arbitration runs once at registration/unregistration time; implementations should stay
     * lightweight and free of side effects.
     *
     * @param type the group entry handle (id / atlas / blend / sortKey are read-only)
     */
    boolean claim(RenderTypeKey type);

    /**
     * Draws the whole group once the claim succeeds (processes all submissions in the given order).
     * Thrown exceptions (including Error) are isolated and fall back to the built-in flushGroup.
     *
     * @param group all submissions of the group (read-only view; the caller must not modify it)
     */
    void flush(List<RenderSubmitView> group);
}
