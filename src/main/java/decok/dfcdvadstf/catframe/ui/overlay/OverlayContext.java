package decok.dfcdvadstf.catframe.ui.overlay;

/**
 * <p>
 * Overlay 渲染上下文 —— 决定一个 {@link Overlay} 在何处被 {@link OverlayManager} 渲染。<br>
 * Overlay render context — decides where an {@link Overlay} is rendered by {@link OverlayManager}.
 * </p>
 *
 * <ul>
 *   <li>{@link #SCREEN} —— 仅在打开的 GUI 界面上渲染（由 Forge {@code DrawScreenEvent.Post} 驱动的
 *       {@code OverlayManager.renderAll}，对所有 {@code GuiScreen} 生效，含主菜单）。<br>
 *       Rendered only on top of an open GUI screen (via {@code renderAll}, driven by Forge's
 *       {@code DrawScreenEvent.Post} for every {@code GuiScreen}, main menu included).</li>
 *   <li>{@link #HUD} —— 仅在游戏内 HUD 渲染（由 Forge {@code RenderGameOverlayEvent} 驱动的
 *       {@code OverlayManager.renderHud}）。<br>
 *       Rendered only on the in-game HUD (via {@code renderHud}, driven by Forge's
 *       {@code RenderGameOverlayEvent}).</li>
 *   <li>{@link #BOTH} —— 两种上下文都渲染；界面打开时由屏幕路径接管，HUD 路径跳过，
 *       避免同帧双重绘制。<br>Rendered in both contexts; while a screen is open the screen
 *       pass takes over and the HUD pass skips it, so it is never drawn twice per frame.</li>
 * </ul>
 */
public enum OverlayContext {
    /** Screen-only overlay / 仅界面覆盖层 */
    SCREEN,
    /** In-game HUD-only overlay / 仅游戏内 HUD 覆盖层 */
    HUD,
    /** Rendered on both screens and the HUD / 界面与 HUD 均渲染 */
    BOTH
}
