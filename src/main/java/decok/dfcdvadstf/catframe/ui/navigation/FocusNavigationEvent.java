package decok.dfcdvadstf.catframe.ui.navigation;

/**
 * <p>
 * 焦点导航事件 —— 描述一次焦点移动请求（Tab 循环 或 方向键移动）。<br>
 * 对标高版本 Minecraft 的 {@code FocusNavigationEvent}，供 {@code nextFocusPath} 消费。
 * </p>
 * <p>
 * Focus navigation event — describes a focus-movement request (Tab cycling or arrow-key
 * movement). Counterpart of the high-version Minecraft {@code FocusNavigationEvent},
 * consumed by {@code nextFocusPath}.
 * </p>
 */
public abstract class FocusNavigationEvent {

    private FocusNavigationEvent() {
    }

    /**
     * @return the axis this navigation travels along / 此次导航所沿的坐标轴
     */
    public abstract ScreenAxis getNavigationAxis();

    /**
     * <p>Tab / Shift+Tab 顺序导航事件。</p>
     * <p>Tab / Shift+Tab sequential navigation event.</p>
     */
    public static final class TabNavigation extends FocusNavigationEvent {

        private final boolean forward;

        public TabNavigation(final boolean forward) {
            this.forward = forward;
        }

        /**
         * @return true for Tab (forward), false for Shift+Tab (backward)
         *         / Tab（前进）为 true，Shift+Tab（后退）为 false
         */
        public boolean forward() {
            return this.forward;
        }

        @Override
        public ScreenAxis getNavigationAxis() {
            return ScreenAxis.VERTICAL;
        }
    }

    /**
     * <p>方向键导航事件（上/下/左/右）。</p>
     * <p>Arrow-key navigation event (up/down/left/right).</p>
     */
    public static final class ArrowNavigation extends FocusNavigationEvent {

        private final ScreenDirection direction;

        public ArrowNavigation(final ScreenDirection direction) {
            this.direction = direction;
        }

        /**
         * @return the direction of movement / 移动方向
         */
        public ScreenDirection direction() {
            return this.direction;
        }

        @Override
        public ScreenAxis getNavigationAxis() {
            return this.direction.getAxis();
        }
    }
}
