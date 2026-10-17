package decok.dfcdvadstf.catframe.model.render;

import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.api.RenderTypeKey;
import decok.dfcdvadstf.catframe.model.ModelRegistry;
import decok.dfcdvadstf.catframe.model.render.pipeline.RenderCommandBuffers;
import decok.dfcdvadstf.catframe.model.render.pipeline.RenderSubmit;
import decok.dfcdvadstf.catframe.model.render.pipeline.RenderTypeRegistry;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateModel;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4d;
import java.util.Map;

/**
 * Uniform Render Pipeline - the <b>submission side</b> of the deferred command pipeline.
 * <p>
 * Following the vanilla 26w+ deferred render pipeline (Extract → Submit → Render), this class no longer
 * "immediately writes to Tessellator"; instead, each render call <b>builds an immutable {@link RenderSubmit} command</b>
 * and passes it to {@link RenderCommandBuffers#submit(RenderSubmit)}:
 * <ul>
 *   <li>Block <b>world</b> rendering ({@link RenderPhase#BLOCK_WORLD}) → the sole path for the vanilla backend
 *       under the three-domain render architecture: goes through
 *       {@link decok.dfcdvadstf.catframe.model.render.pipeline.FeatureRenderDispatcher#flushInline}
 *       and writes inline to the vanilla chunk Tessellator (batch binds the vanilla blocks atlas; quads carry
 *       vanilla UVs; no GL calls; compatible with Beddium multithreaded chunk compilation).</li>
 *   <li>Item/GUI standalone draw paths → enter the render scope command buffer, batch-flushed by the registry's
 *       sort key ({@link RenderTypeKey#sortKey()}, solid→translucent), with single texture binding per batch.</li>
 * </ul>
 * <p>
 * The per-vertex write loop has been extracted to
 * {@link decok.dfcdvadstf.catframe.model.render.pipeline.QuadWriter}; command execution and batch drawing
 * are handled by {@link decok.dfcdvadstf.catframe.model.render.pipeline.FeatureRenderDispatcher}.
 * <p>
 * <b>All public method signatures remain unchanged</b>; callers do not need to be modified.
 */
public final class UniformRenderPipeline {

    private UniformRenderPipeline() {
    }

     // ==================== Block world rendering ====================

    /**
     * Render block quads (world / destroy overlay). Builds a {@link RenderSubmit} and submits it to the command pipeline.
     *
     * @param part        model part to render
     * @param world       world access
     * @param x           block X
     * @param y           block Y
     * @param z           block Z
     * @param block       block instance
     * @param rotationDeg Y-axis rotation degrees (0/90/180/270)
     * @param phase       render phase (BLOCK_WORLD / BLOCK_DESTROY)
     */
    public static void renderBlockQuads(BlockStateModelPart part,
                                         IBlockAccess world, int x, int y, int z,
                                         Block block, float rotationDeg,
                                         RenderPhase phase) {
        renderBlockQuads(part, world, x, y, z, block, rotationDeg, phase, 0);
    }

    /**
     * Render block quads with metadata support (for tinting etc.). Display transform is held by
     * {@link BlockStateModelPart#getDisplay()} and consumed by
     * {@link decok.dfcdvadstf.catframe.model.render.extension.DisplayTransformExtension} in the extension chain.
     *
     * @param part     model part to render
     * @param metadata block metadata (for tinting)
     */
    public static void renderBlockQuads(BlockStateModelPart part,
                                         IBlockAccess world, int x, int y, int z,
                                         Block block, float rotationDeg,
                                         RenderPhase phase,
                                         int metadata) {
        renderBlockQuads(part, world, x, y, z, block, rotationDeg, phase, metadata, null);
    }

    /**
     * Render block quads with metadata and blockstate property support.
     *
     * @param part            model part to render
     * @param metadata       block metadata (for tinting)
     * @param blockstateProps blockstate properties constructed at match time (unmodifiable view, may be null);
     *                        carried with the submission and readable by extensions via {@code RenderContext.blockstateProps}
     */
    public static void renderBlockQuads(BlockStateModelPart part,
                                         IBlockAccess world, int x, int y, int z,
                                         Block block, float rotationDeg,
                                         RenderPhase phase,
                                         int metadata,
                                         @Nullable Map<String, String> blockstateProps) {
        if (part == null) return;

        // World rendering binds the CatAtlas: blocks with getRenderBlockPass()==1 go to the translucent group
        // (blending enabled), others go to the solid group (alpha test). Not opaque and culling remains enabled (disableCull=false).
        boolean translucent = block != null && block.getRenderBlockPass() == 1;
        RenderTypeKey type = translucent
                ? RenderTypeRegistry.BLOCK_ATLAS_TRANSLUCENT
                : RenderTypeRegistry.BLOCK_ATLAS_SOLID;
        // Brightness policy is determined by built-in extension LightPolicyExtension at write time:
        // per-vertex AO quads in BLOCK_WORLD use the aoBrightness channel; only AO-degraded quads have default
        // block brightness computed by it. The submission side no longer resolves brightness (-1 semantics
        // defined in RenderSubmit; QuadWriter fallback is only a safety net if the chain is missing).
        RenderSubmit s = new RenderSubmit(
                phase, part, type,
                x, y, z, rotationDeg,
                block, null, world, metadata,
                null, null,
                false, false,
                blockstateProps, null);
        RenderCommandBuffers.submit(s);
    }

    /**
     * Render block quads in world context (backward compatible; defaults to BLOCK_WORLD with no display).
     */
    public static void renderBlockQuads(BlockStateModelPart part,
                                         IBlockAccess world, int x, int y, int z,
                                         Block block, float rotationDeg) {
        renderBlockQuads(part, world, x, y, z, block, rotationDeg,
                RenderPhase.BLOCK_WORLD, 0);
    }

    // ==================== Item rendering ====================

    /**
     * Official recursive item render entry: resolves the registered {@link ItemStateModel} for the target item
     * and renders its layers.
     * <p>
     * Intended for special model nodes ({@link decok.dfcdvadstf.catframe.model.state.item.ItemStateNode.SpecialNode})
     * and per-item fully custom draw providers to draw contained stacks, replacing third-party hijacking of
     * {@link #renderItemQuads} (which requires a pre-resolved {@link BlockStateModelPart}).
     * <p>
     * Items not registered with a CatFrame model are not rendered.
     *
     * @param stack        item stack to render
     * @param phase        render phase
     * @param preTransform inverse transform to cancel pre-applied transforms; may be null
     */
    public static void renderItemStack(ItemStack stack, RenderPhase phase,
                                        @Nullable Matrix4d preTransform) {
        if (stack == null || phase == null) return;
        Item item = stack.getItem();
        if (item == null) return;
        // Only render items that have a CatFrame model explicitly registered.
        ItemStateModel model = ModelRegistry.registeredItemModels.get(item);
        if (model == null) return;
        model.render(stack, phase, preTransform);
    }

    /**
     * Render item quads (GUI / hand / dropped / item frame). Builds a {@link RenderSubmit} and submits it.
     * <p>
     * Display transform and preTransform are baked into vertex coordinates as vector-space matrices in the
     * extension chain/pipeline, so no GL matrix snapshot is needed during batch flush.
     *
     * @param part         model part to render
     * @param stack        item stack
     * @param phase        render phase (ITEM_GUI / ITEM_HAND_* / DROPPED_* / ITEM_FIXED)
     * @param world        world context (used only for phases like DROPPED_BLOCK_GROUND that need block info; otherwise null)
     * @param x            block X (0 if no world context)
     * @param y            block Y
     * @param z            block Z
     * @param block        block instance (null if none)
     * @param preTransform optional pre-transform matrix (inverse cancellation), applied to vertices after display transform; may be null
     * @param transformation optional item model transformation (items JSON {@code transformation} tag),
     *                       applied to vertices after display transform and before preTransform; may be null (identity)
     */
    public static void renderItemQuads(BlockStateModelPart part,
                                        ItemStack stack, RenderPhase phase,
                                        IBlockAccess world, int x, int y, int z, Block block,
                                        @Nullable Matrix4d preTransform,
                                        @Nullable Matrix4d transformation) {
        renderItemQuads(part, stack, phase, world, x, y, z, block,
                preTransform, transformation, null);
    }

    /**
     * Backward-compatible overload without transformation — delegates to the full version (null means identity).
     */
    public static void renderItemQuads(BlockStateModelPart part,
                                        ItemStack stack, RenderPhase phase,
                                        IBlockAccess world, int x, int y, int z, Block block,
                                        @Nullable Matrix4d preTransform) {
        renderItemQuads(part, stack, phase, world, x, y, z, block, preTransform, null);
    }

    /**
     * Render item quads with item property support.
     *
     * @param itemProps item property set (read-only lazy Map, may be null), built by the item property system;
     *                  carried with the submission and readable by extensions via {@code RenderContext.itemProps}
     */
    public static void renderItemQuads(BlockStateModelPart part,
                                        ItemStack stack, RenderPhase phase,
                                        IBlockAccess world, int x, int y, int z, Block block,
                                        @Nullable Matrix4d preTransform,
                                        @Nullable Matrix4d transformation,
                                        @Nullable Map<String, Comparable<?>> itemProps) {
        if (part == null) return;

        boolean gui = (phase == RenderPhase.ITEM_GUI);
        boolean blendRequired = (gui
                || phase == RenderPhase.DROPPED_ITEM_GROUND
                || phase == RenderPhase.DROPPED_BLOCK_GROUND
                || phase == RenderPhase.ITEM_FIXED);
        // Brightness policy is determined by built-in extension LightPolicyExtension at write time
        // (GUI fixed to 255 / hand uses player light / dropped and item frame use entity light;
        // beforePart computes once per submitted item; atlasSplit produces two submissions with same value).
        // Submission does not resolve brightness (-1 semantics defined in RenderSubmit).
        // Texture atlas selection follows quad markers baked in at bake time (ModelJsonUnbakedAdapter →
        // BakedQuad.blockAtlas), zero guesswork at render time. Mixed-atlas models are split into at most
        // two submissions, each placed into the correct registry group (matching 26.1.2 per-quad itemRenderType).
        // Matching 26.1.2: ITEM_CUTOUT / ITEM_TRANSLUCENT pipelines enable backface culling by default;
        // builtin/generated models have north/south faces as doubles (symmetric winding), and side quad
        // winding aligns with emitFaceFromCorners — culling works correctly in all cases.
        BlockStateModelPart[] split = part.atlasSplit();
        for (int i = 0; i < split.length; i++) {
            BlockStateModelPart sub = split[i];
            if (sub == null) continue;
            RenderTypeKey type = RenderTypeRegistry.of(i == 0, blendRequired);
            // Both submissions share the same itemProps reference (zero copy)
            RenderSubmit s = new RenderSubmit(
                    phase, sub, type,
                    x, y, z, 0,
                    block, stack, world, 0,
                    preTransform,
                    transformation,
                    false, blendRequired,
                    null, itemProps);
            RenderCommandBuffers.submit(s);
        }
    }

    /**
     * Backward-compatible overload — delegates to the full version with no preTransform and no world context.
     */
    public static void renderItemQuads(BlockStateModelPart part,
                                        ItemStack stack, RenderPhase phase) {
        renderItemQuads(part, stack, phase, null, 0, 0, 0, null, null, null);
    }
}