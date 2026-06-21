package decok.dfcdvadstf.catframe.ui.overlay;

import decok.dfcdvadstf.catframe.ui.components.Component;

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
public interface Overlay extends Component {

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
     * Called each tick to update overlay state.
     * <p>每 tick 调用一次以更新状态。</p>
     */
    default void update() {
    }
}
