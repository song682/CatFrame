package decok.dfcdvadstf.catframe.exception;

/**
 * <p>
 * Thrown when mcmeta metadata validation fails.
 * </p>
 * <p> Covered scenarios:</p>
 * <ul>
 *   <li>default width/height is negative</li>
 *   <li>edge value is negative</li>
 *   <li>type field is not a known type</li>
 * </ul>
 */
public class WrongMetadataError extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    /**
     * Invalid default width/height.
     *
     * @param defW default width
     * @param defH default height
     */
    public WrongMetadataError(int defW, int defH) {
        super("Invalid mcmeta default size: width=" + defW + ", height=" + defH
                + ". Both must be >= 0.");
    }

    /**
     * Invalid edge value(s).
     *
     * @param edgeName  edge name (e.g. "left", "top")
     * @param edgeValue the invalid value
     */
    public WrongMetadataError(String edgeName, int edgeValue) {
        super("Invalid mcmeta edge \"" + edgeName + "\": " + edgeValue
                + ". Edge values must be >= 0.");
    }

    /**
     * Unknown stretching type.
     *
     * @param typeStr the unrecognised type string
     */
    public WrongMetadataError(String typeStr) {
        super("Unknown mcmeta stretching type: \"" + typeStr
                + "\". Expected one of: nine_patch, three_patch, tile, static.");
    }
}
