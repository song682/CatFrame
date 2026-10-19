package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Style;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.components.events.GuiEventListener;
import decok.dfcdvadstf.catframe.ui.layouts.ILayout;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;

import javax.annotation.Nullable;

/**
 * <p>
 * Abstract base class for components — provides position, size, visibility and
 * other
 * common fields with default implementations, and implements the
 * {@link Renderable}
 * render entry (counterpart of the high-version
 * {@code AbstractWidget implements Renderable}).
 * </p>
 */
public abstract class AbstractComponent implements GuiEventListener, Renderable, ILayout {

    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean visible = true;
    protected boolean active = true;
    protected float alpha = 1.0F;
    protected boolean isHovered;
    protected boolean focused;
    private int tabOrderGroup;

    /**
     * Tooltip holder — counterpart of 26.1.2 {@code AbstractWidget#tooltip}.
     * Subclasses inherit tooltip capability; the delayed show is pumped during
     * rendering via {@link #updateHoverState(int, int)}.
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

    @Override
    public ScreenRectangle getRectangle() {
        return ScreenRectangle.of(this);
    }

    @Override
    public int getTabOrderGroup() {
        return this.tabOrderGroup;
    }

    public void setTabOrderGroup(final int tabOrderGroup) {
        this.tabOrderGroup = tabOrderGroup;
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
     * Sets this component's tooltip content.
     * <p>
     * Counterpart of 26.1.2 {@code AbstractWidget#setTooltip(Tooltip)}.
     * </p>
     */
    public void setTooltip(@Nullable Tooltip tooltip) {
        this.tooltip.set(tooltip);
    }

    /**
     * Gets this component's current tooltip content.
     */
    @Nullable
    public Tooltip getTooltip() {
        return this.tooltip.get();
    }

    /**
     * Sets the tooltip show delay (milliseconds).
     * <p>
     * Counterpart of 26.1.2 {@code AbstractWidget#setTooltipDelay(Duration)}.
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
     */
    protected void updateHoverState(int mouseX, int mouseY) {
        this.isHovered = isMouseOver(mouseX, mouseY);
        // Pumps the delayed tooltip show — mirrors vanilla driving the tooltip
        // inside extractRenderState. Actual drawing happens at frame end via the
        // Forge DrawScreenEvent.Post -> extractDeferredElements() pass.
        this.tooltip.refreshTooltipForNextRenderPass(mouseX, mouseY, this.isHovered, this.focused, getRectangle());
    }

    // ──── Rendering (Renderable) ────

    /**
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
     * Widget drawing hook — components should override this to draw themselves.
     * <p>
     * Driven uniformly by {@link #extractRenderState}: by the time this is
     * called the visibility check and hover-state refresh (including the tooltip
     * delay pump) have already run. Defaults to a no-op.
     * </p>
     */
    protected abstract void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks);

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
         * Counterpart of 26.1.2
         * {@code AbstractWidget.WithInactiveMessage#defaultInactiveMessage(Component)}.
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
