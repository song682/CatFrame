package decok.dfcdvadstf.catframe.ui.layouts;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * LinearLayout — arranges children in a single row or column.
 * <p>
 * Supports {@link Axis#HORIZONTAL} (left to right) and
 * {@link Axis#VERTICAL} (top to bottom). Use {@link Alignment} to control
 * how children are positioned on the perpendicular axis.
 * </p>
 *
 * <p>
 * LinearLayout —— 将子元素沿单行或单列排列。
 * 支持 {@link Axis#HORIZONTAL}（从左到右）和 {@link Axis#VERTICAL}（从上到下）。
 * 用 {@link Alignment} 控制子元素在垂直于排列方向上的对齐方式。
 * </p>
 */
public class LinearLayout extends AbstractLayout {

    private final List<ChildContainer> containers = new ArrayList<>();
    private Axis axis = Axis.VERTICAL;
    private Alignment alignment = Alignment.START;
    private final LayoutSettings defaultChildLayoutSettings = LayoutSettings.defaults();

    /**
     * Creates a vertical LinearLayout. / 创建一个垂直方向的 LinearLayout。
     */
    public LinearLayout() {
    }

    /**
     * Creates a LinearLayout with the given direction. / 创建指定方向的 LinearLayout。
     */
    public LinearLayout(Axis axis) {
        this.axis = axis;
    }

    /**
     * Creates a LinearLayout with direction and alignment. / 创建指定方向和对其方式的 LinearLayout。
     */
    public LinearLayout(Axis axis, Alignment alignment) {
        this.axis = axis;
        this.alignment = alignment;
    }

    public Axis getAxis() {
        return axis;
    }

    public void setAxis(Axis axis) {
        this.axis = axis;
        recalculate();
    }

    public Alignment getAlignment() {
        return alignment;
    }

    public void setAlignment(Alignment alignment) {
        this.alignment = alignment;
        recalculate();
    }

    // ──── Layout settings ────

    public LayoutSettings newChildLayoutSettings() {
        return this.defaultChildLayoutSettings.copy();
    }

    public LayoutSettings defaultChildLayoutSetting() {
        return this.defaultChildLayoutSettings;
    }

    // ──── Add children ────

    /**
     * Add a child with default layout settings.
     * <p>使用默认布局设置添加子元素。</p>
     */
    public <T extends ILayout> T addChild(T child) {
        return addChild(child, newChildLayoutSettings());
    }

    /**
     * Add a child with the given layout settings.
     * <p>使用指定布局设置添加子元素。</p>
     */
    public <T extends ILayout> T addChild(T child, LayoutSettings settings) {
        this.containers.add(new ChildContainer(child, settings));
        super.add(child); // adds to AbstractLayout's child list + triggers recalculate
        return child;
    }

    /**
     * Add a child using a lambda to configure its layout settings.
     * <p>使用 lambda 配置布局设置后添加子元素。</p>
     */
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
        this.containers.removeIf(w -> w.child == child);
        return super.remove(child);
    }

    @Override
    public void clear() {
        this.containers.clear();
        super.clear();
    }

    // ──── Visit ────

    @Override
    public void visitChildren(Consumer<ILayout> visitor) {
        this.containers.forEach(w -> visitor.accept(w.child));
    }

    // ──── Recalculate ────

    @Override
    public void recalculate() {
        LAYOUT_LOG.debug("[LinearLayout] recalculate: axis={}, pos=({},{}), containers={}",
                axis, x, y, containers.size());
        if (this.containers.isEmpty()) {
            width = 0;
            height = 0;
            return;
        }

        if (axis == Axis.VERTICAL) {
            layoutVertical();
        } else {
            layoutHorizontal();
        }
    }

    private void layoutVertical() {
        int totalChildH = 0;
        int maxChildW = 0;
        for (ChildContainer c : containers) {
            totalChildH += c.getHeight();
            maxChildW = Math.max(maxChildW, c.getWidth());
        }

        width = maxChildW + padding * 2;
        height = totalChildH + spacing * (containers.size() - 1) + padding * 2;

        int contentW = width - padding * 2;
        int contentY = y + padding;

        for (ChildContainer c : containers) {
            int childX;
            switch (alignment) {
                case CENTER:
                    childX = x + padding + (contentW - c.getWidth()) / 2;
                    break;
                case END:
                    childX = x + width - padding - c.getWidth();
                    break;
                default: // START / FILL
                    childX = x + padding;
                    break;
            }

            c.setX(childX, c.getWidth());
            c.setY(contentY, c.getHeight());
            contentY += c.getHeight() + spacing;
        }
    }

    private void layoutHorizontal() {
        int totalChildW = 0;
        int maxChildH = 0;
        for (ChildContainer c : containers) {
            totalChildW += c.getWidth();
            maxChildH = Math.max(maxChildH, c.getHeight());
        }

        width = totalChildW + spacing * (containers.size() - 1) + padding * 2;
        height = maxChildH + padding * 2;

        int contentH = height - padding * 2;
        int contentX = x + padding;

        for (ChildContainer c : containers) {
            int childY;
            switch (alignment) {
                case CENTER:
                    childY = y + padding + (contentH - c.getHeight()) / 2;
                    break;
                case END:
                    childY = y + height - padding - c.getHeight();
                    break;
                default: // START / FILL
                    childY = y + padding;
                    break;
            }

            c.setX(contentX, c.getWidth());
            c.setY(childY, c.getHeight());
            contentX += c.getWidth() + spacing;
        }
    }

    // ──── Internal ────

    private static class ChildContainer extends AbstractChildWrapper {
        protected ChildContainer(ILayout child, LayoutSettings settings) {
            super(child, settings);
        }
    }

    // ──── Enums ────

    /**
     * The primary arrangement direction. / 主排列方向。
     */
    public enum Axis {HORIZONTAL, VERTICAL}

    /**
     * How children align on the axis perpendicular to the layout direction.
     * <p>子元素在垂直于排列方向上的对齐方式。</p>
     */
    public enum Alignment {
        START,
        CENTER,
        END,
        FILL
    }
}
