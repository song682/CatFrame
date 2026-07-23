package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.components.tooltip.Tooltip;
import decok.dfcdvadstf.catframe.ui.components.tooltip.WidgetTooltipHolder;

import javax.annotation.Nullable;

/**
 * <p>
 * 组件抽象基类 —— 提供位置、尺寸、可见性等公共字段和默认实现。<br>
 * Abstract base class for components — provides position, size, visibility and other
 * common fields with default implementations.
 * </p>
 */
public abstract class AbstractComponent implements Component, TabOrderedElement {

    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean visible = true;
    protected boolean active = true;
    protected float alpha = 1.0F;
    protected boolean isHovered;
    protected boolean focused;

    /**
     * 工具提示托管器 —— 对标 26.1.2 {@code AbstractWidget#tooltip}。
     * 子类继承即得 tooltip 能力，渲染时经 {@link #updateHoverState(int, int)} 泵动延迟显示。
     */
    private final WidgetTooltipHolder tooltip = new WidgetTooltipHolder();

    // ──── Constructors ────

    public AbstractComponent() {
    }

    public AbstractComponent(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    // ──── Position / Size ────

    @Override
    public int getX() {
        return x;
    }

    @Override
    public void setX(int x) {
        this.x = x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public void setY(int y) {
        this.y = y;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    // ──── Visibility / Active ────

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }

    // ──── Alpha ────

    public float getAlpha() {
        return alpha;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    // ──── Hover ────

    public boolean isHovered() {
        return isHovered;
    }

    // ──── Tooltip ────

    /**
     * 设置该组件的工具提示内容。
     * <p>对标 26.1.2 {@code AbstractWidget#setTooltip(Tooltip)}。</p>
     */
    public void setTooltip(@Nullable Tooltip tooltip) {
        this.tooltip.set(tooltip);
    }

    /**
     * 获取该组件当前的工具提示内容。
     */
    @Nullable
    public Tooltip getTooltip() {
        return this.tooltip.get();
    }

    /**
     * 设置工具提示的显示延迟（毫秒）。
     * <p>对标 26.1.2 {@code AbstractWidget#setTooltipDelay(Duration)}。</p>
     */
    public void setTooltipDelay(long delayMs) {
        this.tooltip.setDelay(delayMs);
    }

    // ──── Focus ────

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    // ──── Convenience ────

    /**
     * Update hover state based on mouse position. Call before rendering.
     * <p>根据鼠标位置更新悬停状态。在渲染前调用。</p>
     */
    protected void updateHoverState(int mouseX, int mouseY) {
        this.isHovered = isMouseOver(mouseX, mouseY);
        // 泵动 tooltip 延迟显示 —— 对标 vanilla 在 extractRenderState 内驱动 tooltip。
        // 实际绘制由帧末 Forge DrawScreenEvent.Post -> extractDeferredElements() 完成。
        this.tooltip.refreshTooltipForNextRenderPass(mouseX, mouseY, this.isHovered, this.focused, getRectangle());
    }

    // ──── Equals / Hash ────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractComponent)) return false;
        AbstractComponent that = (AbstractComponent) o;
        return x == that.x && y == that.y && width == that.width && height == that.height;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        result = 31 * result + width;
        result = 31 * result + height;
        return result;
    }
}
