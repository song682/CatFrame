package decok.dfcdvadstf.catframe.ui.navigation;

/**
 * ScreenArea — common interface for any render state that carries GUI bounds information.
 * <p>
 * Counterpart of the modern Minecraft {@code ScreenArea}, used by {@code GuiRenderState}
 * for automatic layering ({@code findAppropriateNode}): the intersection/containment
 * relationship of the bounds decides which level of the Node tree a new element belongs to.
 *
 * <p>All render state types added to {@code GuiRenderState} (ItemRenderState,
 * ElementRenderState, etc.) must implement this interface.</p>
 */
public interface ScreenArea {

    /**
     * Returns the bounding rectangle of this area on the screen.
     *
     * @return the bounds rectangle, or {@code null} if this area has no fixed bounds
     */
    ScreenRectangle bounds();
}
