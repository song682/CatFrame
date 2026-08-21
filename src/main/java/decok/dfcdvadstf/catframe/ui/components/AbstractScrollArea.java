package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * <p>
 * 滚动面板基座 —— 提供滚动偏移、滚动条渲染和 Scissor 裁剪能力。<br>
 * 对标高版本 Minecraft 的 {@code AbstractScrollArea}。
 * </p>
 * <p>
 * Scroll panel base — provides scroll offset, scrollbar rendering and Scissor
 * clipping. Counterpart of the high-version Minecraft
 * {@code AbstractScrollArea}.
 * </p>
 */
public abstract class AbstractScrollArea extends AbstractComponent {

    /** Scrollbar width constant / 滚动条宽度常量 */
    public static final int SCROLLBAR_WIDTH = 6;

    /** Scroller (thumb) texture — 6x32, border 1 nine-patch / 滑块纹理 */
    protected static final ResourceLocation SCROLLER_TEXTURE = new ResourceLocation("catframe",
            "textures/gui/widgets/scroll.png");

    /**
     * Scrollbar background track texture — 6x32, border 1 nine-patch / 滚动条背景轨道纹理
     */
    protected static final ResourceLocation SCROLLER_BACKGROUND_TEXTURE = new ResourceLocation("catframe",
            "textures/gui/widgets/scroll_background.png");

    /** Scrollbar texture dimensions / 滚动条纹理尺寸 */
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
     * <p>
     * 拖动期间上一次的鼠标 Y，用于计算相对位移增量（事件接口只提供绝对坐标）。
     * </p>
     */
    private double lastDragY;

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
     * <p>
     * 设置滚动偏移量，限制在 [0, maxScrollAmount] 范围内。
     * </p>
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
     * <p>
     * 内容尺寸变化后重新限制滚动量。
     * </p>
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
        if (ch <= 0)
            return scrollbarSettings.scrollerMinHeight();
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
        if (maxScrollAmount() == 0)
            return this.y;
        int barH = this.height - scrollerHeight();
        int scrollY = (int) (scrollAmount * barH / maxScrollAmount()) + this.y;
        return Math.max(this.y, scrollY);
    }

    /**
     * Check if the given point is over the scrollbar area.
     * <p>
     * 判断给定点是否在滚动条区域内。
     * </p>
     */
    protected boolean isOverScrollbar(int mx, int my) {
        int sbx = scrollBarX();
        return mx >= sbx && mx <= sbx + scrollbarWidth()
                && my >= this.y && my < this.y + this.height;
    }

    /**
     * Begin scrollbar dragging if clicking on the scrollbar.
     * <p>
     * 如果点击在滚动条上，开始拖动。
     * </p>
     *
     * @return true if scrollbar dragging started / 是否开始拖动滚动条
     */
    public boolean updateScrolling(int mx, int my, int mouseButton) {
        this.scrolling = scrollable() && mouseButton == 0 && isOverScrollbar(mx, my);
        if (this.scrolling) {
            // Record the drag-start Y so mouseDrag can accumulate relative deltas.
            // 记录拖动起始 Y，使 mouseDrag 可以累积相对位移增量。
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
     * <p>
     * 启用 GL 裁剪，将渲染限制在此容器的范围内。
     * 容器几何位于 GUI 缩放坐标系，而 {@code glScissor} 需要帧缓冲像素坐标，
     * 因此所有坐标都要乘以 {@link ScaledResolution} 解析出的 GUI 缩放系数。
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
     * <p>
     * 禁用 GL 裁剪。
     * </p>
     */
    protected void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // ──── Scrollbar rendering ────

    /**
     * Render the scrollbar (background track + scroller thumb) via mcmeta-driven
     * textures.
     * <p>
     * 通过 mcmeta 数据驱动纹理渲染滚动条（背景轨道 + 滑块）。
     * 当对应 {@code .mcmeta} 存在时自动读取拉伸参数；否则回退到 nine_patch 6x32 border=1。
     * </p>
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
                // 累加相对鼠标位移（mouseY - lastDragY）而非绝对坐标，
                // 与高版本 AbstractScrollArea 公式 setScrollAmount(scrollAmount() + dy * yDragScale)
                // 一致。
                setScrollAmount(scrollAmount() + (mouseY - lastDragY) * yDragScale);
            }
            // Sync the delta baseline even on the edge branches, so dragging back into
            // the area resumes smoothly without a jump.
            // 边界分支同样同步基准点，拖回区域内时不会产生跳动。
            lastDragY = mouseY;
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
        this.scrolling = false;
        lastDragY = 0;
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
