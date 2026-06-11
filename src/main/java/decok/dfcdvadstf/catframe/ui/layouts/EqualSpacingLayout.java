package decok.dfcdvadstf.catframe.ui.layouts;

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

    private Axis axis = Axis.HORIZONTAL;

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

    // ──── Properties ────

    public void setAxis(Axis axis) {
        this.axis = axis;
        recalculate();
    }

    @Override
    public void recalculate() {
        if (children.isEmpty()) {
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

    // ──── Recalculate ────

    private void layoutHorizontal() {
        int totalChildW = 0;
        int maxH = 0;
        for (ILayout child : children) {
            totalChildW += child.getWidth();
            if (maxH < child.getHeight()) maxH = child.getHeight();
        }

        int contentW = totalChildW;
        if (width > 0) {
            contentW = width - padding * 2;
        }

        // Calculate gap so children are evenly spaced
        int gap = children.size() > 1
                ? (contentW - totalChildW) / (children.size() - 1)
                : 0;
        gap = Math.max(gap, 0);

        // Recalculate total width only if not externally set
        int renderedW = totalChildW + gap * (children.size() - 1);
        if (width <= 0) {
            width = renderedW + padding * 2;
        }
        height = maxH + padding * 2;

        int contentH = height - padding * 2;
        int contentX = x + padding;

        for (ILayout child : children) {
            int childY = y + padding;
            // Centre vertically within content area
            childY += (contentH - child.getHeight()) / 2;
            child.setPosition(contentX, childY);
            contentX += child.getWidth() + gap;
        }
    }

    private void layoutVertical() {
        int totalChildH = 0;
        int maxW = 0;
        for (ILayout child : children) {
            totalChildH += child.getHeight();
            if (maxW < child.getWidth()) maxW = child.getWidth();
        }

        int contentH = totalChildH;
        if (height > 0) {
            contentH = height - padding * 2;
        }

        int gap = children.size() > 1
                ? (contentH - totalChildH) / (children.size() - 1)
                : 0;
        gap = Math.max(gap, 0);

        int renderedH = totalChildH + gap * (children.size() - 1);
        if (height <= 0) {
            height = renderedH + padding * 2;
        }
        width = maxW + padding * 2;

        int contentW = width - padding * 2;
        int contentY = y + padding;

        for (ILayout child : children) {
            int childX = x + padding;
            // Centre horizontally within content area
            childX += (contentW - child.getWidth()) / 2;
            child.setPosition(childX, contentY);
            contentY += child.getHeight() + gap;
        }
    }

    /**
     * The distribution direction. / 分布方向。
     */
    public enum Axis {HORIZONTAL, VERTICAL}
}
