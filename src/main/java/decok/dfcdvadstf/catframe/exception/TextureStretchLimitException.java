package decok.dfcdvadstf.catframe.exception;

/**
 * <p>
 * 纹理拉伸尺寸限制异常 —— 当目标尺寸小于边缘 + 内部宽度/高度之和抛出。<br>
 * Thrown when the target size is smaller than the edge plus the inner width or height.
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
