package decok.dfcdvadstf.catframe.ui.layouts;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * FrameLayout — aligns children within a frame using per-child alignment settings.
 * <p>
 * All children are positioned on top of each other (layered). Each child is aligned
 * within the frame according to its {@link LayoutSettings}. The frame size is the
 * maximum of its minimum dimensions and the largest child (including padding).
 * </p>
 *
 * <p>
 * FrameLayout —— 将子元素在框架中对齐排列。
 * 所有子元素以层叠方式放置，每个子元素根据其 {@link LayoutSettings} 在框架内对齐。
 * 框架尺寸取最小值与最大子元素（含内边距）中的较大值。
 * </p>
 */
public class FrameLayout extends AbstractLayout {

    private final List<ChildContainer> children = new ArrayList<>();
    private int minWidth;
    private int minHeight;
    private final LayoutSettings defaultChildLayoutSettings = LayoutSettings.defaults().align(0.5F, 0.5F);

    public FrameLayout() {
        this(0, 0, 0, 0);
    }

    public FrameLayout(int minWidth, int minHeight) {
        this(0, 0, minWidth, minHeight);
    }

    public FrameLayout(int x, int y, int minWidth, int minHeight) {
        super();
        this.x = x;
        this.y = y;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
    }

    public FrameLayout setMinDimensions(int minWidth, int minHeight) {
        return setMinWidth(minWidth).setMinHeight(minHeight);
    }

    public FrameLayout setMinHeight(int minHeight) {
        this.minHeight = minHeight;
        return this;
    }

    public FrameLayout setMinWidth(int minWidth) {
        this.minWidth = minWidth;
        return this;
    }

    public LayoutSettings newChildLayoutSettings() {
        return this.defaultChildLayoutSettings.copy();
    }

    public LayoutSettings defaultChildLayoutSetting() {
        return this.defaultChildLayoutSettings;
    }

    @Override
    public void recalculate() {
        LAYOUT_LOG.debug("[FrameLayout] recalculate: pos=({},{}), minSize={}x{}, children={}",
                x, y, minWidth, minHeight, children.size());
        int resultWidth = this.minWidth;
        int resultHeight = this.minHeight;

        for (ChildContainer child : this.children) {
            resultWidth = Math.max(resultWidth, child.getWidth());
            resultHeight = Math.max(resultHeight, child.getHeight());
        }

        for (ChildContainer child : this.children) {
            child.setX(this.x, resultWidth);
            child.setY(this.y, resultHeight);
        }

        this.width = resultWidth;
        this.height = resultHeight;
    }

    // ──── Add children ────

    public <T extends ILayout> T addChild(T child) {
        return addChild(child, newChildLayoutSettings());
    }

    public <T extends ILayout> T addChild(T child, LayoutSettings childLayoutSettings) {
        this.children.add(new ChildContainer(child, childLayoutSettings));
        super.add(child); // adds to AbstractLayout's child list + triggers recalculate
        return child;
    }

    public <T extends ILayout> T addChild(T child, Consumer<LayoutSettings> configurator) {
        LayoutSettings settings = newChildLayoutSettings();
        configurator.accept(settings);
        return addChild(child, settings);
    }

    @Override
    public Layout add(ILayout child) {
        addChild(child);
        return this;
    }

    @Override
    public Layout add(ILayout child, LayoutSettings settings) {
        addChild(child, settings);
        return this;
    }

    @Override
    public Layout remove(ILayout child) {
        this.children.removeIf(w -> w.child == child);
        return super.remove(child);
    }

    @Override
    public void clear() {
        this.children.clear();
        super.clear();
    }

    @Override
    public void visitChildren(Consumer<ILayout> visitor) {
        this.children.forEach(w -> visitor.accept(w.child));
    }

    // ──── Static helper methods ────

    /**
     * Centre a widget within the given rectangle.
     * <p>将控件在给定矩形内居中。</p>
     */
    public static void centerInRectangle(ILayout widget, int x, int y, int width, int height) {
        alignInRectangle(widget, x, y, width, height, 0.5F, 0.5F);
    }

    /**
     * Align a widget within the given rectangle using the specified alignment factors.
     * <p>使用指定的对齐因子将控件在给定矩形内对齐。</p>
     */
    public static void alignInRectangle(
            ILayout widget, int x, int y, int width, int height, float alignX, float alignY
    ) {
        alignInDimension(x, width, widget.getWidth(), widget::setX, alignX);
        alignInDimension(y, height, widget.getHeight(), widget::setY, alignY);
    }

    /**
     * Align a single dimension of a widget.
     * <p>对齐控件的单个维度。</p>
     */
    public static void alignInDimension(
            int pos, int length, int widgetLength, Consumer<Integer> setPos, float align
    ) {
        int offset = Math.round(align * (length - widgetLength));
        setPos.accept(pos + offset);
    }

    // ──── Internal ────

    private static class ChildContainer extends AbstractChildWrapper {
        protected ChildContainer(ILayout child, LayoutSettings layoutSettings) {
            super(child, layoutSettings);
        }
    }
}
