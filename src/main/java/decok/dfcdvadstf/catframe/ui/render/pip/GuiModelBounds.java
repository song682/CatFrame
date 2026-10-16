package decok.dfcdvadstf.catframe.ui.render.pip;

import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;

import javax.vecmath.Vector3d;
import java.util.List;

/**
 * GUI model bounds utility — measures whether a {@link BlockStateModelPart}'s geometry overflows the 16x16 slot.
 * <p>
 * The slot corresponds to {@code [0,1]^3} in block space (1.0 == 16px). Model geometry fully inside that box is
 * treated as "not oversized"; otherwise (accounting for {@code min<0} / {@code max>1} offsets on both sides) the
 * total span that must fit into the slot is computed as {@code maxExtent}, so the catcher of
 * {@link decok.dfcdvadstf.catframe.exception.WrongUIOverScaleException} can clamp back into the slot with {@code 1/maxExtent}.
 */
public final class GuiModelBounds {

    /**
     * Overflow tolerance: only {@code maxExtent > 1.0 + OVERSIZE_TOLERANCE} counts as oversized,
     * avoiding false positives from floating-point error at the slot edges (threshold defaults to 1.05).
     */
    public static final float OVERSIZE_TOLERANCE = 0.05f;

    private GuiModelBounds() {
    }

    /**
     * Computes the model's maximum axis span relative to the slot {@code [0,1]} in block space.
     * <p>
     * Each axis takes {@code max(maxCoord, 1.0) - min(minCoord, 0.0)} (counting the &lt;0 and &gt;1 overflows),
     * and the largest of the three axes is returned. Returns {@code 1.0} when the model lies entirely inside the
     * slot. Empty models return {@code 1.0}.
     */
    public static float computeMaxExtent(BlockStateModelPart part) {
        if (part == null) return 1.0f;
        List<BakedQuad> quads = part.getAllQuads();
        if (quads == null || quads.isEmpty()) return 1.0f;

        double minX = 0.0, minY = 0.0, minZ = 0.0;
        double maxX = 1.0, maxY = 1.0, maxZ = 1.0;
        for (BakedQuad quad : quads) {
            if (quad == null || quad.vertices == null) continue;
            for (Vector3d v : quad.vertices) {
                if (v == null) continue;
                if (v.x < minX) minX = v.x;
                if (v.y < minY) minY = v.y;
                if (v.z < minZ) minZ = v.z;
                if (v.x > maxX) maxX = v.x;
                if (v.y > maxY) maxY = v.y;
                if (v.z > maxZ) maxZ = v.z;
            }
        }

        double spanX = maxX - minX;
        double spanY = maxY - minY;
        double spanZ = maxZ - minZ;
        double max = Math.max(spanX, Math.max(spanY, spanZ));
        return (float) max;
    }

    /**
     * Whether the model geometry overflows the slot (with tolerance).
     */
    public static boolean isOversized(BlockStateModelPart part) {
        return computeMaxExtent(part) > 1.0f + OVERSIZE_TOLERANCE;
    }
}
