package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Style;
import decok.dfcdvadstf.catframe.ui.Text;

import javax.annotation.Nullable;

/**
 * <p>
 * 组件抽象基类 —— 提供位置、尺寸、可见性等公共字段和默认实现，
 * 并实现 {@link Renderable} 渲染入口（对标高版本
 * {@code AbstractWidget implements Renderable}）。<br>
 * Abstract base class for components — provides position, size, visibility and
 * other
 * common fields with default implementations, and implements the
 * {@link Renderable}
 * render entry (counterpart of the high-version
 * {@code AbstractWidget implements Renderable}).
 * </p>
 */
public abstract class AbstractComponent implements Component, Renderable, TabOrderedElement {

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
     * <p>
     * 对标 26.1.2 {@code AbstractWidget#setTooltip(Tooltip)}。
     * </p>
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
     * <p>
     * 对标 26.1.2 {@code AbstractWidget#setTooltipDelay(Duration)}。
     * </p>
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
     * <p>
     * 根据鼠标位置更新悬停状态。在渲染前调用。
     * </p>
     */
    protected void updateHoverState(int mouseX, int mouseY) {
        this.isHovered = isMouseOver(mouseX, mouseY);
        // 泵动 tooltip 延迟显示 —— 对标 vanilla 在 extractRenderState 内驱动 tooltip。
        // 实际绘制由帧末 Forge DrawScreenEvent.Post -> extractDeferredElements() 完成。
        this.tooltip.refreshTooltipForNextRenderPass(mouseX, mouseY, this.isHovered, this.focused, getRectangle());
    }

    // ──── Rendering (Renderable) ────

    /**
     * 渲染入口 —— 对标高版本 {@code Renderable.extractRenderState}。<br>
     * 模板方法：不可见时直接跳过；刷新悬停状态后委托 {@link #renderWidget} 完成实际绘制。
     * <p>
     * Render entry point — counterpart of the high-version
     * {@code Renderable.extractRenderState}. Template method: skips when invisible,
     * refreshes hover state, then delegates the actual drawing to
     * {@link #renderWidget}.
     * </p>
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        if (!visible)
            return;
        updateHoverState(mouseX, mouseY);
        renderWidget(graphics, mouseX, mouseY, partialTicks);
    }

    /**
     * 子类绘制钩子 —— 新组件应覆盖此方法实现具体绘制。<br>
     * Widget drawing hook — new components should override this to draw themselves.
     * <p>
     * 默认桥接到旧 {@link #render} 入口，保证既有子类（Button、TabButton 等）
     * 无需改动即可继续渲染。
     * </p>
     */
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        render(mouseX, mouseY, partialTicks);
    }

    /**
     * 旧渲染入口 —— 仅保留给既有子类；新组件请覆盖 {@link #renderWidget}。<br>
     * Legacy render entry — kept only for existing subclasses; new components
     * should override {@link #renderWidget} instead.
     */
    protected void render(int mouseX, int mouseY, float partialTicks) {
    }

    // ──── Equals / Hash ────

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof AbstractComponent))
            return false;
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

    public abstract static class WithInactiveMessage extends AbstractComponent {

        private Text message;
        private Text inactiveMessage;

        /**
         * Creates the default grayed-out inactive message.
         * <p>
         * 创建默认的灰化失效消息。对标 26.1.2
         * {@code AbstractWidget.WithInactiveMessage#defaultInactiveMessage(Component)}。
         * </p>
         */
        public static Text defaultInactiveMessage(final Text activeMessage) {
            return activeMessage.withStyleApplied(Style.EMPTY.withColor(-6250336));
        }

        public WithInactiveMessage() {
        }

        public WithInactiveMessage(int x, int y, int width, int height, Text message) {
            super(x, y, width, height);
            this.message = message;
            this.inactiveMessage = defaultInactiveMessage(message);
        }

        public Text getMessage() {
            return this.active ? this.message : this.inactiveMessage;
        }

        public void setMessage(final Text message) {
            this.message = message;
            this.inactiveMessage = defaultInactiveMessage(message);
        }
    }
}
