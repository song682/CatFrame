package decok.dfcdvadstf.catframe.ui.layouts;

/**
 * LinearLayout — arranges children in a single row or column.
 * <p>
 * Supports {@link Axis#HORIZONTAL} (left → right) and
 * {@link Axis#VERTICAL} (top → bottom). Use {@link Alignment} to control
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

    private Axis axis = Axis.VERTICAL;
    private Alignment alignment = Alignment.START;

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

    // ──── Properties ────

    public Alignment getAlignment() {
        return alignment;
    }

    public void setAlignment(Alignment alignment) {
        this.alignment = alignment;
        recalculate();
    }

    @Override
    public void recalculate() {
        if (children.isEmpty()) {
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
        for (ILayout child : children) {
            totalChildH += child.getHeight();
            if (maxChildW < child.getWidth()) {
                maxChildW = child.getWidth();
            }
        }

        width = maxChildW + padding * 2;
        height = totalChildH + spacing * (children.size() - 1) + padding * 2;

        int contentW = width - padding * 2;
        int contentY = y + padding;

        for (ILayout child : children) {
            int childX;
            switch (alignment) {
                case CENTER:
                    childX = x + padding + (contentW - child.getWidth()) / 2;
                    break;
                case END:
                    childX = x + width - padding - child.getWidth();
                    break;
                case FILL:
                    childX = x + padding;
                    // FILL on the perpendicular axis means we stretch the child.
                    // Since we can't modify the child's width contractually,
                    // we just position it at the left with the full content width.
                    // The child is responsible for honouring its own size.
                    break;
                default: // START
                    childX = x + padding;
                    break;
            }

            child.setPosition(childX, contentY);
            contentY += child.getHeight() + spacing;
        }
    }

    // ──── Recalculate ────

    private void layoutHorizontal() {
        int totalChildW = 0;
        int maxChildH = 0;
        for (ILayout child : children) {
            totalChildW += child.getWidth();
            if (maxChildH < child.getHeight()) {
                maxChildH = child.getHeight();
            }
        }

        width = totalChildW + spacing * (children.size() - 1) + padding * 2;
        height = maxChildH + padding * 2;

        int contentH = height - padding * 2;
        int contentX = x + padding;

        for (ILayout child : children) {
            int childY;
            switch (alignment) {
                case CENTER:
                    childY = y + padding + (contentH - child.getHeight()) / 2;
                    break;
                case END:
                    childY = y + height - padding - child.getHeight();
                    break;
                case FILL:
                    childY = y + padding;
                    break;
                default: // START
                    childY = y + padding;
                    break;
            }

            child.setPosition(contentX, childY);
            contentX += child.getWidth() + spacing;
        }
    }

    /**
     * The primary arrangement direction. / 主排列方向。
     */
    public enum Axis {HORIZONTAL, VERTICAL}

    /**
     * How children align on the axis perpendicular to the layout direction.
     * <p>子元素在垂直于排列方向上的对齐方式。</p>
     */
    public enum Alignment {
        /**
         * Left / Top — children clump at the start. / 左 / 上对齐。
         */
        START,
        /**
         * Children are centred on the perpendicular axis. / 居中对齐。
         */
        CENTER,
        /**
         * Right / Bottom — children clump at the end. / 右 / 下对齐。
         */
        END,
        /**
         * Children are stretched to fill the available space on the
         * perpendicular axis.
         * <p>子元素在垂直方向上拉伸填满可用空间。</p>
         */
        FILL
    }
}
