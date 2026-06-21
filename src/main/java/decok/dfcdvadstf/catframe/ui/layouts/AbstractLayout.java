package decok.dfcdvadstf.catframe.ui.layouts;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Abstract base implementing the common fields, child management and positioning
 * for all {@link Layout}s.
 * <p>
 * Stores x / y / width / height / padding / spacing and an internal child list.
 * Every mutator that affects layout calls {@link #recalculate()} automatically.
 * </p>
 *
 * <p>
 * 所有 {@link Layout} 的抽象基类，实现公共字段、子元素管理和定位。
 * 存储 x/y/width/height/padding/spacing 和一个内部子元素列表。
 * 任何影响布局的变更都会自动调用 {@link #recalculate()}。
 * </p>
 *
 * <h3>每子元素配置 / Per-child configuration</h3>
 * <p>
 * Subclasses can use {@link AbstractChildWrapper} to store per-child
 * {@link LayoutSettings}.  The wrappers store the child together with its
 * padding and alignment so that {@code recalculate()} can position each
 * child precisely.
 * </p>
 *
 * @see LayoutSettings
 * @see AbstractChildWrapper
 */
public abstract class AbstractLayout implements Layout {

    /**
     * Diagnostic logger for layout operations. Enable via log4j2 config for the
     * {@code decok.dfcdvadstf.catframe.ui.layouts} category.
     */
    protected static final Logger LAYOUT_LOG = LogManager.getLogger("CatFrame/Layout");

    private final List<ILayout> children = new ArrayList<>();
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected int padding = 4;
    protected int spacing = 2;

    // ──── Position ────

    @Override
    public int getX() {
        return x;
    }

    /**
     * Sets the X position and translates all children by the same delta.
     * <p>设置 X 位置并将所有子元素平移相同偏移量。</p>
     */
    @Override
    public void setX(int x) {
        int dx = x - this.x;
        this.x = x;
        LAYOUT_LOG.debug("[{}] setX({}) dx={}, children={}", getClass().getSimpleName(), x, dx, children.size());
        if (dx != 0) {
            for (ILayout child : children) {
                child.setX(child.getX() + dx);
            }
        }
    }

    @Override
    public int getY() {
        return y;
    }

    /**
     * Sets the Y position and translates all children by the same delta.
     * <p>设置 Y 位置并将所有子元素平移相同偏移量。</p>
     */
    @Override
    public void setY(int y) {
        int dy = y - this.y;
        this.y = y;
        LAYOUT_LOG.debug("[{}] setY({}) dy={}, children={}", getClass().getSimpleName(), y, dy, children.size());
        if (dy != 0) {
            for (ILayout child : children) {
                child.setY(child.getY() + dy);
            }
        }
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    // ──── Padding / Spacing (instance methods, not on Layout interface) ────

    public int getPadding() {
        return padding;
    }

    public void setPadding(int padding) {
        this.padding = Math.max(0, padding);
        LAYOUT_LOG.debug("[{}] setPadding({}) -> recalculate, pos=({},{}), size={}x{}, children={}",
                getClass().getSimpleName(), padding, x, y, width, height, children.size());
        recalculate();
    }

    public int getSpacing() {
        return spacing;
    }

    public void setSpacing(int spacing) {
        this.spacing = Math.max(0, spacing);
        LAYOUT_LOG.debug("[{}] setSpacing({}) -> recalculate, pos=({},{}), size={}x{}, children={}",
                getClass().getSimpleName(), spacing, x, y, width, height, children.size());
        recalculate();
    }

    // ──── Child management ────

    @Override
    public Layout add(ILayout child) {
        if (child != null && !children.contains(child)) {
            children.add(child);
            LAYOUT_LOG.debug("[{}] add({}) -> recalculate, pos=({},{}), size={}x{}, children={}",
                    getClass().getSimpleName(), child.getClass().getSimpleName(), x, y, width, height, children.size());
            recalculate();
        }
        return this;
    }

    /**
     * Adds a child with the given layout settings.  Subclasses that support
     * per-child settings (e.g. {@link LinearLayout}) should override this.
     * <p>添加子元素并指定布局配置。支持每子元素配置的子类（如 {@link LinearLayout}）应覆写此方法。</p>
     */
    public Layout add(ILayout child, LayoutSettings settings) {
        return add(child);
    }

    /**
     * Adds a child and configures its settings via a consumer lambda.
     * <p>添加子元素并通过 lambda 配置其设置。</p>
     */
    public Layout add(ILayout child, Consumer<LayoutSettings> configurator) {
        LayoutSettings settings = LayoutSettings.defaults();
        configurator.accept(settings);
        return add(child, settings);
    }

    @Override
    public Layout remove(ILayout child) {
        if (child != null && children.remove(child)) {
            LAYOUT_LOG.debug("[{}] remove({}) -> recalculate, pos=({},{}), size={}x{}, children={}",
                    getClass().getSimpleName(), child.getClass().getSimpleName(), x, y, width, height, children.size());
            recalculate();
        }
        return this;
    }

    @Override
    public void clear() {
        if (!children.isEmpty()) {
            int count = children.size();
            children.clear();
            LAYOUT_LOG.debug("[{}] clear() removed {} children -> recalculate, pos=({},{}), size={}x{}",
                    getClass().getSimpleName(), count, x, y, width, height);
            recalculate();
        }
    }

    @Override
    public List<ILayout> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public void visitChildren(Consumer<ILayout> visitor) {
        for (ILayout child : children) {
            visitor.accept(child);
        }
    }

    // ──── Convenience ────

    /**
     * Add multiple children in one call. / 批量添加子元素。
     */
    public Layout addAll(ILayout... children) {
        for (ILayout child : children) {
            if (child != null && !this.children.contains(child)) {
                this.children.add(child);
            }
        }
        LAYOUT_LOG.debug("[{}] addAll({}) -> recalculate, pos=({},{}), size={}x{}, children={}",
                getClass().getSimpleName(), children.length, x, y, width, height, this.children.size());
        recalculate();
        return this;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{x=" + x + ", y=" + y
                + ", w=" + width + ", h=" + height
                + ", children=" + children.size() + "}";
    }

    // ──── Abstract child wrapper (per-child settings) ────

    /**
     * A wrapper that pairs an {@link ILayout} child with its {@link LayoutSettings}.
     * <p>将 {@link ILayout} 子元素与其 {@link LayoutSettings} 配对的包装器。</p>
     */
    protected abstract static class AbstractChildWrapper {
        public final ILayout child;
        public final LayoutSettings.LayoutSettingsImpl settings;

        protected AbstractChildWrapper(ILayout child, LayoutSettings settings) {
            this.child = child;
            this.settings = settings.getExposed();
        }

        /**
         * Returns the total width this child occupies, including padding.
         * <p>返回此子元素占用的总宽度，包含内边距。</p>
         */
        public int getWidth() {
            return child.getWidth() + settings.paddingLeft + settings.paddingRight;
        }

        /**
         * Returns the total height this child occupies, including padding.
         * <p>返回此子元素占用的总高度，包含内边距。</p>
         */
        public int getHeight() {
            return child.getHeight() + settings.paddingTop + settings.paddingBottom;
        }

        /**
         * Position the child along the X axis within the given available space,
         * respecting its horizontal alignment and padding.
         * <p>在给定的可用空间内沿 X 轴定位子元素，考虑水平对齐和内边距。</p>
         */
        public void setX(int x, int availableSpace) {
            float least = settings.paddingLeft;
            float most = availableSpace - child.getWidth() - settings.paddingRight;
            int offset = Math.round(least + settings.xAlignment * (most - least));
            child.setX(x + offset);
        }

        /**
         * Position the child along the Y axis within the given available space,
         * respecting its vertical alignment and padding.
         * <p>在给定的可用空间内沿 Y 轴定位子元素，考虑垂直对齐和内边距。</p>
         */
        public void setY(int y, int availableSpace) {
            float least = settings.paddingTop;
            float most = availableSpace - child.getHeight() - settings.paddingBottom;
            int offset = Math.round(least + settings.yAlignment * (most - least));
            child.setY(y + offset);
        }
    }
}