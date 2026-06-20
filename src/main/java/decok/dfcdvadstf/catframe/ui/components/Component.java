package decok.dfcdvadstf.catframe.ui.components;

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
public interface Component {

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
     * <p>处理按键事件。</p>
     *
     * @param typedChar typed character / 键入的字符
     * @param keyCode   key code / 键码
     */
    default void keyTyped(char typedChar, int keyCode) {
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

    // ──── Utility ────

    /**
     * Check whether the given point is inside this component.
     * <p>检查给定点是否在此组件内部。</p>
     */
    default boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= getX() && mouseX < getX() + getWidth()
            && mouseY >= getY() && mouseY < getY() + getHeight();
    }
}
