package decok.dfcdvadstf.catframe.ui.navigation;

/**
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
     * @return the axis this navigation travels along
     */
    public abstract ScreenAxis getNavigationAxis();

/**
     * Tab / Shift+Tab sequential navigation event.
     */
    public static final class TabNavigation extends FocusNavigationEvent {

        private final boolean forward;

        public TabNavigation(final boolean forward) {
            this.forward = forward;
        }

        /**
         * @return true for Tab (forward), false for Shift+Tab (backward)
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
     * Arrow-key navigation event (up/down/left/right).
     */
    public static final class ArrowNavigation extends FocusNavigationEvent {

        private final ScreenDirection direction;

        public ArrowNavigation(final ScreenDirection direction) {
            this.direction = direction;
        }

        /**
         * @return the direction of movement
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
