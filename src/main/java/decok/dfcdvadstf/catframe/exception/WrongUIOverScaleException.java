package decok.dfcdvadstf.catframe.exception;

/**
 * <p>
 * Thrown when a GUI item model's geometry exceeds the 16x16 inventory slot but
 * the model does not opt into oversized rendering ({@code oversized_in_gui=false}).
 * </p>
 * <p>
 * The catching side uses {@link #getMaxExtent()} / {@link #getSlot()} to compute a
 * clamp factor and shrink the model back into the slot bounds.
 * </p>
 */
public class WrongUIOverScaleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The largest axis extent of the model, in block space ([0,1] == one slot). */
    private final float maxExtent;

    /** The slot reference extent (normally {@code 1.0}). */
    private final float slot;

    public WrongUIOverScaleException(float maxExtent, float slot) {
        super("GUI item model extent " + maxExtent + " exceeds slot " + slot
                + " but oversized_in_gui=false");
        this.maxExtent = maxExtent;
        this.slot = slot;
    }

    public WrongUIOverScaleException(String message) {
        super(message);
        this.maxExtent = 0.0f;
        this.slot = 1.0f;
    }

    public WrongUIOverScaleException(String message, Throwable cause) {
        super(message, cause);
        this.maxExtent = 0.0f;
        this.slot = 1.0f;
    }

    /** The largest axis extent of the model, in block space. */
    public float getMaxExtent() {
        return maxExtent;
    }

    /** The slot reference extent. */
    public float getSlot() {
        return slot;
    }
}
