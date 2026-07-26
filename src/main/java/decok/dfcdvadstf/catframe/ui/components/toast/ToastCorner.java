package decok.dfcdvadstf.catframe.ui.components.toast;

/**
 * <p>
 * Toast 滑出角落枚举 —— 定义 Toast 从屏幕哪个角落滑入/滑出。<br>
 * 默认为 {@link #TOP_RIGHT}（对齐原版 Toast 行为）。
 * </p>
 * <p>
 * Toast slide-in corner enum — defines which screen corner a Toast slides in from
 * and out to. Defaults to {@link #TOP_RIGHT} (matching vanilla Toast behaviour).
 * </p>
 *
 * <h3>堆叠方向 / Stacking direction</h3>
 * <p>
 * TOP 系列角落的槽位自上而下排列，BOTTOM 系列自下而上；每个角落拥有独立的槽位池，
 * 互不占用。
 * </p>
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
     * <p>此角落是否位于屏幕右缘（从右侧滑入）。</p>
     */
    public boolean isRight() {
        return this == TOP_RIGHT || this == BOTTOM_RIGHT;
    }

    /**
     * Whether this corner sits on the bottom screen edge (slots stack upward).
     * <p>此角落是否位于屏幕下缘（槽位自下而上堆叠）。</p>
     */
    public boolean isBottom() {
        return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
    }
}
