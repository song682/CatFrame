package decok.dfcdvadstf.catframe.ui.components.toast;

import decok.dfcdvadstf.catframe.ui.components.events.GuiEventListener;
import net.minecraft.util.ResourceLocation;

/**
 * <p>
 * 高版本风格的 Toast 接口<br>
 * 现在继承 {@link GuiEventListener}，融入统一的组件体系。
 * </p>
 * <p>
 * High-version style Toast interface.<br>
 * Now extends {@link GuiEventListener}, integrated into the unified component system.
 * </p>
 *
 * <p>
 * <strong>BREAKING CHANGE 注意:</strong>
 * {@code render()} 签名已从 {@code render(FontRenderer, long)} 变更为
 * {@code render(int mouseX, int mouseY, float partialTicks)}（Toast 自身的渲染契约，
 * 与组件体系 {@code Renderable} 解耦 —— Toast 由 {@code ToastManager} 独立驱动渲染）。
 * {@code FontRenderer} 需要通过 {@code Minecraft.getMinecraft().fontRenderer} 获取。
 * </p>
 */
public interface Toast extends GuiEventListener {

    /** Default Toast width / 默认 Toast 宽度 */
    int DEFAULT_WIDTH = 160;

    /** Default slot height / 默认槽位高度 */
    int SLOT_HEIGHT = 32;

    /**
     * Get the desired visibility state.
     * <p>
     * 获取期望的可见性状态。
     * </p>
     */
    Visibility getWantedVisibility();

    /**
     * Update the Toast state.
     * <p>
     * 更新 Toast 状态。
     * </p>
     *
     * @param manager           Toast manager / Toast 管理器
     * @param fullyVisibleForMs fully visible duration in ms / 完全可见的持续时间(毫秒)
     */
    void update(ToastManager manager, long fullyVisibleForMs);

    /**
     * Render the Toast content.
     * <p>
     * 渲染 Toast 内容。
     * </p>
     */
    void render(int mouseX, int mouseY, float partialTicks);

    /**
     * Get the unique token for deduplication.
     * <p>
     * 获取唯一标识符(用于去重)。
     * </p>
     */
    default Object getToken() {
        return this;
    }

    /**
     * Sound played when this Toast slides in (visibility changes to
     * {@link Visibility#SHOW}).
     * Played through the {@code SoundHandler} directly, so it is safe in any render
     * context
     * (GUI, HUD, or the main menu) where {@code thePlayer} may be {@code null}.
     * <p>
     * Toast 滑入时播放的音效（可见性切换为 {@link Visibility#SHOW}）。
     * 通过 {@code SoundHandler} 直接播放，不依赖 {@code thePlayer}，
     * 因此在任意渲染上下文（GUI / HUD / 主菜单）中都安全。
     * </p>
     *
     * @return sound ResourceLocation, or {@code null} for silence /
     *         音效资源位置，{@code null} 表示静音
     */
    default ResourceLocation getShowSound() {
        return null;
    }

    /**
     * Sound played when this Toast slides out (visibility changes to
     * {@link Visibility#HIDE}).
     * <p>
     * Toast 滑出时播放的音效（可见性切换为 {@link Visibility#HIDE}）。
     * </p>
     *
     * @return sound ResourceLocation, or {@code null} for silence /
     *         音效资源位置，{@code null} 表示静音
     */
    default ResourceLocation getHideSound() {
        return null;
    }

    /**
     * The screen corner this Toast slides in from and stacks at.
     * <p>
     * 此 Toast 滑入并堆叠所在的屏幕角落。
     * </p>
     *
     * @return the corner, never null; defaults to {@link ToastCorner#TOP_RIGHT}
     *         / 角落，不为 null；默认 {@link ToastCorner#TOP_RIGHT}
     */
    default ToastCorner getCorner() {
        return ToastCorner.TOP_RIGHT;
    }

    /**
     * Get the Toast width.
     * <p>
     * 获取 Toast 宽度。
     * </p>
     */
    default int width() {
        return getWidth();
    }

    /**
     * Get the Toast height.
     * <p>
     * 获取 Toast 高度。
     * </p>
     */
    default int height() {
        return getHeight();
    }

    /**
     * Get the number of occupied slot count.
     * <p>
     * 获取占用的槽位数量。
     * </p>
     */
    default int occupiedSlotCount() {
        return (int) Math.ceil((double) height() / SLOT_HEIGHT);
    }

    /**
     * Calculate the X position (with slide-in animation).
     * Right corners slide in from the right edge, left corners from the left edge,
     * as decided by {@link #getCorner()}.
     * <p>
     * 计算 X 坐标(考虑滑入动画)。右侧角落从右缘滑入，左侧角落从左缘滑入，
     * 由 {@link #getCorner()} 决定。
     * </p>
     *
     * @param screenWidth    screen width / 屏幕宽度
     * @param visiblePortion visible portion (0.0-1.0) / 可见比例(0.0-1.0)
     */
    default float xPos(int screenWidth, float visiblePortion) {
        return getCorner().isRight()
                ? screenWidth - width() * visiblePortion
                : width() * (visiblePortion - 1.0F);
    }

    /**
     * Calculate the Y position.
     * Top corners stack slots downward from the top edge, bottom corners upward
     * from
     * the bottom edge, as decided by {@link #getCorner()}.
     * <p>
     * 计算 Y 坐标。顶部角落的槽位从上缘向下堆叠，底部角落从下缘向上堆叠，
     * 由 {@link #getCorner()} 决定。
     * </p>
     *
     * @param screenHeight   screen height / 屏幕高度
     * @param firstSlotIndex starting slot index / 起始槽位索引
     */
    default float yPos(int screenHeight, int firstSlotIndex) {
        float slotOffset = firstSlotIndex * height();
        return getCorner().isBottom()
                ? screenHeight - height() - slotOffset
                : slotOffset;
    }

    /**
     * Callback when rendering is finished.
     * <p>
     * 渲染完成时回调。
     * </p>
     */
    default void onFinishedRendering() {
    }

    /** Toast visibility enum / Toast 可见性枚举 */
    enum Visibility {
        SHOW,
        HIDE
    }
}
