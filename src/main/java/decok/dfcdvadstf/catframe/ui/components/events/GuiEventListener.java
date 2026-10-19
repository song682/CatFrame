package decok.dfcdvadstf.catframe.ui.components.events;

import decok.dfcdvadstf.catframe.ui.components.TabOrderedElement;
import decok.dfcdvadstf.catframe.ui.layouts.ILayout;
import decok.dfcdvadstf.catframe.ui.navigation.FocusNavigationEvent;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;

import javax.annotation.Nullable;

/**
 * <p>
 * Base component interface — the fundamental abstraction for all UI
 * widgets.<br>
 * Merges the high-version Minecraft equivalents of {@code LayoutElement} and
 * {@code GuiEventListener}.
 * </p>
 */
public interface GuiEventListener extends TabOrderedElement {

    // ──── Position / Size ────

    int getX();

    void setX(int x);

    int getY();

    void setY(int y);

    int getWidth();

    int getHeight();

    /**
     * Set both width and height in one call.
     */
    default void setSize(int width, int height) {
        // subclasses may override for optimisation
    }

    // ──── Visibility / Active ────

    boolean isVisible();

    void setVisible(boolean visible);

    boolean isActive();

    void setActive(boolean active);

    // ──── Event handling ────

    /**
     * Handle mouse click.
     *
     * @param mouseX      mouse X coordinate
     * @param mouseY      mouse Y coordinate
     * @param mouseButton mouse button index
     */
    default void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    }

    /**
     * Handle key press (combined event, kept for legacy compatibility —
     * carries both the typed character and the key code).
     *
     * @param typedChar typed character
     * @param keyCode   key code
     */
    default void keyTyped(char typedChar, int keyCode) {
    }

    /**
     * Handle a key being pressed (or repeated). Split counterpart of the
     * high-version
     * {@code GuiEventListener.keyPressed}.
     * <p>
     * Note: LWJGL2 has no GLFW scancode/modifiers; query {@code KeyTypedEvent}
     * for modifier keys.
     * </p>
     *
     * @param keyCode LWJGL key code
     * @return true if the event was consumed
     */
    default boolean keyPressed(int keyCode) {
        return false;
    }

    /**
     * Handle a key being released. Split counterpart of the high-version
     * {@code GuiEventListener.keyReleased}. Only reachable via the LWJGL2 keyboard
     * event queue (vanilla {@code keyTyped} never fires on release).
     *
     * @param keyCode LWJGL key code
     * @return true if the event was consumed
     */
    default boolean keyReleased(int keyCode) {
        return false;
    }

    /**
     * Handle a translated character being typed. Split counterpart of the
     * high-version
     * {@code GuiEventListener.charTyped}.
     *
     * @param codePoint typed character
     * @return true if the event was consumed
     */
    default boolean charTyped(char codePoint) {
        return false;
    }

    /**
     * Handle mouse scroll.
     *
     * @param delta scroll delta
     */
    default void mouseScrolled(int delta) {
    }

    /**
     * Handle mouse drag.
     */
    default void mouseDrag(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
    }

    /**
     * Handle mouse release.
     */
    default void mouseReleased(int mouseX, int mouseY, int mouseButton) {
    }

    // ──── Focus ────

    /**
     * @return whether this component currently holds keyboard focus
     */
    default boolean isFocused() {
        return false;
    }

    /**
     * Set this component's focus state.
     */
    default void setFocused(boolean focused) {
    }

    /**
     * Resolve the next focus target for the given navigation event.
     * <p>
     * Default leaf implementation: returns itself when it is active, visible
     * and not focused, otherwise {@code null} to indicate that focus should
     * leave this component. Containers should override this method to
     * traverse their children.
     * </p>
     *
     * @param event the navigation request
     * @return the resolved path, or {@code null} if focus should leave this
     *         component
     */
    @Nullable
    default ComponentPath nextFocusPath(FocusNavigationEvent event) {
        if (!isActive() || !isVisible()) {
            return null;
        }
        return isFocused() ? null : ComponentPath.leaf(this);
    }

    /**
     * Check whether the given point is inside this component.
     */
    default boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= getX() && mouseX < getX() + getWidth()
                && mouseY >= getY() && mouseY < getY() + getHeight();
    }

    default ScreenRectangle getRectangle() {
        return ScreenRectangle.empty();
    }

    default String getTheme(){
        return null;
    }
}
