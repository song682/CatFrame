package decok.dfcdvadstf.catframe.model.render;

import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.api.RenderContext;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;

import java.util.List;

/**
 * Render extension interface that mods can register. Before each baked quad is sent to Tessellator,
 * all extensions are traversed in priority order (extensions with equal priority are traversed in
 * registration order). Each extension affects the final render (coloring, brightness, culling, etc.)
 * by modifying fields in {@link RenderContext}.
 * <p>
 * <b>Thread safety contract (v0.5+)</b>: the render path may enter from any thread (e.g., Beddium
 * multithreaded chunk compilation). Extension instances must not hold cross-thread mutable shared
 * state; use ThreadLocal for per-part temporary data or write to {@link RenderContext}. The extension
 * chain isolates exceptions from individual extensions so they do not break the entire render.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #beforePart(List, RenderPhase)} — called once before processing a group of quads;
 *       suitable for global operations like setting GL state.</li>
 *   <li>{@link #apply(RenderContext)} — called for each quad in order.</li>
 *   <li>{@link #afterPart()} — called once after all quads in the group have been processed;
 *       suitable for cleanup like restoring GL state modified in beforePart.</li>
 * </ol>
 *
 * <p>Only {@link #apply(RenderContext)} is mandatory; the other two methods have empty default
 * implementations, so extensions only override what they need.
 *
 * <h3>Usage examples</h3>
 * <pre>{@code
 * // 1. Add a warm tint to the top face of a specific block (block world only)
 * ModelRenderExtensions.register(ctx -> {
 *     if (ctx.phase != RenderPhase.BLOCK_WORLD) return;
 *     if (ctx.block != MyBlocks.LAVA_ROCK) return;
 *     if (ctx.quad.face != Direction.UP) return;
 *     ctx.mulColor(0xFFAA66);
 * });
 *
 * // 2. Custom lighting: halve brightness for all held items
 * ModelRenderExtensions.register(ctx -> {
 *     if (ctx.phase.isHandPhase()) ctx.brightnessOverride = 0x800080;
 * });
 *
 * // 3. Face culling: skip rendering if the quad faces north and the neighbor to the north is an opaque block
 * ModelRenderExtensions.register(ctx -> {
 *     if (ctx.phase != RenderPhase.BLOCK_WORLD) return;
 *     if (ctx.quad.face != Direction.NORTH) return;
 *     if (ctx.world.getBlock(ctx.x, ctx.y, ctx.z - 1).isOpaqueCube()) ctx.skip = true;
 * });
 * }</pre>
 */
public interface IModelRenderExtension {

    /**
     * Lifecycle callback before processing a group of quads. Called once before traversing the quad list.
     * <p>
     * Suitable for global operations that need to run before processing any quad, e.g. detecting model
     * lighting mode and setting GL_LIGHTING state.
     *
     * @param allQuads all BakedQuad in the current part
     * @param phase    current render phase
     * @param part     current BlockStateModelPart being rendered (provides part-level metadata)
     */
    default void beforePart(List<BakedQuad> allQuads, RenderPhase phase, BlockStateModelPart part) {
    }

    /**
     * Process context for a single quad.
     * If {@link RenderContext#skip skip=true} is set, subsequent extensions are skipped and this quad is not rendered.
     */
    void apply(RenderContext ctx);

    /**
     * Lifecycle callback after all quads in the group have been processed.
     * <p>
     * Suitable for cleanup operations that need to run after processing all quads, e.g. restoring
     * GL state modified in {@code beforePart}.
     */
    default void afterPart() {
    }

    /**
     * Legacy two-argument beforePart; defaults to delegating to the three-argument version (part=null).
     * Implementations should override {@link #beforePart(List, RenderPhase, BlockStateModelPart)} instead.
     */
    default void beforePart(List<BakedQuad> allQuads, RenderPhase phase) {
        beforePart(allQuads, phase, null);
    }
}
