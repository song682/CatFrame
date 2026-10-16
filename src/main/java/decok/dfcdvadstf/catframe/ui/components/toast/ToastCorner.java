package decok.dfcdvadstf.catframe.ui.components.toast;

/**
 * <p>
 * Toast slide-in corner enum — defines which screen corner a Toast slides in from
 * and out to. Defaults to {@link #TOP_RIGHT} (matching vanilla Toast behaviour).
 * </p>
 *
 * <h3>Stacking direction</h3>
 * <p>
 * TOP corners stack their slots downward, BOTTOM corners upward; each corner owns an
 * independent slot pool that never collides with the others.
 * </p>
 */
public enum ToastCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT;

    /**
     * Whether this corner sits on the right screen edge (slides in from the right).
     */
    public boolean isRight() {
        return this == TOP_RIGHT || this == BOTTOM_RIGHT;
    }

    /**
     * Whether this corner sits on the bottom screen edge (slots stack upward).
     */
    public boolean isBottom() {
        return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
    }
}
