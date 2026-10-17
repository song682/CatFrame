package decok.dfcdvadstf.catframe.model.render.api;

import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4d;
import java.util.List;
import java.util.Map;

/**
 * A read-only projection of a retained render submission for external
 * consumers. Matrices are returned as defensive copies; the internal part
 * object is only reachable through its quad list.
 */
public interface RenderSubmitView {

    /** Render phase (determines the brightness/gui branches etc.). */
    RenderPhase phase();

    /** Group type (atlas + blend + sort key). */
    RenderTypeKey type();

    /** Block coordinate (usually 0 for item phases). */
    int x();

    /** Block coordinate (usually 0 for item phases). */
    int y();

    /** Block coordinate (usually 0 for item phases). */
    int z();

    /** Y-axis rotation angle in degrees (used by block world/GUI, 0/90/180/270). */
    float rotationDeg();

    /** Block instance (may be null for item phases). */
    @Nullable
    Block block();

    /** Item stack (null for block phases). */
    @Nullable
    ItemStack stack();

    /** World access (null when there is no world context). */
    @Nullable
    IBlockAccess world();

    /** Block metadata (used for tinting and similar). */
    int metadata();

    /** Whether face culling must be disabled at flush (true for the item path, false for the block path). */
    boolean disableCull();

    /** Whether blending must be enabled at flush (consistent with the blend of {@link #type()}). */
    boolean blend();

    /** All quads of the part to render (read-only convention: the caller must not modify them). */
    List<BakedQuad> getAllQuads();

    /**
     * Defensive copy of the pre-transform (counter-transform) matrix, may be null.
     * A copy is returned; modifying it does not affect the internal submission data.
     */
    @Nullable
    Matrix4d preTransformCopy();

    /**
     * Defensive copy of the item model render transform (the items JSON
     * {@code transformation} tag), may be null. A copy is returned; modifying it
     * does not affect the internal submission data.
     */
    @Nullable
    Matrix4d transformationCopy();

    /**
     * Blockstate properties (e.g. pane north/east/south/west, stairs facing/half/shape),
     * computed by the model parsing stage and carried along with the submission.
     * Non-null only for BLOCK_WORLD / BLOCK_DESTROY submissions.
     * <p>
     * Read-only convention: returns an unmodifiable view; the caller must not modify
     * it. Defaults to null; {@code RenderSubmit} overrides it with the real value.
     */
    @Nullable
    default Map<String, String> blockstateProps() {
        return null;
    }

    /**
     * Item properties (e.g. damage / using_item / use_duration / display_context),
     * built by the item property system and carried along with the submission.
     * Non-null only for ITEM_* submissions.
     * <p>
     * Read-only lazy map: reading a particular key only then triggers the
     * corresponding provider's computation, so avoid iterating the whole map.
     * Defaults to null; {@code RenderSubmit} overrides it with the real value.
     */
    @Nullable
    default Map<String, Comparable<?>> itemProps() {
        return null;
    }
}
