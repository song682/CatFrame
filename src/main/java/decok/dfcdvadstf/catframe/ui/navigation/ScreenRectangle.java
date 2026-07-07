package decok.dfcdvadstf.catframe.ui.navigation;

import decok.dfcdvadstf.catframe.ui.layouts.ILayout;

/**
 * <p>
 * 屏幕矩形区域 —— 描述一个控件在屏幕上的位置和尺寸。<br>
 * 对标高版本 Minecraft 的 {@code ScreenRectangle}，用于 Tab 系统布局。
 * </p>
 * <p>
 * Screen rectangle — describes the position and size of a widget on screen.<br>
 * Counterpart of higher Minecraft versions' {@code ScreenRectangle}, used by the Tab system.
 * </p>
 */
public final class ScreenRectangle {

    public final int x;
    public final int y;
    public final int width;
    public final int height;

    public ScreenRectangle(final int x, final int y, final int width, final int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Creates a ScreenRectangle from an ILayout's current bounds.
     * <p>从 ILayout 的当前边界创建 ScreenRectangle。</p>
     */
    public static ScreenRectangle of(final ILayout layout) {
        return new ScreenRectangle(layout.getX(), layout.getY(), layout.getWidth(), layout.getHeight());
    }

    public int left() {
        return this.x;
    }

    public int top() {
        return this.y;
    }

    public int right() {
        return this.x + this.width;
    }

    public int bottom() {
        return this.y + this.height;
    }

    /**
     * 判断两个矩形是否相交（有重叠区域）。
     * <p>对标 26.1.2 {@code ScreenRectangle.intersects()}，
     * 用于 {@code GuiRenderState.Node} 的自动分层判定。</p>
     */
    public boolean intersects(final ScreenRectangle other) {
        return this.x < other.x + other.width
                && this.x + this.width > other.x
                && this.y < other.y + other.height
                && this.y + this.height > other.y;
    }

    /**
     * 判断本矩形是否完全包含另一个矩形。
     * <p>对标 26.1.2 {@code ScreenRectangle.encompasses()}，
     * 用于 {@code GuiRenderState} 判断新元素是否属于当前节点的子层级。</p>
     */
    public boolean encompasses(final ScreenRectangle other) {
        return other.x >= this.x
                && other.y >= this.y
                && other.x + other.width <= this.x + this.width
                && other.y + other.height <= this.y + this.height;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ScreenRectangle)) return false;
        ScreenRectangle that = (ScreenRectangle) o;
        return this.x == that.x && this.y == that.y && this.width == that.width && this.height == that.height;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        result = 31 * result + width;
        result = 31 * result + height;
        return result;
    }

    @Override
    public String toString() {
        return "ScreenRectangle{x=" + x + ", y=" + y + ", w=" + width + ", h=" + height + "}";
    }
}
