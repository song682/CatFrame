package decok.dfcdvadstf.catframe.model.render.pipeline;

import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.api.RenderSubmitView;
import decok.dfcdvadstf.catframe.model.render.api.RenderTypeKey;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4d;
import java.util.List;
import java.util.Map;

/**
 * Immutable data snapshot of a single render submission, mirroring the {@code Submit}
 * command record in the vanilla 26w+ pipeline.
 * <p>
 * Pure data, no GPU logic: it carries all inputs needed by {@link QuadWriter} to
 * reproduce the "per-vertex write loop". It is built by
 * {@link decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline} and enters the
 * command buffer or an immediate flush via {@link RenderCommandBuffers#submit(RenderSubmit)}.
 * <p>
 * The display transform and preTransform are already baked per-vertex into the
 * coordinates on the Java side (vector space), so no GL matrix snapshot is needed at
 * flush time — a scope flush always happens within the GL matrix context already
 * established by the caller.
 */
public final class RenderSubmit implements RenderSubmitView {

    /** Render phase (determines brightness/gui branching, etc.). */
    public final RenderPhase phase;
    /** The part to render (provides {@code getAllQuads()}). */
    public final BlockStateModelPart part;
    /** Group type (atlas + blend); holds a direct registry entry reference (zero lookup on the submit path). */
    public final RenderTypeKey type;

    /** Block coordinates (usually 0 during the item phase). */
    public final int x, y, z;
    /** Y-axis rotation angle (used for block world rendering, supports arbitrary angles). */
    public final float rotationDeg;

    /** Block instance (may be null during the item phase). */
    @Nullable
    public final Block block;
    /** Item stack (null during the block phase). */
    @Nullable
    public final ItemStack stack;
    /** World access (null when there is no world context). */
    @Nullable
    public final IBlockAccess world;
    /** Block metadata (used for tinting and similar scenarios). */
    public final int metadata;

    /** Pre-transform matrix (counter-offset), applied per-vertex after the display transform; may be null. */
    @Nullable
    public final Matrix4d preTransform;

    /**
     * Item model render transformation (the {@code transformation} tag of the
     * {@code minecraft:model} node in items JSON), applied per-vertex after the display
     * transform and before preTransform; may be null (identity transform).
     */
    @Nullable
    public final Matrix4d transformation;

    /** Whether face culling must be disabled at flush time (true for the item path, false for the block path). */
    public final boolean disableCull;
    /** Whether blending must be enabled at flush time (consistent with {@link RenderTypeKey#blend()}). */
    public final boolean blend;

    /**
     * Block state properties (such as pane's north/east/south/west, stairs' facing/half/shape),
     * computed during the model parsing phase and carried with the submission, exposed to the
     * extension chain via {@link RenderContext#blockstateProps}.
     * Only BLOCK_WORLD / BLOCK_DESTROY submissions are non-null.
     */
    @Nullable
    public final Map<String, String> blockstateProps;

    /**
     * Item properties (such as damage / using_item / use_duration / display_context),
     * built by the item property system and carried with the submission, exposed to the
     * extension chain via {@link RenderContext#itemProps}.
     * Only ITEM_* submissions are non-null.
     */
    @Nullable
    public final Map<String, Comparable<?>> itemProps;

    /**
     * Baseline brightness (packed sky<<20 | block<<4 format; the GUI phase uses the
     * 255 screen-light semantics). Once the built-in LightPolicyExtension is ready, the
     * {@code ctx.brightnessOverride} written during its write phase takes precedence over
     * this field (light policy lives uniformly in the extension chain); this field serves
     * only as two kinds of fallback: <b>(a)</b> when a direct constructor explicitly
     * specifies it (≥ 0), QuadWriter constructs ctx with it as the baseline; (b)
     * <b>-1 = unspecified</b> — QuadWriter falls back to computing via RenderPhasePolicy
     * by phase (a safety net for a missing chain; fallback retained).
     */
    public final int baselineBrightness;

    /**
     * Legacy-signature constructor compatibility shim: blockstateProps / itemProps are both null.
     */
    public RenderSubmit(RenderPhase phase, BlockStateModelPart part, RenderTypeKey type,
                        int x, int y, int z, float rotationDeg,
                        @Nullable Block block, @Nullable ItemStack stack,
                        @Nullable IBlockAccess world, int metadata,
                        @Nullable Matrix4d preTransform,
                        @Nullable Matrix4d transformation,
                        boolean disableCull, boolean blend) {
        this(phase, part, type, x, y, z, rotationDeg,
                block, stack, world, metadata,
                preTransform, transformation,
                disableCull, blend, null, null);
    }

    /**
     * Compatibility overload: baseline brightness unspecified (-1) — on the normal render
     * path it is decided by the built-in LightPolicyExtension at write time; QuadWriter's
     * -1 fallback (computed via RenderPhasePolicy by phase) only acts as a safety net for
     * a missing chain (fallback retained).
     */
    public RenderSubmit(RenderPhase phase, BlockStateModelPart part, RenderTypeKey type,
                        int x, int y, int z, float rotationDeg,
                        @Nullable Block block, @Nullable ItemStack stack,
                        @Nullable IBlockAccess world, int metadata,
                        @Nullable Matrix4d preTransform,
                        @Nullable Matrix4d transformation,
                        boolean disableCull, boolean blend,
                        @Nullable Map<String, String> blockstateProps,
                        @Nullable Map<String, Comparable<?>> itemProps) {
        this(phase, part, type, x, y, z, rotationDeg,
                block, stack, world, metadata,
                preTransform, transformation,
                disableCull, blend, blockstateProps, itemProps, -1);
    }

    public RenderSubmit(RenderPhase phase, BlockStateModelPart part, RenderTypeKey type,
                        int x, int y, int z, float rotationDeg,
                        @Nullable Block block, @Nullable ItemStack stack,
                        @Nullable IBlockAccess world, int metadata,
                        @Nullable Matrix4d preTransform,
                        @Nullable Matrix4d transformation,
                        boolean disableCull, boolean blend,
                        @Nullable Map<String, String> blockstateProps,
                        @Nullable Map<String, Comparable<?>> itemProps,
                        int baselineBrightness) {
        this.phase = phase;
        this.part = part;
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.rotationDeg = rotationDeg;
        this.block = block;
        this.stack = stack;
        this.world = world;
        this.metadata = metadata;
        this.preTransform = preTransform;
        this.transformation = transformation;
        this.disableCull = disableCull;
        this.blend = blend;
        this.blockstateProps = blockstateProps;
        this.itemProps = itemProps;
        this.baselineBrightness = baselineBrightness;
    }

    // ==================== RenderSubmitView (read-only view implementation) ====================

    @Override
    public RenderPhase phase() {
        return phase;
    }

    @Override
    public RenderTypeKey type() {
        return type;
    }

    @Override
    public int x() {
        return x;
    }

    @Override
    public int y() {
        return y;
    }

    @Override
    public int z() {
        return z;
    }

    @Override
    public float rotationDeg() {
        return rotationDeg;
    }

    @Override
    public Block block() {
        return block;
    }

    @Override
    public ItemStack stack() {
        return stack;
    }

    @Override
    public IBlockAccess world() {
        return world;
    }

    @Override
    public int metadata() {
        return metadata;
    }

    @Override
    public boolean disableCull() {
        return disableCull;
    }

    @Override
    public boolean blend() {
        return blend;
    }

    @Override
    public List<BakedQuad> getAllQuads() {
        return part.getAllQuads();
    }

    @Override
    public Matrix4d preTransformCopy() {
        return preTransform == null ? null : new Matrix4d(preTransform);
    }

    @Override
    public Matrix4d transformationCopy() {
        return transformation == null ? null : new Matrix4d(transformation);
    }

    @Override
    public Map<String, String> blockstateProps() {
        return blockstateProps;
    }

    @Override
    public Map<String, Comparable<?>> itemProps() {
        return itemProps;
    }
}
