package decok.dfcdvadstf.catframe.exception;

/**
 * <p>
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
