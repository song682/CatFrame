package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * <p>
 * Scroll panel base — provides scroll offset, scrollbar rendering and Scissor
 * clipping. Counterpart of the high-version Minecraft
 * {@code AbstractScrollArea}.
 * </p>
 */
public abstract class AbstractScrollArea extends AbstractComponent {

    /** Scrollbar width constant */
    public static final int SCROLLBAR_WIDTH = 6;

    /** Scroller (thumb) texture — 6x32, border 1 nine-patch */
    protected static final ResourceLocation SCROLLER_TEXTURE = new ResourceLocation("catframe",
            "textures/gui/widgets/scroll.png");

    /**
     * Scrollbar background track texture — 6x32, border 1 nine-patch
     */
    protected static final ResourceLocation SCROLLER_BACKGROUND_TEXTURE = new ResourceLocation("catframe",
            "textures/gui/widgets/scroll_background.png");

    /** Scrollbar texture dimensions */
    protected static final int SCROLLBAR_TEX_W = 6;
    protected static final int SCROLLBAR_TEX_H = 32;
    protected static final int SCROLLBAR_TEX_BORDER = 1;

    private final ScrollbarSettings scrollbarSettings;
    private double scrollAmount;
    private boolean scrolling;

    /**
     * Last mouse Y during scrollbar dragging — used to compute the relative
     * movement
     * delta (dy), since the GuiEventListener interface only provides absolute
     * coords.
     */
    private double lastDragY;

    public AbstractScrollArea(int x, int y, int width, int height, ScrollbarSettings scrollbarSettings) {
        super(x, y, width, height);
        this.scrollbarSettings = scrollbarSettings;
    }

    // ──── Scroll amount ────

    /**
     * @return current scroll offset in pixels
     */
    public double scrollAmount() {
        return scrollAmount;
    }

    /**
     * Set the scroll amount, clamped to [0, maxScrollAmount].
     */
    public void setScrollAmount(double scrollAmount) {
        this.scrollAmount = Math.max(0, Math.min(scrollAmount, maxScrollAmount()));
    }

    /**
     * @return maximum scroll amount (content height minus visible height)
     */
    public int maxScrollAmount() {
        return Math.max(0, contentHeight() - this.height);
    }

    /**
     * @return whether the content is tall enough to scroll
     */
    protected boolean scrollable() {
        return maxScrollAmount() > 0;
    }

    /**
     * Re-clamp the current scroll amount after content size changes.
     */
    public void refreshScrollAmount() {
        setScrollAmount(this.scrollAmount);
    }

    // ──── Scrollbar geometry ────

    /**
     * @return scrollbar width from settings
     */
    public int scrollbarWidth() {
        return scrollbarSettings.scrollbarWidth();
    }

    /**
     * @return scroller (thumb) height, proportional to visible/content ratio
     */
    protected int scrollerHeight() {
        int ch = contentHeight();
        if (ch <= 0)
            return scrollbarSettings.scrollerMinHeight();
        int h = (int) ((float) (this.height * this.height) / ch);
        return Math.max(scrollbarSettings.scrollerMinHeight(), Math.min(h, this.height - 8));
    }

    /**
     * @return X position of the scrollbar
     */
    protected int scrollBarX() {
        return this.x + this.width - scrollbarWidth();
    }

    /**
     * @return Y position of the scroller (thumb)
     */
    public int scrollBarY() {
        if (maxScrollAmount() == 0)
            return this.y;
        int barH = this.height - scrollerHeight();
        int scrollY = (int) (scrollAmount * barH / maxScrollAmount()) + this.y;
        return Math.max(this.y, scrollY);
    }

    /**
     * Check if the given point is over the scrollbar area.
     */
    protected boolean isOverScrollbar(int mx, int my) {
        int sbx = scrollBarX();
        return mx >= sbx && mx <= sbx + scrollbarWidth()
                && my >= this.y && my < this.y + this.height;
    }

    /**
     * Begin scrollbar dragging if clicking on the scrollbar.
     *
     * @return true if scrollbar dragging started
     */
    public boolean updateScrolling(int mx, int my, int mouseButton) {
        this.scrolling = scrollable() && mouseButton == 0 && isOverScrollbar(mx, my);
        if (this.scrolling) {
            // Record the drag-start Y so mouseDrag can accumulate relative deltas.
            this.lastDragY = my;
        }
        return this.scrolling;
    }

    // ──── Scissor clipping ────

    /**
     * Enable GL scissor to clip rendering to this container's bounds.
     * <p>
     * The container geometry lives in GUI-scale space, while {@code glScissor}
     * expects framebuffer pixels, so every coordinate is multiplied by the GUI
     * scale factor resolved from {@link ScaledResolution}.
     * </p>
     */
    protected void enableScissor() {
        Minecraft mc = Minecraft.getMinecraft();
        int scaleFactor = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(this.x * scaleFactor,
                mc.displayHeight - (this.y + this.height) * scaleFactor,
                this.width * scaleFactor,
                this.height * scaleFactor);
    }

    /**
     * Disable GL scissor.
     */
    protected void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // ──── Scrollbar rendering ────

    /**
     * Render the scrollbar (background track + scroller thumb) via mcmeta-driven
     * textures.
     * <p>
     * Stretching parameters are read automatically when the corresponding
     * {@code .mcmeta} exists; otherwise it falls back to nine_patch 6x32
     * border=1.
     * </p>
     */
    protected void renderScrollbar(int mouseX, int mouseY) {
        int sbx = scrollBarX();
        int sbw = scrollbarWidth();
        int scrollerH = scrollerHeight();
        int scrollerY = scrollBarY();

        // Background track — auto-detect from mcmeta, fallback nine_patch 6x32 border=1
        TextureStretching.drawAuto(SCROLLER_BACKGROUND_TEXTURE,
                sbx, this.y, sbw, this.height,
                TextureStretching.StretchType.NINE_PATCH,
                SCROLLBAR_TEX_W, SCROLLBAR_TEX_H,
                SCROLLBAR_TEX_BORDER, SCROLLBAR_TEX_BORDER,
                SCROLLBAR_TEX_BORDER, SCROLLBAR_TEX_BORDER);

        if (scrollable()) {
            // Scroller thumb — same mcmeta-driven approach
            TextureStretching.drawAuto(SCROLLER_TEXTURE,
                    sbx, scrollerY, sbw, scrollerH,
                    TextureStretching.StretchType.NINE_PATCH,
                    SCROLLBAR_TEX_W, SCROLLBAR_TEX_H,
                    SCROLLBAR_TEX_BORDER, SCROLLBAR_TEX_BORDER,
                    SCROLLBAR_TEX_BORDER, SCROLLBAR_TEX_BORDER);
        }
    }

    // ──── Event handling ────

    @Override
    public void mouseScrolled(int delta) {
        if (!visible)
            return;
        setScrollAmount(scrollAmount() - delta * scrollRate());
    }

    @Override
    public void mouseDrag(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
        if (scrolling) {
            if (mouseY < this.y) {
                setScrollAmount(0);
            } else if (mouseY > this.y + this.height) {
                setScrollAmount(maxScrollAmount());
            } else {
                double max = Math.max(1, maxScrollAmount());
                int barH = scrollerHeight();
                double yDragScale = Math.max(1.0, max / (this.height - barH));
                // Accumulate the relative mouse delta (mouseY - lastDragY) instead of the
                // absolute mouseY, matching the high-version AbstractScrollArea formula:
                // setScrollAmount(scrollAmount() + dy * yDragScale).
                setScrollAmount(scrollAmount() + (mouseY - lastDragY) * yDragScale);
            }
            // Sync the delta baseline even on the edge branches, so dragging back into
            // the area resumes smoothly without a jump.
            lastDragY = mouseY;
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
        this.scrolling = false;
        lastDragY = 0;
    }

    /**
     * @return scroll rate from settings
     */
    protected double scrollRate() {
        return scrollbarSettings.scrollRate();
    }

    /**
     * @return the total height of all content (provided by subclass)
     */
    protected abstract int contentHeight();

    // ──── Getters ────

    public ScrollbarSettings getScrollbarSettings() {
        return scrollbarSettings;
    }

    /**
     * @return the bottom Y coordinate
     */
    public int getBottom() {
        return this.y + this.height;
    }

    /**
     * @return the right X coordinate
     */
    public int getRight() {
        return this.x + this.width;
    }
}
