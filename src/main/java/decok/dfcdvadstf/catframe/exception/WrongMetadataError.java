package decok.dfcdvadstf.catframe.exception;

/**
 * <p>
 * mcmeta 元数据校验失败时抛出。<br>
 * Thrown when mcmeta metadata validation fails.
 * </p>
 *
 * <p>覆盖场景 / Covered scenarios:</p>
 * <ul>
 *   <li>default width/height 为负数 / negative</li>
 *   <li>edge 值为负数 / negative</li>
 *   <li>type 字段不是已知类型 / unknown type</li>
 * </ul>
 */
public class WrongMetadataError extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    /**
     * Invalid default width/height.
     * <p>无效的默认宽高。</p>
     *
     * @param defW default width / 默认宽度
     * @param defH default height / 默认高度
     */
    public WrongMetadataError(int defW, int defH) {
        super("Invalid mcmeta default size: width=" + defW + ", height=" + defH
                + ". Both must be >= 0.");
    }

    /**
     * Invalid edge value(s).
     * <p>无效的边缘值。</p>
     *
     * @param edgeName  edge name (e.g. "left", "top") / 边缘名称
     * @param edgeValue the invalid value / 无效值
     */
    public WrongMetadataError(String edgeName, int edgeValue) {
        super("Invalid mcmeta edge \"" + edgeName + "\": " + edgeValue
                + ". Edge values must be >= 0.");
    }

    /**
     * Unknown stretching type.
     * <p>未知的拉伸类型。</p>
     *
     * @param typeStr the unrecognised type string / 无法识别的类型字符串
     */
    public WrongMetadataError(String typeStr) {
        super("Unknown mcmeta stretching type: \"" + typeStr
                + "\". Expected one of: nine_patch, three_patch, tile, static.");
    }
}
