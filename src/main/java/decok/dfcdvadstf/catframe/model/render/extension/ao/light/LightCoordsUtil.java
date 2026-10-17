package decok.dfcdvadstf.catframe.model.render.extension.ao.light;

/**
 * Utility class for packing light coordinates and smooth blending.
 *
 * <p>Aligned with 26.1.2 {@code net.minecraft.util.LightCoordsUtil}.
 * The light coordinate format matches vanilla 1.7.10: {@code blockLight << 4 | skyLight << 20}.
 *
 * <p>Core features:
 * <ul>
 *   <li>{@link #pack} / {@link #block} / {@link #sky} — packing and unpacking</li>
 *   <li>{@link #smoothBlend} — 4-neighborhood smooth blending (when the center brightness > 2, zero-valued neighbors inherit the center component,
 *       avoiding unnatural dark edges at corners)</li>
 *   <li>{@link #smoothWeightedBlend} — partial-face weighted smooth blending</li>
 * </ul>
 */
public final class LightCoordsUtil {

    /** Full-brightness constant (block=15, sky=15). */
    public static final int FULL_BRIGHT = 15728880;

    /** Sky-only full brightness (block=0, sky=15). */
    public static final int FULL_SKY = 15728640;

    /** Maximum discrete level of smooth lighting (used by the smoothPack format). */
    private static final int MAX_SMOOTH_LIGHT_LEVEL = 240;

    private LightCoordsUtil() {}

    // ==================== Packing / unpacking ====================

    /**
     * Packs the block and sky brightness into a single int.
     * Format: {@code block << 4 | sky << 20}.
     */
    public static int pack(int block, int sky) {
        return (block & 15) << 4 | (sky & 15) << 20;
    }

    /** Extracts the block brightness component (0-15) from packed coordinates. */
    public static int block(int packed) {
        return (packed >> 4) & 15;
    }

    /** Extracts the sky brightness component (0-15) from packed coordinates. */
    public static int sky(int packed) {
        return (packed >> 20) & 15;
    }

    /**
     * Replaces the block component in packed coordinates while keeping the sky component.
     */
    public static int withBlock(int coords, int block) {
        return (coords & 0xFF0000) | (block & 15) << 4;
    }

    // ==================== Smooth format (for weighted blending) ====================

    /**
     * Packs block/sky into the smooth format (8 bits each, range 0-240).
     * Used for the intermediate computation of {@link #smoothWeightedBlend}.
     */
    public static int smoothPack(int block, int sky) {
        return (block & 0xFF) | (sky & 0xFF) << 16;
    }

    /** Extracts the block component from the smooth format. */
    public static int smoothBlock(int packed) {
        return packed & 0xFF;
    }

    /** Extracts the sky component from the smooth format. */
    public static int smoothSky(int packed) {
        return (packed >> 16) & 0xFF;
    }

    // ==================== Blending ====================

    /**
     * 4-neighborhood smooth blending (equal-weight average).
     *
     * <p>Aligned with 26.1.2 {@code LightCoordsUtil.smoothBlend}:
     * when the center brightness > 2, neighbors with brightness 0 automatically inherit the corresponding center component,
     * preventing unnatural dark edges at corners.
     *
     * @param neighbor1 edge neighbor 1 (packed format)
     * @param neighbor2 edge neighbor 2 (packed format)
     * @param neighbor3 corner neighbor (packed format)
     * @param center    center brightness (packed format)
     * @return the blended packed light coordinate
     */
    public static int smoothBlend(int neighbor1, int neighbor2, int neighbor3, int center) {
        // When the center brightness is bright enough, zero-valued neighbors inherit the center component
        if (sky(center) > 2 || block(center) > 2) {
            if (sky(neighbor1) == 0) {
                neighbor1 |= center & 0xFF0000; // inherit sky
            }
            if (block(neighbor1) == 0) {
                neighbor1 |= center & 0xFF;     // inherit block
            }
            if (sky(neighbor2) == 0) {
                neighbor2 |= center & 0xFF0000;
            }
            if (block(neighbor2) == 0) {
                neighbor2 |= center & 0xFF;
            }
            if (sky(neighbor3) == 0) {
                neighbor3 |= center & 0xFF0000;
            }
            if (block(neighbor3) == 0) {
                neighbor3 |= center & 0xFF;
            }
        }

        // Equal-weight average, preserving the block and sky bit fields
        return (neighbor1 + neighbor2 + neighbor3 + center) >> 2 & 0x00FF00FF;
    }

    /**
     * Weighted smooth blending (used for bilinear interpolation on partial faces).
     *
     * <p>Aligned with 26.1.2 {@code LightCoordsUtil.smoothWeightedBlend}:
     * blends 4 smooth-format light coordinates by weight.
     *
     * @param coords1..4 smooth-format light coordinates
     * @param weight1..4 the weight of each coordinate (usually computed from faceShape)
     * @return the blended smooth-format light coordinate
     */
    public static int smoothWeightedBlend(
            int coords1, int coords2, int coords3, int coords4,
            float weight1, float weight2, float weight3, float weight4) {
        int sky = (int) (smoothSky(coords1) * weight1
                       + smoothSky(coords2) * weight2
                       + smoothSky(coords3) * weight3
                       + smoothSky(coords4) * weight4);
        int blk = (int) (smoothBlock(coords1) * weight1
                       + smoothBlock(coords2) * weight2
                       + smoothBlock(coords3) * weight3
                       + smoothBlock(coords4) * weight4);
        return smoothPack(blk, sky);
    }
}
