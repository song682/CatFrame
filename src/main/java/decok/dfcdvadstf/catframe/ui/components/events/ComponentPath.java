package decok.dfcdvadstf.catframe.ui.components.events;

/**
 * <p>
 * Focus navigation path — describes a chain from a root container down to the focused
 * leaf component. Counterpart of the high-version Minecraft {@code ComponentPath}, used
 * to apply focus after {@code nextFocusPath} resolves a target.
 * </p>
 */
public interface ComponentPath {

    /**
     * @return the leaf (focused) component at the end of this path
     */
    GuiEventListener component();

    /**
     * Apply (or clear) focus along the whole path.
     *
     * @param focused true to focus, false to unfocus
     */
    void applyFocus(boolean focused);

    /**
     * Convenience: {@code applyFocus(true)}.
     */
    default void applyFocus() {
        applyFocus(true);
    }

    /**
     * Create a leaf path pointing at a single component.
     */
    static ComponentPath leaf(final GuiEventListener guiScreenEvent) {
        return new Leaf(guiScreenEvent);
    }

    /**
     * Create a path node linking a parent container to a child path.
     */
    static ComponentPath path(final ContainerEventHandler parent, final ComponentPath childPath) {
        return new Path(parent, childPath);
    }

    // ──── Implementations ────

    /**
     * <p>Leaf node — focuses a single component directly.</p>
     */
    final class Leaf implements ComponentPath {

        private final GuiEventListener guiScreenEvent;

        Leaf(final GuiEventListener guiScreenEvent) {
            this.guiScreenEvent = guiScreenEvent;
        }

        @Override
        public GuiEventListener component() {
            return this.guiScreenEvent;
        }

        @Override
        public void applyFocus(final boolean focused) {
            this.guiScreenEvent.setFocused(focused);
        }
    }

    /**
     * <p>Path node — links a parent container's focused child to a deeper path.</p>
     */
    final class Path implements ComponentPath {

        private final ContainerEventHandler parent;
        private final ComponentPath childPath;

        Path(final ContainerEventHandler parent, final ComponentPath childPath) {
            this.parent = parent;
            this.childPath = childPath;
        }

        @Override
        public GuiEventListener component() {
            return this.childPath.component();
        }

        @Override
        public void applyFocus(final boolean focused) {
            if (focused) {
                this.childPath.applyFocus(true);
                this.parent.setFocused(this.childPath.component());
            } else {
                this.childPath.applyFocus(false);
                this.parent.setFocused((GuiEventListener) null);
            }
        }
    }
}
