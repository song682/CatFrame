package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.components.events.ContainerEventHandler;

import javax.annotation.Nullable;

/**
 * <p>
 * 容器控件 —— 组合滚动能力与焦点管理/事件分发。<br>
 * 对标高版本 Minecraft 的 {@code AbstractContainerWidget}。
 * </p>
 * <p>
 * Container widget — combines scrolling with focus management and event dispatch.<br>
 * Counterpart of the high-version Minecraft {@code AbstractContainerWidget}.
 * </p>
 */
public abstract class AbstractContainerWidget extends AbstractScrollArea implements ContainerEventHandler {

    @Nullable
    private Component focused;
    private boolean containerDragging;

    public AbstractContainerWidget(int x, int y, int width, int height, ScrollbarSettings scrollbarSettings) {
        super(x, y, width, height, scrollbarSettings);
    }

    // ──── ContainerEventHandler: dragging ────

    @Override
    public final boolean isDragging() {
        return containerDragging;
    }

    @Override
    public final void setDragging(boolean dragging) {
        this.containerDragging = dragging;
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
            this.focused.setActive(true); // reset visual focus if needed
        }
        if (focused != null) {
            focused.setActive(true);
        }
        this.focused = focused;
    }

    @Override
    public boolean isFocused() {
        return ContainerEventHandler.super.isFocused();
    }

    // ──── Event handling ────
    // Component uses void return for event methods.
    // We delegate to ContainerEventHandler's dispatch* methods internally.

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        // First check if we're starting to drag the scrollbar
        updateScrolling(mouseX, mouseY, mouseButton);
        // Then dispatch to children via ContainerEventHandler
        dispatchMouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
        super.mouseReleased(mouseX, mouseY, mouseButton);
        dispatchMouseReleased(mouseX, mouseY, mouseButton);
    }

    @Override
    public void mouseDrag(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
        super.mouseDrag(mouseX, mouseY, mouseButton, timeSinceLastClick);
        dispatchMouseDragged(mouseX, mouseY, mouseButton, timeSinceLastClick);
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        dispatchKeyTyped(typedChar, keyCode);
    }
}
