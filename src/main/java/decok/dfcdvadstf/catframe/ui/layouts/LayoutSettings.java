package decok.dfcdvadstf.catframe.ui.layouts;

/**
 * Per-child layout settings controlling padding and alignment within a layout cell.
 * <p>
 * 每子元素布局配置——控制子元素在其单元格内的内边距和对齐方式。
 * </p>
 *
 * <p>Usage / 用法:</p>
 * <pre>{@code
 * layout.add(child, LayoutSettings.defaults()
 *     .padding(5)
 *     .align(0.5F, 0.5F));
 *
 * // or with a lambda
 * layout.add(child, s -> s.padding(5).alignHorizontallyCenter());
 * }</pre>
 */
public interface LayoutSettings {

    /** Uniform padding on all sides / 统一四周内边距 */
    LayoutSettings padding(int padding);

    /** Horizontal & vertical padding / 水平 + 垂直内边距 */
    LayoutSettings padding(int horizontal, int vertical);

    /** Per-side padding / 分别设置四边内边距 */
    LayoutSettings padding(int left, int top, int right, int bottom);

    LayoutSettings paddingLeft(int padding);

    LayoutSettings paddingTop(int padding);

    LayoutSettings paddingRight(int padding);

    LayoutSettings paddingBottom(int padding);

    /** Convenience: set left + right / 快捷设置左右 */
    LayoutSettings paddingHorizontal(int padding);

    /** Convenience: set top + bottom / 快捷设置上下 */
    LayoutSettings paddingVertical(int padding);

    /** Set both horizontal and vertical alignment (0.0 – 1.0) / 同时设置水平和垂直对齐 */
    LayoutSettings align(float xAlignment, float yAlignment);

    /** Horizontal alignment (0.0 = left, 0.5 = centre, 1.0 = right) */
    LayoutSettings alignHorizontally(float xAlignment);

    /** Vertical alignment (0.0 = top, 0.5 = middle, 1.0 = bottom) */
    LayoutSettings alignVertically(float yAlignment);

    // ──── Convenience defaults ────

    default LayoutSettings alignHorizontallyLeft() {
        return this.alignHorizontally(0.0F);
    }

    default LayoutSettings alignHorizontallyCenter() {
        return this.alignHorizontally(0.5F);
    }

    default LayoutSettings alignHorizontallyRight() {
        return this.alignHorizontally(1.0F);
    }

    default LayoutSettings alignVerticallyTop() {
        return this.alignVertically(0.0F);
    }

    default LayoutSettings alignVerticallyMiddle() {
        return this.alignVertically(0.5F);
    }

    default LayoutSettings alignVerticallyBottom() {
        return this.alignVertically(1.0F);
    }

    /** Creates an independent copy / 创建独立副本 */
    LayoutSettings copy();

    /**
     * Returns the underlying implementation for direct field access.
     * <p>返回底层实现以直接访问字段。</p>
     */
    LayoutSettingsImpl getExposed();

    /**
     * Creates a new {@code LayoutSettings} with default values
     * (zero padding, left/top alignment).
     * <p>使用默认值（零内边距，左/上对齐）创建新实例。</p>
     */
    static LayoutSettings defaults() {
        return new LayoutSettingsImpl();
    }

    // ──── Implementation ────

    /**
     * Default implementation that stores padding and alignment values directly.
     * <p>直接存储内边距和对齐值的默认实现。</p>
     */
    class LayoutSettingsImpl implements LayoutSettings {
        public int paddingLeft;
        public int paddingTop;
        public int paddingRight;
        public int paddingBottom;
        public float xAlignment;
        public float yAlignment;

        public LayoutSettingsImpl() {
        }

        public LayoutSettingsImpl(LayoutSettingsImpl other) {
            this.paddingLeft = other.paddingLeft;
            this.paddingTop = other.paddingTop;
            this.paddingRight = other.paddingRight;
            this.paddingBottom = other.paddingBottom;
            this.xAlignment = other.xAlignment;
            this.yAlignment = other.yAlignment;
        }

        @Override
        public LayoutSettingsImpl padding(int padding) {
            return padding(padding, padding);
        }

        @Override
        public LayoutSettingsImpl padding(int horizontal, int vertical) {
            return paddingHorizontal(horizontal).paddingVertical(vertical);
        }

        @Override
        public LayoutSettingsImpl padding(int left, int top, int right, int bottom) {
            return paddingLeft(left).paddingRight(right).paddingTop(top).paddingBottom(bottom);
        }

        @Override
        public LayoutSettingsImpl paddingLeft(int padding) {
            this.paddingLeft = padding;
            return this;
        }

        @Override
        public LayoutSettingsImpl paddingTop(int padding) {
            this.paddingTop = padding;
            return this;
        }

        @Override
        public LayoutSettingsImpl paddingRight(int padding) {
            this.paddingRight = padding;
            return this;
        }

        @Override
        public LayoutSettingsImpl paddingBottom(int padding) {
            this.paddingBottom = padding;
            return this;
        }

        @Override
        public LayoutSettingsImpl paddingHorizontal(int padding) {
            return paddingLeft(padding).paddingRight(padding);
        }

        @Override
        public LayoutSettingsImpl paddingVertical(int padding) {
            return paddingTop(padding).paddingBottom(padding);
        }

        @Override
        public LayoutSettingsImpl align(float xAlignment, float yAlignment) {
            this.xAlignment = xAlignment;
            this.yAlignment = yAlignment;
            return this;
        }

        @Override
        public LayoutSettingsImpl alignHorizontally(float xAlignment) {
            this.xAlignment = xAlignment;
            return this;
        }

        @Override
        public LayoutSettingsImpl alignVertically(float yAlignment) {
            this.yAlignment = yAlignment;
            return this;
        }

        @Override
        public LayoutSettingsImpl copy() {
            return new LayoutSettingsImpl(this);
        }

        @Override
        public LayoutSettingsImpl getExposed() {
            return this;
        }
    }
}
