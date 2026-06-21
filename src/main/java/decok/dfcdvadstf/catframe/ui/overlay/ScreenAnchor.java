package decok.dfcdvadstf.catframe.ui.overlay;

/**
 * <p>
 * 屏幕锚点枚举 —— 定义 Overlay 在屏幕上的定位基准点。<br>
 * Screen anchor enum — defines the anchor point for Overlay positioning on screen.
 * </p>
 */
public enum ScreenAnchor {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    CENTER_LEFT,
    CENTER,
    CENTER_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT;

    /**
     * Resolve the X coordinate for an element at this anchor.
     * <p>解析此锚点处元素的 X 坐标。</p>
     *
     * @param screenWidth  screen width / 屏幕宽度
     * @param elementWidth element width / 元素宽度
     * @param offset       pixel offset from anchor / 像素偏移
     * @return the X coordinate / X 坐标
     */
    public int resolveX(int screenWidth, int elementWidth, int offset) {
        switch (this) {
            case TOP_LEFT:
            case CENTER_LEFT:
            case BOTTOM_LEFT:
                return offset;
            case TOP_CENTER:
            case CENTER:
            case BOTTOM_CENTER:
                return (screenWidth - elementWidth) / 2 + offset;
            case TOP_RIGHT:
            case CENTER_RIGHT:
            case BOTTOM_RIGHT:
                return screenWidth - elementWidth - offset;
            default:
                return offset;
        }
    }

    /**
     * Resolve the Y coordinate for an element at this anchor.
     * <p>解析此锚点处元素的 Y 坐标。</p>
     *
     * @param screenHeight screen height / 屏幕高度
     * @param elementHeight element height / 元素高度
     * @param offset       pixel offset from anchor / 像素偏移
     * @return the Y coordinate / Y 坐标
     */
    public int resolveY(int screenHeight, int elementHeight, int offset) {
        switch (this) {
            case TOP_LEFT:
            case TOP_CENTER:
            case TOP_RIGHT:
                return offset;
            case CENTER_LEFT:
            case CENTER:
            case CENTER_RIGHT:
                return (screenHeight - elementHeight) / 2 + offset;
            case BOTTOM_LEFT:
            case BOTTOM_CENTER:
            case BOTTOM_RIGHT:
                return screenHeight - elementHeight - offset;
            default:
                return offset;
        }
    }

    /**
     * Whether stacking at this anchor goes downward (true) or upward (false).
     * <p>此锚点的堆叠方向：true = 向下堆叠，false = 向上堆叠。</p>
     */
    public boolean stacksDownward() {
        switch (this) {
            case BOTTOM_LEFT:
            case BOTTOM_CENTER:
            case BOTTOM_RIGHT:
                return false;
            default:
                return true;
        }
    }
}
