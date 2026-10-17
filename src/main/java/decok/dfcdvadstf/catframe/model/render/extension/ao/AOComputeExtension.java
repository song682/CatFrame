package decok.dfcdvadstf.catframe.model.render.extension.ao;

import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.api.RenderContext;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.extension.ao.light.BlockModelLighter;

/**
 * Built-in render extension: performs per-vertex AO (ambient occlusion) computation.
 *
 * <p>Delegates to {@link BlockModelLighter#prepareQuadAmbientOcclusion},
 * aligned with the 26.1.2 {@code BlockModelLighter.prepareQuadAmbientOcclusion} algorithm:
 * face shape analysis → 9-position sampling → corner fallback → smoothBlend smooth blending → partial-face weighting.
 *
 * <p>Output is written to {@link RenderContext#aoBrightness}[4] and {@link RenderContext#aoColorMul}[4].
 * aoColorMul contains only AO shade; directional lighting (CardinalLighting) is carried by {@link RenderContext#shade}.
 *
 * <p>This extension is forced to be the first extension in the extension chain, ensuring that subsequent extensions (such as AOShadeExtension)
 * can read/modify the AO computation results.
 */
public final class AOComputeExtension implements IModelRenderExtension {

    @Override
    public void apply(RenderContext ctx) {
        // Run AO computation only during the world render phase
        if (ctx.phase != RenderPhase.BLOCK_WORLD) return;
        if (ctx.world == null || ctx.block == null) return;

        // Emissive blocks never take the per-vertex AO path (aligned with 26.1.2 ModelBlockRenderer
        // dispatch, which additionally requires blockState.getLightEmission() == 0). They degrade to
        // the flat path via LightPolicyExtension writing the block's mixed brightness.
        if (ctx.block.getLightValue(ctx.world, ctx.x, ctx.y, ctx.z) != 0) return;

        BakedQuad q = ctx.quad;
        if (q.face == null) return;

        // Model-level ambientocclusion=false → flat lighting path (aligned with 26.1.2
        // ModelBlockRenderer dispatching such models to tesselateFlat): per-vertex AO must not run.
        // The degrade brightness/shade is written by LightPolicyExtension / AOShadeExtension instead.
        if (q.ambientOcclusion != null && !q.ambientOcclusion) return;

        BlockModelLighter.get().prepareQuadAmbientOcclusion(
                ctx.world, ctx.x, ctx.y, ctx.z,
                ctx.block, q, ctx);
    }
}
