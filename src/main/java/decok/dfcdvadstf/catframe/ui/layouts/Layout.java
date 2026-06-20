package decok.dfcdvadstf.catframe.ui.layouts;

import java.util.List;
import java.util.function.Consumer;

/**
 * Layout — the core interface for any layout that arranges child elements.
 * <p>
 * Extends {@link ILayout} with child management, padding, spacing,
 * recalculation, and optional drawing (background, separators, etc.).
 * </p>
 *
 * <p>
 * Layout —— 所有排布类实例的核心接口，支持子元素管理、内边距、间距、
 * 重新计算和可选绘制（背景、分隔线等）。
 * </p>
 */
public interface Layout extends ILayout {

    // ──── Child management ────

    /**
     * Adds a child element. / 添加子元素。
     */
    Layout add(ILayout child);

    /**
     * Removes a child element. / 移除子元素。
     */
    Layout remove(ILayout child);

    /**
     * Removes all children. / 移除所有子元素。
     */
    void clear();

    /**
     * Returns an unmodifiable view of the children. / 返回子元素列表的只读视图。
     */
    List<ILayout> getChildren();

    /**
     * Visit all direct children. / 访问所有直接子元素。
     */
    void visitChildren(Consumer<ILayout> visitor);

    // ──── Layout logic ────

    /**
     * Recalculate the position of each child based on this layout's
     * current x, y, width, height, padding and spacing.
     * <p>根据当前的位置、尺寸、内边距和间距重新计算所有子元素的位置。</p>
     */
    void recalculate();

    /**
     * Arrange elements recursively. Default implementation just calls recalculate().
     * <p>递归排列所有元素。默认实现仅调用 recalculate()。</p>
     */
    default void arrangeElements() {
        recalculate();
    }

    /**
     * Draw the layout background / decoration.
     * Default implementation is a no-op — override in layouts that have
     * a visual container (e.g. {@code HeaderFooterLayout}).
     * <p>绘制布局的背景/装饰。默认为空 —— 在需要视觉容器的布局中覆写。</p>
     */
    default void draw(int mouseX, int mouseY, float partialTicks) {
    }
}
