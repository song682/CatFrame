package decok.dfcdvadstf.catframe.ui.overlay;

/**
 * <p>
 * Screen anchor enum — defines the anchor point for Overlay positioning on screen.
 * </p>
 */
public enum ScreenAnchor {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    CENTER_LEFT,
    CENTER,
    CENTER_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT;

    /**
     * Resolve the X coordinate for an element at this anchor.
     *
     * @param screenWidth  screen width
     * @param elementWidth element width
     * @param offset       pixel offset from anchor
     * @return the X coordinate
     */
    public int resolveX(int screenWidth, int elementWidth, int offset) {
        switch (this) {
            case TOP_LEFT:
            case CENTER_LEFT:
            case BOTTOM_LEFT:
                return offset;
            case TOP_CENTER:
            case CENTER:
            case BOTTOM_CENTER:
                return (screenWidth - elementWidth) / 2 + offset;
            case TOP_RIGHT:
            case CENTER_RIGHT:
            case BOTTOM_RIGHT:
                return screenWidth - elementWidth - offset;
            default:
                return offset;
        }
    }

    /**
     * Resolve the Y coordinate for an element at this anchor.
     *
     * @param screenHeight screen height
     * @param elementHeight element height
     * @param offset       pixel offset from anchor
     * @return the Y coordinate
     */
    public int resolveY(int screenHeight, int elementHeight, int offset) {
        switch (this) {
            case TOP_LEFT:
            case TOP_CENTER:
            case TOP_RIGHT:
                return offset;
            case CENTER_LEFT:
            case CENTER:
            case CENTER_RIGHT:
                return (screenHeight - elementHeight) / 2 + offset;
            case BOTTOM_LEFT:
            case BOTTOM_CENTER:
            case BOTTOM_RIGHT:
                return screenHeight - elementHeight - offset;
            default:
                return offset;
        }
    }

    /**
     * Whether stacking at this anchor goes downward (true) or upward (false).
     */
    public boolean stacksDownward() {
        switch (this) {
            case BOTTOM_LEFT:
            case BOTTOM_CENTER:
            case BOTTOM_RIGHT:
                return false;
            default:
                return true;
        }
    }
}
