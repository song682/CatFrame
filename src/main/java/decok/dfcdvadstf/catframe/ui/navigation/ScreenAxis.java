package decok.dfcdvadstf.catframe.ui.navigation;

/**
 * <p>
 * 屏幕坐标轴 —— 描述导航发生的方向轴（水平/垂直）。<br>
 * 对标高版本 Minecraft 的 {@code ScreenAxis}，用于焦点导航方向判定。
 * </p>
 * <p>
 * Screen axis — describes the axis along which navigation occurs (horizontal/vertical).<br>
 * Counterpart of the high-version Minecraft {@code ScreenAxis}, used for focus navigation.
 * </p>
 */
public enum ScreenAxis {
    HORIZONTAL,
    VERTICAL;

    /**
     * @return the perpendicular axis / 正交（垂直方向）的坐标轴
     */
    public ScreenAxis orthogonal() {
        return this == HORIZONTAL ? VERTICAL : HORIZONTAL;
    }
}
