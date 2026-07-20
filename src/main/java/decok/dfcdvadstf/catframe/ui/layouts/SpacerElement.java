package decok.dfcdvadstf.catframe.ui.layouts;

import java.util.function.Consumer;

/**
 * <p>
 * SpacerElement —— 一个不可见的占位元素，仅在布局中预留空间，对标高版本
 * Minecraft 的 {@code SpacerElement}。<br>
 * 它没有任何渲染内容，也不遍历出任何子控件；常用于在 {@link LinearLayout}、
 * {@link GridLayout} 等布局中制造固定间距或撑开空白区域。
 * </p>
 * <p>
 * SpacerElement — an invisible layout element that only reserves space,
 * counterpart of the high-version Minecraft {@code SpacerElement}. It renders
 * nothing and exposes no child widgets; use it to create fixed gaps or push
 * space open inside {@link LinearLayout}, {@link GridLayout}, etc.
 * </p>
 */
public class SpacerElement implements ILayout {

    private int x;
    private int y;
    private final int width;
    private final int height;

    public SpacerElement(int width, int height) {
        this(0, 0, width, height);
    }

    public SpacerElement(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Create a spacer that only reserves horizontal space.
     * <p>创建一个仅预留水平空间的占位元素。</p>
     */
    public static SpacerElement width(int width) {
        return new SpacerElement(width, 0);
    }

    /**
     * Create a spacer that only reserves vertical space.
     * <p>创建一个仅预留垂直空间的占位元素。</p>
     */
    public static SpacerElement height(int height) {
        return new SpacerElement(0, height);
    }

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

    /**
     * A spacer has no child widgets, so this is intentionally a no-op.
     * <p>占位元素没有子控件，因此此方法有意为空。</p>
     */
    @Override
    public void visitWidgets(Consumer<Object> widgetVisitor) {
    }
}
