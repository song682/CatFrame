package decok.dfcdvadstf.catframe.ui.layouts;

/**
 * SimpleLayout — a general-purpose container layout.
 * <p>
 * Children keep whatever positions they already have; this layout only
 * calculates its bounding box from the union of all children, applies
 * padding, and provides a grouped container.
 * </p>
 * <p>
 * Use this when you just want to group elements without any automatic
 * arrangement — you set each child's position yourself.
 * </p>
 *
 * <p>
 * SimpleLayout —— 通用容器布局。
 * 子元素保留各自的位置，布局只计算所有子元素的包围盒加上内边距。
 * 适合你手动定位每个子元素、不需要自动排布的场景。
 * </p>
 */
public class SimpleLayout extends AbstractLayout {

    public SimpleLayout() {
    }

    public SimpleLayout(int padding) {
        this.padding = padding;
    }

    @Override
    public void recalculate() {
        if (getChildren().isEmpty()) {
            width = 0;
            height = 0;
            return;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (ILayout child : getChildren()) {
            int cx = child.getX();
            int cy = child.getY();
            minX = Math.min(minX, cx);
            minY = Math.min(minY, cy);
            maxX = Math.max(maxX, cx + child.getWidth());
            maxY = Math.max(maxY, cy + child.getHeight());
        }

        // Bounding box plus padding on all sides.
        // If no children with real size, fall back to zero.
        if (maxX < minX || maxY < minY) {
            width = padding * 2;
            height = padding * 2;
            return;
        }

        width = (maxX - minX) + padding * 2;
        height = (maxY - minY) + padding * 2;
    }
}
