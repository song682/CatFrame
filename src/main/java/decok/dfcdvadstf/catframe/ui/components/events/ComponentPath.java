package decok.dfcdvadstf.catframe.ui.components.events;

import decok.dfcdvadstf.catframe.ui.components.Component;

/**
 * <p>
 * 焦点导航路径 —— 描述从根容器到当前焦点叶子组件的一条链路。<br>
 * 对标高版本 Minecraft 的 {@code ComponentPath}，用于 {@code nextFocusPath} 的焦点应用。
 * </p>
 * <p>
 * Focus navigation path — describes a chain from a root container down to the focused
 * leaf component. Counterpart of the high-version Minecraft {@code ComponentPath}, used
 * to apply focus after {@code nextFocusPath} resolves a target.
 * </p>
 */
public interface ComponentPath {

    /**
     * @return the leaf (focused) component at the end of this path / 路径末端的焦点叶子组件
     */
    Component component();

    /**
     * Apply (or clear) focus along the whole path.
     * <p>沿整条路径应用（或清除）焦点。</p>
     *
     * @param focused true to focus, false to unfocus / true 聚焦，false 取消聚焦
     */
    void applyFocus(boolean focused);

    /**
     * Convenience: {@code applyFocus(true)}.
     * <p>便捷方法：等价于 {@code applyFocus(true)}。</p>
     */
    default void applyFocus() {
        applyFocus(true);
    }

    /**
     * Create a leaf path pointing at a single component.
     * <p>创建指向单个组件的叶子路径。</p>
     */
    static ComponentPath leaf(final Component component) {
        return new Leaf(component);
    }

    /**
     * Create a path node linking a parent container to a child path.
     * <p>创建连接父容器与子路径的路径节点。</p>
     */
    static ComponentPath path(final ContainerEventHandler parent, final ComponentPath childPath) {
        return new Path(parent, childPath);
    }

    // ──── Implementations ────

    /**
     * <p>叶子节点 —— 直接聚焦一个组件。</p>
     * <p>Leaf node — focuses a single component directly.</p>
     */
    final class Leaf implements ComponentPath {

        private final Component component;

        Leaf(final Component component) {
            this.component = component;
        }

        @Override
        public Component component() {
            return this.component;
        }

        @Override
        public void applyFocus(final boolean focused) {
            this.component.setFocused(focused);
        }
    }

    /**
     * <p>路径节点 —— 将父容器的焦点子组件与更深一层的路径相连。</p>
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
        public Component component() {
            return this.childPath.component();
        }

        @Override
        public void applyFocus(final boolean focused) {
            if (focused) {
                this.childPath.applyFocus(true);
                this.parent.setFocused(this.childPath.component());
            } else {
                this.childPath.applyFocus(false);
                this.parent.setFocused((Component) null);
            }
        }
    }
}
