package decok.dfcdvadstf.catframe.resources.atlas.source;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Pixel-level transform descriptor — applied after sprite decode by the two
 * source kinds M2 unstitch (grid clipping) and M4 paletted_permutations
 * (key-color replacement); an optional payload of SpriteRef.
 * <p>
 * Transforms are constructed on the main thread in CatSpriteLoader (closures
 * capture palette/overlay pixels) and applied on the parallel decode threads —
 * implementations must be stateless and thread-safe (read-only closures).
 */
@SideOnly(Side.CLIENT)
public interface PixelTransform {

    /**
     * Applies the transform to the source pixels.
     *
     * @param src       source pixels (flat ARGB, row-major)
     * @param srcWidth  source width
     * @param srcHeight source height
     * @return the transform result (pixels + output size)
     */
    Result apply(int[] src, int srcWidth, int srcHeight);

    /**
     * Transform result: output pixels and output size (CatSprite content size is based on this).
     */
    final class Result {
        /** Output pixels (flat ARGB, row-major). */
        public final int[] pixels;
        /** Output width. */
        public final int width;
        /** Output height. */
        public final int height;

        public Result(int[] pixels, int width, int height) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
        }
    }
}
