package decok.dfcdvadstf.catframe.ui.components.toast;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.components.AbstractComponent;
import decok.dfcdvadstf.catframe.ui.overlay.Overlay;
import decok.dfcdvadstf.catframe.ui.overlay.OverlayContext;
import decok.dfcdvadstf.catframe.ui.overlay.OverlayManager;
import decok.dfcdvadstf.catframe.ui.overlay.ScreenAnchor;
import net.minecraft.client.Minecraft;

/**
 * <p>
 * Toast overlay — plugs the whole Toast system into {@link OverlayManager} as a single
 * {@link OverlayContext#BOTH} {@link Overlay}, replacing the previous standalone render
 * path hooked directly onto {@code RenderGameOverlayEvent}.
 * </p>
 *
 * <h3>Render contexts</h3>
 * <p>
 * The {@code BOTH} context puts Toasts on two render paths: the in-game HUD
 * ({@code RenderGameOverlayEvent.Post} → {@code renderHud}) and any open GUI screen
 * (Forge's {@code DrawScreenEvent.Post} → {@code renderAll}, which also covers the vanilla
 * main menu). While a screen is open, {@link OverlayManager} skips the HUD pass for
 * {@code BOTH} overlays so nothing is drawn twice in the same frame.
 * </p>
 *
 * <h3>Layout note</h3>
 * <p>
 * {@link ToastManager} performs its own four-corner slot layout (each Toast picks its
 * slide-in corner via {@link ToastCorner}, defaulting to top-right; the slide animation
 * depends on screen size), so this overlay reports a 0×0 size — the resolved anchor
 * position is intentionally ignored and positioning stays fully owned by
 * {@link ToastManager}.
 * </p>
 */
public class ToastOverlay extends AbstractComponent implements Overlay {

    /** Singleton instance */
    public static final ToastOverlay INSTANCE = new ToastOverlay();

    /** The wrapped global Toast manager */
    private final ToastManager toastManager = new ToastManager(Minecraft.getMinecraft());

    private ToastOverlay() {
        super(0, 0, 0, 0);
    }

    /**
     * @return The global ToastManager
     */
    public ToastManager getToastManager() {
        return toastManager;
    }

    // ──── Overlay contract ────

    @Override
    public OverlayContext getContext() {
        return OverlayContext.BOTH;
    }

    @Override
    public ScreenAnchor getAnchor() {
        return ScreenAnchor.TOP_RIGHT;
    }

    @Override
    public int getOffsetX() {
        return 0;
    }

    @Override
    public int getOffsetY() {
        return 0;
    }

    // ──── Rendering ────

    /**
     * Advance and draw the Toast queue. State is updated here (per frame, not per tick)
     * to keep the millisecond-based slide animation smooth, mirroring how vanilla's
     * {@code GuiToast.draw()} updates inline.
     */
    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        toastManager.update();
        toastManager.render();
    }
}
