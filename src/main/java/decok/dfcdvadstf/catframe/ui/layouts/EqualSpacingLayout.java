package decok.dfcdvadstf.catframe.ui.layouts;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * EqualSpacingLayout — distributes children evenly across the available space.
 * <p>
 * Unlike {@link LinearLayout} where children are packed with a fixed gap,
 * this layout calculates the gap automatically so that children are evenly
 * distributed from edge to edge of the content area.
 * </p>
 *
 * <p>
 * EqualSpacingLayout —— 将子元素等间距分布在可用空间内。
 * 与 {@link LinearLayout} 使用固定间距不同，此布局自动计算间距，
 * 使子元素从内容区域的一端均匀分布到另一端。
 * </p>
 */
public class EqualSpacingLayout extends AbstractLayout {

    private final List<ChildContainer> containers = new ArrayList<>();
    private Axis axis = Axis.HORIZONTAL;
    private final LayoutSettings defaultChildLayoutSettings = LayoutSettings.defaults();

    /**
     * Creates a horizontal EqualSpacingLayout. / 创建水平等间距布局。
     */
    public EqualSpacingLayout() {
    }

    /**
     * Creates an EqualSpacingLayout with the given direction. / 创建指定方向的等间距布局。
     */
    public EqualSpacingLayout(Axis axis) {
        this.axis = axis;
    }

    public Axis getAxis() {
        return axis;
    }

    public void setAxis(Axis axis) {
        this.axis = axis;
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

    public <T extends ILayout> T addChild(T child) {
        return addChild(child, newChildLayoutSettings());
    }

    public <T extends ILayout> T addChild(T child, LayoutSettings settings) {
        this.containers.add(new ChildContainer(child, settings));
        super.add(child);
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
        this.containers.removeIf(w -> w.child == child);
        return super.remove(child);
    }

    @Override
    public void clear() {
        this.containers.clear();
        super.clear();
    }

    @Override
    public void visitChildren(Consumer<ILayout> visitor) {
        this.containers.forEach(w -> visitor.accept(w.child));
    }

    // ──── Recalculate ────

    @Override
    public void recalculate() {
        if (this.containers.isEmpty()) {
            width = 0;
            height = 0;
            return;
        }

        if (axis == Axis.HORIZONTAL) {
            layoutHorizontal();
        } else {
            layoutVertical();
        }
    }

    private void layoutHorizontal() {
        int totalChildW = 0;
        int maxH = 0;
        for (ChildContainer c : containers) {
            totalChildW += c.getWidth();
            maxH = Math.max(maxH, c.getHeight());
        }

        int contentW = totalChildW;
        if (width > 0) {
            contentW = width - padding * 2;
        }

        int gap = containers.size() > 1
                ? Math.max(0, (contentW - totalChildW) / (containers.size() - 1))
                : 0;

        int renderedW = totalChildW + gap * (containers.size() - 1);
        if (width <= 0) {
            width = renderedW + padding * 2;
        }
        height = maxH + padding * 2;

        int contentH = height - padding * 2;
        int contentX = x + padding;

        for (ChildContainer c : containers) {
            c.setX(contentX, c.getWidth());
            int childY = y + padding + (contentH - c.getHeight()) / 2;
            c.setY(childY, c.getHeight());
            contentX += c.getWidth() + gap;
        }
    }

    private void layoutVertical() {
        int totalChildH = 0;
        int maxW = 0;
        for (ChildContainer c : containers) {
            totalChildH += c.getHeight();
            maxW = Math.max(maxW, c.getWidth());
        }

        int contentH = totalChildH;
        if (height > 0) {
            contentH = height - padding * 2;
        }

        int gap = containers.size() > 1
                ? Math.max(0, (contentH - totalChildH) / (containers.size() - 1))
                : 0;

        int renderedH = totalChildH + gap * (containers.size() - 1);
        if (height <= 0) {
            height = renderedH + padding * 2;
        }
        width = maxW + padding * 2;

        int contentW = width - padding * 2;
        int contentY = y + padding;

        for (ChildContainer c : containers) {
            int childX = x + padding + (contentW - c.getWidth()) / 2;
            c.setX(childX, c.getWidth());
            c.setY(contentY, c.getHeight());
            contentY += c.getHeight() + gap;
        }
    }

    // ──── Internal ────

    private static class ChildContainer extends AbstractChildWrapper {
        protected ChildContainer(ILayout child, LayoutSettings settings) {
            super(child, settings);
        }
    }

    /**
     * The distribution direction. / 分布方向。
     */
    public enum Axis {HORIZONTAL, VERTICAL}
}
