package decok.dfcdvadstf.catframe.ui.overlay;

import decok.dfcdvadstf.catframe.ui.components.events.GuiEventListener;

/**
 * <p>
 * Overlay 接口 —— 可注册到 {@link OverlayManager} 的屏幕覆盖层元素。<br>
 * 支持锚点定位、偏移、自动堆叠和自定义纹理/大小。
 * </p>
 * <p>
 * Overlay interface — a screen overlay element registerable with {@link OverlayManager}.<br>
 * Supports anchor-based positioning, offsets, auto-stacking, and customisable texture/size.
 * </p>
 *
 * <h3>用法 / Usage</h3>
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
     * <p>决定 {@link OverlayManager} 在何处绘制此 Overlay 的渲染上下文（界面 / HUD / 两者）。
     * 为向后兼容，默认 {@link OverlayContext#SCREEN}。</p>
     */
    default OverlayContext getContext() {
        return OverlayContext.SCREEN;
    }

    /**
     * The anchor point on screen where this overlay is positioned.
     * <p>此 Overlay 在屏幕上的定位锚点。</p>
     */
    ScreenAnchor getAnchor();

    /**
     * Horizontal pixel offset from the anchor point.
     * <p>距锚点的水平像素偏移。</p>
     */
    int getOffsetX();

    /**
     * Vertical pixel offset from the anchor point.
     * <p>距锚点的垂直像素偏移。</p>
     */
    int getOffsetY();

    /**
     * Stacking priority within the same anchor. Lower values stack first (closer to anchor).
     * <p>同锚点内的堆叠优先级。值越小越靠近锚点。</p>
     */
    default int getStackPriority() {
        return 0;
    }

    /**
     * Whether this overlay blocks interaction with elements below it.
     * <p>此 Overlay 是否阻断下层交互。</p>
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
     * <p>此 Overlay 是否请求在显示期间暂停单人世界，语义对齐
     * {@link net.minecraft.client.gui.GuiScreen#doesGuiPauseGame()}。</p>
     * <p>仅对 {@link OverlayContext#SCREEN} 上下文有意义——它绘制在已打开的界面之上，暂停本就是原版行为。
     * HUD 上下文的 Overlay 在世界持续运行时绘制，为一条提示性质的信息而暂停世界毫无意义，因此
     * {@link OverlayManager} 会把在 HUD（或 {@link OverlayContext#BOTH}）下返回 {@code true} 的
     * Overlay 视为编码错误并抛出异常。</p>
     */
    default boolean isPausingGame() {
        return false;
    }

    /**
     * Called each tick to update overlay state.
     * <p>每 tick 调用一次以更新状态。</p>
     */
    default void update() {
    }
}
