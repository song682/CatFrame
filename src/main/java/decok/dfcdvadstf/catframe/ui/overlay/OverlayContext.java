package decok.dfcdvadstf.catframe.ui.overlay;

/**
 * <p>
 * Overlay render context — decides where an {@link Overlay} is rendered by {@link OverlayManager}.
 * </p>
 *
 * <ul>
 *   <li>{@link #SCREEN} — Rendered only on top of an open GUI screen (via {@code renderAll}, driven by Forge's
 *       {@code DrawScreenEvent.Post} for every {@code GuiScreen}, main menu included).</li>
 *   <li>{@link #HUD} — Rendered only on the in-game HUD (via {@code renderHud}, driven by Forge's
 *       {@code RenderGameOverlayEvent}).</li>
 *   <li>{@link #BOTH} — Rendered in both contexts; while a screen is open the screen
 *       pass takes over and the HUD pass skips it, so it is never drawn twice per frame.</li>
 * </ul>
 */
public enum OverlayContext {
    /** Screen-only overlay */
    SCREEN,
    /** In-game HUD-only overlay */
    HUD,
    /** Rendered on both screens and the HUD */
    BOTH
}
