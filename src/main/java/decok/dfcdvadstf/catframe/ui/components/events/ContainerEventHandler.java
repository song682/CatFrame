package decok.dfcdvadstf.catframe.ui.components.events;

import decok.dfcdvadstf.catframe.ui.components.Component;
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
 * 容器事件处理器 —— 管理子组件的焦点和事件分发。<br>
 * 对标高版本 Minecraft 的 {@code ContainerEventHandler} 接口，适配 CatFrame 的事件签名。
 * </p>
 * <p>
 * Container event handler — manages focus and event dispatch for child components.<br>
 * Counterpart of the high-version Minecraft {@code ContainerEventHandler} interface,
 * adapted to CatFrame's event signatures.
 * </p>
 */
public interface ContainerEventHandler {

    /**
     * Returns the list of child components in this container.
     * <p>返回此容器中的子组件列表。</p>
     */
    List<? extends Component> children();

    /**
     * Find the child component at the given mouse position.
     * <p>查找给定鼠标位置下的子组件。</p>
     */
    default Optional<Component> getChildAt(int x, int y) {
        for (Component child : children()) {
            if (child.isMouseOver(x, y)) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    /**
     * Handle mouse click — finds the child under the cursor and forwards the event.
     * Automatically sets focus to the clicked child.
     * <p>处理鼠标点击 —— 找到鼠标下的子组件并转发事件，自动设焦点。</p>
     */
    default void dispatchMouseClicked(int mouseX, int mouseY, int mouseButton) {
        Optional<Component> child = getChildAt(mouseX, mouseY);
        if (!child.isPresent()) {
            return;
        }
        Component widget = child.get();
        widget.mouseClicked(mouseX, mouseY, mouseButton);
        setFocused(widget);
        if (mouseButton == 0) {
            setDragging(true);
        }
    }

    /**
     * Handle mouse release — forwards to the focused child.
     * <p>处理鼠标释放 —— 转发给当前焦点子组件。</p>
     */
    default void dispatchMouseReleased(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && isDragging()) {
            setDragging(false);
            Component focused = getFocused();
            if (focused != null) {
                focused.mouseReleased(mouseX, mouseY, mouseButton);
            }
        }
    }

    /**
     * Handle mouse drag — forwards to the focused child.
     * <p>处理鼠标拖动 —— 转发给当前焦点子组件。</p>
     */
    default void dispatchMouseDragged(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
        Component focused = getFocused();
        if (focused != null && isDragging() && mouseButton == 0) {
            focused.mouseDrag(mouseX, mouseY, mouseButton, timeSinceLastClick);
        }
    }

    /**
     * Handle mouse scroll — forwards to the child under the cursor.
     * <p>处理鼠标滚轮 —— 转发给鼠标下的子组件。</p>
     */
    default void dispatchMouseScrolled(int mouseX, int mouseY, int delta) {
        Optional<Component> child = getChildAt(mouseX, mouseY);
        if (child.isPresent()) {
            child.get().mouseScrolled(delta);
        }
    }

    /**
     * Handle key typed — forwards to the focused child.
     * <p>处理按键 —— 转发给当前焦点子组件。</p>
     */
    default void dispatchKeyTyped(char typedChar, int keyCode) {
        Component focused = getFocused();
        if (focused != null) {
            focused.keyTyped(typedChar, keyCode);
        }
    }

    /**
     * Handle split key-pressed — forwards to the focused child.
     * <p>处理拆分后的按键按下 —— 转发给当前焦点子组件。</p>
     */
    default boolean dispatchKeyPressed(int keyCode) {
        Component focused = getFocused();
        return focused != null && focused.keyPressed(keyCode);
    }

    /**
     * Handle split key-released — forwards to the focused child.
     * <p>处理拆分后的按键松开 —— 转发给当前焦点子组件。</p>
     */
    default boolean dispatchKeyReleased(int keyCode) {
        Component focused = getFocused();
        return focused != null && focused.keyReleased(keyCode);
    }

    /**
     * Handle split char-typed — forwards to the focused child.
     * <p>处理拆分后的字符输入 —— 转发给当前焦点子组件。</p>
     */
    default boolean dispatchCharTyped(char codePoint) {
        Component focused = getFocused();
        return focused != null && focused.charTyped(codePoint);
    }

    // ──── Focus navigation (Tab / Arrow) ────

    /**
     * Resolve the next focus target inside this container for a navigation event.
     * <p>为导航事件在本容器内解析下一个焦点目标。</p>
     * <p>先尝试让当前焦点子组件内部消费导航（嵌套容器）；若子组件交回
     * {@code null}，则在本层按 Tab 顺序或方向几何继续查找。</p>
     *
     * @return a path rooted at this container, or {@code null} if none / 以本容器为根的路径；无则 {@code null}
     */
    @Nullable
    default ComponentPath nextFocusPathInContainer(FocusNavigationEvent event) {
        Component current = getFocused();
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
     * <p>Tab / Shift+Tab 遍历：从当前焦点出发，按 tab 组排序逐个步进，
     * 返回第一个接受焦点的子组件。</p>
     */
    @Nullable
    default ComponentPath tabNavigate(FocusNavigationEvent event, @Nullable Component current, boolean forward) {
        List<Component> ordered = new ArrayList<Component>(children());
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
     * <p>方向键遍历：选中在请求方向上中心最近且可聚焦的子组件。
     * 基础几何启发式（中心距离）。</p>
     */
    @Nullable
    default ComponentPath arrowNavigate(FocusNavigationEvent.ArrowNavigation event, @Nullable Component current) {
        ScreenDirection dir = event.direction();
        ScreenRectangle from = current != null ? current.getRectangle() : null;
        Component best = null;
        long bestScore = Long.MAX_VALUE;
        for (Component child : children()) {
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
     *         / 组件的 tab 顺序组（非 {@link TabOrderedElement} 时为 0）
     */
    static int tabOrderOf(Component c) {
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
     * @return the currently focused child, or null / 当前焦点子组件，或 null
     */
    @Nullable
    Component getFocused();

    /**
     * Set the focused child component.
     * <p>设置焦点子组件。</p>
     */
    void setFocused(@Nullable Component focused);

    /**
     * @return whether this container has focus / 此容器是否有焦点
     */
    default boolean isFocused() {
        return getFocused() != null;
    }

    /**
     * Called when the container itself gains or loses focus.
     * <p>当容器本身获得或失去焦点时调用。</p>
     */
    default void setFocused(boolean focused) {
        if (!focused) {
            setFocused(null);
        }
    }

    // ──── Dragging state ────

    /**
     * @return whether a drag is in progress / 是否正在进行拖动
     */
    boolean isDragging();

    /**
     * Set the dragging state.
     * <p>设置拖动状态。</p>
     */
    void setDragging(boolean dragging);
}
