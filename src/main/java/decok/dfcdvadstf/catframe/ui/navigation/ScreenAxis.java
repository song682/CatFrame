package decok.dfcdvadstf.catframe.ui.navigation;

/**
 * <p>
 * Screen axis — describes the axis along which navigation occurs (horizontal/vertical).<br>
 * Counterpart of the high-version Minecraft {@code ScreenAxis}, used for focus navigation.
 * </p>
 */
public enum ScreenAxis {
    HORIZONTAL,
    VERTICAL;

    /**
     * @return the perpendicular axis
     */
    public ScreenAxis orthogonal() {
        return this == HORIZONTAL ? VERTICAL : HORIZONTAL;
    }
}
