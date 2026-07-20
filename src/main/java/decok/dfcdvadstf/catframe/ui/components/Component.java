package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.components.events.ComponentPath;
import decok.dfcdvadstf.catframe.ui.layouts.ILayout;
import decok.dfcdvadstf.catframe.ui.navigation.FocusNavigationEvent;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;

import javax.annotation.Nullable;

/**
 * <p>
 * 组件基接口 —— 所有 UI 控件的基础抽象。<br>
 * 对标高版本 Minecraft 的 {@code LayoutElement + Renderable + GuiEventListener} 的合并接口。
 * </p>
 * <p>
 * Base component interface — the fundamental abstraction for all UI widgets.<br>
 * Merges the high-version Minecraft equivalents of {@code LayoutElement}, {@code Renderable},
 * and {@code GuiEventListener}.
 * </p>
 */
public interface Component extends ILayout {

    // ──── Position / Size ────

    int getX();

    void setX(int x);

    int getY();

    void setY(int y);

    int getWidth();

    int getHeight();

    /**
     * Set both width and height in one call.
     * <p>一次性设置宽度和高度。</p>
     */
    default void setSize(int width, int height) {
        // subclasses may override for optimisation
    }

    // ──── Visibility / Active ────

    boolean isVisible();

    void setVisible(boolean visible);

    boolean isActive();

    void setActive(boolean active);

    // ──── Rendering ────

    /**
     * Render this component at its current position.
     * <p>在当前位置渲染此组件。</p>
     *
     * @param mouseX       mouse X coordinate / 鼠标 X 坐标
     * @param mouseY       mouse Y coordinate / 鼠标 Y 坐标
     * @param partialTicks partial tick time / 部分 tick 时间
     */
    default void render(int mouseX, int mouseY, float partialTicks) {
    }

    // ──── Event handling ────

    /**
     * Handle mouse click.
     * <p>处理鼠标点击事件。</p>
     *
     * @param mouseX       mouse X coordinate / 鼠标 X 坐标
     * @param mouseY       mouse Y coordinate / 鼠标 Y 坐标
     * @param mouseButton  mouse button index / 鼠标按钮索引
     */
    default void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    }

    /**
     * Handle key press.
     * <p>处理按键事件（合并式，兼容旧代码 —— 同时携带字符与键码）。</p>
     *
     * @param typedChar typed character / 键入的字符
     * @param keyCode   key code / 键码
     */
    default void keyTyped(char typedChar, int keyCode) {
    }

    /**
     * Handle a key being pressed (or repeated). Split counterpart of the high-version
     * {@code GuiEventListener.keyPressed}.
     * <p>处理按键按下（或重复）。对标高版本 {@code GuiEventListener.keyPressed} 的拆分事件。</p>
     * <p>注：LWJGL2 无 GLFW 的 scancode/modifiers，修饰键请用 {@code KeyTypedEvent} 查询。</p>
     *
     * @param keyCode LWJGL key code / LWJGL 键码
     * @return true if the event was consumed / 若事件被消费则返回 true
     */
    default boolean keyPressed(int keyCode) {
        return false;
    }

    /**
     * Handle a key being released. Split counterpart of the high-version
     * {@code GuiEventListener.keyReleased}. Only reachable via the LWJGL2 keyboard
     * event queue (vanilla {@code keyTyped} never fires on release).
     * <p>处理按键松开。对标高版本 {@code GuiEventListener.keyReleased}。仅能通过 LWJGL2
     * 键盘事件队列获得（原版 {@code keyTyped} 不会在松开时触发）。</p>
     *
     * @param keyCode LWJGL key code / LWJGL 键码
     * @return true if the event was consumed / 若事件被消费则返回 true
     */
    default boolean keyReleased(int keyCode) {
        return false;
    }

    /**
     * Handle a translated character being typed. Split counterpart of the high-version
     * {@code GuiEventListener.charTyped}.
     * <p>处理键入的可显示字符。对标高版本 {@code GuiEventListener.charTyped}。</p>
     *
     * @param codePoint typed character / 键入的字符
     * @return true if the event was consumed / 若事件被消费则返回 true
     */
    default boolean charTyped(char codePoint) {
        return false;
    }

    /**
     * Handle mouse scroll.
     * <p>处理鼠标滚轮事件。</p>
     *
     * @param delta scroll delta / 滚轮增量
     */
    default void mouseScrolled(int delta) {
    }

    /**
     * Handle mouse drag.
     * <p>处理鼠标拖动事件。</p>
     */
    default void mouseDrag(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
    }

    /**
     * Handle mouse release.
     * <p>处理鼠标释放事件。</p>
     */
    default void mouseReleased(int mouseX, int mouseY, int mouseButton) {
    }

    // ──── Focus ────

    /**
     * @return whether this component currently holds keyboard focus / 此组件当前是否持有键盘焦点
     */
    default boolean isFocused() {
        return false;
    }

    /**
     * Set this component's focus state.
     * <p>设置此组件的焦点状态。</p>
     */
    default void setFocused(boolean focused) {
    }

    /**
     * Resolve the next focus target for the given navigation event.
     * <p>为给定导航事件解析下一个焦点目标。</p>
     * <p>叶子组件默认实现：未聚焦且可用时返回自身，否则返回 {@code null}
     * 表示焦点应离开本组件。容器应覆盖此方法以遍历子组件。</p>
     *
     * @param event the navigation request / 导航请求
     * @return the resolved path, or {@code null} if focus should leave this component
     *         / 解析出的路径；若焦点应离开本组件则返回 {@code null}
     */
    @Nullable
    default ComponentPath nextFocusPath(FocusNavigationEvent event) {
        if (!isActive() || !isVisible()) {
            return null;
        }
        return isFocused() ? null : ComponentPath.leaf(this);
    }

    // ──── Utility ────

    /**
     * @return this component's bounds as a {@link ScreenRectangle} / 以 {@link ScreenRectangle} 返回此组件边界
     */
    default ScreenRectangle getRectangle() {
        return new ScreenRectangle(getX(), getY(), getWidth(), getHeight());
    }

    /**
     * Check whether the given point is inside this component.
     * <p>检查给定点是否在此组件内部。</p>
     */
    default boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= getX() && mouseX < getX() + getWidth()
            && mouseY >= getY() && mouseY < getY() + getHeight();
    }
}
