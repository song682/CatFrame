package decok.dfcdvadstf.catframe.ui.components.toast;

import decok.dfcdvadstf.catframe.ui.components.AbstractComponent;
import decok.dfcdvadstf.catframe.ui.overlay.Overlay;
import decok.dfcdvadstf.catframe.ui.overlay.OverlayContext;
import decok.dfcdvadstf.catframe.ui.overlay.OverlayManager;
import decok.dfcdvadstf.catframe.ui.overlay.ScreenAnchor;
import net.minecraft.client.Minecraft;

/**
 * <p>
 * Toast 覆盖层 —— 将整个 Toast 系统作为一个 {@link OverlayContext#BOTH} 上下文的
 * {@link Overlay} 接入 {@link OverlayManager}，取代原先直接挂在
 * {@code RenderGameOverlayEvent} 上的独立渲染路径。
 * </p>
 * <p>
 * Toast overlay — plugs the whole Toast system into {@link OverlayManager} as a single
 * {@link OverlayContext#BOTH} {@link Overlay}, replacing the previous standalone render
 * path hooked directly onto {@code RenderGameOverlayEvent}.
 * </p>
 *
 * <h3>渲染上下文 / Render contexts</h3>
 * <p>
 * {@code BOTH} 上下文意味着 Toast 同时出现在两条渲染路径上：游戏内 HUD
 * （{@code RenderGameOverlayEvent.Post} → {@code renderHud}）以及任意打开的 GUI 界面
 * （Forge {@code DrawScreenEvent.Post} → {@code renderAll}，含原版主菜单 {@code GuiMainMenu}）。
 * 界面打开时 {@link OverlayManager} 会自动跳过 HUD 那一趟，避免同帧双重绘制。
 * </p>
 * <p>
 * The {@code BOTH} context puts Toasts on two render paths: the in-game HUD
 * ({@code RenderGameOverlayEvent.Post} → {@code renderHud}) and any open GUI screen
 * (Forge's {@code DrawScreenEvent.Post} → {@code renderAll}, which also covers the vanilla
 * main menu). While a screen is open, {@link OverlayManager} skips the HUD pass for
 * {@code BOTH} overlays so nothing is drawn twice in the same frame.
 * </p>
 *
 * <h3>布局说明 / Layout note</h3>
 * <p>
 * {@link ToastManager} 自带右上角槽位布局（滑入动画 X 坐标依赖屏幕宽度），因此本覆盖层
 * 向 {@link OverlayManager} 报告 0×0 尺寸——锚点解析结果被有意忽略，实际定位完全由
 * {@link ToastManager} 负责。
 * </p>
 * <p>
 * {@link ToastManager} performs its own top-right slot layout (the slide-in X depends on
 * screen width), so this overlay reports a 0×0 size — the resolved anchor position is
 * intentionally ignored and positioning stays fully owned by {@link ToastManager}.
 * </p>
 */
public class ToastOverlay extends AbstractComponent implements Overlay {

    /** Singleton instance / 单例实例 */
    public static final ToastOverlay INSTANCE = new ToastOverlay();

    /** The wrapped global Toast manager / 被包装的全局 Toast 管理器 */
    private final ToastManager toastManager = new ToastManager(Minecraft.getMinecraft());

    private ToastOverlay() {
        super(0, 0, 0, 0);
    }

    /**
     * @return The global ToastManager / 全局 Toast 管理器
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
     * <p>推进并绘制 Toast 队列。状态在此按帧（而非按 tick）更新，
     * 以保证基于毫秒的滑动动画流畅——与原版 {@code GuiToast.draw()} 内联更新的做法一致。</p>
     */
    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        toastManager.update();
        toastManager.render();
    }
}
