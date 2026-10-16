package decok.dfcdvadstf.catframe.ui.components.events;

import decok.dfcdvadstf.catframe.ui.components.TabOrderedElement;
import decok.dfcdvadstf.catframe.ui.navigation.FocusNavigationEvent;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenDirection;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * <p>
 * Container event handler — manages focus and event dispatch for child components.<br>
 * Counterpart of the high-version Minecraft {@code ContainerEventHandler} interface,
 * adapted to CatFrame's event signatures.
 * </p>
 */
public interface ContainerEventHandler {

    /**
     * Returns the list of child components in this container.
     */
    List<? extends GuiEventListener> children();

    /**
     * Find the child component at the given mouse position.
     */
    default Optional<GuiEventListener> getChildAt(int x, int y) {
        for (GuiEventListener child : children()) {
            if (child.isMouseOver(x, y)) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    /**
     * Handle mouse click — finds the child under the cursor and forwards the event.
     * Automatically sets focus to the clicked child.
     */
    default void dispatchMouseClicked(int mouseX, int mouseY, int mouseButton) {
        Optional<GuiEventListener> child = getChildAt(mouseX, mouseY);
        if (!child.isPresent()) {
            return;
        }
        GuiEventListener widget = child.get();
        widget.mouseClicked(mouseX, mouseY, mouseButton);
        setFocused(widget);
        if (mouseButton == 0) {
            setDragging(true);
        }
    }

    /**
     * Handle mouse release — forwards to the focused child.
     */
    default void dispatchMouseReleased(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && isDragging()) {
            setDragging(false);
            GuiEventListener focused = getFocused();
            if (focused != null) {
                focused.mouseReleased(mouseX, mouseY, mouseButton);
            }
        }
    }

    /**
     * Handle mouse drag — forwards to the focused child.
     */
    default void dispatchMouseDragged(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
        GuiEventListener focused = getFocused();
        if (focused != null && isDragging() && mouseButton == 0) {
            focused.mouseDrag(mouseX, mouseY, mouseButton, timeSinceLastClick);
        }
    }

    /**
     * Handle mouse scroll — forwards to the child under the cursor.
     */
    default void dispatchMouseScrolled(int mouseX, int mouseY, int delta) {
        Optional<GuiEventListener> child = getChildAt(mouseX, mouseY);
        if (child.isPresent()) {
            child.get().mouseScrolled(delta);
        }
    }

    /**
     * Handle key typed — forwards to the focused child.
     */
    default void dispatchKeyTyped(char typedChar, int keyCode) {
        GuiEventListener focused = getFocused();
        if (focused != null) {
            focused.keyTyped(typedChar, keyCode);
        }
    }

    /**
     * Handle split key-pressed — forwards to the focused child.
     */
    default boolean dispatchKeyPressed(int keyCode) {
        GuiEventListener focused = getFocused();
        return focused != null && focused.keyPressed(keyCode);
    }

    /**
     * Handle split key-released — forwards to the focused child.
     */
    default boolean dispatchKeyReleased(int keyCode) {
        GuiEventListener focused = getFocused();
        return focused != null && focused.keyReleased(keyCode);
    }

    /**
     * Handle split char-typed — forwards to the focused child.
     */
    default boolean dispatchCharTyped(char codePoint) {
        GuiEventListener focused = getFocused();
        return focused != null && focused.charTyped(codePoint);
    }

    // ──── Focus navigation (Tab / Arrow) ────

    /**
     * Resolve the next focus target inside this container for a navigation event.
     * <p>
     * First lets the currently focused child consume the navigation internally
     * (nested containers); if the child hands back {@code null}, continues the
     * search at this level by tab order or directional geometry.
     * </p>
     *
     * @return a path rooted at this container, or {@code null} if none
     */
    @Nullable
    default ComponentPath nextFocusPathInContainer(FocusNavigationEvent event) {
        GuiEventListener current = getFocused();
        if (current != null) {
            ComponentPath childPath = current.nextFocusPath(event);
            if (childPath != null) {
                return ComponentPath.path(this, childPath);
            }
        }
        if (event instanceof FocusNavigationEvent.TabNavigation) {
            return tabNavigate(event, current, ((FocusNavigationEvent.TabNavigation) event).forward());
        }
        if (event instanceof FocusNavigationEvent.ArrowNavigation) {
            return arrowNavigate((FocusNavigationEvent.ArrowNavigation) event, current);
        }
        return null;
    }

    /**
     * Tab / Shift+Tab traversal: step through children (ordered by tab group) from the
     * current focus, returning the first child that accepts focus.
     */
    @Nullable
    default ComponentPath tabNavigate(FocusNavigationEvent event, @Nullable GuiEventListener current, boolean forward) {
        List<GuiEventListener> ordered = new ArrayList<GuiEventListener>(children());
        ordered.sort(Comparator.comparingInt(ContainerEventHandler::tabOrderOf));
        int size = ordered.size();
        if (size == 0) {
            return null;
        }
        int start;
        if (current != null) {
            int idx = ordered.indexOf(current);
            start = (idx < 0) ? (forward ? 0 : size - 1) : idx + (forward ? 1 : -1);
        } else {
            start = forward ? 0 : size - 1;
        }
        if (forward) {
            for (int i = start; i < size; i++) {
                ComponentPath p = ordered.get(i).nextFocusPath(event);
                if (p != null) {
                    return ComponentPath.path(this, p);
                }
            }
        } else {
            for (int i = start; i >= 0; i--) {
                ComponentPath p = ordered.get(i).nextFocusPath(event);
                if (p != null) {
                    return ComponentPath.path(this, p);
                }
            }
        }
        return null;
    }

    /**
     * Arrow-key traversal: pick the focusable child whose centre is nearest in the
     * requested direction. Basic geometric heuristic (centre distance).
     */
    @Nullable
    default ComponentPath arrowNavigate(FocusNavigationEvent.ArrowNavigation event, @Nullable GuiEventListener current) {
        ScreenDirection dir = event.direction();
        ScreenRectangle from = current != null ? current.getRectangle() : null;
        GuiEventListener best = null;
        long bestScore = Long.MAX_VALUE;
        for (GuiEventListener child : children()) {
            if (child == current) {
                continue;
            }
            if (child.nextFocusPath(event) == null) {
                continue;
            }
            ScreenRectangle to = child.getRectangle();
            if (from != null && !isInDirection(from, to, dir)) {
                continue;
            }
            long score = directionScore(from, to, dir);
            if (score < bestScore) {
                bestScore = score;
                best = child;
            }
        }
        if (best != null) {
            ComponentPath p = best.nextFocusPath(event);
            if (p != null) {
                return ComponentPath.path(this, p);
            }
        }
        return null;
    }

    /**
     * @return the tab-order group of a component (0 if it is not a {@link TabOrderedElement})
     */
    static int tabOrderOf(GuiEventListener c) {
        return (c instanceof TabOrderedElement) ? ((TabOrderedElement) c).getTabOrderGroup() : 0;
    }

    static boolean isInDirection(ScreenRectangle from, ScreenRectangle to, ScreenDirection dir) {
        int fromCentre = centre(from, dir.getAxis());
        int toCentre = centre(to, dir.getAxis());
        return dir.isBefore(fromCentre, toCentre);
    }

    static long directionScore(@Nullable ScreenRectangle from, ScreenRectangle to, ScreenDirection dir) {
        if (from == null) {
            return 0L;
        }
        int along = Math.abs(centre(to, dir.getAxis()) - centre(from, dir.getAxis()));
        int perp = Math.abs(centre(to, dir.getAxis().orthogonal()) - centre(from, dir.getAxis().orthogonal()));
        // Bias toward staying aligned on the orthogonal axis.
        return (long) perp * 4L + (long) along;
    }

    static int centre(ScreenRectangle rect, decok.dfcdvadstf.catframe.ui.navigation.ScreenAxis axis) {
        return axis == decok.dfcdvadstf.catframe.ui.navigation.ScreenAxis.HORIZONTAL
                ? rect.x + rect.width / 2
                : rect.y + rect.height / 2;
    }

    // ──── Focus management ────

    /**
     * @return the currently focused child, or null
     */
    @Nullable
    GuiEventListener getFocused();

    /**
     * Set the focused child component.
     */
    void setFocused(@Nullable GuiEventListener focused);

    /**
     * @return whether this container has focus
     */
    default boolean isFocused() {
        return getFocused() != null;
    }

    /**
     * Called when the container itself gains or loses focus.
     */
    default void setFocused(boolean focused) {
        if (!focused) {
            setFocused(null);
        }
    }

    // ──── Dragging state ────

    /**
     * @return whether a drag is in progress
     */
    boolean isDragging();

    /**
     * Set the dragging state.
     */
    void setDragging(boolean dragging);
}
