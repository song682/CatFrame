package decok.dfcdvadstf.catframe.ui.components.toast;

import decok.dfcdvadstf.catframe.ui.components.Component;

/**
 * <p>
 * 高版本风格的 Toast 接口<br>
 * 现在继承 {@link Component}，融入统一的组件体系。
 * </p>
 * <p>
 * High-version style Toast interface.<br>
 * Now extends {@link Component}, integrated into the unified component system.
 * </p>
 *
 * <p><strong>BREAKING CHANGE 注意:</strong>
 * {@code render()} 签名已从 {@code render(FontRenderer, long)} 变更为
 * {@code render(int mouseX, int mouseY, float partialTicks)}（继承自 {@link Component}）。
 * {@code FontRenderer} 需要通过 {@code Minecraft.getMinecraft().fontRenderer} 获取。
 * </p>
 */
public interface Toast extends Component {

    /** Default Toast width / 默认 Toast 宽度 */
    int DEFAULT_WIDTH = 160;

    /** Default slot height / 默认槽位高度 */
    int SLOT_HEIGHT = 32;

    /**
     * Get the desired visibility state.
     * <p>获取期望的可见性状态。</p>
     */
    Visibility getWantedVisibility();

    /**
     * Update the Toast state.
     * <p>更新 Toast 状态。</p>
     *
     * @param manager            Toast manager / Toast 管理器
     * @param fullyVisibleForMs  fully visible duration in ms / 完全可见的持续时间(毫秒)
     */
    void update(ToastManager manager, long fullyVisibleForMs);

    /**
     * Render the Toast content.
     * <p>渲染 Toast 内容。</p>
     */
    @Override
    void render(int mouseX, int mouseY, float partialTicks);

    /**
     * Get the unique token for deduplication.
     * <p>获取唯一标识符(用于去重)。</p>
     */
    default Object getToken() {
        return this;
    }

    /**
     * Get the Toast width.
     * <p>获取 Toast 宽度。</p>
     */
    default int width() {
        return getWidth();
    }

    /**
     * Get the Toast height.
     * <p>获取 Toast 高度。</p>
     */
    default int height() {
        return getHeight();
    }

    /**
     * Get the number of occupied slot count.
     * <p>获取占用的槽位数量。</p>
     */
    default int occupiedSlotCount() {
        return (int) Math.ceil((double) height() / SLOT_HEIGHT);
    }

    /**
     * Calculate the X position (with slide-in animation).
     * <p>计算 X 坐标(考虑滑入动画)。</p>
     *
     * @param screenWidth   screen width / 屏幕宽度
     * @param visiblePortion visible portion (0.0-1.0) / 可见比例(0.0-1.0)
     */
    default float xPos(int screenWidth, float visiblePortion) {
        return screenWidth - width() * visiblePortion;
    }

    /**
     * Calculate the Y position.
     * <p>计算 Y 坐标。</p>
     *
     * @param firstSlotIndex starting slot index / 起始槽位索引
     */
    default float yPos(int firstSlotIndex) {
        return firstSlotIndex * height();
    }

    /**
     * Callback when rendering is finished.
     * <p>渲染完成时回调。</p>
     */
    default void onFinishedRendering() {
    }

    /** Toast visibility enum / Toast 可见性枚举 */
    enum Visibility {
        SHOW,
        HIDE
    }
}
