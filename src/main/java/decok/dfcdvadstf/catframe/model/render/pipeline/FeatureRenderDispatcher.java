package decok.dfcdvadstf.catframe.model.render.pipeline;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry;
import decok.dfcdvadstf.catframe.model.render.api.IRenderGroupHandler;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.api.RenderSubmitView;
import decok.dfcdvadstf.catframe.model.render.api.RenderTypeKey;
import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Command executor / batch flush, matching the {@code FeatureRenderDispatcher} in the
 * vanilla 26w+ pipeline.
 * <p>
 * Consumes the {@link RenderSubmit}s accumulated in {@link SubmitNodeStorage} and
 * executes draws according to the registry-sorted snapshot
 * ({@link RenderTypeKey#sortKey()} ascending, solid before translucent).
 *
 * <h3>Compromise between batching and zero regression</h3>
 * <p>Within the same {@link RenderTypeKey} group, <b>texture binding and GL state
 * (blend / cull) are set up only once</b>, and groups are flushed in ascending
 * sort-key order, thereby reducing texture switches and fixing translucent blending
 * order.
 * <p>However, each {@link RenderSubmit} still performs its own
 * {@code applyBeforePart → startDrawingQuads → write → draw → applyAfterPart},
 * strictly preserving the original per-part lifecycle:
 * <ul>
 *   <li>{@code GuiLightExtension} toggles {@code GL_LIGHTING} in {@code beforePart},
 *       which must take effect for that part's {@code draw()} and be restored in
 *       {@code afterPart};</li>
 *   <li>{@code DisplayTransformExtension} computes the display matrix in
 *       {@code beforePart} and writes it per quad in {@code apply} to
 *       {@link decok.dfcdvadstf.catframe.model.render.api.RenderContext#displayTransform}.</li>
 * </ul>
 * Per-part drawing is fully equivalent to the original single-part path; only texture
 * binding is shared and ordering is rearranged, so there is zero render regression.
 */
public final class FeatureRenderDispatcher {

    /** Reusable query buffer: reads GL_ALPHA_TEST_REF (client rendering is single-threaded, so no concurrency).
     * <p>Note: it must be allocated with 16 elements — LWJGL2's {@code glGetFloat} shares {@code glGetFloatv}
     * underneath (matrix queries return at most 16 floats), and {@code BufferChecks.checkBuffer}
     * ignores the actual enum return count and unconditionally requires a buffer of at least
     * 16 elements, otherwise it throws IllegalArgumentException. */
    private static final FloatBuffer STATE_QUERY = BufferUtils.createFloatBuffer(16);

    private FeatureRenderDispatcher() {
    }

    /**
     * Batch-flushes the command buffer (used for the item / GUI standalone draw scope).
     * Iterates non-empty groups in registry sort-snapshot order (solid before
     * translucent).
     * <p>
     * <b>Group-claiming SPI</b>: when the arbitrated {@link IRenderGroupHandler} claims a
     * group, the whole group is handed to the handler for drawing as a read-only
     * {@link RenderSubmitView} view (CatFrame does not run its built-in flush logic);
     * no handler / handler throws (including Error) → fall back to the built-in
     * {@link #flushGroup} (zero regression on the default path).
     */
    public static void flushBatched(SubmitNodeStorage storage) {
        for (Map.Entry<RenderTypeKey, List<RenderSubmit>> entry : storage.groups()) {
            List<RenderSubmit> group = entry.getValue();
            if (group == null || group.isEmpty()) continue;
            RenderTypeKey type = entry.getKey();

            // Claimed path: the handler takes full control of the group (manages its own GL state and restores it afterwards).
            IRenderGroupHandler handler = RenderGroupHandlerRegistry.handlerFor(type);
            if (handler != null) {
                try {
                    handler.flush(new ArrayList<RenderSubmitView>(group));
                    continue;
                } catch (Throwable t) {
                    // Error isolation: log the handler exception and fall back to the built-in flushGroup, without killing the whole render
                    CatFrame.logger.warn("[FeatureRenderDispatcher] group handler {} failed for {}: {}",
                            handler.getClass().getName(), type.id(), t.toString(), t);
                }
            }
            flushGroup(type, group);
        }
    }

    /**
     * Built-in group-level flush: texture binding + GL state are set up once, then each
     * submission runs the {@code applyBeforePart → startDrawingQuads → write → draw →
     * applyAfterPart} lifecycle. This path is also reused on claim fallback (behavior
     * byte-for-byte identical to before the refactor).
     */
    private static void flushGroup(RenderTypeKey type, List<RenderSubmit> group) {
        Tessellator t = Tessellator.instance;

        // Submissions within a group are homogeneous (they come from the same item/GUI
        // draw within the scope), so the cull flag of the first submission represents the whole group.
        boolean disableCull = group.get(0).disableCull;
        boolean blend = type.blend();
        // [Plan B] GL-lit item phases (dropped / item frame) send per-face normals;
        // enabling GL_NORMALIZE keeps normals unit-length after display / hand transform
        // scaling, relying only on direction.
        // Single source of judgment: RenderPhasePolicy.isItemGlLit (submissions within a
        // group are homogeneous, so the first represents the whole group).
        boolean itemGlLit = RenderPhasePolicy.isItemGlLit(group.get(0).phase);

        // ==== Once per group: texture binding + GL state ====
        Minecraft.getMinecraft().getTextureManager().bindTexture(type.atlas());
        if (disableCull) {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }
        if (blend) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        if (itemGlLit) {
            GL11.glEnable(GL11.GL_NORMALIZE);
        }

        // Write-side preconditions of the glint depth mask: the normal pass must run with
        // alpha test enabled so transparent texels (alpha ≤ 0.1) are trimmed and never
        // write depth, and the GL_EQUAL in renderEnchantmentGlint only fails at
        // transparent pixels; depth test also ensures the normal pass writes the depth
        // value that GL_EQUAL compares against.
        // Vanilla manages this per draw (RenderItem.renderItemIntoGUI enables alpha test
        // and disables it after drawing, then renderEffect re-enables it), whereas the
        // Forge custom-renderer path (IItemRenderer) manages neither — the inherited
        // environment state is untrustworthy (the previous draw may have just disabled
        // it), so it is forcibly established per group and precisely restored afterwards.
        boolean prevAlphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int prevAlphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        STATE_QUERY.clear();
        GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF, STATE_QUERY);
        float prevAlphaRef = STATE_QUERY.get(0);
        boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        try {
            for (RenderSubmit s : group) {
                // Lifecycle: before quad processing (DisplayTransformExtension computes
                // the matrix, GuiLightExtension disables GL_LIGHTING as needed)
                ModelRenderRegistry.applyBeforePart(s.part.getAllQuads(), s.phase, s.part);
                try {
                    t.startDrawingQuads();
                    boolean hasSolidColor;
                    if (isBlockPhase(s.phase)) {
                        QuadWriter.writeBlockQuads(s, t);
                        hasSolidColor = false;
                    } else {
                        hasSolidColor = QuadWriter.writeItemQuads(s, t);
                    }
                    // Always draw: even with no vertices, draw() safely resets the
                    // Tessellator's isDrawing state, preventing the next submission's
                    // startDrawingQuads() from throwing "Already tesselating".
                    t.draw();

                    // Second pass: untextured rendering of solidColor quads (solid-color sides).
                    // Disabling texture → GL_MODULATE no longer multiplies in the alpha of
                    // translucent texels → sides stay opaque; orthogonal to back-face culling:
                    // culling is still controlled by the group GL state, and the two do not
                    // affect each other.
                    if (hasSolidColor) {
                        GL11.glDisable(GL11.GL_TEXTURE_2D);
                        t.startDrawingQuads();
                        QuadWriter.writeSolidColorQuads(s, t);
                        t.draw();
                        GL11.glEnable(GL11.GL_TEXTURE_2D);
                    }

                    // Enchantment glint pass: must run before applyAfterPart() so the
                    // beforePart state (display matrix, etc.) is still valid and the replayed
                    // geometry coincides with the normal pass. The implementation lives in
                    // GuiGraphicsExtractor, which internally protects everything (including
                    // texture binding) with glPushAttrib.
                    if (GuiGraphicsExtractor.glintApplicable(s)) {
                        GuiGraphicsExtractor.renderEnchantmentGlint(s, t);
                    }
                } finally {
                    // Lifecycle: after quad processing (GuiLightExtension restores
                    // GL_LIGHTING, DisplayTransformExtension clears the matrix)
                    ModelRenderRegistry.applyAfterPart();
                }
            }
        } finally {
            // ==== Once per group: restore GL state ====
            if (disableCull) {
                GL11.glEnable(GL11.GL_CULL_FACE);
            }
            if (blend) {
                GL11.glDisable(GL11.GL_BLEND);
            }
            if (itemGlLit) {
                GL11.glDisable(GL11.GL_NORMALIZE);
            }
            // Restore the alpha test (including func/ref — glPushAttrib does not cover alpha func) and depth test switch from before this group
            GL11.glAlphaFunc(prevAlphaFunc, prevAlphaRef);
            if (prevAlphaTest) {
                GL11.glEnable(GL11.GL_ALPHA_TEST);
            } else {
                GL11.glDisable(GL11.GL_ALPHA_TEST);
            }
            if (!prevDepthTest) {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }
        }
    }

    /**
     * Inline flush (used for {@link RenderPhase#BLOCK_DESTROY} and
     * {@link RenderPhase#BLOCK_WORLD}).
     * <p>
     * [Three-domain rendering architecture] Vanilla backend (the only path): world blocks
     * are written inline into the vanilla batch during chunk compilation (quads carry
     * vanilla atlas UVs, and the batch binds the vanilla blocks atlas), sharing the same
     * "write vertices only, do not touch GL state" contract with the destroy decal.
     * <p>
     * The block-destroy decal occurs inside the vanilla
     * {@code drawBlockDamageTexture}'s startDrawingQuads batch (vanilla has already bound
     * the blocks atlas and manages GL state uniformly), so this method only writes
     * vertices to the current Tessellator and does <b>not</b>
     * {@code startDrawingQuads}/{@code draw}, does <b>not</b> bind a texture, and does
     * <b>not</b> change GL state. The destroy icon (destroy_stage_N) belongs to the
     * vanilla atlas space, consistent with the batch binding.
     * <p>
     * <b>Never consults the group-claiming handler</b> (contract wording: inline
     * submissions cannot be claimed) — world geometry must be rerouted at the collection
     * end (e.g. via a render scope) before it can be claimed within the executor-end
     * scope.
     * <p>
     * Note: this method may be entered from a background thread (e.g. Beddium's
     * multi-threaded chunk compilation); binding a texture is a GL call and is
     * redundant here anyway (vanilla has already bound the blocks atlas), so it is
     * deliberately not bound here.
     */
    public static void flushInline(RenderSubmit s) {
        // Do not bind a texture: vanilla has already bound the blocks atlas, so binding
        // again here is redundant, and a background thread (Beddium's multi-threaded
        // chunk compilation) performing a GL call would break GL state ownership.
        ModelRenderRegistry.applyBeforePart(s.part.getAllQuads(), s.phase, s.part);
        try {
            QuadWriter.writeBlockQuads(s, Tessellator.instance);
        } finally {
            ModelRenderRegistry.applyAfterPart();
        }
    }

    private static boolean isBlockPhase(RenderPhase phase) {
        return phase == RenderPhase.BLOCK_WORLD;
    }
}
