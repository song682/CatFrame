package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.components.events.ContainerEventHandler;
import decok.dfcdvadstf.catframe.ui.components.events.GuiEventListener;

import javax.annotation.Nullable;

/**
 * <p>
 * Container object selection list — each entry is itself a container that can
 * hold child components (buttons, sliders, etc.).<br>
 * Counterpart of the high-version Minecraft {@code ContainerObjectSelectionList}.
 * </p>
 *
 * @param <E> the entry type
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
    public void setFocused(@Nullable GuiEventListener focused) {
        if (getFocused() != focused) {
            super.setFocused(focused);
            if (focused == null) {
                setSelected(null);
            }
        }
    }

    /**
     * <p>
     * Container entry — also a ContainerEventHandler that can contain and manage
     * child components.
     * </p>
     *
     * @param <E> the entry type
     */
    public abstract static class Entry<E extends Entry<E>> extends AbstractSelectionList.Entry<E>
            implements ContainerEventHandler {

        @Nullable
        private GuiEventListener focused;
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
        public GuiEventListener getFocused() {
            return focused;
        }

        @Override
        public void setFocused(@Nullable GuiEventListener focused) {
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
