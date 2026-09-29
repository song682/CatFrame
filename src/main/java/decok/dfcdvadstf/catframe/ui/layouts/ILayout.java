package decok.dfcdvadstf.catframe.ui.layouts;

import java.util.function.Consumer;

/**
 * Base interface for any positioned element in the layout system.
 */
public interface ILayout {

    int getX();

    void setX(int x);

    int getY();

    void setY(int y);

    int getWidth();

    int getHeight();

    default void setPosition(final int x, final int y) {
        this.setX(x);
        this.setY(y);
    }

    /**
     * Visit all "leaf" widgets inside this element (no-op by default).
     */
    default void visitWidgets(final Consumer<Object> widgetVisitor) {
    }
}
