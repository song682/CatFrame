package decok.dfcdvadstf.catframe.ui.layouts;

import java.util.function.Consumer;

/**
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
     */
    public static SpacerElement width(int width) {
        return new SpacerElement(width, 0);
    }

    /**
     * Create a spacer that only reserves vertical space.
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
     */
    @Override
    public void visitWidgets(Consumer<Object> widgetVisitor) {
    }
}
