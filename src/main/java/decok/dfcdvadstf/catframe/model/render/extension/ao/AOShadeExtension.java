package decok.dfcdvadstf.catframe.model.render.extension.ao;

import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry;
import decok.dfcdvadstf.catframe.model.render.api.RenderContext;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.extension.ao.light.CardinalLighting;

/**
 * Built-in render extension: handles the {@code "ambientocclusion"}, {@code "shade"},
 * and {@code "shade_direction_override"} (26.3+) fields in a JSON element.
 *
 * <h3>Per-vertex AO principle (aligned with the 26.1 {@code BlockModelLighter} algorithm)</h3>
 * <p>During the {@link RenderPhase#BLOCK_WORLD} phase,
 * {@link AOComputeExtension} delegates to
 * {@link decok.dfcdvadstf.catframe.model.render.extension.ao.light.BlockModelLighter#prepareQuadAmbientOcclusion}
 * to precompute per-vertex data using the 26.1 algorithm:
 * face shape analysis (faceCubic / facePartial), 9-position sampling (including translucent corner fallback),
 * {@link decok.dfcdvadstf.catframe.model.render.extension.ao.light.LightCoordsUtil#smoothBlend} smooth blending,
 * and partial-face weighted blending. Directional lighting (CardinalLighting) is carried by {@link RenderContext#shade}.</p>
 *
 * <h3>Behavior rules</h3>
 * <ul>
 *   <li><strong>Default behavior ({@code null}): AO active</strong> — per-vertex AO data is left untouched,
 *     and the renderer emits each of the 4 vertices independently (each vertex with its own brightness and color).</li>
 *   <li>{@code ambientOcclusion = false}: clears the per-vertex data, degrading to flat lighting
 *     (using the block's own brightness + CardinalLighting directional shading). Corresponds to the
 *     {@code BlockModelLighter.prepareQuadFlat} path in 26.1.</li>
 *   <li>{@code shadeEnabled = false}: disables directional shading, forcing {@code shade = 1.0f} (uniform lighting).
 *     Per-vertex AO data is retained; only the directional coefficient is flattened.</li>
 *   <li>{@code shadeDirectionOverride != null} (26.3+): instead of the face's actual orientation, looks up the
 *     {@link CardinalLighting} table using the specified direction to compute directional shading; mutually exclusive
 *     with {@code shade}, and this field takes precedence when both are declared
 *     (a duplicate warning is already issued during parsing; see the ElementDeserializer in {@code ModelJson}).</li>
 * </ul>
 *
 * <p>This extension is installed automatically by {@link ModelRenderRegistry} on first use.
 */
public final class AOShadeExtension implements IModelRenderExtension {
    @Override
    public void apply(RenderContext ctx) {
        // Handle ambientocclusion
        if (ctx.quad.ambientOcclusion != null && !ctx.quad.ambientOcclusion) {
            // AO explicitly disabled: clear per-vertex data → degrade to flat lighting
            // The 26.1 flat path uses the block's own brightness (not the emissive 0xF000F0)
            for (int i = 0; i < 4; i++) {
                ctx.aoBrightness[i] = -1;
                ctx.aoColorMul[i] = 1.0f;
            }
            if (ctx.phase == RenderPhase.BLOCK_WORLD && ctx.block != null && ctx.world != null) {
                ctx.brightnessOverride = ctx.block.getMixedBrightnessForBlock(
                        ctx.world, ctx.x, ctx.y, ctx.z);
            }
        }

        // Handle shade_direction_override (26.3+): look up the table by the specified direction to compute directional shading; takes precedence over shade
        if (ctx.quad.shadeDirectionOverride != null) {
            ctx.shade = CardinalLighting.DEFAULT.byFace(ctx.quad.shadeDirectionOverride);
        } else if (ctx.quad.shadeEnabled != null && !ctx.quad.shadeEnabled) {
            // Directional shading disabled: flatten the directional coefficient; per-vertex AO data is retained
            ctx.shade = 1.0f;
        }
    }
}
