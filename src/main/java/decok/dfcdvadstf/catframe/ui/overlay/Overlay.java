package decok.dfcdvadstf.catframe.ui.overlay;

import decok.dfcdvadstf.catframe.ui.components.events.GuiEventListener;

/**
 * <p>
 * Overlay interface — a screen overlay element registerable with {@link OverlayManager}.<br>
 * Supports anchor-based positioning, offsets, auto-stacking, and customisable texture/size.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * public class MyOverlay extends AbstractComponent implements Overlay {
 *     @Override public ScreenAnchor getAnchor() { return ScreenAnchor.TOP_RIGHT; }
 *     @Override public int getOffsetX() { return 4; }
 *     @Override public int getOffsetY() { return 4; }
 *     @Override public int getStackPriority() { return 0; }
 * }
 *
 * OverlayManager.INSTANCE.register(myOverlay);
 * }</pre>
 */
public interface Overlay extends GuiEventListener {

    /**
     * The render context deciding where {@link OverlayManager} draws this overlay
     * (screen, HUD, or both). Defaults to {@link OverlayContext#SCREEN} for backward
     * compatibility.
     */
    default OverlayContext getContext() {
        return OverlayContext.SCREEN;
    }

    /**
     * The anchor point on screen where this overlay is positioned.
     */
    ScreenAnchor getAnchor();

    /**
     * Horizontal pixel offset from the anchor point.
     */
    int getOffsetX();

    /**
     * Vertical pixel offset from the anchor point.
     */
    int getOffsetY();

    /**
     * Stacking priority within the same anchor. Lower values stack first (closer to anchor).
     */
    default int getStackPriority() {
        return 0;
    }

    /**
     * Whether this overlay blocks interaction with elements below it.
     */
    default boolean isBlocking() {
        return false;
    }

    /**
     * Whether this overlay requests the single-player world to pause while it is shown,
     * analogous to {@link net.minecraft.client.gui.GuiScreen#doesGuiPauseGame()}.
     * <p>
     * Only meaningful for {@link OverlayContext#SCREEN} overlays, which live on top of an open
     * GUI where pausing is the vanilla behaviour. A HUD overlay is by definition drawn while the
     * world keeps ticking; halting the world for a transient, hint-style HUD notification makes no
     * sense, so {@link OverlayManager} treats any HUD (or {@link OverlayContext#BOTH}) overlay that
     * returns {@code true} here as a programming error and throws.
     * </p>
     */
    default boolean isPausingGame() {
        return false;
    }

    /**
     * Called each tick to update overlay state.
     */
    default void update() {
    }
}
