package decok.dfcdvadstf.catframe.ui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * Overlay 管理器 —— 管理屏幕覆盖层的注册、定位、自动堆叠和渲染。<br>
 * 参考 {@code ToastManager} 的槽位管理架构。
 * </p>
 * <p>
 * Overlay Manager — manages registration, positioning, auto-stacking, and rendering
 * of screen overlays. Inspired by the {@code ToastManager} slot architecture.
 * </p>
 *
 * <h3>自动堆叠 / Auto-stacking</h3>
 * <p>
 * 同一锚点的多个 Overlay 按 {@link Overlay#getStackPriority()} 排序，
 * 沿 Y 轴自动堆叠。TOP 系列锚点向下堆叠，BOTTOM 系列锚点向上堆叠。
 * </p>
 */
public class OverlayManager {

    /** Singleton instance / 单例实例 */
    public static final OverlayManager INSTANCE = new OverlayManager();

    /** Spacing between stacked overlays / 堆叠 Overlay 之间的间距 */
    private static final int STACK_SPACING = 2;

    private final Map<ScreenAnchor, List<Overlay>> overlays = new EnumMap<>(ScreenAnchor.class);

    private OverlayManager() {
        for (ScreenAnchor anchor : ScreenAnchor.values()) {
            overlays.put(anchor, new ArrayList<Overlay>());
        }
    }

    // ──── Registration ────

    /**
     * Register an overlay.
     * <p>注册一个 Overlay。</p>
     */
    public void register(Overlay overlay) {
        if (overlay == null) return;
        List<Overlay> list = overlays.get(overlay.getAnchor());
        if (!list.contains(overlay)) {
            list.add(overlay);
            sortAnchorList(list);
        }
    }

    /**
     * Unregister an overlay.
     * <p>注销一个 Overlay。</p>
     */
    public void unregister(Overlay overlay) {
        if (overlay == null) return;
        overlays.get(overlay.getAnchor()).remove(overlay);
    }

    /**
     * Remove all overlays.
     * <p>移除所有 Overlay。</p>
     */
    public void clearAll() {
        for (List<Overlay> list : overlays.values()) {
            list.clear();
        }
    }

    /**
     * Remove all overlays at the given anchor.
     * <p>移除指定锚点的所有 Overlay。</p>
     */
    public void clearAnchor(ScreenAnchor anchor) {
        overlays.get(anchor).clear();
    }

    // ──── Query ────

    /**
     * Get all registered overlays at the given anchor.
     * <p>获取指定锚点的所有已注册 Overlay。</p>
     */
    public List<Overlay> getOverlays(ScreenAnchor anchor) {
        return Collections.unmodifiableList(overlays.get(anchor));
    }

    /**
     * Get total count of all registered overlays.
     * <p>获取所有已注册 Overlay 的总数。</p>
     */
    public int getOverlayCount() {
        int count = 0;
        for (List<Overlay> list : overlays.values()) {
            count += list.size();
        }
        return count;
    }

    // ──── Update ────

    /**
     * Update all visible overlays. Call once per tick.
     * <p>更新所有可见 Overlay。每 tick 调用一次。</p>
     */
    public void updateAll() {
        for (List<Overlay> list : overlays.values()) {
            for (Overlay overlay : list) {
                if (overlay.isVisible()) {
                    overlay.update();
                }
            }
        }
    }

    // ──── Rendering ────

    /**
     * Render all visible overlays. Call from the screen's drawScreen method.
     * <p>渲染所有可见 Overlay。从屏幕的 drawScreen 方法中调用。</p>
     *
     * @param mouseX       mouse X / 鼠标 X
     * @param mouseY       mouse Y / 鼠标 Y
     * @param partialTicks partial tick / 部分 tick
     */
    public void renderAll(int mouseX, int mouseY, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int screenWidth = sr.getScaledWidth();
        int screenHeight = sr.getScaledHeight();

        for (Map.Entry<ScreenAnchor, List<Overlay>> entry : overlays.entrySet()) {
            ScreenAnchor anchor = entry.getKey();
            List<Overlay> list = entry.getValue();
            if (list.isEmpty()) continue;

            int stackOffsetY = 0;
            for (Overlay overlay : list) {
                if (!overlay.isVisible()) continue;

                int resolvedX = anchor.resolveX(screenWidth, overlay.getWidth(), overlay.getOffsetX());
                int resolvedY = anchor.resolveY(screenHeight, overlay.getHeight(), overlay.getOffsetY());

                // Apply stacking offset
                if (anchor.stacksDownward()) {
                    resolvedY += stackOffsetY;
                } else {
                    resolvedY -= stackOffsetY;
                }

                // Temporarily set position for rendering
                int oldX = overlay.getX();
                int oldY = overlay.getY();
                overlay.setX(resolvedX);
                overlay.setY(resolvedY);

                overlay.render(mouseX, mouseY, partialTicks);

                // Restore original position
                overlay.setX(oldX);
                overlay.setY(oldY);

                // Accumulate stack offset
                stackOffsetY += overlay.getHeight() + STACK_SPACING;
            }
        }
    }

    // ──── Input forwarding ────

    /**
     * Forward mouse click to overlays. Returns true if a blocking overlay consumed it.
     * <p>将鼠标点击转发给 Overlay。如果阻断型 Overlay 消费了事件则返回 true。</p>
     */
    public boolean handleMouseClick(int mouseX, int mouseY, int mouseButton) {
        for (List<Overlay> list : overlays.values()) {
            for (Overlay overlay : list) {
                if (overlay.isVisible() && overlay.isBlocking()
                        && overlay.isMouseOver(mouseX, mouseY)) {
                    overlay.mouseClicked(mouseX, mouseY, mouseButton);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Forward key press to overlays. Returns true if a blocking overlay consumed it.
     * <p>将按键转发给 Overlay。如果阻断型 Overlay 消费了事件则返回 true。</p>
     */
    public boolean handleKeyPress(char typedChar, int keyCode) {
        for (List<Overlay> list : overlays.values()) {
            for (Overlay overlay : list) {
                if (overlay.isVisible() && overlay.isBlocking()) {
                    overlay.keyTyped(typedChar, keyCode);
                    return true;
                }
            }
        }
        return false;
    }

    // ──── Internal ────

    private void sortAnchorList(List<Overlay> list) {
        Collections.sort(list, new Comparator<Overlay>() {
            @Override
            public int compare(Overlay a, Overlay b) {
                return Integer.compare(a.getStackPriority(), b.getStackPriority());
            }
        });
    }
}
