package decok.dfcdvadstf.catframe.ui.layouts;

/**
 * HorizontalLayout — a linear layout that arranges children left to right.
 * <p>
 * Convenience wrapper around {@link LinearLayout} preset with
 * {@link LinearLayout.Axis#HORIZONTAL} and
 * {@link LinearLayout.Alignment#CENTER} (vertical centering by default).
 * </p>
 *
 * <p>
 * HorizontalLayout —— 从左到右排列子元素的水平线性布局。
 * 是对 {@link LinearLayout} 的便捷封装，默认预设
 * {@link LinearLayout.Axis#HORIZONTAL} + 垂直居中。
 * </p>
 */
public class HorizontalLayout extends LinearLayout {

    /**
     * Creates a horizontal layout with centre alignment on the Y axis.
     */
    public HorizontalLayout() {
        super(Axis.HORIZONTAL, Alignment.CENTER);
    }

    /**
     * Creates a horizontal layout with the given perpendicular alignment.
     */
    public HorizontalLayout(Alignment alignment) {
        super(Axis.HORIZONTAL, alignment);
    }
}
