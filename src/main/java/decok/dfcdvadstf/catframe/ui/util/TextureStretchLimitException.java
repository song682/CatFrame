package decok.dfcdvadstf.catframe.ui.util;

/**
 * <p>
 * 纹理拉伸尺寸限制异常 —— 当目标尺寸小于边缘/最小限制时抛出。<br>
 * Thrown when the target size is smaller than the edge or minimum size constraints.
 * </p>
 */
public class TextureStretchLimitException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TextureStretchLimitException(String message) {
        super(message);
    }

    public TextureStretchLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
