package decok.dfcdvadstf.catframe.ui.navigation;

import decok.dfcdvadstf.catframe.ui.layouts.ILayout;

/**
 * <p>
 * Screen rectangle — describes the position and size of a widget on screen.<br>
 * Counterpart of higher Minecraft versions' {@code ScreenRectangle}, used by screen layouts.
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
     * Creates a ScreenRectangle from an ILayout's current bounds.\
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
     * Determine whether two of rectangle has some overlapping regions.
     * <p>Align with modern {@code ScreenRectangle.intersects()}，
     * for {@code GuiRenderState.Node} 's Automatic Layering Judgment.</p>
     */
    public boolean intersects(final ScreenRectangle other) {
        return this.x < other.x + other.width
                && this.x + this.width > other.x
                && this.y < other.y + other.height
                && this.y + this.height > other.y;
    }

    /**
     * Check if this rectangle completely contains another rectangle.
     * <p>Align with modern {@code ScreenRectangle.encompasses()},
     *  used by {@code GuiRenderState} to check if a new element belongs to the current node's sub-hierarchy.</p>
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
