package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * <p>
 * 滚动面板基座 —— 提供滚动偏移、滚动条渲染和 Scissor 裁剪能力。<br>
 * 对标高版本 Minecraft 的 {@code AbstractScrollArea}。
 * </p>
 * <p>
 * Scroll panel base — provides scroll offset, scrollbar rendering and Scissor
 * clipping. Counterpart of the high-version Minecraft {@code AbstractScrollArea}.
 * </p>
 */
public abstract class AbstractScrollArea extends AbstractComponent {

    /** Scrollbar width constant / 滚动条宽度常量 */
    public static final int SCROLLBAR_WIDTH = 6;

    /** Scroller (thumb) texture — 6x32, border 1 nine-patch / 滑块纹理 */
    protected static final ResourceLocation SCROLLER_TEXTURE =
            new ResourceLocation("catframe", "textures/gui/widgets/scroll.png");

    /** Scrollbar background track texture — 6x32, border 1 nine-patch / 滚动条背景轨道纹理 */
    protected static final ResourceLocation SCROLLER_BACKGROUND_TEXTURE =
            new ResourceLocation("catframe", "textures/gui/widgets/scroll_background.png");

    /** Scrollbar texture dimensions / 滚动条纹理尺寸 */
    protected static final int SCROLLBAR_TEX_W = 6;
    protected static final int SCROLLBAR_TEX_H = 32;
    protected static final int SCROLLBAR_TEX_BORDER = 1;

    private final ScrollbarSettings scrollbarSettings;
    private double scrollAmount;
    private boolean scrolling;

    public AbstractScrollArea(int x, int y, int width, int height, ScrollbarSettings scrollbarSettings) {
        super(x, y, width, height);
        this.scrollbarSettings = scrollbarSettings;
    }

    // ──── Scroll amount ────

    /**
     * @return current scroll offset in pixels / 当前滚动偏移量（像素）
     */
    public double scrollAmount() {
        return scrollAmount;
    }

    /**
     * Set the scroll amount, clamped to [0, maxScrollAmount].
     * <p>设置滚动偏移量，限制在 [0, maxScrollAmount] 范围内。</p>
     */
    public void setScrollAmount(double scrollAmount) {
        this.scrollAmount = Math.max(0, Math.min(scrollAmount, maxScrollAmount()));
    }

    /**
     * @return maximum scroll amount (content height minus visible height) / 最大滚动量
     */
    public int maxScrollAmount() {
        return Math.max(0, contentHeight() - this.height);
    }

    /**
     * @return whether the content is tall enough to scroll / 内容是否可滚动
     */
    protected boolean scrollable() {
        return maxScrollAmount() > 0;
    }

    /**
     * Re-clamp the current scroll amount after content size changes.
     * <p>内容尺寸变化后重新限制滚动量。</p>
     */
    public void refreshScrollAmount() {
        setScrollAmount(this.scrollAmount);
    }

    // ──── Scrollbar geometry ────

    /**
     * @return scrollbar width from settings / 滚动条宽度
     */
    public int scrollbarWidth() {
        return scrollbarSettings.scrollbarWidth();
    }

    /**
     * @return scroller (thumb) height, proportional to visible/content ratio / 滑块高度
     */
    protected int scrollerHeight() {
        int ch = contentHeight();
        if (ch <= 0) return scrollbarSettings.scrollerMinHeight();
        int h = (int) ((float) (this.height * this.height) / ch);
        return Math.max(scrollbarSettings.scrollerMinHeight(), Math.min(h, this.height - 8));
    }

    /**
     * @return X position of the scrollbar / 滚动条 X 坐标
     */
    protected int scrollBarX() {
        return this.x + this.width - scrollbarWidth();
    }

    /**
     * @return Y position of the scroller (thumb) / 滑块 Y 坐标
     */
    public int scrollBarY() {
        if (maxScrollAmount() == 0) return this.y;
        int barH = this.height - scrollerHeight();
        int scrollY = (int) (scrollAmount * barH / maxScrollAmount()) + this.y;
        return Math.max(this.y, scrollY);
    }

    /**
     * Check if the given point is over the scrollbar area.
     * <p>判断给定点是否在滚动条区域内。</p>
     */
    protected boolean isOverScrollbar(int mx, int my) {
        int sbx = scrollBarX();
        return mx >= sbx && mx <= sbx + scrollbarWidth()
                && my >= this.y && my < this.y + this.height;
    }

    /**
     * Begin scrollbar dragging if clicking on the scrollbar.
     * <p>如果点击在滚动条上，开始拖动。</p>
     *
     * @return true if scrollbar dragging started / 是否开始拖动滚动条
     */
    public boolean updateScrolling(int mx, int my, int mouseButton) {
        this.scrolling = scrollable() && mouseButton == 0 && isOverScrollbar(mx, my);
        return this.scrolling;
    }

    // ──── Scissor clipping ────

    /**
     * Enable GL scissor to clip rendering to this container's bounds.
     * <p>启用 GL 裁剪，将渲染限制在此容器的范围内。</p>
     */
    protected void enableScissor() {
        int displayHeight = Minecraft.getMinecraft().displayHeight;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(this.x, displayHeight - this.y - this.height, this.width, this.height);
    }

    /**
     * Disable GL scissor.
     * <p>禁用 GL 裁剪。</p>
     */
    protected void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // ──── Scrollbar rendering ────

    /**
     * Render the scrollbar (background track + scroller thumb) via mcmeta-driven textures.
     * <p>通过 mcmeta 数据驱动纹理渲染滚动条（背景轨道 + 滑块）。
     * 当对应 {@code .mcmeta} 存在时自动读取拉伸参数；否则回退到 nine_patch 6x32 border=1。</p>
     */
    protected void renderScrollbar(int mouseX, int mouseY) {
        int sbx = scrollBarX();
        int sbw = scrollbarWidth();
        int scrollerH = scrollerHeight();
        int scrollerY = scrollBarY();

        // Background track — auto-detect from mcmeta, fallback nine_patch 6x32 border=1
        // 背景轨道 — 从 mcmeta 自动检测，回退 nine_patch 6x32 border=1
        TextureStretching.drawAuto(SCROLLER_BACKGROUND_TEXTURE,
                sbx, this.y, sbw, this.height,
                TextureStretching.StretchType.NINE_PATCH,
                SCROLLBAR_TEX_W, SCROLLBAR_TEX_H,
                SCROLLBAR_TEX_BORDER, SCROLLBAR_TEX_BORDER,
                SCROLLBAR_TEX_BORDER, SCROLLBAR_TEX_BORDER);

        if (scrollable()) {
            // Scroller thumb — same mcmeta-driven approach
            // 滑块 — 同样的 mcmeta 驱动方式
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
        if (!visible) return;
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
                setScrollAmount(scrollAmount() + mouseY * yDragScale);
            }
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
        this.scrolling = false;
    }

    /**
     * @return scroll rate from settings / 每次滚轮的滚动量
     */
    protected double scrollRate() {
        return scrollbarSettings.scrollRate();
    }

    /**
     * @return the total height of all content (provided by subclass) / 内容总高度
     */
    protected abstract int contentHeight();

    // ──── Getters ────

    public ScrollbarSettings getScrollbarSettings() {
        return scrollbarSettings;
    }

    /**
     * @return the bottom Y coordinate / 底部 Y 坐标
     */
    public int getBottom() {
        return this.y + this.height;
    }

    /**
     * @return the right X coordinate / 右侧 X 坐标
     */
    public int getRight() {
        return this.x + this.width;
    }
}
