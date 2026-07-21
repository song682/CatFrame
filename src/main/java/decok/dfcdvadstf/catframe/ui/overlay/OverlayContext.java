package decok.dfcdvadstf.catframe.ui.overlay;

/**
 * <p>
 * Overlay 渲染上下文 —— 决定一个 {@link Overlay} 在何处被 {@link OverlayManager} 渲染。<br>
 * Overlay render context — decides where an {@link Overlay} is rendered by {@link OverlayManager}.
 * </p>
 *
 * <ul>
 *   <li>{@link #SCREEN} —— 仅在打开的 GUI 界面上渲染（由界面的 {@code drawScreen} 调用
 *       {@code OverlayManager.renderAll}）。<br>
 *       Rendered only on top of an open GUI screen (via {@code renderAll} from {@code drawScreen}).</li>
 *   <li>{@link #HUD} —— 仅在游戏内 HUD 渲染（由 Forge {@code RenderGameOverlayEvent} 驱动的
 *       {@code OverlayManager.renderHud}）。<br>
 *       Rendered only on the in-game HUD (via {@code renderHud}, driven by Forge's
 *       {@code RenderGameOverlayEvent}).</li>
 *   <li>{@link #BOTH} —— 两种上下文都渲染。<br>Rendered in both contexts.</li>
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
