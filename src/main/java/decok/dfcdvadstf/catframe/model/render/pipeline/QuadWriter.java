package decok.dfcdvadstf.catframe.model.render.pipeline;

import decok.dfcdvadstf.catframe.core.Direction;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry;
import decok.dfcdvadstf.catframe.model.render.api.RenderContext;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.extension.ao.light.CardinalLighting;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;

import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import java.util.List;

/**
 * Vertex writer: extracts the "per-vertex Tessellator writing loop" from the former
 * {@code UniformRenderPipeline} into reusable pure static write methods, mirroring the
 * {@code QuadWriter} of the vanilla 26w+ pipeline.
 * <p>
 * <b>Responsibility boundary (strict, post-DBF)</b>: this class <em>only emits vertices</em> - based on
 * {@link RenderSubmit} (fully resolved submit input) and the extension chain result, it computes color/UV,
 * performs vertex transforms, and calls {@code t.addVertexWithUV}. It does <b>not</b> call
 * {@code startDrawingQuads}/{@code draw}, <b>not</b> modify GL state, <b>not</b> bind textures, <b>not</b> call
 * {@code applyBeforePart}/{@code applyAfterPart}
 * (those lifecycles are managed per submit item by {@link FeatureRenderDispatcher}).
 * <p>
 * Phase policy (brightness baseline / GL lighting mode decision) has been moved out to the built-in
 * {@code LightPolicyExtension} (which writes {@code brightnessOverride} during the extension chain apply;
 * the override always takes priority over the constructor input) and {@link RenderPhasePolicy}
 * (the single computation helper): this class no longer makes any decision based on {@code RenderPhase},
 * and only falls back to computing via {@code RenderPhasePolicy} when
 * {@code RenderSubmit.baselineBrightness = -1} (direct constructors / built-in extension absent) as a
 * chain-missing fallback (fallback retained).
 * The residual "geometry / shading emission rules" (per-face-direction baked shade, GUI screen-space
 * directional light, destroy UV projection) are per-quad emission semantics and stay in this class.
 * <p>
 * The per-vertex logic remains line-by-line identical to the former {@code UniformRenderPipeline}
 * to guarantee zero render regressions.
 */
public final class QuadWriter {

    private QuadWriter() {
    }

    /**
     * Writes block quads (world / destroy overlay). Migrated from the for-quad loop of the former
     * {@code renderBlockQuads}.
     * <p>
     * [Three-domain render architecture] The only path on the vanilla backend: BLOCK_WORLD is written
     * inline into the chunk batch by {@code flushInline} (bound to the vanilla blocks atlas), and the quads
     * carry vanilla IIcons at bake time (vanilla-space UVs); the destroy decal (BLOCK_DESTROY) is likewise
     * written into the vanilla destroy batch by {@code flushInline} (also bound to the vanilla blocks atlas),
     * with its iconOverride being the vanilla destroy_stage_N, consistent with the batch binding.
     * Both share the same branchless write loop.
     *
     * @return whether any vertex was written (lets the caller decide whether to call {@code t.draw()})
     */
    public static boolean writeBlockQuads(RenderSubmit s, Tessellator t) {
        List<BakedQuad> allQuads = s.part.getAllQuads();

        double a = Math.toRadians(s.rotationDeg);
        double cos = Math.cos(a), sin = Math.sin(a);
        Point3d tmpVec = new Point3d();

        boolean hasVertices = false;
        // Brightness baseline: on the normal path the built-in LightPolicyExtension writes brightnessOverride
        // during apply (the override takes priority over this constructor input); this is only a fallback - -1
        // (direct constructors / chain missing) falls back to computing per phase via RenderPhasePolicy (fallback retained).
        // The base brightness is quad-independent, so hoisting it outside the loop eliminates repeated per-quad sampling
        // (the per-vertex AO path overrides it via aoBrightness).
        int baseBrightness = s.baselineBrightness >= 0
                ? s.baselineBrightness
                : RenderPhasePolicy.baselineBrightness(s.phase, s.world, s.x, s.y, s.z, s.block);
        for (BakedQuad q : allQuads) {
            // Directional shading: take the CardinalLighting coefficient by face direction.
            float baseShade = CardinalLighting.DEFAULT.byFace(q.face);

            // Create the context and run the extension chain
            RenderContext ctx = new RenderContext(s.phase, q,
                    s.world, s.x, s.y, s.z, s.block, null, baseBrightness, baseShade,
                    s.blockstateProps, s.itemProps);
            ctx.metadata = s.metadata;
            ModelRenderRegistry.apply(ctx);
            if (ctx.skip)
                continue;

            // [Three-domain render architecture] The only path on the vanilla backend: quads carry vanilla IIcons
            // (vanilla-space UVs), consistent with the vanilla blocks atlas bound by both the BLOCK_WORLD chunk batch
            // and the BLOCK_DESTROY destroy batch; iconOverride (the vanilla destroy_stage_N injected by the destroy decal)
            // also lives in the vanilla atlas space. The same branchless write loop applies.
            // Vanilla backend only: quad icons live in the vanilla atlas space bound
            // by both chunk batches and the destroy batch.
            IIcon icon = (ctx.iconOverride != null) ? ctx.iconOverride : q.icon;
            if (icon == null) {
                // Defense: skip quads without an icon (such as solidColor side quads)
                continue;
            }
            hasVertices = true;

            // Submit to the Tessellator
            boolean hasVertexAO = ctx.aoBrightness[0] >= 0;
            // Local reference to the UV override (model space 0-16): null = no override; shared by the AO / non-AO regular branches.
            // The destroy decal projection branch does not consume it (decal UVs are decoupled from model UVs by design).
            final float[] uvOv = ctx.effectiveUvOverride();

            if (hasVertexAO) {
                for (int i = 0; i < 4; i++) {
                    t.setBrightness(ctx.aoBrightness[i]);
                    float cr = ((ctx.color >> 16) & 0xFF) / 255.0f * ctx.shade * ctx.aoColorMul[i];
                    float cg = ((ctx.color >> 8) & 0xFF) / 255.0f * ctx.shade * ctx.aoColorMul[i];
                    float cb = (ctx.color & 0xFF) / 255.0f * ctx.shade * ctx.aoColorMul[i];
                    t.setColorOpaque_F(cr, cg, cb);

                    double vx = q.vx(i), vy = q.vy(i), vz = q.vz(i);
                    if (s.rotationDeg != 0) {
                        double px = vx - 0.5, pz = vz - 0.5;
                        vx = px * cos - pz * sin + 0.5;
                        vz = px * sin + pz * cos + 0.5;
                    }
                    // Apply the display transform (block phases do not set it by default in the extension chain; the null check is a fallback)
                    if (ctx.displayTransform != null) {
                        tmpVec.set(vx, vy, vz);
                        ctx.displayTransform.transform(tmpVec);
                        vx = tmpVec.x;
                        vy = tmpVec.y;
                        vz = tmpVec.z;
                    }
                    double U = icon.getInterpolatedU(uvOv != null ? uvOv[i * 2] : q.up[i]);
                    double V = icon.getInterpolatedV(uvOv != null ? uvOv[i * 2 + 1] : q.vp[i]);
                    t.addVertexWithUV(s.x + vx, s.y + vy, s.z + vz, U, V);
                }
            } else {
                t.setBrightness(ctx.effectiveBrightness());
                float cr = ((ctx.color >> 16) & 0xFF) / 255.0f * ctx.shade;
                float cg = ((ctx.color >> 8) & 0xFF) / 255.0f * ctx.shade;
                float cb = (ctx.color & 0xFF) / 255.0f * ctx.shade;
                t.setColorOpaque_F(cr, cg, cb);

                for (int i = 0; i < 4; i++) {
                    double vx = q.vx(i), vy = q.vy(i), vz = q.vz(i);
                    if (s.rotationDeg != 0) {
                        double px = vx - 0.5, pz = vz - 0.5;
                        vx = px * cos - pz * sin + 0.5;
                        vz = px * sin + pz * cos + 0.5;
                    }
                    // Apply the display transform (block phases do not set it by default in the extension chain; the null check is a fallback)
                    if (ctx.displayTransform != null) {
                        tmpVec.set(vx, vy, vz);
                        ctx.displayTransform.transform(tmpVec);
                        vx = tmpVec.x;
                        vy = tmpVec.y;
                        vz = tmpVec.z;
                    }
                    double U, V;
                    if (s.phase == RenderPhase.BLOCK_DESTROY) {
                        // Destroy decal UV = block-space position projection (replicating the 1.7.10 renderFace*
                        // renderMin/Max*16 semantics): pick the tangent axes by face normal, interpolate the rotated
                        // vertex local coordinates ×16 → the texture is pinned to the geometry and continuous
                        // around the block (aligned with 26.1.2 decal semantics).
                        // Vertical faces use V = 16 - y*16 (vanilla binds the top vertex y=maxY → V(0), bottom → V(16)),
                        // otherwise it would be flipped; NORTH/EAST reverse U to 16 - x*16 / 16 - z*16,
                        // keeping U continuous as the texture wraps south→east→north→west (sticker wrap semantics).
                        // Destroy decal UVs: block-space position projection, mimicking the
                        // vanilla renderMin/Max*16 semantics; the decal is pinned to the
                        // geometry and wraps continuously around the four vertical faces.
                        // Vertical faces use V = 16 - y*16 (vanilla binds V(0) to the top
                        // edge), and NORTH/EAST flip U so the texture wraps seamlessly
                        // around the block perimeter.
                        Direction f = q.face;
                        if (f == Direction.DOWN || f == Direction.UP) {
                            U = icon.getInterpolatedU(vx * 16.0);
                            V = icon.getInterpolatedV(vz * 16.0);
                        } else if (f == Direction.NORTH) {
                            U = icon.getInterpolatedU(16.0 - vx * 16.0);
                            V = icon.getInterpolatedV(16.0 - vy * 16.0);
                        } else if (f == Direction.SOUTH) {
                            U = icon.getInterpolatedU(vx * 16.0);
                            V = icon.getInterpolatedV(16.0 - vy * 16.0);
                        } else if (f == Direction.WEST) {
                            U = icon.getInterpolatedU(vz * 16.0);
                            V = icon.getInterpolatedV(16.0 - vy * 16.0);
                        } else if (f == Direction.EAST) {
                            U = icon.getInterpolatedU(16.0 - vz * 16.0);
                            V = icon.getInterpolatedV(16.0 - vy * 16.0);
                        } else {
                            // face is null (direction-less quads such as cross): fall back to model UVs
                            // Fall back to baked model UVs for direction-less quads
                            // the destroy branch does not consume uvOverride (decal UVs are decoupled from model UVs by design)
                            U = icon.getInterpolatedU(q.up[i]);
                            V = icon.getInterpolatedV(q.vp[i]);
                        }
                    } else {
                        // non-destroy: uvOverride (model-space override) takes priority
                        U = icon.getInterpolatedU(uvOv != null ? uvOv[i * 2] : q.up[i]);
                        V = icon.getInterpolatedV(uvOv != null ? uvOv[i * 2 + 1] : q.vp[i]);
                    }
                    t.addVertexWithUV(s.x + vx, s.y + vy, s.z + vz, U, V);
                }
            }
        }
        return hasVertices;
    }

    /**
     * Writes item quads (GUI / hand / dropped / item frame). Migrated from the for-quad loop of the former
     * {@code renderItemQuads}.
     * <p>
     * Quads with {@code solidColor != 0} (solid-color side quads) are <b>skipped</b> in this method and rendered
     * by {@link #writeSolidColorQuads(RenderSubmit, Tessellator)} in a separate untextured draw call:
     * side quads have UVs hugging the "opaque→transparent" boundary, so bilinear / mipmap sampling would blend in
     * transparent neighbor texels, and GL_MODULATE would multiply the texel alpha into the fragment, rendering the
     * narrow faces transparent.
     *
     * @return {@code true} if any solidColor quad was skipped (the caller must perform a second untextured pass)
     */
    public static boolean writeItemQuads(RenderSubmit s, Tessellator t) {
        List<BakedQuad> allQuads = s.part.getAllQuads();
        boolean gui = (s.phase == RenderPhase.ITEM_GUI);
        // [Plan B] GL_LIGHTING decision converges on the single source RenderPhasePolicy.isItemGlLit: non-GUI and
        // non-hand item phases (dropped / item frame) keep GL_LIGHTING and use per-face normals so GL computes
        // directional lighting, no longer baking CardinalLighting directional shade into vertex colors, avoiding
        // "baked shade + GL lighting" double shading.
        // The GUI and hand phases keep baked shading: the hand phase does not enable GL_LIGHTING (avoiding double
        // shading), and brightness (lightmap) samples world light at the player position, rendering the item fully
        // black when there is no external light, mirroring the 1.7.10 ItemRenderer hand brightness semantics
        // (the private helper has migrated to RenderPhasePolicy along with the brightness policy).
        boolean glLit = RenderPhasePolicy.isItemGlLit(s.phase);
        Matrix4d preTransform = s.preTransform;
        // Item model render transformation (items JSON transformation tag): always applied after the display transform
        // Per-model item transformation: always applied after the display transform
        Matrix4d transformation = s.transformation;
        Point3d tmpVec = new Point3d();
        Vector3d tmpNormal = glLit ? new Vector3d() : null;

        // Brightness (lightmap): on the normal path the built-in LightPolicyExtension (relayed computation in
        // beforePart → writes brightnessOverride in apply, which takes priority over this constructor input); this is
        // only a fallback - -1 (direct constructors / chain missing) falls back to computing per phase via
        // RenderPhasePolicy, preserving the semantics of dropped items being as dark as their surroundings at night /
        // with no light source, and GUI being always 255.
        int baseBrightness = s.baselineBrightness >= 0
                ? s.baselineBrightness
                : RenderPhasePolicy.baselineBrightness(s.phase, s.world, s.x, s.y, s.z, s.block);
        boolean hasSolidColor = false;

        for (BakedQuad q : allQuads) {
            // Skip solidColor quads: rendered separately by writeSolidColorQuads without textures
            if (q.solidColor != 0) {
                hasSolidColor = true;
                continue;
            }

            // Directional shading: the GUI bakes CardinalLighting coefficients by face direction;
            // non-GUI defers to GL_LIGHTING with per-face normals, using baseShade 1.0 to avoid double shading.
            float baseShade = glLit ? 1.0f : CardinalLighting.DEFAULT.byFace(q.face);
            RenderContext ctx = new RenderContext(s.phase, q,
                    s.world, s.x, s.y, s.z, s.block, s.stack, baseBrightness, baseShade,
                    s.blockstateProps, s.itemProps);
            ModelRenderRegistry.apply(ctx);
            if (ctx.skip)
                continue;

            // GUI-phase screen-space lighting: block-like models (gui_light="side"/null) are shaded by the rotated
            // normal direction, with brightness fixed on screen (top 1.0 / left 0.8 / right 0.6 / bottom 0.5),
            // independent of display.gui.rotation; gui_light="front" 2D items stay fully lit.
            if (gui && !"front".equals(ctx.quad.guiLight) && ctx.displayTransform != null) {
                ctx.shade = guiScreenShade(q, ctx.displayTransform);
            }

            // Non-GUI phases: emit per-face normals (rotated along with display / transformation / preTransform)
            // for GL_LIGHTING.
            if (glLit) {
                writeQuadNormal(t, q, ctx.displayTransform, transformation, preTransform, tmpNormal);
            }

            t.setBrightness(ctx.effectiveBrightness());
            float cr = ((ctx.color >> 16) & 0xFF) / 255.0f * ctx.shade;
            float cg = ((ctx.color >> 8) & 0xFF) / 255.0f * ctx.shade;
            float cb = (ctx.color & 0xFF) / 255.0f * ctx.shade;
            if (gui) {
                t.setColorRGBA_F(cr, cg, cb, 1.0f);
            } else {
                t.setColorOpaque_F(cr, cg, cb);
            }

            IIcon icon = (ctx.iconOverride != null) ? ctx.iconOverride : q.icon;
            final float[] uvOv = ctx.effectiveUvOverride();
            for (int i = 0; i < 4; i++) {
                double U = icon.getInterpolatedU(uvOv != null ? uvOv[i * 2] : q.up[i]);
                double V = icon.getInterpolatedV(uvOv != null ? uvOv[i * 2 + 1] : q.vp[i]);

                tmpVec.set(q.vx(i), q.vy(i), q.vz(i));
                applyTransformChain(tmpVec, ctx.displayTransform, transformation, preTransform);
                t.addVertexWithUV(tmpVec.x, tmpVec.y, tmpVec.z, U, V);
            }
        }
        return hasSolidColor;
    }

    /**
     * Writes solidColor quads (solid-color side quads) - pure vertex-color rendering; <b>the caller must disable textures first</b>.
     * <p>
     * Mirrors the 26.1.2 {@code ItemModelGenerator} side-render semantics: sides use the edge pixel's RGB as an
     * opaque solid fill, unaffected by texture alpha. Under the 1.7.10 fixed pipeline, {@code GL_MODULATE} would
     * multiply the translucent texel alpha into the final fragment, making the sides of translucent textures disappear;
     * with textures disabled the fragment color is entirely determined by the vertex color and alpha is always 1.0.
     * <p>
     * <b>Responsibility boundary</b>: this method only writes vertices and performs no GL state management such as
     * {@code glDisable(GL_TEXTURE_2D)} (handled by {@link FeatureRenderDispatcher}).
     */
    public static void writeSolidColorQuads(RenderSubmit s, Tessellator t) {
        List<BakedQuad> allQuads = s.part.getAllQuads();
        boolean gui = (s.phase == RenderPhase.ITEM_GUI);
        // Consistent with writeItemQuads (single source RenderPhasePolicy.isItemGlLit): the hand phase does not
        // apply GL_LIGHTING and takes the baked-shading branch
        boolean glLit = RenderPhasePolicy.isItemGlLit(s.phase);
        Matrix4d preTransform = s.preTransform;
        Matrix4d transformation = s.transformation;
        Point3d tmpVec = new Point3d();
        Vector3d tmpNormal = glLit ? new Vector3d() : null;

        // Brightness branch consistent with writeItemQuads: the override written by LightPolicyExtension takes priority;
        // the -1 fallback here is only a chain-missing safety net (fallback retained, computed per phase via RenderPhasePolicy)
        int baseBrightness = s.baselineBrightness >= 0
                ? s.baselineBrightness
                : RenderPhasePolicy.baselineBrightness(s.phase, s.world, s.x, s.y, s.z, s.block);

        for (BakedQuad q : allQuads) {
            if (q.solidColor == 0)
                continue;

            float baseShade = glLit ? 1.0f : CardinalLighting.DEFAULT.byFace(q.face);
            RenderContext ctx = new RenderContext(s.phase, q,
                    s.world, s.x, s.y, s.z, s.block, s.stack, baseBrightness, baseShade,
                    s.blockstateProps, s.itemProps);
            ModelRenderRegistry.apply(ctx);
            if (ctx.skip)
                continue;

            // GUI-phase screen-space lighting: the same screen-space directional shading as writeItemQuads
            if (gui && !"front".equals(ctx.quad.guiLight) && ctx.displayTransform != null) {
                ctx.shade = guiScreenShade(q, ctx.displayTransform);
            }

            // Non-GUI phases: emit per-face normals (rotated along with display / transformation / preTransform)
            // for GL_LIGHTING.
            if (glLit) {
                writeQuadNormal(t, q, ctx.displayTransform, transformation, preTransform, tmpNormal);
            }

            t.setBrightness(ctx.effectiveBrightness());
            // Use solidColor's RGB with alpha fixed at 1.0 (sides are always opaque)
            // Multiply by ctx.color (injected by tint extensions) to match the front-face pass, so tinted
            // items (e.g. grass) no longer show raw un-tinted colors on side extrusions.
            float cr = ((q.solidColor >> 16) & 0xFF) / 255.0f * ((ctx.color >> 16) & 0xFF) / 255.0f * ctx.shade;
            float cg = ((q.solidColor >> 8) & 0xFF) / 255.0f * ((ctx.color >> 8) & 0xFF) / 255.0f * ctx.shade;
            float cb = (q.solidColor & 0xFF) / 255.0f * (ctx.color & 0xFF) / 255.0f * ctx.shade;
            t.setColorRGBA_F(cr, cg, cb, 1.0f);

            for (int i = 0; i < 4; i++) {
                // UVs are ignored without textures, but addVertexWithUV is the Tessellator's only submit API
                // this pass does not consume uvOverride (textureless rendering; UVs do not participate in sampling)
                tmpVec.set(q.vx(i), q.vy(i), q.vz(i));
                applyTransformChain(tmpVec, ctx.displayTransform, transformation, preTransform);
                t.addVertexWithUV(tmpVec.x, tmpVec.y, tmpVec.z, 0, 0);
            }
        }
    }

    /**
     * Write enchantment glint quads: replays the <b>textured</b> geometry of the submit item (skipping solid-color
     * sides) so {@code GuiGraphicsExtractor.renderEnchantmentGlint} can overlay it with the glint texture and a
     * scrolling texture matrix, letting the glint follow the opaque texel contour.
     * <p>
     * Differences from {@link #writeItemQuads}:
     * <ul>
     * <li><b>Skips</b> quads with {@code solidColor != 0} - side depth is written by the second untextured pass of
     * the normal render with forced-opaque alpha (no alpha cutout); replaying them would make the glint appear on
     * transparent pixels covered by the side projection (overflow). The glint only needs to hug the textured contour
     * whose depth was written by the normal pass's first draw after alpha-test culling, matching vanilla 1.7.10 glint semantics;</li>
     * <li>Color is forced to glint purple (mirroring vanilla {@code RenderItem.renderEffect}'s
     * {@code (0.5, 0.25, 0.8)}; for non-GUI phases it mirrors the 1.7.10 {@code ItemRenderer}
     * hand glint multiplied by 0.76);</li>
     * <li>No normals are written - the glint pass has {@code GL_LIGHTING} disabled;</li>
     * <li>UVs still use the baked atlas UVs; the flow span is controlled by the glint pass's texture matrix scale.</li>
     * </ul>
     * The vertex transform chain is <b>bit-identical</b> to {@link #writeItemQuads}
     * (v' = M_pre × M_transformation × M_display × v), and the same extension chain runs honoring
     * {@code ctx.skip}, guaranteeing the replayed geometry exactly coincides with the normal pass and passes the
     * {@code GL_EQUAL} depth test.
     * <p>
     * <b>Precondition</b>: must be called while {@code applyBeforePart} state is still valid
     * (only then can beforePart state such as the display matrix be recomputed consistently).
     */
    public static void writeGlintQuads(RenderSubmit s, Tessellator t) {
        List<BakedQuad> allQuads = s.part.getAllQuads();
        boolean gui = (s.phase == RenderPhase.ITEM_GUI);
        Matrix4d preTransform = s.preTransform;
        Matrix4d transformation = s.transformation;
        Point3d tmpVec = new Point3d();

        int baseBrightness = gui ? 255 : 15728880;
        // Glint purple: GUI matches RenderItem.renderEffect (0.5, 0.25, 0.8);
        // non-GUI matches the 1.7.10 ItemRenderer hand glint (same color multiplied by 0.76)
        float k = gui ? 1.0f : 0.76f;
        float gr = 0.5f * k, gg = 0.25f * k, gb = 0.8f * k;

        for (BakedQuad q : allQuads) {
            // Skip solid-color side quads: their depth is written by the second untextured pass with forced-opaque
            // alpha (no cutout), so replaying them would let the GL_EQUAL glint render over transparent pixels
            // covered by the side projection (under rotated hand views the side projection deviates from the front
            // contour and the overflow is obvious). Skipping confines the glint strictly to the opaque texels whose
            // depth was written by the normal pass's first draw (after alpha-test culling), preserving the same visual
            // behavior as vanilla 1.7.10.
            if (q.solidColor != 0) {
                continue;
            }

            // Run the extension chain: obtain displayTransform and honor the skip flag (consistent visibility with the normal pass)
            RenderContext ctx = new RenderContext(s.phase, q,
                    s.world, s.x, s.y, s.z, s.block, s.stack, baseBrightness, 1.0f,
                    s.blockstateProps, s.itemProps);
            ModelRenderRegistry.apply(ctx);
            if (ctx.skip)
                continue;

            t.setBrightness(baseBrightness);
            t.setColorRGBA_F(gr, gg, gb, 1.0f);

            IIcon icon = (ctx.iconOverride != null) ? ctx.iconOverride : q.icon;
            final float[] uvOv = ctx.effectiveUvOverride();
            for (int i = 0; i < 4; i++) {
                // solid-color quads may lack an icon; fall back to UV 0 (the flow animation is still driven by the texture matrix)
                double U = (icon != null) ? icon.getInterpolatedU(uvOv != null ? uvOv[i * 2] : q.up[i]) : 0.0;
                double V = (icon != null) ? icon.getInterpolatedV(uvOv != null ? uvOv[i * 2 + 1] : q.vp[i]) : 0.0;

                tmpVec.set(q.vx(i), q.vy(i), q.vz(i));
                applyTransformChain(tmpVec, ctx.displayTransform, transformation, preTransform);
                t.addVertexWithUV(tmpVec.x, tmpVec.y, tmpVec.z, U, V);
            }
        }
    }

    /**
     * Single source for the vertex transform chain: v' = M_pre × M_transformation × M_display × v.
     * <p>
     * Shared by {@link #writeItemQuads} / {@link #writeSolidColorQuads} /
     * {@link #writeGlintQuads} (the geometry of the three passes must be bit-identical, otherwise the glint replay
     * would not coincide with the normal pass): first apply the display transform (injected by the extension chain),
     * then the transformation (the items JSON {@code transformation} tag, always after display),
     * and finally the preTransform (inverse cancellation). Each matrix may be null (the corresponding step is skipped).
     * <p>
     * {@link #writeBlockQuads} does not use this chain: block-phase geometry semantics differ (rotation around the
     * 0.5 pivot plus a per-vertex displayTransform, with no items transformation / preTransform), so it keeps its own branch.
     * Single source for the vertex transform chain shared by the three item passes;
     * block-phase writing keeps its own rotation branch instead.
     *
     * @param tmp            vertex buffer (transformed in place)
     * @param display        the display matrix injected by the extension chain; may be null
     * @param transformation the items JSON item model render transformation; may be null
     * @param preTransform   the pre-transform (inverse cancellation); may be null
     */
    private static void applyTransformChain(Point3d tmp, Matrix4d display,
                                            Matrix4d transformation, Matrix4d preTransform) {
        if (display != null) {
            display.transform(tmp);
        }
        if (transformation != null) {
            transformation.transform(tmp);
        }
        if (preTransform != null) {
            preTransform.transform(tmp);
        }
    }

    /**
     * [Plan B] Computes the quad's per-face normal and writes it to the Tessellator.
     * <p>
     * The normal is taken from {@link BakedQuad#face}'s direction vector, transformed sequentially by the rotation
     * parts of display / transformation / preTransform (staying consistent with the software-transformed vertices),
     * then normalized and passed to {@code t.setNormal}. The length is handled by {@code GL_NORMALIZE}
     * (see {@link FeatureRenderDispatcher}), so only the direction must be correct.
     */
    private static void writeQuadNormal(Tessellator t, BakedQuad q,
            Matrix4d displayTransform, Matrix4d transformation,
            Matrix4d preTransform, Vector3d tmp) {
        Direction face = q.face;
        if (face != null) {
            tmp.set(face.getStepX(), face.getStepY(), face.getStepZ());
        } else {
            tmp.set(0.0, 1.0, 0.0);
        }
        if (displayTransform != null)
            transformDirection(displayTransform, tmp);
        if (transformation != null)
            transformDirection(transformation, tmp);
        if (preTransform != null)
            transformDirection(preTransform, tmp);
        double len = tmp.length();
        if (len > 1.0e-6)
            tmp.scale(1.0 / len);
        t.setNormal((float) tmp.x, (float) tmp.y, (float) tmp.z);
    }

    /** Transforms a direction vector by the 3x3 rotation part of a 4x4 matrix (ignoring translation). */
    private static void transformDirection(Matrix4d m, Vector3d v) {
        double x = m.m00 * v.x + m.m01 * v.y + m.m02 * v.z;
        double y = m.m10 * v.x + m.m11 * v.y + m.m12 * v.z;
        double z = m.m20 * v.x + m.m21 * v.y + m.m22 * v.z;
        v.set(x, y, z);
    }

    /**
     * Computes the GUI-phase screen-space directional lighting (mirroring the visual semantics of the 1.7.10
     * {@code RenderItem} block branch): the light direction is fixed on screen - top 1.0, screen-left 0.8,
     * screen-right 0.6, bottom 0.5 - independent of the {@code display.gui.rotation} angle.
     * <p>
     * How it works: the quad's model-space face normal is transformed into world/screen space by the rotation part of
     * the display matrix, then looked up by the dominant axis of the rotated normal. This guarantees that regardless
     * of the GUI view angle, the shading distribution is always "top brightest, lower-left next, lower-right darkest",
     * avoiding the left/right brightness flip that model-space shade suffers as the view rotates.
     * <p>
     * Used only for block-like models with {@code gui_light="side"} (or null); {@code gui_light="front"}
     * 2D items stay fully lit (shade=1.0) and do not enter this method.
     * <p>
     * Screen-space directional shading for GUI block models: the light is fixed
     * on screen (top 1.0 / screen-left 0.8 / screen-right 0.6 / bottom 0.5)
     * regardless of the display.gui.rotation angle.
     */
    private static float guiScreenShade(BakedQuad q, Matrix4d displayTransform) {
        // Model-space face normal (axis-aligned unit vector); treated as UP when face is null (direction-less faces such as cross)
        double nx = 0, ny = 1, nz = 0;
        if (q.face != null) {
            nx = q.face.getStepX();
            ny = q.face.getStepY();
            nz = q.face.getStepZ();
        }
        // Rotated into screen/world space by the display matrix (translation ignored; GUI scale is usually uniform,
        // so the direction deviation under non-uniform scale is acceptable)
        if (displayTransform != null) {
            double x = displayTransform.m00 * nx + displayTransform.m01 * ny + displayTransform.m02 * nz;
            double y = displayTransform.m10 * nx + displayTransform.m11 * ny + displayTransform.m12 * nz;
            double z = displayTransform.m20 * nx + displayTransform.m21 * ny + displayTransform.m22 * nz;
            nx = x;
            ny = y;
            nz = z;
        }
        double ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
        if (ay >= ax && ay >= az) return ny > 0 ? 1.0f : 0.5f; // top face brightest / bottom face darkest
        if (ax >= ay && ax >= az) return nx > 0 ? 0.6f : 0.8f; // screen-right dark / screen-left bright
        return 0.8f; // facing the screen (north/south face semantics)
    }

}
