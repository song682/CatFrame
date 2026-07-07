package decok.dfcdvadstf.catframe.model.render.extension.ao.light;

/**
 * 光照坐标打包与平滑混合工具类。
 *
 * <p>对齐 26.1.2 {@code net.minecraft.util.LightCoordsUtil}。
 * 光照坐标格式与 1.7.10 原版一致：{@code blockLight << 4 | skyLight << 20}。
 *
 * <p>核心功能：
 * <ul>
 *   <li>{@link #pack} / {@link #block} / {@link #sky} — 打包与解包</li>
 *   <li>{@link #smoothBlend} — 4 邻域平滑混合（当中心亮度 > 2 时，零值邻居继承中心分量，
 *       避免暗角处出现不自然的黑边）</li>
 *   <li>{@link #smoothWeightedBlend} — 部分面加权平滑混合</li>
 * </ul>
 */
public final class LightCoordsUtil {

    /** 全亮度常量（block=15, sky=15）。 */
    public static final int FULL_BRIGHT = 15728880;

    /** 纯天光全亮度（block=0, sky=15）。 */
    public static final int FULL_SKY = 15728640;

    /** 平滑光照的最大离散级别（用于 smoothPack 格式）。 */
    private static final int MAX_SMOOTH_LIGHT_LEVEL = 240;

    private LightCoordsUtil() {}

    // ==================== 打包 / 解包 ====================

    /**
     * 将 block 和 sky 亮度打包为单个 int。
     * 格式：{@code block << 4 | sky << 20}。
     */
    public static int pack(int block, int sky) {
        return (block & 15) << 4 | (sky & 15) << 20;
    }

    /** 提取 packed 坐标中的 block 亮度分量（0-15）。 */
    public static int block(int packed) {
        return (packed >> 4) & 15;
    }

    /** 提取 packed 坐标中的 sky 亮度分量（0-15）。 */
    public static int sky(int packed) {
        return (packed >> 20) & 15;
    }

    /**
     * 替换 packed 坐标中的 block 分量，保留 sky 分量。
     */
    public static int withBlock(int coords, int block) {
        return (coords & 0xFF0000) | (block & 15) << 4;
    }

    // ==================== 平滑格式（用于加权混合） ====================

    /**
     * 将 block/sky 打包为平滑格式（各 8 位，范围 0-240）。
     * 用于 {@link #smoothWeightedBlend} 的中间计算。
     */
    public static int smoothPack(int block, int sky) {
        return (block & 0xFF) | (sky & 0xFF) << 16;
    }

    /** 从平滑格式中提取 block 分量。 */
    public static int smoothBlock(int packed) {
        return packed & 0xFF;
    }

    /** 从平滑格式中提取 sky 分量。 */
    public static int smoothSky(int packed) {
        return (packed >> 16) & 0xFF;
    }

    // ==================== 混合 ====================

    /**
     * 4 邻域平滑混合（等权平均）。
     *
     * <p>对齐 26.1.2 {@code LightCoordsUtil.smoothBlend}：
     * 当中心亮度 > 2 时，亮度为 0 的邻居自动继承中心对应分量，
     * 防止暗角处出现不自然的黑边。
     *
     * @param neighbor1 边邻居 1（packed 格式）
     * @param neighbor2 边邻居 2（packed 格式）
     * @param neighbor3 角邻居（packed 格式）
     * @param center    中心亮度（packed 格式）
     * @return 混合后的 packed 光照坐标
     */
    public static int smoothBlend(int neighbor1, int neighbor2, int neighbor3, int center) {
        // 当中心亮度足够亮时，零值邻居继承中心分量
        if (sky(center) > 2 || block(center) > 2) {
            if (sky(neighbor1) == 0) {
                neighbor1 |= center & 0xFF0000; // 继承 sky
            }
            if (block(neighbor1) == 0) {
                neighbor1 |= center & 0xFF;     // 继承 block
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

        // 等权平均，保留 block 和 sky 位域
        return (neighbor1 + neighbor2 + neighbor3 + center) >> 2 & 0x00FF00FF;
    }

    /**
     * 加权平滑混合（用于部分面的双线性插值）。
     *
     * <p>对齐 26.1.2 {@code LightCoordsUtil.smoothWeightedBlend}：
     * 将 4 个平滑格式光照坐标按权重混合。
     *
     * @param coords1..4 平滑格式光照坐标
     * @param weight1..4 各坐标的权重（通常由 faceShape 计算得出）
     * @return 混合后的平滑格式光照坐标
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
