package decok.dfcdvadstf.catframe.ui.navigation;

/**
 * <p>
 * 屏幕方向 —— 四向（上/下/左/右）方向枚举，用于方向键焦点导航。<br>
 * 对标高版本 Minecraft 的 {@code ScreenDirection}。
 * </p>
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
     * @return the axis this direction travels along / 此方向所处的坐标轴
     */
    public ScreenAxis getAxis() {
        return this.axis;
    }

    /**
     * @return true if this direction increases the coordinate (DOWN / RIGHT)
     *         / 若此方向使坐标增大（下/右）则返回 true
     */
    public boolean isPositive() {
        return this.sign > 0;
    }

    /**
     * @return the opposite direction / 相反方向
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
     * <p>沿此方向前进时，坐标 {@code a} 是否在 {@code b} 之前。</p>
     */
    public boolean isBefore(final int a, final int b) {
        return isPositive() ? a < b : a > b;
    }
}
