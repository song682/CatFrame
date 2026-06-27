package decok.dfcdvadstf.catframe.ui.components.events;

import decok.dfcdvadstf.catframe.ui.components.Component;

import javax.annotation.Nullable;
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
