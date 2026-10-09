package decok.dfcdvadstf.catframe.ui.layouts;

import java.util.List;
import java.util.function.Consumer;

/**
 * Layout — the core interface for any layout that arranges child elements.
 * <p>
 * Extends {@link ILayout} with child management, padding, spacing,
 * recalculation, and optional drawing (background, separators, etc.).
 * </p>
 *
 */
public interface Layout extends ILayout {

    // ──── Child management ────

    /**
     * Adds a child element.
     */
    Layout add(ILayout child);

    /**
     * Removes a child element.
     */
    Layout remove(ILayout child);

    /**
     * Removes all children.
     */
    void clear();

    /**
     * Returns an unmodifiable view of the children.
     */
    List<ILayout> getChildren();

    /**
     * Visit all direct children.
     */
    void visitChildren(Consumer<ILayout> visitor);

    // ──── Layout logic ────

    /**
     * Recalculate the position of each child based on this layout's
     * current x, y, width, height, padding and spacing.
     */
    void recalculate();

    /**
     * Arrange elements recursively. First arranges child layouts, then recalculates self.
     */
    default void arrangeElements() {
        visitChildren(child -> {
            if (child instanceof Layout) {
                ((Layout) child).arrangeElements();
            }
        });
        recalculate();
    }

    /**
     * Draw the layout background / decoration.
     * Default implementation is a no-op — override in layouts that have
     * a visual container (e.g. {@code HeaderFooterLayout}).
     */
    default void draw(int mouseX, int mouseY, float partialTicks) {
    }
}
