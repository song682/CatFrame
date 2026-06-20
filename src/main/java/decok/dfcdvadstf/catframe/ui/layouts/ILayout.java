package decok.dfcdvadstf.catframe.ui.layouts;

import java.util.function.Consumer;

/**
 * Base interface for any positioned element in the layout system.
 * <p>
 * 布局系统中所有可定位元素的基础接口。
 * </p>
 */
public interface ILayout {

    int getX();

    void setX(int x);

    int getY();

    void setY(int y);

    int getWidth();

    int getHeight();

    default void setPosition(final int x, final int y) {
        this.setX(x);
        this.setY(y);
    }

    /**
     * Visit all "leaf" widgets inside this element (no-op by default).
     * <p>遍历此元素内的所有"叶子"控件（默认空操作）。</p>
     */
    default void visitWidgets(final Consumer<Object> widgetVisitor) {
    }
}
