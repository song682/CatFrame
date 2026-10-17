package decok.dfcdvadstf.catframe.model.render.extension;

import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.api.RenderContext;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.pipeline.RenderPhasePolicy;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;

import java.util.List;

/**
 * Built-in extension: phase-default brightness policy (light policy is centralized at the end
 * of the extension chain).
 * <p>
 * Takes over the original brightness resolution responsibility from the submission side
 * (UniformRenderPipeline / RenderDispatcher): the baseline brightness for each submit item
 * (GUI: always 255; held item: world light from the holder's position; dropped items and
 * item frames: world light from the entity's position; block-world AO-degenerate quads: block
 * mixed brightness) is written into {@link RenderContext#brightnessOverride} and consumed by
 * the emitter via {@link RenderContext#effectiveBrightness()}. The QuadWriter and submission
 * side no longer make any phase brightness decisions (DBF-oriented: policy moved to the
 * extension side).
 * <p>
 * <b>Relay protocol (why not compute directly in apply)</b>: Item-phase brightness is a
 * <b>per-submit-item constant</b> (shared by all quads in the same submission), whereas
 * {@code apply} runs per quad. Computing it in {@code apply} would repeat world-light lookups
 * N times for N quads of the same model. Therefore:
 * <ol>
 *   <li>{@code beforePart} (once per submit item) computes the item-phase brightness and stores
 *       it in a {@link ThreadLocal} (relay cache);</li>
 *   <li>{@code apply} (per quad) reads the cache and writes to
 *       {@code brightnessOverride} (integer assignment, no lookup);</li>
 *   <li>{@code afterPart} clears the cache (same cleanup protocol as GuiLightExtension to prevent
 *       cross-submit-item leakage).</li>
 * </ol>
 * ThreadLocal is used because the rendering path can enter from any thread (Beddium multithreaded
 * chunk compilation), and submit items alternate on the same thread (flushGroup:
 * beforePart→apply×N→afterPart); instance fields would overwrite each other (the extension chain
 * container's COW also requires extension instances to have no cross-thread shared mutable state).
 * <p>
 * <b>Phase branches</b>:
 * <ul>
 *   <li>Item phase (non-block, non-destroy) → write relay value in beforePart; if relay is
 *       missing (extreme edge path), do not write, fall back to ctx baseline;</li>
 *   <li>{@code BLOCK_WORLD} → only AO-degenerate quads ({@code aoBrightness[0] < 0}, non-vertex
 *       AO path) compute block mixed brightness on-the-fly (degenerate quads are few, cost
 *       negligible; overlaps with AOShadeExtension's {@code ambientOcclusion=false} branch are
 *       harmless); vertex-AO quads go through the aoBrightness channel from the emitter and do
 *       not pass through this extension;</li>
 *   <li>{@code BLOCK_DESTROY} → skip (block-destroy crack overlay full brightness is fully
 *       managed by BlockDestroyExtension).</li>
 * </ul>
 * Value source is {@link RenderPhasePolicy#baselineBrightness} (the single computation utility,
 * bit-for-bit semantic replica of the pre-migration QuadWriter branches). Once this extension is
 * active, its override always takes precedence over {@code RenderSubmit.baselineBrightness}.
 * <p>
 * Built-in extension: the phase-default brightness policy, written into brightnessOverride per
 * quad from a beforePart-computed relay value.
 */
public final class LightPolicyExtension implements IModelRenderExtension {

    /**
     * Relay cache for submit-item-level brightness computed in beforePart: null means this
     * submit item has no submit-level constant (block destroy / edge case skip), so apply
     * does not write override.
     */
    private final ThreadLocal<Integer> pending = new ThreadLocal<>();

    @Override
    public void beforePart(List<BakedQuad> allQuads, RenderPhase phase, BlockStateModelPart part) {
        if (isItemPhase(phase)) {
            // Item-phase brightness is a submit-level constant: GUI is always 255 (cheap);
            // held/dropped/item-frame brightness is determined inside RenderPhasePolicy via
            // Minecraft player singleton and RenderJsonItemModel's entity context (write-phase
            // flush and submit occur in the same entity context; see RenderPhasePolicy).
            pending.set(RenderPhasePolicy.baselineBrightness(phase, null, 0, 0, 0, null));
        } else {
            // BLOCK_WORLD / BLOCK_DESTROY / null: no submit-level constant (block brightness
            // requires coordinates and can only be computed per degenerate quad in apply;
            // destroy is fully managed by BlockDestroyExtension).
            pending.remove();
        }
    }

    @Override
    public void apply(RenderContext ctx) {
        if (ctx.phase == RenderPhase.BLOCK_WORLD) {
            // Vertex-AO quads (aoBrightness[0] >= 0) are consumed directly by the emitter
            // and do not go through this path; only degenerate/uncomputed quads need default
            // block brightness (same submit and same coordinates => same value; triggered only
            // in small quantities, equivalent to AOShadeExtension's ambientOcclusion=false branch).
            if (ctx.aoBrightness[0] < 0 && ctx.world != null && ctx.block != null) {
                ctx.brightnessOverride = ctx.block.getMixedBrightnessForBlock(
                        ctx.world, ctx.x, ctx.y, ctx.z);
            }
            return;
        }
        if (!isItemPhase(ctx.phase)) {
            // BLOCK_DESTROY: block-destroy crack overlay full brightness is managed by
            // BlockDestroyExtension (constant 15728880).
            return;
        }
        Integer v = pending.get();
        if (v != null) {
            ctx.brightnessOverride = v;
        }
        // If pending is null (beforePart skipped due to edge case), do not write override;
        // brightness falls back to ctx baseline (QuadWriter's -1 fallback chain).
    }

    @Override
    public void afterPart() {
        pending.remove();
    }

    /**
     * Whether the render phase is an item render phase (not block world / not block destroy).
     * Unlike {@link RenderPhasePolicy#isItemGlLit}, GUI and held-item phases also require
     * default brightness (255 / holder light), so this is a full classification rather than a
     * GL lighting classification.
     */
    private static boolean isItemPhase(RenderPhase phase) {
        return phase != null
                && phase != RenderPhase.BLOCK_WORLD
                && phase != RenderPhase.BLOCK_DESTROY;
    }
}
