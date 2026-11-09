package decok.dfcdvadstf.catframe.ui.components.events;

import decok.dfcdvadstf.catframe.ui.components.AbstractComponent;
import decok.dfcdvadstf.catframe.ui.navigation.FocusNavigationEvent;

import javax.annotation.Nullable;

/**
 * <p>
 * Abstract base for container event handling — provides focus fields, dragging state and
 * event dispatch for non-scrolling containers. Counterpart of the high-version Minecraft
 * {@code AbstractContainerEventHandler}.
 * </p>
 * <p>
 * Note: for scrolling containers use {@link decok.dfcdvadstf.catframe.ui.components.AbstractContainerWidget},
 * which extends {@code AbstractScrollArea} and therefore cannot extend this class, but still
 * implements {@link ContainerEventHandler}.
 * </p>
 */
public abstract class AbstractContainerEventHandler extends AbstractComponent implements ContainerEventHandler {

    @Nullable
    private GuiEventListener focusedChild;
    private boolean dragging;

    public AbstractContainerEventHandler() {
    }

    public AbstractContainerEventHandler(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    // ──── Dragging ────

    @Override
    public final boolean isDragging() {
        return dragging;
    }

    @Override
    public final void setDragging(boolean dragging) {
        this.dragging = dragging;
    }

    // ──── Focus ────

    @Nullable
    @Override
    public GuiEventListener getFocused() {
        return focusedChild;
    }

    @Override
    public void setFocused(@Nullable GuiEventListener focused) {
        if (this.focusedChild == focused) {
            return;
        }
        if (this.focusedChild != null) {
            this.focusedChild.setFocused(false);
        }
        this.focusedChild = focused;
        if (focused != null) {
            focused.setFocused(true);
        }
    }

    @Override
    public boolean isFocused() {
        return ContainerEventHandler.super.isFocused();
    }

    @Override
    public void setFocused(boolean focused) {
        ContainerEventHandler.super.setFocused(focused);
    }

    @Nullable
    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        return nextFocusPathInContainer(event);
    }

    // ──── Event handling (delegates to ContainerEventHandler dispatch) ────

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
    public void mouseScrolled(int delta) {
        // Non-scrolling container: forward to whichever child is hovered handled by
        // dispatchMouseScrolled requires coordinates; subclasses may override.
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        dispatchKeyTyped(typedChar, keyCode);
    }

    @Override
    public boolean keyPressed(int keyCode) {
        return dispatchKeyPressed(keyCode);
    }

    @Override
    public boolean keyReleased(int keyCode) {
        return dispatchKeyReleased(keyCode);
    }

    @Override
    public boolean charTyped(char codePoint) {
        return dispatchCharTyped(codePoint);
    }
}
