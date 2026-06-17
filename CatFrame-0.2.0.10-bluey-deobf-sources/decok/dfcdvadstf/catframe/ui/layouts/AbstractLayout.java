package decok.dfcdvadstf.catframe.ui.layouts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base implementing the common fields and child management
 * for all {@link Layout}s.
 * <p>
 * Stores x / y / width / height / padding / spacing and a mutable child list.
 * Every mutator that affects layout calls {@link #recalculate()} automatically.
 * </p>
 *
 * <p>
 * 所有 {@link Layout} 的抽象基类，实现公共字段和子元素管理。
 * 存储 x/y/width/height/padding/spacing 和一个可变的子元素列表。
 * 任何影响布局的变更都会自动调用 {@link #recalculate()}。
 * </p>
 */
public abstract class AbstractLayout implements Layout {

    protected final List<ILayout> children = new ArrayList<>();
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

    @Override
    public void setX(int x) {
        this.x = x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public void setY(int y) {
        this.y = y;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    // ──── Padding / Spacing ────

    @Override
    public int getPadding() {
        return padding;
    }

    @Override
    public void setPadding(int padding) {
        this.padding = Math.max(0, padding);
        recalculate();
    }

    @Override
    public int getSpacing() {
        return spacing;
    }

    @Override
    public void setSpacing(int spacing) {
        this.spacing = Math.max(0, spacing);
        recalculate();
    }

    // ──── Child management ────

    @Override
    public Layout add(ILayout child) {
        if (child != null && !children.contains(child)) {
            children.add(child);
            recalculate();
        }
        return this;
    }

    @Override
    public Layout remove(ILayout child) {
        if (child != null && children.remove(child)) {
            recalculate();
        }
        return this;
    }

    @Override
    public void clear() {
        if (!children.isEmpty()) {
            children.clear();
            recalculate();
        }
    }

    @Override
    public List<ILayout> getChildren() {
        return Collections.unmodifiableList(children);
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
}