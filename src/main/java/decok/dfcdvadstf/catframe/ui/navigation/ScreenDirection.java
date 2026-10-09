package decok.dfcdvadstf.catframe.ui.navigation;

/**
 * <p>
 * Screen direction — the four cardinal directions (up/down/left/right) used by
 * arrow-key focus navigation. Counterpart of the high-version {@code ScreenDirection}.
 * </p>
 */
public enum ScreenDirection {
    UP(ScreenAxis.VERTICAL, -1),
    DOWN(ScreenAxis.VERTICAL, 1),
    LEFT(ScreenAxis.HORIZONTAL, -1),
    RIGHT(ScreenAxis.HORIZONTAL, 1);

    private final ScreenAxis axis;
    private final int sign;

    ScreenDirection(final ScreenAxis axis, final int sign) {
        this.axis = axis;
        this.sign = sign;
    }

    /**
     * @return the axis this direction travels along
     */
    public ScreenAxis getAxis() {
        return this.axis;
    }

    /**
     * @return true if this direction increases the coordinate (DOWN / RIGHT)
     */
    public boolean isPositive() {
        return this.sign > 0;
    }

    /**
     * @return the opposite direction
     */
    public ScreenDirection getOpposite() {
        switch (this) {
            case UP:
                return DOWN;
            case DOWN:
                return UP;
            case LEFT:
                return RIGHT;
            default:
                return LEFT;
        }
    }

    /**
     * Whether coordinate {@code a} comes before {@code b} when travelling in this direction.
     */
    public boolean isBefore(final int a, final int b) {
        return isPositive() ? a < b : a > b;
    }
}
