package decok.dfcdvadstf.catframe.ui.layouts;

/**
 * VerticalLayout — a linear layout that arranges children top to bottom.
 * <p>
 * Convenience wrapper around {@link LinearLayout} preset with
 * {@link LinearLayout.Axis#VERTICAL} and
 * {@link LinearLayout.Alignment#CENTER} (horizontal centering by default).
 * </p>
 *
 * <p>
 * VerticalLayout —— 从上到下排列子元素的垂直线性布局。
 * 是对 {@link LinearLayout} 的便捷封装，默认预设
 * {@link LinearLayout.Axis#VERTICAL} + 水平居中。
 * </p>
 */
public class VerticalLayout extends LinearLayout {

    /**
     * Creates a vertical layout with centre alignment on the X axis.
     */
    public VerticalLayout() {
        super(Axis.VERTICAL, Alignment.CENTER);
    }

    /**
     * Creates a vertical layout with the given perpendicular alignment.
     */
    public VerticalLayout(Alignment alignment) {
        super(Axis.VERTICAL, alignment);
    }
}
