package decok.dfcdvadstf.catframe.resources.atlas.source;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 像素级变换描述 —— 供 M2 unstitch（网格裁剪）与 M4 paletted_permutations
 * （关键色替换）两类源在 sprite 解码后施加，属 SpriteRef 的可选载荷。
 * <p>
 * 变换在 CatSpriteLoader 中于主线程构造（闭包捕获 palette/overlay 像素），
 * 在并行解码线程应用 —— 实现必须无状态、线程安全（只读闭包）。
 *
 * <p>Optional pixel-level transform carried by {@link SpriteRef}; applied by the
 * sprite loader after PNG decode. Implementations must be stateless and
 * thread-safe (captured data only), since the transform runs on the decode pool.
 */
@SideOnly(Side.CLIENT)
public interface PixelTransform {

    /**
     * 对源像素施加变换。
     *
     * @param src       源像素（flat ARGB，row-major）
     * @param srcWidth  源宽度
     * @param srcHeight 源高度
     * @return 变换结果（像素 + 输出尺寸）
     */
    Result apply(int[] src, int srcWidth, int srcHeight);

    /**
     * 变换结果：输出像素与输出尺寸（CatSprite 内容尺寸以此为准）。
     */
    final class Result {
        /** 输出像素（flat ARGB，row-major）。 */
        public final int[] pixels;
        /** 输出宽度。 */
        public final int width;
        /** 输出高度。 */
        public final int height;

        public Result(int[] pixels, int width, int height) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
        }
    }
}
