package decok.dfcdvadstf.catframe.model.render.extension;

import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.api.RenderContext;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Built-in extension that handles the JSON model {@code "gui_light"} field.
 *
 * <p>Controls directional shading and OpenGL {@code GL_LIGHTING} based on the model's {@code gui_light} setting:
 * <ul>
 *   <li>{@code "front"} — front lighting, no directional shading ({@link RenderContext#shade} = 1.0),
 *       disables {@code GL_LIGHTING} (flat lighting, e.g. items). Automatically restored by {@code afterPart}.</li>
 *   <li>{@code "side"} — side lighting, preserves default directional shading (shade determined by normals),
 *       does not modify {@code GL_LIGHTING}.</li>
 *   <li>unset — defaults to {@code "side"} behavior.</li>
 * </ul>
 *
 * <h3>Alignment with vanilla</h3>
 * <p>Vanilla {@code RenderBlocks} does not manage {@code GL_LIGHTING} state in
 * {@code renderStandardBlockWithColorMultiplier} or {@code renderStandardBlockWithAmbientOcclusion};
 * {@code GL_LIGHTING} is managed by {@code RenderGlobal/EntityRenderer} at a higher level.
 * This extension only mimics vanilla {@code RenderItem}'s behavior of disabling {@code GL_LIGHTING}
 * for items with {@code gui_light="front"}. It never enables {@code GL_LIGHTING} itself to avoid
 * interfering with externally managed OpenGL lighting state.</p>
 *
 * <h3>Lifecycle</h3>
 * <p>This extension manages {@code GL_LIGHTING} state via {@link IModelRenderExtension#beforePart(List, RenderPhase)}
 * and {@link IModelRenderExtension#afterPart()}, dispatched by
 * {@link decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry}.</p>
 */
public final class GuiLightExtension implements IModelRenderExtension {

    /**
     * Flag indicating whether {@code GL_LIGHTING} was changed by {@code beforePart} on current thread:
     * {@code true} means {@code afterPart} must restore it.
     *
     * <p>Thread-local: rendering may enter from any thread (e.g. Beddium multithreaded chunk compilation);
     * sharing instance fields across threads would cause beforePart/afterPart state interference.</p>
     */
    private final ThreadLocal<Boolean> changedLighting = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Override
    @SuppressWarnings("deprecation")
    public void beforePart(List<BakedQuad> allQuads, RenderPhase phase, BlockStateModelPart part) {

        // Block destroy phase does not manage GL_LIGHTING: vanilla destroy batch (drawBlockDamageTexture)
        // owns GL state; gui_light is irrelevant to crack overlay.
        if (phase == RenderPhase.BLOCK_DESTROY) {
            changedLighting.set(Boolean.FALSE);
            return;
        }

        // Attached item phases (hand/head slot) always disable GL_LIGHTING (matching 1.7.10 RenderItem
        // glDisable(GL_LIGHTING) semantics): items use constant lighting independent of scene lights;
        // even if gui_light="side", force disable to prevent attached items from being affected by
        // directional scene lighting.
        if (phase.isAttachedItemPhase()) {
            changedLighting.set(Boolean.TRUE);
            GL11.glDisable(GL11.GL_LIGHTING);
            return;
        }

        // Block rendering: decide based on model's gui_light field
        boolean frontLight = needsFrontLighting(allQuads);
        if (frontLight) {
            changedLighting.set(Boolean.TRUE);
            GL11.glDisable(GL11.GL_LIGHTING);
        } else {
            changedLighting.set(Boolean.FALSE);
        }
    }

    @Override
    public void apply(RenderContext ctx) {
        String guiLight = ctx.quad.guiLight;

        // "front" lighting: no directional shading, all faces receive uniform lighting
        if ("front".equals(guiLight)) {
            ctx.shade = 1.0f;
            return;
        }

        // "side" or null: preserve default directional shading (computed by CardinalLighting.byFace)
        // Note: do not modify ctx.quad.guiLight to avoid side effects on other rendering paths
    }

    @Override
    public void afterPart() {
        if (Boolean.TRUE.equals(changedLighting.get())) {
            GL11.glEnable(GL11.GL_LIGHTING);
        }
        changedLighting.set(Boolean.FALSE);
    }

    /**
     * Check if quads of the model need front lighting ({@code gui_light = "front"}).
     * Following vanilla convention, checking the first quad's guiLight is sufficient to determine
     * the lighting mode for the entire model.
     */
    private static boolean needsFrontLighting(List<BakedQuad> quads) {
        if (quads == null || quads.isEmpty()) return false;
        return "front".equals(quads.get(0).guiLight);
    }
}