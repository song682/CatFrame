package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.components.events.ContainerEventHandler;

import javax.annotation.Nullable;

/**
 * <p>
 * 容器嵌套选择列表 —— 每个条目本身也是一个容器，可以包含子组件（按钮、滑块等）。<br>
 * 对标高版本 Minecraft 的 {@code ContainerObjectSelectionList}。
 * </p>
 * <p>
 * Container object selection list — each entry is itself a container that can
 * hold child components (buttons, sliders, etc.).<br>
 * Counterpart of the high-version Minecraft {@code ContainerObjectSelectionList}.
 * </p>
 *
 * @param <E> the entry type / 条目类型
 */
public abstract class ContainerObjectSelectionList<E extends ContainerObjectSelectionList.Entry<E>> extends AbstractSelectionList<E> {

    public ContainerObjectSelectionList(int width, int height, int y, int itemHeight) {
        super(width, height, y, itemHeight);
    }

    @Override
    protected boolean entriesCanBeSelected() {
        return false;
    }

    @Override
    public void setFocused(@Nullable Component focused) {
        if (getFocused() != focused) {
            super.setFocused(focused);
            if (focused == null) {
                setSelected(null);
            }
        }
    }

    /**
     * <p>
     * 容器条目 —— 同时是 ContainerEventHandler，可以包含和管理子组件。<br>
     * Container entry — also a ContainerEventHandler that can contain and manage
     * child components.
     * </p>
     *
     * @param <E> the entry type / 条目类型
     */
    public abstract static class Entry<E extends Entry<E>> extends AbstractSelectionList.Entry<E>
            implements ContainerEventHandler {

        @Nullable
        private Component focused;
        private boolean dragging;

        // ──── ContainerEventHandler: dragging ────

        @Override
        public boolean isDragging() {
            return dragging;
        }

        @Override
        public void setDragging(boolean dragging) {
            this.dragging = dragging;
        }

        // ──── ContainerEventHandler: focus ────

        @Nullable
        @Override
        public Component getFocused() {
            return focused;
        }

        @Override
        public void setFocused(@Nullable Component focused) {
            if (this.focused != null) {
                this.focused.setActive(true);
            }
            if (focused != null) {
                focused.setActive(true);
            }
            this.focused = focused;
        }

        // ──── Event handling — delegate to ContainerEventHandler dispatch ────

        @Override
        public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
            dispatchMouseClicked(mouseX, mouseY, mouseButton);
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
            dispatchMouseReleased(mouseX, mouseY, mouseButton);
        }

        @Override
        public void mouseDrag(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
            dispatchMouseDragged(mouseX, mouseY, mouseButton, timeSinceLastClick);
        }

        @Override
        public void keyTyped(char typedChar, int keyCode) {
            dispatchKeyTyped(typedChar, keyCode);
        }
    }
}
